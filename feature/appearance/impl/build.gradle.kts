// Appearance feature (docs/11 §4): the built-in catalog of nine schemes and eight typographies, exposed
// through appearanceModule (AM-801).
plugins {
    id("am.feature.impl")
}

dependencies {
    implementation(project(":feature:appearance:api"))
    // Colour tokens and Wcag/mix used by deriveTokens.
    implementation(project(":core:design-tokens"))
    // Outcome and Logger.
    implementation(project(":core:domain"))
    // JsonStorage and StoreSpec for theme.json (AM-804, ADR 0023).
    implementation(project(":core:storage"))
    // ProcessRunner behind `defaults read -g AppleInterfaceStyle` (AM-804, ADR 0023).
    implementation(project(":core:process"))
    implementation(libs.kotlinx.serialization.json)

    // AppearanceApiContract.
    testImplementation(testFixtures(project(":feature:appearance:api")))
    // ContrastAudit and SampleTokens.
    testImplementation(testFixtures(project(":core:design-tokens")))
    // InMemoryJsonStore, FakeProcessRunner and RecordingLogger for theme.json and the mode watcher (AM-804).
    testImplementation(testFixtures(project(":core:storage")))
    testImplementation(testFixtures(project(":core:process")))
    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
    // AmFonts.families for TypographyFontsTest (docs/11 section 3, rule 2: impl may use core).
    testImplementation(project(":core:design-system"))
}
