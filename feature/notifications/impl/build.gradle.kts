// Notifications feature (docs/07, docs/11 §4): the pure decide() of NotificationPolicy and the settings
// repository persisted as notifications.json (AM-701), exposed through notificationSettingsModule.
plugins {
    id("am.feature.impl")
}

dependencies {
    implementation(project(":feature:notifications:api"))
    // SessionEvent and Session: the input of decide().
    implementation(project(":feature:sessions:api"))
    // Outcome and Logger.
    implementation(project(":core:domain"))
    // JsonStorage and StoreSpec for notifications.json.
    implementation(project(":core:storage"))

    // The settings contract and the fake.
    testImplementation(testFixtures(project(":feature:notifications:api")))
    // aSession for the events fed to decide().
    testImplementation(testFixtures(project(":feature:sessions:api")))
    // InMemoryJsonStore for the repository unit tests.
    testImplementation(testFixtures(project(":core:storage")))
    testImplementation(project(":core:testing"))
}
