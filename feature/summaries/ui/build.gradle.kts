// The settings screen of session summaries (FR-S7, AM-606): the switch of the model (Haiku) summary behind
// summaries.json.
plugins {
    id("am.feature.ui")
}

dependencies {
    implementation(project(":feature:summaries:api"))
    implementation(project(":core:domain"))
    // Colours, shape and typography of the screen come from AmTheme (docs/11 section 3, rule 1).
    implementation(project(":core:design-system"))

    // FakeSummarySettingsRepo for the controller and dialog tests.
    testImplementation(testFixtures(project(":feature:summaries:api")))
    // AmTestTheme: the screen is shown in the plex theme.
    testImplementation(testFixtures(project(":core:design-system")))
    // Compose UI tests (runComposeUiTest) with the desktop runtime of the current OS.
    testImplementation(libs.compose.ui.test)
    testImplementation(compose.desktop.currentOs)
}
