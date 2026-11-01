// Notification settings and the vocabulary of the event x channel matrix (docs/07, docs/11 §4). No
// implementation here; it lives in notifications:impl.
plugins {
    id("am.feature.api")
}

dependencies {
    api(project(":core:domain"))

    // The contract and the fake live in testFixtures so every implementation runs the same tests.
    testFixturesApi(project(":core:testing"))
    testFixturesImplementation(platform(libs.junit.bom))
    testFixturesImplementation(libs.junit.jupiter)
    testFixturesImplementation(libs.kotest.assertions)
    testFixturesImplementation(libs.kotlinx.coroutines.test)
}
