// Appearance feature (docs/11 §4): the built-in catalog of nine schemes and eight typographies, exposed
// through appearanceModule (AM-801).
plugins {
    id("am.feature.impl")
}

dependencies {
    implementation(project(":feature:appearance:api"))
    // Colour tokens and Wcag/mix used by deriveTokens.
    implementation(project(":core:design-tokens"))

    // AppearanceApiContract.
    testImplementation(testFixtures(project(":feature:appearance:api")))
    // ContrastAudit and SampleTokens.
    testImplementation(testFixtures(project(":core:design-tokens")))
}
