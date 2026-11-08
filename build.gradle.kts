// Root build: shared configuration for the service projects.
// The project version is the git commit hash, not a hand-edited build number and not a
// "-dirty" variant. It keys every pact verification result in the broker, so a fixed value like
// "0.1.0" silently overwrites the previous result on each build: a failing verification gets masked
// by a later passing one. PACT_VERSION overrides it for CI systems that expose their own identifier,
// or for builds without a git checkout.
//
// A version must identify exactly one tree, so a dirty working tree cannot be published: pactPublish
// refuses to run, and test runs from a dirty tree do not publish their verification results.
fun git(vararg args: String): String =
    providers
        .exec {
            commandLine("git", *args)
            isIgnoreExitValue = true
        }.standardOutput
        .asText
        .get()
        .trim()

val pactVersion: String =
    providers.environmentVariable("PACT_VERSION").orNull
        ?: git("rev-parse", "--short=12", "HEAD").ifEmpty { "0.0.0-unknown" }

// Publishing from a dirty tree would attach uncommitted content to a commit hash.
val treeIsClean: Boolean = git("status", "--porcelain").isEmpty()

// The branch recorded with each published pact and provider contract. It defaults to the checked-out
// branch: publishing a feature branch as `main` lets it satisfy main-branch selectors it was never
// verified on. CI overrides per side with PACT_CONSUMER_BRANCH / PACT_PROVIDER_BRANCH; a detached
// HEAD (no branch name) falls back to `main` unless one of those is set.
val gitBranch =
    git("rev-parse", "--abbrev-ref", "HEAD").takeIf { it.isNotEmpty() && it != "HEAD" } ?: "main"

extensions.extraProperties.set("pactConsumerBranch", System.getenv("PACT_CONSUMER_BRANCH") ?: gitBranch)
extensions.extraProperties.set("pactProviderBranch", System.getenv("PACT_PROVIDER_BRANCH") ?: gitBranch)
extensions.extraProperties.set("pactTreeClean", treeIsClean)

allprojects {
    group = "com.example.contracttesting"
    version = pactVersion

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // Pact files are written to <project>/build/pacts (Maven's pact.rootDir, but per project).
        systemProperty(
            "pact.rootDir",
            layout.buildDirectory
                .dir("pacts")
                .get()
                .asFile.absolutePath,
        )
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
tasks.register<Copy>("downloadJavadocs") {
    val ownGroup = project.group.toString()
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    val deps = listOf("runtimeClasspath", "testRuntimeClasspath")
        .flatMap {
            project.configurations.getByName(it)
                .resolvedConfiguration.lenientConfiguration.artifacts
        }
        .map { it.moduleVersion.id }
        .filter { it.group != ownGroup }
        .filter { it.name == "consumer" }   // only this artifact
        .distinct()
        .map { project.dependencies.create("${it.group}:${it.name}:${it.version}:javadoc@jar") }
        .toTypedArray()

    val jars = project.configurations.detachedConfiguration(*deps)
        .resolvedConfiguration.lenientConfiguration
        .getArtifacts()
        .map { it.file }

    jars.forEach { jar ->
        from(zipTree(jar)) {
            into(jar.nameWithoutExtension.removeSuffix("-javadoc"))
        }
    }
    into(rootProject.layout.buildDirectory.dir("javadocs"))
}
}
