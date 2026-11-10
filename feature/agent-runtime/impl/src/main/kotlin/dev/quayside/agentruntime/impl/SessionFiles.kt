package dev.quayside.agentruntime.impl

import dev.quayside.core.domain.Outcome
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path
import java.time.Instant
import kotlin.math.abs

/** Metadata of one Claude Code session file; [title] is user data (ENG-C8): never logged. */
internal data class SessionFileSummary(
    /** UUID = file name stem (conversation id, not the short supervisor id). */
    val sessionId: String,
    /** Cwd of the first entry that has an absolute cwd (start directory). */
    val startCwd: Path,
    /** First `timestamp` in the head. */
    val startedAt: Instant?,
    /** Last `timestamp` in the tail. */
    val lastActivityAt: Instant?,
    /** More than one distinct cwd seen in head + tail (lower bound: the middle is not read). */
    val cwdChanged: Boolean,
    /** First real user message, at most 80 code points; null when none in the head. */
    val title: String?,
) {
    /** Without [title]: it is user data (ENG-C8) and must not reach logs through string templates. */
    override fun toString(): String =
        "SessionFileSummary(sessionId=$sessionId, startCwd=$startCwd, startedAt=$startedAt, " +
            "lastActivityAt=$lastActivityAt, cwdChanged=$cwdChanged, title=<redacted>)"
}

/** Why a file is not a listable session; fixed categories, never file text. */
internal enum class SessionFileProblem { UNREADABLE, NOT_JSONL, NO_SESSION_ID, ID_MISMATCH, NO_CWD, SIDECHAIN }

/** Pure rules for session files (AM-311 spike). */
internal object SessionFiles {
    private const val MAX_NAME = 200
    private const val RADIX = 36

    /**
     * Pure. [head] may end and [tail] may start in the middle of a line; such partial lines never parse as a
     * JSON object and are skipped. When [headIsWhole], [tail] is ignored.
     */
    fun parse(
        fileStem: String,
        head: String,
        tail: String,
        headIsWhole: Boolean,
    ): Outcome<SessionFileSummary, SessionFileProblem> {
        val headEntries = SessionEntries.entriesOf(head)
        val tailEntries = if (headIsWhole) headEntries else SessionEntries.entriesOf(tail)
        val all = headEntries + tailEntries
        val problem = problemOf(fileStem, headEntries, all)
        if (problem != null) return Outcome.Err(problem)
        val cwds = all.mapNotNull(SessionEntries::absoluteCwd)
        val firstStamp = headEntries.mapNotNull(SessionEntries::timestampOf).firstOrNull()
        val lastStamp = tailEntries.mapNotNull(SessionEntries::timestampOf).lastOrNull()
        return Outcome.Ok(
            SessionFileSummary(
                sessionId = fileStem,
                startCwd = cwds.first(),
                startedAt = firstStamp?.let(SessionEntries::instantOf),
                lastActivityAt = lastStamp?.let(SessionEntries::instantOf),
                cwdChanged = cwds.toSet().size > 1,
                title = headEntries.firstNotNullOfOrNull(SessionEntries::titleOf),
            ),
        )
    }

    /**
     * Pure: directory name Claude Code 2.1.322 uses for [cwd] (measured, AM-311): every UTF-16 unit outside
     * `A-Z a-z 0-9` becomes "-" (an emoji gives two dashes). A name longer than 200 characters is cut to the first
     * 200, then "-" and the absolute value of the Java `String.hashCode` of the original path in base 36.
     */
    fun dirNameOf(cwd: Path): String {
        val encoded = encode(cwd)
        if (encoded.length <= MAX_NAME) return encoded
        return encoded.take(MAX_NAME) + "-" + abs(cwd.toString().hashCode().toLong()).toString(RADIX)
    }

    private fun encode(cwd: Path): String =
        cwd.toString().map { if (it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9') it else '-' }.joinToString("")

    /**
     * Pure: [dirNames] that may hold sessions of [project] or its subfolders (equal or prefix + "-"). The hash of a
     * name over 200 characters is not known for a subfolder, so such a project matches by the first 200 characters.
     * The check of the real `cwd` follows.
     */
    fun candidates(
        project: Path,
        dirNames: List<String>,
    ): List<String> {
        val own = dirNameOf(project)
        val longPrefix = encode(project).take(MAX_NAME).takeIf { own.length > MAX_NAME }
        return dirNames.filter {
            it == own || it.startsWith("$own-") ||
                (longPrefix != null && it.startsWith(longPrefix))
        }
    }

    private fun problemOf(
        fileStem: String,
        head: List<JsonObject>,
        all: List<JsonObject>,
    ): SessionFileProblem? {
        val id = all.firstNotNullOfOrNull { SessionEntries.text(it, "sessionId") }
        return when {
            head.isEmpty() -> SessionFileProblem.NOT_JSONL
            head.first()["isSidechain"] == JsonPrimitive(true) -> SessionFileProblem.SIDECHAIN
            id == null -> SessionFileProblem.NO_SESSION_ID
            id != fileStem -> SessionFileProblem.ID_MISMATCH
            all.none { SessionEntries.absoluteCwd(it) != null } -> SessionFileProblem.NO_CWD
            else -> null
        }
    }
}
