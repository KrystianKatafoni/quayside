// Popups of the notifications (FR-N3, AM-702): the always-on-top window with the stack of cards over the popups of the
// NotificationCenter.
plugins {
    id("am.feature.ui")
}

dependencies {
    implementation(project(":feature:notifications:api"))
    implementation(project(":core:domain"))
    // The list of registered projects for the project exceptions (AM-706, docs/11 section 3, rule 1).
    implementation(project(":feature:projects:api"))
    // Colours, shape and typography of the screens come from AmTheme (AM-806, docs/11 section 3, rule 1).
    implementation(project(":core:design-system"))

    // RecordingNotificationCenter and FakeNotificationSettingsRepo for the controller and card tests.
    testImplementation(testFixtures(project(":feature:notifications:api")))
    // aProject and FakeProjectsApi for the project exceptions (AM-706).
    testImplementation(testFixtures(project(":feature:projects:api")))
    // AmTestTheme and SampleTokens: screens are shown in the plex theme.
    testImplementation(testFixtures(project(":core:design-system")))
    // Compose UI tests (runComposeUiTest) with the desktop runtime of the current OS.
    testImplementation(libs.compose.ui.test)
    testImplementation(compose.desktop.currentOs)
}
