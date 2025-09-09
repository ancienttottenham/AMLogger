pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AMLoggerProject"
if (System.getenv("JITPACK") != "true") {
    include(":app")
}
include(":amlogger")

val wantsCore =
    gradle.startParameter.taskNames.any { it.contains(":amlogger-core") }

if (wantsCore && file("amlogger-core").isDirectory) {
    include(":amlogger-core")
}