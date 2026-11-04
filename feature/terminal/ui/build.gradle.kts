// Embedded terminals (FR-T2, ADR 0012): tabs per project, panes, maximize and the key dispatcher (AM-503).
plugins {
    id("am.feature.ui")
}

dependencies {
    implementation(project(":feature:terminal:api"))
    implementation(project(":core:domain"))
    // Colours, shape and typography of the screens come from AmTheme (AM-806, docs/11 section 3, rule 1).
    implementation(project(":core:design-system"))

    // FakeTerminalApi for the controller and UI tests.
    testImplementation(testFixtures(project(":feature:terminal:api")))
    // RecordingLogger.
    testImplementation(project(":core:testing"))
    // AmTestTheme and SampleTokens: screens are shown in the plex theme.
    testImplementation(testFixtures(project(":core:design-system")))
    // Compose UI tests (runComposeUiTest) with the desktop runtime of the current OS.
    testImplementation(libs.compose.ui.test)
    testImplementation(compose.desktop.currentOs)
}
