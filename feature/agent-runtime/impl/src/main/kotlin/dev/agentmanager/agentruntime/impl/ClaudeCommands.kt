package dev.agentmanager.agentruntime.impl

import dev.agentmanager.agentruntime.api.PermissionMode
import dev.agentmanager.agentruntime.api.RuntimeSession
import dev.agentmanager.agentruntime.api.StartSpec
import dev.agentmanager.core.domain.SessionId
import java.nio.file.Path

/** Pure argv builders of the `claude` CLI; [executable] is the program name or an absolute path. */
internal object ClaudeCommands {
    /** `[exe, agents, --json, --all]`; no `--cwd`, which needs 2.1.141. */
    fun list(executable: String): List<String> = listOf(executable, "agents", "--json", "--all")

    /** `[exe, --version]`. */
    fun version(executable: String): List<String> = listOf(executable, "--version")

    /** `[exe, daemon, status]`; no `--json`, which the CLI does not know. */
    fun daemonStatus(executable: String): List<String> = listOf(executable, "daemon", "status")

    /** `[exe, agents, --json]`; only the exit code and the message matter. */
    fun agentViewProbe(executable: String): List<String> = listOf(executable, "agents", "--json")

    /**
     * `[exe, --bg, --name n?, --agent a?, --model m?, --permission-mode p?, --, prompt]`.
     * [PermissionMode.DEFAULT] passes no flag: the CLI has no `default` value and starts in its default mode.
     */
    fun start(
        executable: String,
        spec: StartSpec,
    ): List<String> =
        buildList {
            add(executable)
            add("--bg")
            option("--name", spec.name)
            option("--agent", spec.agent)
            option("--model", spec.model)
            option("--permission-mode", spec.permissionMode?.cliValue)
            add("--")
            add(spec.prompt)
        }

    /** `[exe, verb, id]` for verb in `attach`, `logs`, `stop`, `respawn`, `rm`. */
    fun byId(
        executable: String,
        verb: String,
        id: SessionId,
    ): List<String> = listOf(executable, verb, id.value)

    private fun MutableList<String>.option(
        flag: String,
        value: String?,
    ) {
        if (value != null) {
            add(flag)
            add(value)
        }
    }

    private val PermissionMode.cliValue: String?
        get() =
            when (this) {
                PermissionMode.DEFAULT -> null
                PermissionMode.ACCEPT_EDITS -> "acceptEdits"
                PermissionMode.PLAN -> "plan"
                PermissionMode.BYPASS_PERMISSIONS -> "bypassPermissions"
            }
}

/** Reads the output of `claude --bg`. */
internal object BgOutput {
    private val BACKGROUNDED = Regex("""^backgrounded · ([A-Za-z0-9_-]+)(?:\s|$)""")

    /** Id from the first `backgrounded · <id>` line; null when there is none. */
    fun sessionId(stdout: String): SessionId? =
        stdout.lineSequence().firstNotNullOfOrNull { line ->
            BACKGROUNDED
                .find(line.trim())
                ?.groupValues
                ?.get(1)
                ?.let(::SessionId)
        }
}

/** Tail of `claude logs` returned by `recentOutput`. */
internal object OutputTail {
    const val MAX_LINES: Int = 200
    const val MAX_CHARS: Int = 16_384

    /** Without ANSI and control characters (except `\n`, `\t`), trimmed at the end, last lines and chars only. */
    fun of(stdout: String): String = TerminalText.clean(stdout).tail(MAX_LINES, MAX_CHARS)
}

/** Redacted tail of stderr carried by `RuntimeError.CommandFailed`. */
internal object StderrTail {
    const val MAX_CHARS: Int = 512
    private const val MAX_LINES = 5
    private const val REDACTED = "***"
    private const val MIN_SECRET_LINE = 8
    private val HOME_PATH = Regex("""/(?:Users|home)/[^/\s'"]+(?=/|\b)""")
    private val TOKENS = listOf(Regex("""sk-ant-[A-Za-z0-9_-]+"""), Regex("""Bearer \S+"""))

    /**
     * Without ANSI, control characters, [secrets], home paths and tokens; last 5 lines and [MAX_CHARS].
     * Each secret is masked whole and also line by line (every line of at least 8 characters), since
     * the CLI may quote only part of a multi-line prompt.
     */
    fun of(
        stderr: String,
        secrets: List<String>,
    ): String {
        val withoutSecrets = maskable(secrets).fold(stderr) { text, s -> text.replace(s, REDACTED) }
        val withoutTokens =
            TOKENS.fold(TerminalText.clean(withoutSecrets).replace(HOME_PATH, "~")) { text, token ->
                text.replace(token, REDACTED)
            }
        return withoutTokens.tail(MAX_LINES, MAX_CHARS)
    }

    /** Whole secrets and their lines of at least [MIN_SECRET_LINE] chars, longest first. */
    private fun maskable(secrets: List<String>): List<String> {
        val lines = secrets.flatMap { it.lines() }.map { it.trim() }.filter { it.length >= MIN_SECRET_LINE }
        return (secrets.filter { it.isNotBlank() } + lines).distinct().sortedByDescending { it.length }
    }
}

/** Cleanup of text a terminal program wrote. */
internal object TerminalText {
    // CSI (`ESC [ … final`), OSC (`ESC ] … BEL|ST`) and the remaining two-character escapes.
    private val ESCAPES =
        Regex(
            listOf(
                """\u001b\[[0-?]*[ -/]*[@-~]""",
                """\u001b\][^\u0007\u001b]*(?:\u0007|\u001b\\)?""",
                """\u001b[@-_]""",
            ).joinToString("|"),
        )
    private val CONTROLS = Regex("""[\u0000-\u0008\u000b-\u001f\u007f]""")

    /** Without escape sequences and control characters except `\n` and `\t`; `\r\n` becomes `\n`. */
    fun clean(text: String): String =
        text
            .replace(ESCAPES, "")
            .replace("\r\n", "\n")
            .replace(CONTROLS, "")
            .trimEnd()
}

private fun String.tail(
    maxLines: Int,
    maxChars: Int,
): String = lines().takeLast(maxLines).joinToString("\n").takeLast(maxChars)

/**
 * Whether `cwd` equals [dir] or lies below it, by normalized path components. Symbolic links are not
 * resolved: the caller passes the real path of [dir] (as `claude` reports real paths).
 */
internal fun RuntimeSession.isUnder(dir: Path): Boolean = cwd.normalize().startsWith(dir.normalize())
