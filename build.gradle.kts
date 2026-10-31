plugins {
    base
}

// `./gradlew check` also verifies the convention plugins themselves.
tasks.named("check") {
    dependsOn(gradle.includedBuild("build-logic").task(":check"))
}
