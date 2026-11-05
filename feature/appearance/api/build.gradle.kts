// Appearance: the catalog of themes and typographies (FR-A1, FR-A2, docs/11 §4). AM-801 has the read-only catalog;
// no implementation here, it lives in appearance:impl.
plugins {
    id("am.feature.api")
}

dependencies {
    api(project(":core:design-tokens"))
    // Outcome in AppearanceApi.update and the coroutines of StateFlow (AM-803).
    api(project(":core:domain"))

    // The contract and the fake live in testFixtures so every implementation runs the same tests.
    testFixturesImplementation(platform(libs.junit.bom))
    testFixturesImplementation(libs.junit.jupiter)
    testFixturesImplementation(libs.kotest.assertions)
    // runTest in the contract of update (AM-803).
    testFixturesImplementation(libs.kotlinx.coroutines.test)
    // SampleTokens for the fake's catalog.
    testFixturesImplementation(testFixtures(project(":core:design-tokens")))

    // SampleTokens for the catalog tests.
    testImplementation(testFixtures(project(":core:design-tokens")))
}
