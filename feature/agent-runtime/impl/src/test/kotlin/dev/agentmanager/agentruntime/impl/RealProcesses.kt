package dev.agentmanager.agentruntime.impl

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

// Helpers for tests that run the fake `claude` as a real process (docs/12 §4, as in AM-106).

/** The shell the tests give LoginShellProcessRunner: POSIX, present on macOS and Linux. */
internal val TEST_SHELL: java.nio.file.Path =
    java.nio.file.Path
        .of("/bin/sh")

private const val MAX_ALIVE_CHECKS = 300

/** Polls briefly on [io]: a killed orphan is reaped by init shortly after the signal. */
internal suspend fun isAlive(
    pid: Long,
    io: CoroutineDispatcher,
): Boolean =
    withContext(io) {
        val handle = ProcessHandle.of(pid)
        var checks = 0
        while (handle.map { it.isAlive }.orElse(false) && checks++ < MAX_ALIVE_CHECKS) delay(10.milliseconds)
        handle.map { it.isAlive }.orElse(false)
    }

/** Kills processes a test left behind on purpose (the `bg-child` mode). */
internal fun killAll(pids: List<Long>) {
    pids.forEach { pid -> ProcessHandle.of(pid).ifPresent { it.destroyForcibly() } }
}
