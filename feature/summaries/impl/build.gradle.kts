// Summaries feature (docs/11 §4): the pure LastMessageSummary core, the Summarizer on top of it (AM-604 A) and
// the Haiku SummaryRefiner with its summaries.json switch (AM-604 B), exposed through summariesModule.
plugins {
    id("am.feature.impl")
}

dependencies {
    implementation(project(":feature:summaries:api"))
    // OneShotPrompt: the port the model summary asks through (ADR 0019).
    implementation(project(":feature:agent-runtime:api"))
    // Outcome and Logger.
    implementation(project(":core:domain"))
    // JsonStorage and StoreSpec for summaries.json.
    implementation(project(":core:storage"))

    // SummarizerContract.
    testImplementation(testFixtures(project(":feature:summaries:api")))
    // FakeOneShotPrompt behind the refiner.
    testImplementation(testFixtures(project(":feature:agent-runtime:api")))
    // Tree access to summaries.json (SummarySettingsSerializer); the compiler plugin is not needed.
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":core:testing"))
}
