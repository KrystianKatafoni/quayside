package dev.agentmanager.terminal.impl.replyspike

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

/**
 * `runTest` whose body runs on a real dispatcher: the PTY tests wait for real processes and the spike code
 * measures real time (AM-507 plan), so virtual time would only skip the waits.
 */
@Suppress("InjectDispatcher")
internal fun realTimeTest(block: suspend CoroutineScope.() -> Unit): TestResult =
    runTest { withContext(Dispatchers.Default) { block() } }

/** Copies a classpath resource of `reply-spike/` to [target]; [executable] for scripts. */
internal fun copyReplySpikeResource(
    name: String,
    target: Path,
    executable: Boolean = false,
): Path {
    val stream =
        checkNotNull(object {}.javaClass.getResourceAsStream("/reply-spike/$name")) { "missing resource $name" }
    stream.use { Files.copy(it, target) }
    if (executable) target.toFile().setExecutable(true)
    return target
}

/** Text of a classpath resource of `reply-spike/`. */
internal fun replySpikeResource(name: String): String =
    checkNotNull(object {}.javaClass.getResourceAsStream("/reply-spike/$name")) { "missing resource $name" }
        .use { it.readBytes().toString(Charsets.UTF_8) }
