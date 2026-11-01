// Popups of the notifications (FR-N3, AM-702): the always-on-top window with the stack of cards over the popups of the
// NotificationCenter.
plugins {
    id("am.feature.ui")
}

dependencies {
    implementation(project(":feature:notifications:api"))
    implementation(project(":core:domain"))

    // RecordingNotificationCenter and FakeNotificationSettingsRepo for the controller and card tests.
    testImplementation(testFixtures(project(":feature:notifications:api")))
    // Compose UI tests (runComposeUiTest) with the desktop runtime of the current OS.
    testImplementation(libs.compose.ui.test)
    testImplementation(compose.desktop.currentOs)
}
