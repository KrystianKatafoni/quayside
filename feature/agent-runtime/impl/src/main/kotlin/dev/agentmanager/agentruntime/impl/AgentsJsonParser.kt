package dev.agentmanager.agentruntime.impl

import dev.agentmanager.agentruntime.api.ConversationId
import dev.agentmanager.agentruntime.api.RuntimeError
import dev.agentmanager.agentruntime.api.RuntimePhase
import dev.agentmanager.agentruntime.api.RuntimeSession
import dev.agentmanager.agentruntime.api.WaitingFor
import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.SessionId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.time.DateTimeException
import java.time.Instant

/** Pure parser of `claude agents --json`; tolerant to new fields and values (NFR-8). Never throws. */
internal object AgentsJsonParser {
    /** Upper bound of accepted stdout; larger input is rejected before parsing. */
    const val MAX_INPUT_CHARS: Int = 4 * 1024 * 1024

    /**
     * [observedAt] replaces a missing or unreadable `startedAt` (the parser has no clock).
     * Err only when the whole document is unusable; a bad entry is skipped and reported in [AgentsListing.notices].
     */
    fun parse(
        stdout: String,
        observedAt: Instant,
    ): Outcome<AgentsListing, RuntimeError.Unparseable> {
        val tooLarge = stdout.length > MAX_INPUT_CHARS
        val blank = !tooLarge && stdout.isBlank()
        val tree = if (tooLarge || blank) null else parseTree(stdout)
        return when {
            tooLarge -> unparseable(UnparseableReason.TOO_LARGE)
            blank -> unparseable(UnparseableReason.EMPTY)
            tree == null -> unparseable(UnparseableReason.MALFORMED)
            tree !is JsonArray -> unparseable(UnparseableReason.NOT_AN_ARRAY)
            else -> Outcome.Ok(listEntries(tree, observedAt))
        }
    }

    // Exception messages of kotlinx.serialization quote the input, so neither message nor cause is kept.
    private fun parseTree(stdout: String): JsonElement? =
        try {
            Json.parseToJsonElement(stdout)
        } catch (ignored: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException.
            null
        }

    private fun unparseable(reason: String): Outcome<Nothing, RuntimeError.Unparseable> =
        Outcome.Err(RuntimeError.Unparseable(reason))
}

/** Parsed sessions in input order plus everything that was tolerated, for DEBUG logs and `/compat-check`. */
internal data class AgentsListing(
    val sessions: List<RuntimeSession>,
    val notices: List<ParseNotice>,
)

// A sealed class rather than the plan's sealed interface: only class members may carry `internal`,
// which the Konsist rule `impl exposes only its module factory` requires on nested declarations too.

/**
 * Something the parser tolerated. Never carries input text except the session id
 * and tokens that passed the safe-token check.
 */
internal sealed class ParseNotice {
    /** Entry at [index] of the input array was dropped for [reason]. */
    internal data class EntrySkipped(
        val index: Int,
        val reason: SkipReason,
    ) : ParseNotice()

    /** Session [id] had an unknown `state`/`status`; [token] is null when neither was present. */
    internal data class UnknownPhase(
        val id: SessionId,
        val token: String?,
    ) : ParseNotice()

    /** Session [id] had no readable `startedAt`; the observation time was used instead. */
    internal data class StartedAtMissing(
        val id: SessionId,
    ) : ParseNotice()
}

/** Why an entry of `claude agents --json` was dropped. */
internal enum class SkipReason { NOT_AN_OBJECT, INTERACTIVE, MISSING_ID, INVALID_CWD, DUPLICATE_ID }

/** Fixed categories of [RuntimeError.Unparseable.detail]; the only values the parser ever puts there. */
internal object UnparseableReason {
    const val EMPTY = "agents-json:empty"
    const val TOO_LARGE = "agents-json:too-large"
    const val MALFORMED = "agents-json:malformed"
    const val NOT_AN_ARRAY = "agents-json:not-an-array"
}

private const val INTERACTIVE_KIND = "interactive"
private const val REDACTED = "<redacted>"
private const val MAX_OTHER_REASON_CHARS = 64

/** Numbers at or above this are epoch millis (2.1.302 recording), below it epoch seconds. */
private const val EPOCH_MILLIS_THRESHOLD = 100_000_000_000L

private val SAFE_TOKEN = Regex("^[A-Za-z][A-Za-z0-9_ -]{0,31}$")

private val PHASES: Map<String, RuntimePhase> =
    mapOf(
        RuntimePhase.WORKING to listOf("working", "busy", "running", "active"),
        RuntimePhase.BLOCKED to listOf("blocked", "waiting", "needs_input"),
        RuntimePhase.DONE to listOf("done", "idle", "completed", "finished"),
        RuntimePhase.FAILED to listOf("failed", "error", "errored", "crashed"),
        RuntimePhase.STOPPED to listOf("stopped", "exited", "killed", "terminated"),
    ).flatMap { (phase, tokens) -> tokens.map { it to phase } }.toMap()

private val WAITING_FOR: Map<String, WaitingFor> =
    listOf("permission", "permission_prompt").associateWith { WaitingFor.Permission } +
        listOf("input", "input_needed", "idle_prompt", "agent_needs_input").associateWith { WaitingFor.Input }

/** Result of mapping one object of the input array; nested classes carry `internal` for Konsist, as above. */
private sealed class EntryResult {
    internal data class Skipped(
        val reason: SkipReason,
    ) : EntryResult()

    internal data class Kept(
        val session: RuntimeSession,
        val notices: List<ParseNotice>,
    ) : EntryResult()
}

private fun listEntries(
    array: JsonArray,
    observedAt: Instant,
): AgentsListing {
    val sessions = mutableListOf<RuntimeSession>()
    val notices = mutableListOf<ParseNotice>()
    val seen = mutableSetOf<SessionId>()
    array.forEachIndexed { index, element ->
        val result =
            (element as? JsonObject)?.let { mapEntry(it, observedAt) } ?: EntryResult.Skipped(SkipReason.NOT_AN_OBJECT)
        when {
            result is EntryResult.Skipped -> {
                notices += ParseNotice.EntrySkipped(index, result.reason)
            }

            result is EntryResult.Kept && !seen.add(result.session.id) -> {
                notices += ParseNotice.EntrySkipped(index, SkipReason.DUPLICATE_ID)
            }

            result is EntryResult.Kept -> {
                sessions += result.session
                notices += result.notices
            }
        }
    }
    return AgentsListing(sessions, notices)
}

// Interactive sessions have no supervisor id and no actions (ADR 0002), so both checks drop them.
private fun mapEntry(
    entry: JsonObject,
    observedAt: Instant,
): EntryResult {
    val id = entry.text("id")
    val cwd = readCwd(entry.text("cwd"))
    return when {
        entry.text("kind") == INTERACTIVE_KIND -> EntryResult.Skipped(SkipReason.INTERACTIVE)
        id == null -> EntryResult.Skipped(SkipReason.MISSING_ID)
        cwd == null -> EntryResult.Skipped(SkipReason.INVALID_CWD)
        else -> keep(SessionId(id), cwd, entry, observedAt)
    }
}

private fun keep(
    id: SessionId,
    cwd: Path,
    entry: JsonObject,
    observedAt: Instant,
): EntryResult.Kept {
    val notices = mutableListOf<ParseNotice>()
    val rawPhase = entry.text("state") ?: entry.text("status")
    val waitingFor = entry.text("waitingFor")?.let(::readWaitingFor)
    val phase =
        rawPhase?.let { PHASES[normalize(it)] }
            ?: (if (waitingFor != null) RuntimePhase.BLOCKED else RuntimePhase.WORKING)
                .also { notices += ParseNotice.UnknownPhase(id, rawPhase?.let(::safeToken)) }
    val startedAt =
        readStartedAt(entry["startedAt"]) ?: observedAt.also { notices += ParseNotice.StartedAtMissing(id) }
    val session =
        RuntimeSession(
            id = id,
            conversationId = entry.text("sessionId")?.let(::ConversationId),
            name = entry.text("name") ?: id.value,
            cwd = cwd,
            phase = phase,
            waitingFor = waitingFor.takeIf { phase == RuntimePhase.BLOCKED },
            startedAt = startedAt,
            pid = readPid(entry["pid"]).takeUnless { phase == RuntimePhase.STOPPED },
        )
    return EntryResult.Kept(session, notices)
}

/** A JSON string that is not blank; blank ids would make the value class constructors throw. */
private fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun normalize(token: String): String =
    token
        .trim()
        .lowercase()
        .replace('-', '_')
        .replace(' ', '_')

private fun safeToken(raw: String): String = if (SAFE_TOKEN.matches(raw)) raw else REDACTED

private fun readWaitingFor(raw: String): WaitingFor =
    WAITING_FOR[normalize(raw)] ?: WaitingFor.Other(raw.take(MAX_OTHER_REASON_CHARS))

private fun readCwd(raw: String?): Path? =
    try {
        raw?.let { Path.of(it) }?.takeIf { it.isAbsolute }?.normalize()
    } catch (ignored: InvalidPathException) {
        null
    }

private fun readStartedAt(element: JsonElement?): Instant? {
    val primitive = element as? JsonPrimitive
    return try {
        when {
            primitive == null -> {
                null
            }

            primitive.isString -> {
                Instant.parse(primitive.content)
            }

            else -> {
                primitive.longOrNull?.let {
                    if (it >= EPOCH_MILLIS_THRESHOLD) Instant.ofEpochMilli(it) else Instant.ofEpochSecond(it)
                }
            }
        }
    } catch (ignored: DateTimeException) {
        null
    }
}

private fun readPid(element: JsonElement?): Long? =
    (element as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull?.takeIf { it > 0 }
