plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `java-library`
}

kotlin { jvmToolchain(17) }

dependencies {
    api(libs.coroutines.core)
    api(libs.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
}
