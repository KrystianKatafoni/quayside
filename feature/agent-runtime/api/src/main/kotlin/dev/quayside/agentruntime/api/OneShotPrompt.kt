package dev.quayside.agentruntime.api

import dev.quayside.core.domain.Outcome
import kotlin.time.Duration

/**
 * Asks a model one question through Claude Code in print mode, outside any session and without hooks
 * (ADR 0019). Never throws except CancellationException; any dispatcher. Errors: NotInstalled, Timeout,
 * CommandFailed (the whole prompt is masked in `stderrTail`, but fragments of it may remain: never log it),
 * Unparseable (fixed category). Costs tokens: call only
 * after the user opted in.
 */
public fun interface OneShotPrompt {
    /** The model's answer to [request]. */
    public suspend fun ask(request: PromptRequest): Outcome<String, RuntimeError>
}

/**
 * One question; [toString] hides [prompt] (ENG-C8).
 *
 * @property prompt the question, not blank and at most [MAX_PROMPT] chars.
 * @property model model alias or name (`--model`), not blank.
 * @property timeout how long the answer may take, positive.
 * @throws IllegalArgumentException when [prompt] is blank or longer than [MAX_PROMPT], [model] is blank or
 * [timeout] is not positive.
 */
public data class PromptRequest(
    val prompt: String,
    val model: String,
    val timeout: Duration,
) {
    init {
        require(prompt.isNotBlank()) { "prompt must not be blank" }
        require(prompt.length <= MAX_PROMPT) { "prompt must have at most $MAX_PROMPT chars" }
        require(model.isNotBlank()) { "model must not be blank" }
        require(timeout.isPositive()) { "timeout must be positive" }
    }

    override fun toString(): String = "PromptRequest(prompt=<${prompt.length} chars>, model=$model, timeout=$timeout)"

    public companion object {
        /** Longest prompt, in chars. */
        public const val MAX_PROMPT: Int = 16_000
    }
}
