// In-memory CommandRegistry (AM-406), exposed through the commandPaletteModule factory.
plugins { id("am.feature.impl") }

dependencies {
    implementation(project(":feature:command-palette:api"))
    implementation(project(":core:domain"))

    // CommandRegistryContract, FakeCommandProvider and aCommand.
    testImplementation(testFixtures(project(":feature:command-palette:api")))
    // RecordingLogger.
    testImplementation(project(":core:testing"))
}
