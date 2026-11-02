// Summaries feature (docs/11 §4): the pure LastMessageSummary core and the Summarizer on top of it (AM-604),
// exposed through the summariesModule factory.
plugins {
    id("am.feature.impl")
}

dependencies {
    implementation(project(":feature:summaries:api"))
    // Outcome.
    implementation(project(":core:domain"))

    // SummarizerContract.
    testImplementation(testFixtures(project(":feature:summaries:api")))
}
