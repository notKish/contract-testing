// Root build: shared configuration for the three service projects.
allprojects {
    group = "com.example.contracttesting"
    version = "0.1.0"

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
