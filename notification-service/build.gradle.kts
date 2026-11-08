plugins {
    java
    alias(libs.plugins.pact)
}

dependencies {
    // Minimal JSON parsing for the consumer; swap for whatever messaging stack you really use.
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")

    // Message consumer side: the consumer DSL + JUnit 5 extension.
    testImplementation(libs.pact.consumer.junit5)
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

/**
 * Broker settings come from the environment so nothing secret is committed.
 * PACT_BROKER_BASE_URL plus either PACT_BROKER_TOKEN (PactFlow) or
 * PACT_BROKER_USERNAME/PACT_BROKER_PASSWORD (the local docker broker).
 */
val brokerToken = System.getenv("PACT_BROKER_TOKEN")

pact {
    broker {
        pactBrokerUrl = System.getenv("PACT_BROKER_BASE_URL") ?: "http://localhost:9292"
        if (brokerToken != null) {
            pactBrokerToken = brokerToken
        } else if (System.getenv("PACT_BROKER_USERNAME") != null) {
            pactBrokerUsername = System.getenv("PACT_BROKER_USERNAME")
            pactBrokerPassword = System.getenv("PACT_BROKER_PASSWORD")
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

// Publishing uploads build/pacts, which the consumer tests generate — so publish after test.
tasks.named("pactPublish") {
    dependsOn("test")
    // Captured as a plain value: touching rootProject inside doFirst breaks the configuration cache.
    val treeClean = rootProject.extra["pactTreeClean"] as Boolean
    doFirst {
        check(treeClean) {
            "Working tree is dirty: commit first so the consumer version is a real commit hash."
        }
    }
}
