// Session summaries: the Summarizer contract that turns the last message of a turn into a short line
// (FR-S7, docs/11 §4). No implementation here; it lives in summaries:impl.
plugins {
    id("am.feature.api")
}

dependencies {
    api(project(":core:domain"))

    // The contract and the fake live in testFixtures so every implementation runs the same tests.
    testFixturesImplementation(platform(libs.junit.bom))
    testFixturesImplementation(libs.junit.jupiter)
    testFixturesImplementation(libs.kotest.assertions)
}
