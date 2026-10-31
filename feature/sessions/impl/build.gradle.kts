// Sessions feature (docs/11 §4, §6, §7): the pure SessionStateReducer and runtime mappings (AM-402), the
// polling SessionMonitor (AM-403) and the SessionCommands with their overlay (AM-404), exposed through the
// sessionsModule factory.
plugins {
    id("am.feature.impl")
}

dependencies {
    implementation(project(":feature:sessions:api"))
    // RuntimeSession, RuntimePhase, WaitingFor and RuntimeError, the input of the reducer (AM-402).
    implementation(project(":feature:agent-runtime:api"))
    // ProjectsApi and Project: the project dirs the monitor attributes sessions to (AM-403).
    implementation(project(":feature:projects:api"))
    // Outcome, ProjectId, SessionId, Clock and Logger.
    implementation(project(":core:domain"))

    // aSession, the sessions builders and SessionMonitorContract.
    testImplementation(testFixtures(project(":feature:sessions:api")))
    // aRuntimeSession and FakeAgentRuntime for the listings fed to the reducer and the monitor.
    testImplementation(testFixtures(project(":feature:agent-runtime:api")))
    // FakeProjectsApi for the project map of the monitor (AM-403).
    testImplementation(testFixtures(project(":feature:projects:api")))
    // T0, aSessionId, TestClock and RecordingLogger.
    testImplementation(project(":core:testing"))
}
