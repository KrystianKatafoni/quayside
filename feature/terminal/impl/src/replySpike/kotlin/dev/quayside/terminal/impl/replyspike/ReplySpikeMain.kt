package dev.quayside.terminal.impl.replyspike

import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.getOrElse
import dev.quayside.core.domain.getOrNull
import dev.quayside.core.process.LoginShellProcessRunner
import dev.quayside.terminal.impl.PtyLauncher
import dev.quayside.terminal.impl.PtyTtyConnector
import dev.quayside.terminal.impl.ShellLaunch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

private const val DEFAULT_TRIALS = 10
private const val FRAME_WAIT_SECONDS = 15
private val OUT = Path.of("build", "reply-spike")
private val KINDS = mapOf("t" to Kind.TEXT, "o" to Kind.OPTIONS, "z" to Kind.PERMISSION)

/** Writes log lines (level, message, field names and values) to a file; the screen and answers never reach it. */
private class FileLogger(
    private val file: Path,
) : Logger {
    override fun log(
        level: LogLevel,
        message: String,
        fields: Map<String, Any?>,
        cause: Throwable?,
    ) {
        Files.writeString(file, "$level $message $fields\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }
}

/**
 * Manual spike runner (`./gradlew :feature:terminal:impl:runReplySpike --args="..."`), never part of the build gates.
 * Modes, arguments `key=value`:
 * - `record kind=t|o|z`: one session, the screen of its attach is written to `build/reply-spike/frame-<kind>.txt`
 *   (the only place that holds screen text) next to the classifier verdict;
 * - `single kind=t|o|z [text=paste|type] [choice=digit|arrows] [detach=key|hangup]`: one trial;
 * - `run [kinds=tzo] [trials=10] [race=3] ...`: the measurement of every kind, then the summary.
 * Output: `trials.jsonl`, `summary.txt`, `spike.log` in `build/reply-spike/`; nothing goes to the console.
 */
@Suppress("InjectDispatcher", "ForbiddenMethodCall")
fun main(args: Array<String>) {
    Files.createDirectories(OUT)
    val options = args.drop(1).associate { it.substringBefore('=') to it.substringAfter('=', "") }
    val logger = FileLogger(OUT.resolve("spike.log"))
    val runner = LoginShellProcessRunner(Dispatchers.IO, logger = logger)
    val claude = SpikeClaude(runner)
    val shell = LoginShellProcessRunner.defaultShell()
    val hidden = HiddenReply(shell, System.getenv(), logger)
    val trials = TrialRunner(claude, hidden, logger, newWorkRoot())
    runBlocking {
        val version = claude.version().getOrElse { "unknown" }
        when (args.firstOrNull()) {
            "record" -> record(options, claude, shell)
            "single" -> measure(options, listOf(kindOf(options)), 1, trials, version)
            else -> measure(options, kindsOf(options), options["trials"]?.toInt() ?: DEFAULT_TRIALS, trials, version)
        }
    }
}

private fun kindOf(options: Map<String, String>): Kind = KINDS.getValue(options["kind"] ?: "t")

private fun kindsOf(options: Map<String, String>): List<Kind> =
    (options["kinds"] ?: "tzo").mapNotNull {
        KINDS[it.toString()]
    }

private fun settingsOf(options: Map<String, String>) =
    TrialSettings(
        variant =
            KeyVariant(
                text = if (options["text"] == "type") TextKeys.TYPE else TextKeys.PASTE,
                choice = if (options["choice"] == "arrows") ChoiceKeys.ARROWS else ChoiceKeys.DIGIT,
            ),
        detach = if (options["detach"] == "key") Detach.KEY else Detach.HANGUP,
    )

private suspend fun measure(
    options: Map<String, String>,
    kinds: List<Kind>,
    count: Int,
    runner: TrialRunner,
    version: String,
) {
    val settings = settingsOf(options)
    val races = options["race"]?.toInt() ?: 0
    val records =
        kinds.flatMap { kind ->
            List(count) { runner.trial(kind, it, settings) } +
                List(races) { runner.trial(kind, it, settings, race = true) }
        }
    records.forEach { append(OUT.resolve("trials.jsonl"), line(it, version, settings)) }
    Files.writeString(
        OUT.resolve("summary.txt"),
        "claude $version\n" + ReplyReport.summarize(records).entries.joinToString("\n"),
    )
}

private fun line(
    record: TrialRecord,
    version: String,
    settings: TrialSettings,
): String =
    buildJsonObject {
        put("kind", record.kind.name)
        put("verdict", describe(record.verdict))
        put("millis", record.duration.inWholeMilliseconds)
        put("race", record.race)
        put("cleanDetach", record.cleanDetach)
        put("version", version)
        put("variant", settings.variant.toString() + " " + settings.detach)
    }.toString() + "\n"

private fun append(
    file: Path,
    text: String,
) {
    Files.writeString(file, text, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
}

/** Opens the attach of a fresh blocked session in a PTY, lets it draw and saves the screen (record mode only). */
private suspend fun record(
    options: Map<String, String>,
    claude: SpikeClaude,
    shell: Path,
) {
    val kind = kindOf(options)
    val dir = Files.createDirectories(newWorkRoot().resolve("record-${kind.name.lowercase()}"))
    val name = sessionName(kind, 0, race = false, Random.Default)
    val scenario = Scenarios.of(kind, 0, "token")
    try {
        claude.start(name, scenario.prompt, dir)
        waitBlocked(claude, name)?.let { capture(it, kind, dir, shell) }
    } finally {
        withContext(NonCancellable) { claude.sessions(name).getOrNull()?.forEach { claude.stopAndRemove(it.id) } }
    }
}

private suspend fun capture(
    id: String,
    kind: Kind,
    dir: Path,
    shell: Path,
) {
    val argv = ShellLaunch.command(shell, listOf("claude", "attach", id))
    val process = PtyLauncher.start(argv, dir, ShellLaunch.environment(System.getenv())).getOrNull() ?: return
    val connector = PtyTtyConnector(process)
    try {
        HeadlessScreen(connector).use { screen ->
            screen.await(FRAME_WAIT_SECONDS.seconds) { ScreenClassifier.classify(it) != Prompt.None }
            val rows = screen.rows()
            Files.writeString(
                OUT.resolve("frame-${kind.name.lowercase()}.txt"),
                rows.joinToString("\n") { it.trimEnd() },
            )
            append(OUT.resolve("spike.log"), "record ${kind.name} ${ScreenClassifier.classify(rows)}\n")
        }
    } finally {
        connector.close()
    }
}

private suspend fun waitBlocked(
    claude: SpikeClaude,
    name: String,
): String? {
    repeat(RECORD_POLLS) { _ ->
        val session = claude.find(name).getOrNull()
        if (session?.phase == SpikePhase.BLOCKED) return session.id
        delay(1.seconds)
    }
    return null
}

private const val RECORD_POLLS = 120
