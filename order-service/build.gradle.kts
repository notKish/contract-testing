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
        pactDirectory =
            layout.buildDirectory
                .dir("pacts")
                .get()
                .asFile.absolutePath
    }
}

tasks.withType<Test>().configureEach {
    // Where this provider reads its partner pacts from. Override with -Dpact.pactdir=... (e.g. a broker export).
    systemProperty(
        "pact.pactdir",
        System.getProperty("pact.pactdir")
            ?: rootProject.file("notification-service/build/pacts").absolutePath,
    )
    dependsOn(":notification-service:test")
}
