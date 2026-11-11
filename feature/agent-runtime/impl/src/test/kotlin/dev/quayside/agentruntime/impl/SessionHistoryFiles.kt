package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.ConversationMessage
import dev.quayside.agentruntime.api.ConversationRole
import dev.quayside.agentruntime.api.PastConversation
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes
import kotlin.io.path.writeText

// Synthetic Claude Code data directory for the history tests: `<claudeHome>/projects/<encoded cwd>/<uuid>.jsonl`.

/** The directory of the session files of [cwd] in [claudeHome], where Claude Code puts them (AM-311). */
internal fun projectDirOf(
    claudeHome: Path,
    cwd: Path,
): Path = claudeHome.resolve("projects").resolve(SessionFiles.dirNameOf(cwd))

/** Writes the file of [conv] (a user line and an assistant line) and sets its `mtime` to [mtime]. */
internal fun writeSessionFile(
    claudeHome: Path,
    conv: PastConversation,
    mtime: Instant = conv.modifiedAt,
): Path {
    val id = conv.conversationId.value
    val cwd = conv.startCwd.toString()
    val text =
        lines(
            sessionEntry("user", id, cwd, conv.startedAt?.toString(), conv.title?.let(::JsonPrimitive)),
            sessionEntry("assistant", id, cwd, conv.lastActivityAt?.toString(), content = null),
        )
    return writeRaw(claudeHome, conv.startCwd, id, text, mtime)
}

/** Writes [text] as the file of conversation [id] in the directory of [cwd] and sets its `mtime`. */
internal fun writeRaw(
    claudeHome: Path,
    cwd: Path,
    id: String,
    text: String,
    mtime: Instant,
): Path {
    val file = projectDirOf(claudeHome, cwd).createDirectories().resolve("$id.jsonl")
    file.writeText(text)
    Files.setLastModifiedTime(file, FileTime.from(mtime))
    return file
}

/** SHA-256 and `mtime` of every regular file under [root], by relative path: what "never changes a file" compares. */
internal fun fingerprint(root: Path): Map<String, Pair<String, Instant>> =
    Files.walk(root).use { paths ->
        paths
            .filter { it.isRegularFile() }
            .toList()
            .associate { file ->
                val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                val hash = digest.joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
                root.relativize(file).toString() to (hash to Files.getLastModifiedTime(file).toInstant())
            }
    }

/** One `user` or `assistant` line per message of [messages], in order, with [id], [cwd] and the time of the message. */
internal fun conversationText(
    id: String,
    cwd: String,
    messages: List<ConversationMessage>,
): String =
    messages.joinToString("") { message ->
        val type = if (message.role == ConversationRole.USER) "user" else "assistant"
        sessionEntry(type, id, cwd, message.at?.toString(), JsonPrimitive(message.text)) + "\n"
    }

/** Writes the file of [conv] with exactly [messages] and sets its `mtime`. */
internal fun writeConversationFile(
    claudeHome: Path,
    conv: PastConversation,
    messages: List<ConversationMessage>,
    mtime: Instant = conv.modifiedAt,
): Path =
    writeRaw(
        claudeHome,
        conv.startCwd,
        conv.conversationId.value,
        conversationText(conv.conversationId.value, conv.startCwd.toString(), messages),
        mtime,
    )

/** A `user` line of about [bytes] bytes that carries only a `tool_result`, as the fillers of big files do. */
internal fun fillerLine(bytes: Int): String =
    sessionEntry("user", content = parts(TOOL_RESULT)).replace("tool output", "x".repeat(bytes))
