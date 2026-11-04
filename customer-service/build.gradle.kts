plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

// pact-jvm's own POM pins Spring 5.3; spring-boot's BOM (Spring 6) is applied by the plugin and wins.
dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // Provider side: verifies the HTTP pacts published by order-service.
    testImplementation(libs.pact.provider.junit5spring)
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
}
