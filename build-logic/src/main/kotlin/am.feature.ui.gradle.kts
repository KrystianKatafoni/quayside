import dev.agentmanager.buildlogic.ModuleRules
import dev.agentmanager.buildlogic.enforceModuleRule
import org.gradle.api.artifacts.VersionCatalogsExtension

// Compose screens of a feature. Depends on feature APIs only, never on impl modules.
plugins {
    id("am.kotlin")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    "implementation"(libs.findLibrary("compose-runtime").get())
    "implementation"(libs.findLibrary("compose-foundation").get())
}

enforceModuleRule("ui never depends on impl", ModuleRules::violatesUiRule)
