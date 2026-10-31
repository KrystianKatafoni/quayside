// Configuration files as JSON (docs/11 §8): atomic writes, schema version with migrations and
// recovery of broken files. Each file has exactly one owning module.
plugins {
    id("am.feature.api")
}

dependencies {
    api(project(":core:domain"))
    api(libs.kotlinx.serialization.json)

    // The JsonStore contract lives in testFixtures so every store implementation runs the same tests.
    testFixturesImplementation(platform(libs.junit.bom))
    testFixturesImplementation(libs.junit.jupiter)
    testFixturesImplementation(libs.kotest.assertions)
    testFixturesImplementation(libs.kotlinx.coroutines.test)

    testImplementation(project(":core:testing"))
}
