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
    // Where this provider reads its partner pacts from. Override with -Dpact.pactdir=... (e.g. a broker export).
    systemProperty(
        "pact.pactdir",
        System.getProperty("pact.pactdir")
            ?: rootProject.file("order-service/build/pacts").absolutePath,
    )
    dependsOn(":order-service:test")
}
