// Runs external programs through the user's login shell (docs/11 §2). The only module that
// starts processes; adapters in feature impl modules use it through the ProcessRunner port.
plugins {
    id("am.feature.api")
}

dependencies {
    api(project(":core:domain"))

    testImplementation(project(":core:testing"))
}
