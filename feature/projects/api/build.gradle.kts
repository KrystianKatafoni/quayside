// Project catalogue and directory inspection (docs/11 §4). No implementation here; it lives in
// projects:impl.
plugins {
    id("am.feature.api")
}

dependencies {
    api(project(":core:domain"))

    // Contracts, fakes and builders live in testFixtures so every implementation runs the same
    // tests and other features test against the fakes.
    testFixturesApi(project(":core:testing"))
    testFixturesImplementation(platform(libs.junit.bom))
    testFixturesImplementation(libs.junit.jupiter)
    testFixturesImplementation(libs.kotest.assertions)
    testFixturesImplementation(libs.kotlinx.coroutines.test)
}
