// Session monitoring and commands (docs/11 §4, ENG-I2): read side SessionMonitor, write side
// SessionCommands. No implementation here; it lives in sessions:impl.
plugins {
    id("am.feature.api")
}

dependencies {
    api(project(":core:domain"))

    // Contracts, SessionsWorld, the fake and builders live in testFixtures so every implementation
    // runs the same tests and other features test against FakeSessions.
    testFixturesApi(project(":core:testing"))
    testFixturesImplementation(platform(libs.junit.bom))
    testFixturesImplementation(libs.junit.jupiter)
    testFixturesImplementation(libs.kotest.assertions)
    testFixturesImplementation(libs.kotlinx.coroutines.test)
}
