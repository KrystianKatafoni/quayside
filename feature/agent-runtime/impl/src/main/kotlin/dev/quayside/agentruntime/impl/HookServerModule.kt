package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.AgentEventSource
import dev.quayside.core.domain.Clock
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.error
import dev.quayside.core.domain.info
import dev.quayside.core.process.ProcessRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import java.nio.file.Path

/**
 * Starts the hook server (ADR 0004) in [scope] and returns its event stream. The token comes from
 * the macOS Keychain and is created on first use. The server stops when [scope] is cancelled; if
 * it cannot start (no Keychain access, port taken) the stream stays silent, the error is logged
 * and polling remains the source of truth.
 *
 * @param homeDir working directory of the `security` calls.
 * @param port TCP port on 127.0.0.1; the hooks written by `HookInstaller` must use the same one.
 */
@Suppress("LongParameterList")
public fun hookEventSourceModule(
    process: ProcessRunner,
    clock: Clock,
    logger: Logger,
    scope: CoroutineScope,
    homeDir: Path,
    port: Int = DEFAULT_HOOK_PORT,
): AgentEventSource = startHookServer(KeychainHookTokenStore(process, homeDir), clock, logger, scope, port)

/** Port from docs/11 §9 (`RuntimeConfig.hookPort`). */
internal const val DEFAULT_HOOK_PORT: Int = 47321

/** Same as the public factory, with any [HookTokenStore]; the tests use it with a fixed token. */
internal fun startHookServer(
    tokens: HookTokenStore,
    clock: Clock,
    logger: Logger,
    scope: CoroutineScope,
    port: Int,
): AgentEventSource {
    val server = KtorHookServer(clock, logger, port)
    scope.launch {
        try {
            when (val token = tokens.getOrCreate()) {
                is Outcome.Err -> {
                    logger.error("hook token unavailable", "reason" to token.error)
                }

                is Outcome.Ok -> {
                    when (val bound = server.start(token.value)) {
                        is Outcome.Err -> {
                            logger.error("hook server did not start", "reason" to bound.error)
                        }

                        is Outcome.Ok -> {
                            logger.info("hook server listening", "port" to bound.value)
                            awaitCancellation()
                        }
                    }
                }
            }
        } finally {
            server.stop()
        }
    }
    return server
}
