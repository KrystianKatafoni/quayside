import org.gradle.api.artifacts.VersionCatalogsExtension

// The desktop application (composition root). May depend on every module; the only place where
// feature impl modules are wired together (ENG-M8).
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
