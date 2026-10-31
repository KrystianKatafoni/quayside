package dev.agentmanager.terminal.impl.replyspike

import dev.agentmanager.core.domain.ShellCommand
import dev.agentmanager.core.testing.RecordingLogger
import kotlinx.coroutines.delay
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Behaviour switches of the fake TUI; see the header of `fake-claude-tui.sh`. */
internal data class TuiOptions(
    val stuck: Boolean = false,
    val withChild: Boolean = false,
    val ignoreHangup: Boolean = false,
    val detachExits: Boolean = false,
    val sentFrame: String = "text-sent.ansi",
)

/**
 * A temporary `HOME` whose `.profile` puts the fake `claude` (`fake-claude-tui.sh`) on `PATH`, as the user's
 * `.zprofile` does (pattern of `PtyLauncherTest`). Never the real Claude Code.
 */
internal class FakeTuiEnvironment(
    val home: Path,
) {
    val logger = RecordingLogger()
    private val log = home.resolve("tui.log")
    private val clientPidFile = home.resolve("client.pid")
    private val childPidFile = home.resolve("child.pid")

    init {
        val bin = Files.createDirectories(home.resolve("bin"))
        copyReplySpikeResource("fake-claude-tui.sh", bin.resolve("claude"), executable = true)
        Files.writeString(home.resolve(".profile"), "PATH=\"$bin:\$PATH\"\nexport PATH\n")
    }

    val hiddenReply: HiddenReply
        get() =
            HiddenReply(
                shell = Path.of("/bin/sh"),
                baseEnvironment = mapOf("PATH" to "/usr/bin:/bin", "HOME" to home.toString(), "LC_ALL" to "C.UTF-8"),
                logger = logger,
            )

    /**
     * `claude attach s-1` drawing [frame] (a file of `reply-spike/`, none: an empty screen) after [delaySeconds].
     * For `text.ansi` the fake also draws `text-filled.ansi` when the paste ends and [TuiOptions.sentFrame]
     * (default `text-sent.ansi`: empty box and spinner) after Enter, as the real input box behaves.
     */
    fun attach(
        frame: String? = null,
        delaySeconds: String = "0",
        options: TuiOptions = TuiOptions(),
    ): ShellCommand {
        val frameFile = frame?.let { copyReplySpikeResource(it, home.resolve("frame-$it")) }
        val textFrames = frame == "text.ansi"
        val filled = if (textFrames) copyReplySpikeResource("text-filled.ansi", home.resolve("filled.ansi")) else null
        val sent =
            if (textFrames) {
                val name = options.sentFrame
                copyReplySpikeResource(name, home.resolve("sent.ansi"))
            } else {
                null
            }
        val env =
            buildMap {
                put("FAKE_TUI_DELAY", delaySeconds)
                put("FAKE_TUI_LOG", log.toString())
                put("FAKE_TUI_PID_FILE", clientPidFile.toString())
                frameFile?.let { put("FAKE_TUI_FRAME", it.toString()) }
                filled?.let { put("FAKE_TUI_FILLED_FRAME", it.toString()) }
                sent?.let { put("FAKE_TUI_SENT_FRAME", it.toString()) }
                putAll(switches(options))
            }
        return ShellCommand(listOf("claude", "attach", "s-1"), home, env)
    }

    private fun switches(options: TuiOptions): Map<String, String> =
        buildMap {
            if (options.stuck) put("FAKE_TUI_STUCK", "1")
            if (options.ignoreHangup) put("FAKE_TUI_IGNORE_HUP", "1")
            if (options.detachExits) put("FAKE_TUI_DETACH_EXITS", "1")
            if (options.withChild) put("FAKE_TUI_CHILD_PID_FILE", childPidFile.toString())
        }

    private fun logLines(): List<String> = if (Files.exists(log)) Files.readAllLines(log) else emptyList()

    /** Bytes the fake TUI received after it drew its frame, as the keystrokes the driver typed. */
    fun typed(): String {
        val hex = logLines().filter { it != "frame" && !it.startsWith("early ") }
        return String(ByteArray(hex.size) { hex[it].toInt(16).toByte() }, Charsets.UTF_8)
    }

    /** Bytes that reached the fake TUI before its frame was drawn: the driver did not wait for the prompt. */
    fun typedBeforeFrame(): List<String> = logLines().filter { it.startsWith("early ") }

    fun frameWasDrawn(): Boolean = "frame" in logLines()

    /** Pid written by the fake client or by the child it started; null when not written (yet). */
    fun clientPid(): Long? = readPid(clientPidFile)

    fun childPid(): Long? = readPid(childPidFile)

    private fun readPid(file: Path): Long? =
        if (Files.exists(file)) Files.readString(file).trim().toLongOrNull() else null

    /** Polls real time until [condition] holds or [timeoutMillis] passes. */
    suspend fun eventually(
        timeoutMillis: Long = 3_000,
        condition: () -> Boolean,
    ): Boolean {
        var waited = 0L
        while (!condition() && waited < timeoutMillis) {
            delay(50.milliseconds)
            waited += 50
        }
        return condition()
    }

    fun cleanUp() {
        listOfNotNull(clientPid(), childPid()).forEach { pid ->
            ProcessHandle.of(pid).ifPresent { it.destroyForcibly() }
        }
    }

    companion object {
        val SHORT = ReplyLimits(ready = 1.seconds, ack = 1.seconds, keyGap = 10.milliseconds)
    }
}
