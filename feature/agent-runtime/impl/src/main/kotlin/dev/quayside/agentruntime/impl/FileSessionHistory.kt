package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.agentruntime.api.HistoryError
import dev.quayside.agentruntime.api.PastConversation
import dev.quayside.agentruntime.api.SessionHistory
import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.name

/** One session file seen on disk: the cache key is path, size and modification time. */
private data class FileStamp(
    val file: Path,
    val size: Long,
    val modifiedAt: Instant,
)

/**
 * [SessionHistory] over `<projects>/<dir>/<uuid>.jsonl` (ADR 0032). Only reads; a file is opened only when it is
 * not older than `since`, lies in a candidate directory ([SessionFiles.candidates]) and its stamp is not cached.
 * The log gets counts and fixed categories, never a path, an id or text from a file (ENG-C8).
 */
internal class FileSessionHistory(
    private val projects: Path,
    private val io: CoroutineDispatcher,
    private val logger: Logger,
    private val reader: SessionFileReader = SessionFileReader(io),
) : SessionHistory {
    private val cache = ConcurrentHashMap<FileStamp, Outcome<SessionFileSummary, SessionFileProblem>>()

    override suspend fun list(
        dirs: List<Path>,
        since: Instant?,
    ): Outcome<List<PastConversation>, HistoryError> =
        withContext(io) {
            val names = directoryNames() ?: return@withContext Outcome.Err(HistoryError.UNREADABLE)
            val wanted = dirs.distinct()
            val candidates = wanted.flatMap { SessionFiles.candidates(it, names) }.distinct()
            val stamps = candidates.flatMap { stampsIn(projects.resolve(it)) }
            forget(stamps, candidates)
            val read = stamps.filter { since == null || it.modifiedAt >= since }.map { it to readCached(it) }
            report(read.map { it.second })
            Outcome.Ok(conversations(read, wanted))
        }

    /** Names of the subdirectories of `projects`; empty when it is missing, null when it cannot be listed. */
    private fun directoryNames(): List<String>? =
        when {
            !Files.exists(projects) -> {
                emptyList()
            }

            else -> {
                try {
                    Files.list(projects).use { paths -> paths.filter(Files::isDirectory).map { it.name }.toList() }
                } catch (ignored: IOException) {
                    null
                }
            }
        }

    private fun stampsIn(dir: Path): List<FileStamp> =
        try {
            Files.list(dir).use { paths ->
                paths.filter { it.name.endsWith(SUFFIX) }.toList().mapNotNull(::stampOf)
            }
        } catch (ignored: IOException) {
            emptyList()
        }

    private fun stampOf(file: Path): FileStamp? =
        try {
            val attributes = Files.readAttributes(file, BasicFileAttributes::class.java)
            if (attributes.isRegularFile) {
                FileStamp(file, attributes.size(), attributes.lastModifiedTime().toInstant())
            } else {
                null
            }
        } catch (ignored: IOException) {
            null
        }

    /** Drops cache entries of files that are gone and of directories that are no longer candidates. */
    private fun forget(
        seen: List<FileStamp>,
        listed: List<String>,
    ) {
        val directories = listed.map(projects::resolve).toSet()
        val current = seen.toSet()
        cache.keys.removeIf { it.file.parent !in directories || it !in current }
    }

    private suspend fun readCached(stamp: FileStamp): Outcome<SessionFileSummary, SessionFileProblem> =
        cache[stamp] ?: reader.read(stamp.file).also {
            // A momentary failure to read is not remembered: the next call tries again.
            if (it != Outcome.Err(SessionFileProblem.UNREADABLE)) cache[stamp] = it
        }

    private fun conversations(
        read: List<Pair<FileStamp, Outcome<SessionFileSummary, SessionFileProblem>>>,
        wanted: List<Path>,
    ): List<PastConversation> =
        read
            .mapNotNull { (stamp, outcome) -> (outcome as? Outcome.Ok)?.value?.let { stamp to it } }
            .filter { (_, summary) -> wanted.any(summary.startCwd::startsWith) }
            .map { (stamp, summary) ->
                PastConversation(
                    conversationId = ConversationId(summary.sessionId),
                    startCwd = summary.startCwd,
                    startedAt = summary.startedAt,
                    lastActivityAt = summary.lastActivityAt,
                    modifiedAt = stamp.modifiedAt,
                    cwdChanged = summary.cwdChanged,
                    title = summary.title,
                )
            }.sortedWith(compareByDescending<PastConversation> { it.modifiedAt }.thenBy { it.conversationId.value })

    private fun report(outcomes: List<Outcome<SessionFileSummary, SessionFileProblem>>) {
        val skipped =
            outcomes
                .mapNotNull { (it as? Outcome.Err)?.error }
                .groupingBy { it.name }
                .eachCount()
        logger.log(LogLevel.DEBUG, "Session files read", mapOf("files" to outcomes.size) + skipped)
    }

    private companion object {
        const val SUFFIX = ".jsonl"
    }
}
