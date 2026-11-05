// Appearance screen (FR-A3, FR-A4, docs/08, ADR 0022): the dialog with the lists of themes and typographies, the sizes,
// the terminal font and the live preview that `app` fills in (AM-803).
plugins {
    id("am.feature.ui")
}

dependencies {
    implementation(project(":feature:appearance:api"))
    // The terminal font and size are saved through TerminalSettingsRepo (decision 3 of AM-803).
    implementation(project(":feature:terminal:api"))
    implementation(project(":core:domain"))
    // Colours, shape and typography of the screen come from AmTheme (AM-806, docs/11 section 3, rule 1).
    implementation(project(":core:design-system"))

    // FakeAppearanceApi (showcaseCatalog) for the controller and UI tests.
    testImplementation(testFixtures(project(":feature:appearance:api")))
    // FakeTerminalSettingsRepo.
    testImplementation(testFixtures(project(":feature:terminal:api")))
    // AmTestTheme and SampleTokens: the dialog is shown in the plex theme.
    testImplementation(testFixtures(project(":core:design-system")))
    // Compose UI tests (runComposeUiTest) with the desktop runtime of the current OS.
    testImplementation(libs.compose.ui.test)
    testImplementation(compose.desktop.currentOs)
}
