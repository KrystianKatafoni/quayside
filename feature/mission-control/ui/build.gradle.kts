// Mission Control (docs/05, docs/11 §1, MVI): header, filter, session list, details panel and mode bar
// for the project selected in the sidebar (AM-405).
plugins {
    id("am.feature.ui")
}

dependencies {
    implementation(project(":feature:sessions:api"))
    // Name and directory of the selected project.
    implementation(project(":feature:projects:api"))
    implementation(project(":core:domain"))

    // FakeSessions, aSession and SessionsWorld for the controller and UI tests.
    testImplementation(testFixtures(project(":feature:sessions:api")))
    // FakeProjectsApi and aProject.
    testImplementation(testFixtures(project(":feature:projects:api")))
    // TestClock and T0.
    testImplementation(project(":core:testing"))
    // Compose UI tests (runComposeUiTest) with the desktop runtime of the current OS.
    testImplementation(libs.compose.ui.test)
    testImplementation(compose.desktop.currentOs)
}
