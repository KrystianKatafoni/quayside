// Command palette contract (docs/11 §2, §4): Command, Shortcut, CommandRegistry and the pure CommandMatcher.
// No implementation here; the registry lives in command-palette:impl.
plugins { id("am.feature.api") }

dependencies {
    api(project(":core:domain"))

    // Contract, fake and builders live in testFixtures so every registry runs the same tests (ENG-T2).
    testFixturesApi(project(":core:testing"))
    testFixturesImplementation(platform(libs.junit.bom))
    testFixturesImplementation(libs.junit.jupiter)
    testFixturesImplementation(libs.kotest.assertions)
    testFixturesImplementation(libs.kotlinx.coroutines.test)
}
