// Accounts feature (docs/11 §4, ADR 0035): the stored list of accounts (accounts.json) and the disk side of
// account setup (new directory, shared projects link, one-time copy with backup), exposed through accountsModule.
plugins {
    id("am.feature.impl")
}

dependencies {
    implementation(project(":feature:accounts:api"))
    // AccountId, Outcome and Logger.
    implementation(project(":core:domain"))
    // JsonStorage and StoreSpec for accounts.json.
    implementation(project(":core:storage"))
    // Tree access to accounts.json (AccountsSerializer); the compiler plugin is not needed.
    implementation(libs.kotlinx.serialization.json)

    // The contracts and the fake.
    testImplementation(testFixtures(project(":feature:accounts:api")))
    testImplementation(project(":core:testing"))
}
