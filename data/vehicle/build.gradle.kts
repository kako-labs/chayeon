import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

val androidConfig = Properties().apply {
    rootProject.file("config/android-test.properties").inputStream().use { load(it) }
}
android {
    namespace = "dev.chayeon.data.vehicle"
    compileSdk = androidConfig.getProperty("compileSdk").toInt()
    buildToolsVersion = androidConfig.getProperty("buildTools")
    useLibrary("android.car", false)
    sourceSets["main"].assets.srcDir(rootProject.file("fixtures"))
    defaultConfig {
        minSdk = androidConfig.getProperty("minSdk").toInt()
    }
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
tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
    maxParallelForks = 1
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:signature"))
    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.vehicle)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
