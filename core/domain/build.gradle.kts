// Pure Kotlin base types shared by every module (ENG-M6): no IO, no UI, no third-party libraries
// except kotlinx.coroutines. Uses the API convention for explicit visibility and test fixtures.
plugins {
    id("am.feature.api")
}

dependencies {
    api(libs.kotlinx.coroutines.core)
}
