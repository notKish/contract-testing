// Root build: shared configuration for the three service projects.
// The project version is the git commit, not a hand-edited build number.
// It keys every pact verification result in the broker, so a fixed value like "0.1.0" silently
// overwrites the previous result on each build: a failing verification gets masked by a later
// passing one. `--dirty` marks results produced from uncommitted code. PACT_VERSION overrides it
// for CI systems that expose their own identifier, or for builds without a git checkout.
val pactVersion: String =
    providers.environmentVariable("PACT_VERSION").orNull
        ?: providers
            .exec {
                commandLine("git", "describe", "--always", "--dirty", "--abbrev=12")
                isIgnoreExitValue = true
            }.standardOutput
            .asText
            .get()
            .trim()
            .ifEmpty { "0.0.0-unknown" }

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
}
