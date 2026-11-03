// Appearance: the catalog of themes and typographies (FR-A1, FR-A2, docs/11 §4). AM-801 has the read-only catalog;
// no implementation here, it lives in appearance:impl.
plugins {
    id("am.feature.api")
}

dependencies {
    api(project(":core:design-tokens"))

    // The contract and the fake live in testFixtures so every implementation runs the same tests.
    testFixturesImplementation(platform(libs.junit.bom))
    testFixturesImplementation(libs.junit.jupiter)
    testFixturesImplementation(libs.kotest.assertions)
    // SampleTokens for the fake's catalog.
    testFixturesImplementation(testFixtures(project(":core:design-tokens")))

    // SampleTokens for the catalog tests.
    testImplementation(testFixtures(project(":core:design-tokens")))
}
