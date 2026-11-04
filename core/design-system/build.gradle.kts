// Design system of the app (FR-A1, NFR-6; docs/11 §2-§3): AmTheme provides colour, shape and typography tokens to
// Compose; small components read them. Bundled OFL fonts of the eight base typographies live in resources (ADR 0021).
plugins {
    id("am.feature.ui")
    // AmTestTheme: one test wrapper for the UI tests of every screen module (AM-806).
    `java-test-fixtures`
}

kotlin {
    explicitApi()
}

dependencies {
    api(project(":core:design-tokens"))
    api(libs.compose.runtime)
    api(libs.compose.foundation)

    // AmTestTheme wraps the plex tokens of SampleTokens, so its users get them too.
    testFixturesApi(testFixtures(project(":core:design-tokens")))

    // SampleTokens and ContrastAudit for the theme and component colour tests.
    testImplementation(testFixtures(project(":core:design-tokens")))
    // Compose UI tests (runComposeUiTest) with the desktop runtime of the current OS.
    testImplementation(libs.compose.ui.test)
    testImplementation(compose.desktop.currentOs)
}

tasks.test {
    // Font tests use java.awt.Font without a screen (CI).
    systemProperty("java.awt.headless", "true")
}
