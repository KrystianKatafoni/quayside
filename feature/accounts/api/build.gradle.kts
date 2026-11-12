// Quayside accounts (FR-L1, FR-L2, FR-L9, docs/11 §4, ADR 0029, ADR 0035): the account model, the stored list
// (AccountsRepo), the disk side (AccountSetup) and the pure rules and selector. No implementation here; it lives
// in accounts:impl.
plugins {
    id("am.feature.api")
}

dependencies {
    api(project(":core:domain"))

    // The contracts and the fake live in testFixtures so every implementation runs the same tests.
    testFixturesApi(project(":core:testing"))
    testFixturesImplementation(platform(libs.junit.bom))
    testFixturesImplementation(libs.junit.jupiter)
    testFixturesImplementation(libs.kotest.assertions)
    testFixturesImplementation(libs.kotlinx.coroutines.test)
}
