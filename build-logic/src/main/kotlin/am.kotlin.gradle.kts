import org.gradle.api.artifacts.VersionCatalogsExtension

// Base for every Kotlin module: JVM 21, warnings as errors, JUnit Platform with Kotest assertions.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("am.quality")
}

val libs = the<VersionCatalogsExtension>().named("libs")

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    "testImplementation"(platform(libs.findLibrary("junit-bom").get()))
    "testImplementation"(libs.findLibrary("junit-jupiter").get())
    "testRuntimeOnly"(libs.findLibrary("junit-platform-launcher").get())
    "testImplementation"(libs.findLibrary("kotest-assertions").get())
    "testImplementation"(libs.findLibrary("kotlinx-coroutines-test").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
