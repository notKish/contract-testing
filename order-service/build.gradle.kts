plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.pact)
}

// pact-jvm's own POM pins Spring 5.3; spring-boot's BOM (Spring 6) is applied by the plugin and wins.
dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // Consumer side (HTTP): generates the customer-service pact + the order.created message pact.
    testImplementation(libs.pact.consumer.junit5)
    // Provider side (messages): verifies the order.created pact against the real producer code.
    testImplementation(libs.pact.provider.junit5)
}

/**
 * Broker settings come from the environment so nothing secret is committed:
 * PACT_BROKER_BASE_URL / PACT_BROKER_USERNAME / PACT_BROKER_PASSWORD.
 */
pact {
    broker {
        pactBrokerUrl = System.getenv("PACT_BROKER_BASE_URL") ?: "http://localhost:9292"
        if (System.getenv("PACT_BROKER_USERNAME") != null) {
            pactBrokerUsername = System.getenv("PACT_BROKER_USERNAME")
            pactBrokerPassword = System.getenv("PACT_BROKER_PASSWORD")
        }
    }
    publish {
        consumerBranch = System.getenv("PACT_CONSUMER_BRANCH") ?: "main"
        pactDirectory =
            layout.buildDirectory
                .dir("pacts")
                .get()
                .asFile.absolutePath
    }
}

tasks.withType<Test>().configureEach {
    // Pacts come from the broker, so this needs `docker compose -f docker-compose.broker.yml up -d`.
    // Credentials come from the environment; the values below match docker-compose.broker.yml.
    systemProperty("pactbroker.url", System.getenv("PACT_BROKER_BASE_URL") ?: "http://localhost:9292")
    systemProperty("pactbroker.auth.username", System.getenv("PACT_BROKER_USERNAME") ?: "pact")
    systemProperty("pactbroker.auth.password", System.getenv("PACT_BROKER_PASSWORD") ?: "pact")
    // Verification results are published back to the broker, keyed by provider version + branch.
    systemProperty("pact.provider.version", project.version.toString())
    systemProperty("pact.provider.branch", System.getenv("PACT_PROVIDER_BRANCH") ?: "main")
    systemProperty("pact.verifier.publishResults", "true")

    // Verification reads the broker, so the consumer's pact must be published first. This keeps
    // `./gradlew test` a single command; without it the provider can run before the publish.
    // dependsOn(":notification-service:pactPublish")
}

// Publishing only needs the pacts the consumer tests generate. Depending on `test` would also run the
// message provider verification, which needs the partner's pact to already be in the broker — a
// deadlock on a cold broker.
val pactConsumerTest =
    tasks.register<Test>("pactConsumerTest") {
        description = "Runs only the consumer contract tests that generate this service's pacts."
        group = "verification"
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        filter { includeTestsMatching("*PactTest") }
    }

tasks.named("pactPublish") { dependsOn(pactConsumerTest) }
