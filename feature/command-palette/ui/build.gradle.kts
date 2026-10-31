// Command palette (FR-K1, ADR 0011): the ⌘K overlay that searches and runs the commands of the CommandRegistry (AM-408).
plugins {
    id("am.feature.ui")
}

dependencies {
    implementation(project(":feature:command-palette:api"))
    implementation(project(":core:domain"))

    // FakeCommandRegistry and aCommand for the controller and UI tests.
    testImplementation(testFixtures(project(":feature:command-palette:api")))
    // RecordingLogger.
    testImplementation(project(":core:testing"))
    // Compose UI tests (runComposeUiTest) with the desktop runtime of the current OS.
    testImplementation(libs.compose.ui.test)
    testImplementation(compose.desktop.currentOs)
}
