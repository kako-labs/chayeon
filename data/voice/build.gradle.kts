import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

val androidConfig = Properties().apply {
    rootProject.file("config/android-test.properties").inputStream().use { load(it) }
}
android {
    namespace = "dev.chayeon.data.voice"
    compileSdk = androidConfig.getProperty("compileSdk").toInt()
    buildToolsVersion = androidConfig.getProperty("buildTools")
    defaultConfig { minSdk = androidConfig.getProperty("minSdk").toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint {
        abortOnError = true
        warningsAsErrors = true
        disable += setOf("OldTargetApi", "AndroidGradlePluginVersion", "GradleDependency")
    }
    packaging { resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1") }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core:model"))
    implementation(libs.core.ktx)
    implementation(libs.pleos.tts) { exclude(group = "ai.pleos.playground.security") }
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
