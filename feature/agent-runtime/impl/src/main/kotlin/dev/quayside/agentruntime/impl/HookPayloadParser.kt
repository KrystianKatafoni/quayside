package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.AgentEvent
import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.agentruntime.api.EventMeta
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.time.Instant

/**
 * Pure parser of the JSON body a Claude Code HTTP hook posts (docs/03 "Hooki"). Tolerant to new
 * fields and values (NFR-8). Never throws; a rejection names a constant reason, never input text.
 */
internal object HookPayloadParser {
    /** Notification types that mean "the agent waits for an answer". */
    private val NEEDS_INPUT_TYPES = setOf("idle_prompt", "agent_needs_input", "elicitation_dialog")
    private const val PERMISSION_TYPE = "permission_prompt"
    private const val UNKNOWN = "unknown"
    private const val MAX_KIND_CHARS = 40
    private val TOOL_IN_MESSAGE = Regex("""permission to use ([A-Za-z0-9_.:-]+)""")
    private val SAFE_TOKEN = Regex("[A-Za-z0-9_.:-]+")

    /** [at] is when Quayside received the request (the hook carries no time). */
    fun parse(
        body: String,
        at: Instant,
    ): HookParse {
        val root = parseObject(body)
        val name = root?.string("hook_event_name")
        val conversation = root?.string("session_id")?.takeIf { it.isNotBlank() }
        val cwd = root?.string("cwd")?.let(::toPath)
        return when {
            root == null -> HookParse.Invalid(HookRejection.MALFORMED)
            name == null -> HookParse.Invalid(HookRejection.MISSING_EVENT_NAME)
            conversation == null -> HookParse.Invalid(HookRejection.MISSING_SESSION_ID)
            cwd == null -> HookParse.Invalid(HookRejection.MISSING_CWD)
            else -> toEvent(name, root, EventMeta(ConversationId(conversation), cwd, at))
        }
    }

    private fun toEvent(
        name: String,
        root: JsonObject,
        meta: EventMeta,
    ): HookParse {
        val event: AgentEvent? =
            when (name) {
                "Notification" -> notification(root, meta)
                "Stop" -> AgentEvent.TurnEnded(meta, root.string("last_assistant_message"))
                "StopFailure" -> AgentEvent.Failed(meta, safeKind(root.string("error_type") ?: root.string("error")))
                "SessionEnd" -> AgentEvent.SessionEnded(meta, safeKind(root.string("reason")))
                else -> null // SessionStart and events added later: valid, nothing to publish.
            }
        return if (event == null) HookParse.Ignored else HookParse.Event(event)
    }

    private fun notification(
        root: JsonObject,
        meta: EventMeta,
    ): AgentEvent? {
        val type = root.string("notification_type")
        return when {
            type == PERMISSION_TYPE -> AgentEvent.PermissionRequested(meta, toolOf(root.string("message")))
            type in NEEDS_INPUT_TYPES -> AgentEvent.NeedsInput(meta, root.string("message"))
            else -> null
        }
    }

    // "Claude needs your permission to use Bash": the tool is the word after "use", when it looks like a tool name.
    private fun toolOf(message: String?): String? =
        message
            ?.let { TOOL_IN_MESSAGE.find(it) }
            ?.groupValues
            ?.get(1)
            ?.takeIf { it.length <= MAX_KIND_CHARS }

    // Kinds and reasons end up in logs and UI state, so only short token-like values pass.
    private fun safeKind(raw: String?): String =
        raw?.takeIf { it.length <= MAX_KIND_CHARS && SAFE_TOKEN.matches(it) } ?: UNKNOWN

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun toPath(raw: String): Path? =
        try {
            raw.takeIf { it.isNotBlank() }?.let { Path.of(it) }
        } catch (ignored: InvalidPathException) {
            null
        }

    // Exception messages of kotlinx.serialization quote the input, so neither message nor cause is kept.
    private fun parseObject(body: String): JsonObject? =
        try {
            Json.parseToJsonElement(body) as? JsonObject
        } catch (ignored: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException.
            null
        }
}

/** Result of [HookPayloadParser.parse]. */
internal sealed class HookParse {
    /** A hook the app publishes as [event]. */
    internal data class Event(
        val event: AgentEvent,
    ) : HookParse()

    /** A well-formed hook with nothing to publish (`SessionStart`, other notification types). */
    internal data object Ignored : HookParse()

    /** Not a usable hook request; [reason] is one of [HookRejection]. */
    internal data class Invalid(
        val reason: String,
    ) : HookParse()
}

/** Constant reasons for [HookParse.Invalid]; safe to log. */
internal object HookRejection {
    const val MALFORMED = "malformed"
    const val MISSING_EVENT_NAME = "missing-event-name"
    const val MISSING_SESSION_ID = "missing-session-id"
    const val MISSING_CWD = "missing-cwd"
}
