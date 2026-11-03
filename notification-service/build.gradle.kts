plugins {
    java
}

dependencies {
    // Minimal JSON parsing for the consumer; swap for whatever messaging stack you really use.
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")

    // Message consumer side: the consumer DSL + JUnit 5 extension.
    testImplementation(libs.pact.consumer.junit5)
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
