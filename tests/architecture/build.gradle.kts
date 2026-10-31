// Architecture tests (ENG-T5): Konsist reads the sources of every module, so no project dependencies.
plugins {
    id("am.kotlin")
}

dependencies {
    testImplementation(libs.konsist)
}

tasks.test {
    // Sources of all modules are inputs; never serve a stale result from the build cache.
    outputs.upToDateWhen { false }
}

// The agent's Stop hook runs quickCheck; architecture rules are fast enough to belong there.
tasks.named("quickCheck") {
    dependsOn(tasks.test)
}
