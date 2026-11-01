pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        // JediTerm is published only here (not on Maven Central); nothing else may come from it (AM-501, ADR 0012).
        exclusiveContent {
            forRepository {
                maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
            }
            filter { includeGroup("org.jetbrains.jediterm") }
        }
        mavenCentral()
        google()
    }
}

rootProject.name = "quayside"

// Modules are added by their roadmap tasks and by /new-module.
include(":app")
include(":core:domain")
include(":core:process")
include(":core:storage")
include(":core:testing")
include(":feature:agent-runtime:api")
include(":feature:agent-runtime:impl")
include(":feature:command-palette:api")
include(":feature:command-palette:impl")
include(":feature:command-palette:ui")
include(":feature:mission-control:ui")
include(":feature:notifications:api")
include(":feature:notifications:impl")
include(":feature:notifications:ui")
include(":feature:projects:api")
include(":feature:projects:impl")
include(":feature:projects:ui")
include(":feature:sessions:api")
include(":feature:sessions:impl")
include(":feature:terminal:api")
include(":feature:terminal:impl")
include(":feature:terminal:ui")
include(":tests:architecture")
