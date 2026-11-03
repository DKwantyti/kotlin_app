plugins {
    id("info.solidsoft.pitest") version "1.19.0"
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test {
    useJUnitPlatform()
}

pitest {
    pitestVersion.set("1.22.0")
    junit5PluginVersion.set("1.2.3")
    targetTests.set(setOf("com.university.messenger.domain.policy.*Test"))
    coverageThreshold.set(80)
    targetClasses.set(setOf(
        "com.university.messenger.domain.policy.FileUploadPolicy",
        "com.university.messenger.domain.policy.RegistrationRules",
    ))
}