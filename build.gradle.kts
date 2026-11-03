import org.gradle.api.artifacts.ProjectDependency

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.roborazzi) apply false
}

val allowedEdges = mapOf(
    ":app" to setOf(":core:model", ":core:signature", ":core:ui", ":data:social", ":data:vehicle",
        ":feature:meet", ":feature:history", ":feature:signature"),
    ":core:model" to emptySet(),
    ":core:signature" to setOf(":core:model"),
    ":core:ui" to setOf(":core:model"),
    ":data:social" to setOf(":core:model"),
    ":data:vehicle" to setOf(":core:model", ":core:signature"),
    ":feature:meet" to setOf(":core:model", ":core:signature", ":core:ui"),
    ":feature:history" to setOf(":core:model", ":core:signature", ":core:ui"),
    ":feature:signature" to setOf(":core:model", ":core:signature", ":core:ui"),
    ":server" to setOf(":core:model", ":core:signature"),
)

val verifyArchitecture by tasks.registering {
    group = "verification"
    description = "Reject module dependencies outside docs/architecture.md."
    doLast {
        subprojects.filter { it.buildFile.exists() }.forEach { module ->
            val allowed = checkNotNull(allowedEdges[module.path]) {
                "Declare ${module.path} in allowedEdges before adding the module"
            }
            module.configurations.forEach { configuration ->
                configuration.dependencies.withType<ProjectDependency>().forEach { dependency ->
                    check(dependency.path == module.path || dependency.path in allowed) {
                        "Forbidden module dependency: ${module.path} -> ${dependency.path}"
                    }
                }
            }
        }
    }
}

val verifyAll by tasks.registering {
    group = "verification"
    description = "Run architecture, host tests, screenshot verification, strict lint, and builds."
    dependsOn(verifyArchitecture)
}

subprojects {
    tasks.matching { it.name == "preBuild" || it.name == "compileKotlin" }.configureEach {
        dependsOn(rootProject.tasks.named("verifyArchitecture"))
    }
    val module = path
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        verifyAll.configure { dependsOn("$module:build") }
    }
    listOf("com.android.application", "com.android.library").forEach { plugin ->
        pluginManager.withPlugin(plugin) {
            tasks.withType<Test>().configureEach {
                val testHome = rootProject.layout.projectDirectory.dir(".gradle/robolectric/home").asFile
                systemProperty("user.home", testHome.absolutePath)
                systemProperty("maven.repo.local", File(System.getProperty("user.home"), ".m2/repository").absolutePath)
                doFirst { testHome.mkdirs() }
            }
            verifyAll.configure {
                dependsOn("$module:testDebugUnitTest", "$module:lintDebug", "$module:assembleDebug")
            }
        }
    }
    pluginManager.withPlugin("io.github.takahirom.roborazzi") {
        verifyAll.configure { dependsOn("$module:verifyRoborazziDebug") }
    }
}
