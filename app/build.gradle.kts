import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

val androidConfig = Properties().apply {
    rootProject.file("config/android-test.properties").inputStream().use { load(it) }
}
android {
    namespace = "dev.chayeon"
    compileSdk = androidConfig.getProperty("compileSdk").toInt()
    buildToolsVersion = androidConfig.getProperty("buildTools")
    defaultConfig {
        minSdk = androidConfig.getProperty("minSdk").toInt()
        applicationId = "dev.chayeon"
        targetSdk = androidConfig.getProperty("targetSdk").toInt()
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val serverUrl = providers.gradleProperty("chayeon.serverUrl").orElse("").get()
            .replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
        buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
        buildConfigField("boolean", "DEMO_CONTROLS_ENABLED", providers.gradleProperty("chayeon.demo").orElse("false").get())
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    signingConfigs {
        create("localRelease") {
            val localStore = rootProject.file("build/signing/release.keystore")
            if (localStore.exists()) {
                storeFile = localStore
                storePassword = System.getenv("CHAYEON_SIGNING_PASSWORD")
                keyAlias = "chayeon-local"
                keyPassword = System.getenv("CHAYEON_SIGNING_PASSWORD")
            }
        }
    }
    buildTypes {
        debug { buildConfigField("boolean", "DEMO_CONTROLS_ENABLED", providers.gradleProperty("chayeon.demo").orElse("true").get()) }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("localRelease")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    val localHttp = providers.gradleProperty("chayeon.serverUrl").orElse("").get().startsWith("http://10.0.2.2:")
    if (localHttp) sourceSets["debug"].res.srcDir("src/localServer/res")
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

roborazzi { outputDir.set(file("src/test/screenshots")) }

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:signature"))
    implementation(project(":core:engine"))
    implementation(project(":core:ui"))
    implementation(project(":data:social"))
    implementation(project(":data:vehicle"))
    implementation(project(":feature:meet"))
    implementation(project(":feature:history"))
    implementation(project(":feature:signature"))
    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.text)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.foundation.layout)
    implementation(libs.compose.animation)
    implementation(libs.compose.animation.core)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.compose.ui.test)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
