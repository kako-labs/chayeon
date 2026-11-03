pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://nexus-playground.pleos.ai/repository/maven-releases/") {
            content { includeGroup("ai.pleos.playground") }
        }
    }
}

rootProject.name = "Chayeon"
include(":core:model", ":core:signature", ":core:engine", ":server")
if (providers.gradleProperty("chayeon.serverOnly").orNull != "true") {
    include(":app", ":core:ui", ":data:social", ":data:vehicle")
    include(":feature:meet", ":feature:history", ":feature:signature")
}
