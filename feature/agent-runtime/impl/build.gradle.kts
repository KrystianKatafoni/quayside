// Adapters of the agent-runtime port (docs/11 §4, ADR 0002): the parser of `claude agents --json`
// (AM-202) and ClaudeCliAdapter with its module factory (AM-203).
plugins {
    id("am.feature.impl")
}

dependencies {
    implementation(project(":feature:agent-runtime:api"))
    implementation(project(":core:domain"))
    implementation(project(":core:process"))
    // Tree parsing only (Json.parseToJsonElement), so the serialization compiler plugin is not needed.
    implementation(libs.kotlinx.serialization.json)

    // aRuntimeSession and other builders (they bring core:testing with T0).
    testImplementation(testFixtures(project(":feature:agent-runtime:api")))
    // FakeProcessRunner for the adapter unit tests.
    testImplementation(testFixtures(project(":core:process")))
    testImplementation(project(":core:testing"))
}
