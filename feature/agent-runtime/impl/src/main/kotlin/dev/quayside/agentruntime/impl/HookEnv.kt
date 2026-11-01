package dev.quayside.agentruntime.impl

import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.warn

/** Name of the variable the installed HTTP hooks read their token from (`X-AM-Token` header). */
internal const val HOOK_TOKEN_ENV = "AM_HOOK_TOKEN"

/**
 * `AM_HOOK_TOKEN` for the process that (re)starts a session, so the HTTP hooks can send it in
 * `X-AM-Token`. Empty when there is no store or the Keychain fails: the session still starts and
 * polling stays the source of truth. The token only goes to the child environment, never to logs.
 */
internal suspend fun HookTokenStore?.hookEnv(logger: Logger): Map<String, String> =
    when (val token = this?.getOrCreate()) {
        null -> {
            emptyMap()
        }

        is Outcome.Ok -> {
            mapOf(HOOK_TOKEN_ENV to token.value.value)
        }

        is Outcome.Err -> {
            logger.warn("hook token unavailable for claude", "reason" to token.error)
            emptyMap()
        }
    }
