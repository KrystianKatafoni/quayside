// Port to the Claude Code supervisor (docs/11 §4, ADR 0002): runtime sessions, hook events and the
// hook installer. No implementation here; adapters live in agent-runtime:impl.
plugins {
    id("am.feature.api")
}

dependencies {
    api(project(":core:domain"))

    // Contracts, RuntimeWorld, fakes and builders live in testFixtures so every AgentRuntime
    // implementation runs the same tests and other features test against the fakes.
    testFixturesApi(project(":core:testing"))
    testFixturesImplementation(platform(libs.junit.bom))
    testFixturesImplementation(libs.junit.jupiter)
    testFixturesImplementation(libs.kotest.assertions)
    testFixturesImplementation(libs.kotlinx.coroutines.test)
}
