// Terminal feature (AM-501 spike, AM-502): JediTerm widget over a pty4j PTY. Only the spike factory is public.
plugins {
    id("am.feature.impl")
}

// AM-507 spike: hidden reply through `claude attach`. Own compilation, associated with main to see its internals.
// Not part of the jar. Documented JVM pattern: https://kotlinlang.org/docs/gradle-configure-project.html
val replySpike =
    kotlin.target.compilations.create("replySpike") {
        associateWith(kotlin.target.compilations.getByName("main"))
    }

kotlin.target.compilations.getByName("test") {
    associateWith(replySpike)
}

// Spike code is measured by hand, not by coverage; its tests still run (AM-507).
kover {
    reports {
        filters {
            excludes {
                packages("dev.quayside.terminal.impl.replyspike")
            }
        }
    }
}

// Detekt of the spike set runs in `check` and `quickCheck` like main and test; the set is never in `check` otherwise
// than compiled, and `runReplySpike` (real `claude`, real tokens) is never a dependency of any gate.
tasks.named("check") { dependsOn("compileReplySpikeKotlin", "detektReplySpike") }
tasks.named("quickCheck") { dependsOn("detektReplySpike") }

tasks.register<JavaExec>("runReplySpike") {
    group = "verification"
    description = "AM-507: hidden attach reply against the real claude (manual, macOS, costs tokens). Use --args."
    classpath =
        replySpike.output.allOutputs +
        kotlin.target.compilations
            .getByName("main")
            .output.allOutputs +
        replySpike.runtimeDependencyFiles
    mainClass = "dev.quayside.terminal.impl.replyspike.ReplySpikeMainKt"
}

dependencies {
    // Outcome, ShellCommand and Logger.
    implementation(project(":core:domain"))
    // TerminalApi, TerminalSession, TerminalId and TerminalError (AM-502); the module factory returns them (AM-502).
    implementation(project(":feature:terminal:api"))
    // JsonStorage and StoreSpec for terminal.json (AM-508, ADR 0016); the serializer works on JsonElement.
    implementation(project(":core:storage"))
    implementation(libs.kotlinx.serialization.json)
    // Dispatchers.Swing: widgets are built and disposed on the event thread.
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.jediterm.core)
    implementation(libs.jediterm.ui)
    implementation(libs.pty4j)

    // Spike only (AM-507): ProcessRunner for `claude --bg`, `agents`, `stop`, `rm`; JsonElement for `agents --json`.
    "replySpikeImplementation"(project(":core:domain"))
    "replySpikeImplementation"(project(":core:process"))
    "replySpikeImplementation"(libs.kotlinx.coroutines.core)
    "replySpikeImplementation"(libs.kotlinx.serialization.json)
    "replySpikeImplementation"(libs.jediterm.core)
    "replySpikeImplementation"(libs.pty4j)

    // TerminalApiContract, run against the PTY implementation.
    testImplementation(testFixtures(project(":feature:terminal:api")))
    // FakeProcessRunner for SpikeClaudeTest (AM-507).
    testImplementation(testFixtures(project(":core:process")))
    testImplementation(project(":core:process"))
    // InMemoryJsonStore for the settings repo tests (AM-508).
    testImplementation(testFixtures(project(":core:storage")))
    // RecordingLogger.
    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
}
