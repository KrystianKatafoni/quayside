plugins {
    `kotlin-dsl`
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.kts", "src/**/*.kts")
        ktlint(libs.versions.ktlint.get())
    }
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.compose.compiler.gradle.plugin)
    implementation(libs.compose.gradle.plugin)
    implementation(libs.spotless.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.kover.gradle.plugin)

    testImplementation(gradleTestKit())
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // TestKit builds resolve the same catalog as the real build.
    systemProperty("am.versionCatalog", rootDir.resolve("../gradle/libs.versions.toml").canonicalPath)
    systemProperty("am.detektConfig", rootDir.resolve("../config/detekt/detekt.yml").canonicalPath)
    // Each TestKit build downloads dependencies; keep the suite bounded.
    maxParallelForks = 1
}
