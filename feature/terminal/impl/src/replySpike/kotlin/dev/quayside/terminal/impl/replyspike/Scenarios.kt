package dev.quayside.terminal.impl.replyspike

import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

private const val MAX_SUFFIX = 1_000_000
private const val ANSWER_FILE = "answer.txt"
private const val APPROVAL_FILE = "approved.txt"

// T and O write answer.txt; acceptEdits keeps the file write from raising a permission prompt of its own.
// Z stays in the default mode, because its permission prompt is the subject of the trial.
private const val ACCEPT_EDITS = "acceptEdits"
private val CHOICES = listOf("alfa", "beta", "gamma")

/** One question to the session and the answer the runner gives; [effect] reads what the answer did in the dir. */
internal class Scenario(
    val kind: Kind,
    val prompt: String,
    val reply: Reply,
    val permissionMode: String? = null,
    private val check: (Path) -> Effect,
) {
    fun effect(dir: Path): Effect = check(dir)
}

/** Scenarios T, O and Z of the AM-507 protocol; trial [n] picks the option and alternates allow and deny. */
internal object Scenarios {
    fun of(
        kind: Kind,
        n: Int,
        token: String,
    ): Scenario =
        when (kind) {
            Kind.TEXT -> text(token)
            Kind.OPTIONS -> options(n % CHOICES.size)
            Kind.PERMISSION -> permission(allow = n % 2 == 0)
        }

    private fun text(token: String) =
        Scenario(
            Kind.TEXT,
            "Zapytaj mnie zwykłym tekstem (bez narzędzi) o jedno słowo i zapisz je w pliku $ANSWER_FILE.",
            Reply.Text(token),
            ACCEPT_EDITS,
        ) { dir ->
            content(dir, ANSWER_FILE)?.let { if (it == token) Effect.AS_EXPECTED else Effect.WRONG }
                ?: Effect.ABSENT
        }

    private fun options(chosen: Int) =
        Scenario(
            Kind.OPTIONS,
            "Zapytaj narzędziem pytań o wybór: ${CHOICES.joinToString("/")}. Zapisz wybór w pliku $ANSWER_FILE.",
            Reply.Option(chosen + 1),
            ACCEPT_EDITS,
        ) { dir ->
            content(
                dir,
                ANSWER_FILE,
            )?.let { if (it.lowercase().contains(CHOICES[chosen])) Effect.AS_EXPECTED else Effect.WRONG }
                ?: Effect.ABSENT
        }

    private fun permission(allow: Boolean) =
        Scenario(Kind.PERMISSION, "Uruchom polecenie `touch $APPROVAL_FILE`.", Reply.Permission(allow)) { dir ->
            val approved = Files.exists(dir.resolve(APPROVAL_FILE))
            when {
                approved == allow -> Effect.AS_EXPECTED
                approved -> Effect.WRONG
                else -> Effect.ABSENT
            }
        }

    private fun content(
        dir: Path,
        file: String,
    ): String? = dir.resolve(file).takeIf { Files.exists(it) }?.let { Files.readString(it).trim() }
}

/** Session name of a trial, unique per call (random suffix); a race trial never shares the name of a measured one. */
internal fun sessionName(
    kind: Kind,
    n: Int,
    race: Boolean,
    random: Random,
): String = "am507-${kind.name.lowercase()}-$n${if (race) "-race" else ""}-${random.nextInt(0, MAX_SUFFIX)}"

/** Fresh directory `am507*` under [parent] (default: the per-user temp dir); the JDK gives it owner-only rights. */
internal fun newWorkRoot(parent: Path = Path.of(System.getProperty("java.io.tmpdir"))): Path =
    Files.createTempDirectory(parent, "am507")
