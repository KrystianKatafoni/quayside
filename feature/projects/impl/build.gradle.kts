// Adapters of the projects feature (docs/11 §4): JsonProjectStore over core:storage (AM-302) and the
// read-only DirectoryInspector over git, the runtime and settings files (AM-303).
plugins {
    id("am.feature.impl")
}

dependencies {
    implementation(project(":feature:projects:api"))
    implementation(project(":core:domain"))
    implementation(project(":core:storage"))
    // AgentRuntime, HookKind and RuntimePhase for the inspector (AM-303).
    implementation(project(":feature:agent-runtime:api"))
    // ProcessRunner for the git calls of the inspector (AM-303).
    implementation(project(":core:process"))

    // Contracts, fakes and builders of the port.
    testImplementation(testFixtures(project(":feature:projects:api")))
    // InMemoryJsonStore for the store unit tests.
    testImplementation(testFixtures(project(":core:storage")))
    // FakeAgentRuntime and aRuntimeSession for the inspector tests.
    testImplementation(testFixtures(project(":feature:agent-runtime:api")))
    // FakeProcessRunner for the git adapter unit tests.
    testImplementation(testFixtures(project(":core:process")))
    testImplementation(project(":core:testing"))
}
