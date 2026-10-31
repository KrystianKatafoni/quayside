// Projects sidebar (docs/11 §1, MVI): the list with selection, adding a folder from disk after a
// read-only inspection, renaming, per-project session counts and removing with a confirmation dialog
// (AM-304, AM-305).
plugins {
    id("am.feature.ui")
}

dependencies {
    implementation(project(":feature:projects:api"))
    // Session counts per project and "stop all sessions in a project" for the remove dialog (FR-P6, FR-P4, AM-305).
    implementation(project(":feature:sessions:api"))
    implementation(project(":core:domain"))

    // FakeProjectsApi, FakeProjectInspector and aProject for the controller and UI tests.
    testImplementation(testFixtures(project(":feature:projects:api")))
    // FakeSessions, SessionsWorld and aSession for the counts, the remove flow and the rename scenarios.
    testImplementation(testFixtures(project(":feature:sessions:api")))
    // RecordingLogger and aSessionId.
    testImplementation(project(":core:testing"))
    // Compose UI tests (runComposeUiTest) with the desktop runtime of the current OS.
    testImplementation(libs.compose.ui.test)
    testImplementation(compose.desktop.currentOs)
}
