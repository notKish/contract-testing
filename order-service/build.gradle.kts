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
 * Broker settings come from the environment so nothing secret is committed.
 * PACT_BROKER_BASE_URL plus either PACT_BROKER_TOKEN (PactFlow) or
 * PACT_BROKER_USERNAME/PACT_BROKER_PASSWORD (the local docker broker).
 *
 * GitHub Actions turns a missing secret into an empty string, so blank has to mean "unset":
 * otherwise the empty value wins over the fallback.
 */
fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }
val brokerToken = env("PACT_BROKER_TOKEN")
val brokerUser = env("PACT_BROKER_USERNAME")

pact {
    broker {
        pactBrokerUrl = env("PACT_BROKER_BASE_URL") ?: "http://localhost:9292"
        if (brokerToken != null) {
            pactBrokerToken = brokerToken
        } else if (brokerUser != null) {
            pactBrokerUsername = brokerUser
            pactBrokerPassword = env("PACT_BROKER_PASSWORD")
        }
    }
    publish {
        consumerBranch = rootProject.extra["pactConsumerBranch"] as String
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
    systemProperty("pactbroker.url", env("PACT_BROKER_BASE_URL") ?: "http://localhost:9292")
    // pact-jvm uses basic auth whenever a username is set, so the token case has to blank it.
    systemProperty("pactbroker.auth.username", if (brokerToken != null) "" else env("PACT_BROKER_USERNAME") ?: "pact")
    systemProperty("pactbroker.auth.password", if (brokerToken != null) "" else env("PACT_BROKER_PASSWORD") ?: "pact")
    systemProperty("pactbroker.auth.token", brokerToken ?: "")
    // Verification results are published back to the broker, keyed by provider version + branch.
    // A dirty tree would attach uncommitted behaviour to a commit hash, so it only tests locally.
    systemProperty("pact.provider.version", project.version.toString())
    systemProperty("pact.provider.branch", rootProject.extra["pactProviderBranch"] as String)
    systemProperty("pact.verifier.publishResults", (rootProject.extra["pactTreeClean"] as Boolean).toString())

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

tasks.named("pactPublish") {
    dependsOn(pactConsumerTest)
    // Captured as a plain value: touching rootProject inside doFirst breaks the configuration cache.
    val treeClean = rootProject.extra["pactTreeClean"] as Boolean
    doFirst {
        check(treeClean) {
            "Working tree is dirty: commit first so the consumer version is a real commit hash."
        }
    }
}
