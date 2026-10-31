// Terminal contract (docs/11 §4, AM-502): TerminalApi, TerminalSession, TerminalId and TerminalError.
// No implementation here; the JediTerm/pty4j one lives in terminal:impl.
plugins { id("am.feature.api") }

dependencies {
    api(project(":core:domain"))

    // Contract and fake live in testFixtures so every TerminalApi runs the same tests (ENG-T2).
    testFixturesApi(project(":core:testing"))
    testFixturesImplementation(platform(libs.junit.bom))
    testFixturesImplementation(libs.junit.jupiter)
    testFixturesImplementation(libs.kotest.assertions)
    testFixturesImplementation(libs.kotlinx.coroutines.test)
}
