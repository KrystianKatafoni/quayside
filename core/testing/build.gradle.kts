// Shared test helpers (ENG-T6). Used only from test and testFixtures source sets of other modules.
plugins {
    id("am.feature.api")
}

dependencies {
    api(project(":core:domain"))
    api(libs.kotlinx.coroutines.test)
}
