package dev.quayside.agentruntime.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.process.ProcessRunner
import dev.quayside.core.process.RunOptions
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Duration.Companion.seconds

/** Secret the hook server expects in the `X-AM-Token` header (NFR-4). */
@JvmInline
internal value class HookToken(
    val value: String,
) {
    override fun toString(): String = "HookToken(***)"
}

/** Why the token could not be read or stored; a constant category (ENG-C8). */
internal enum class HookTokenError { KEYCHAIN_UNAVAILABLE, KEYCHAIN_REJECTED }

/** Keeps the hook token between runs, so hooks installed in `settings.json` keep working after a restart. */
internal fun interface HookTokenStore {
    /** The stored token, or a new one that was stored first. */
    suspend fun getOrCreate(): Outcome<HookToken, HookTokenError>
}

/**
 * [HookTokenStore] on the macOS Keychain through `/usr/bin/security` (docs/11 §8).
 *
 * The token is an argument of `add-generic-password` for the moment that call runs, because the
 * tool has no other non-interactive way to take it; it is visible to the same user only.
 */
internal class KeychainHookTokenStore(
    private val process: ProcessRunner,
    private val workDir: Path,
    private val newToken: () -> String = ::randomToken,
) : HookTokenStore {
    override suspend fun getOrCreate(): Outcome<HookToken, HookTokenError> =
        when (
            val found =
                process.run(
                    command(listOf("find-generic-password", "-s", SERVICE, "-a", ACCOUNT, "-w")),
                    OPTIONS,
                )
        ) {
            is Outcome.Err -> {
                Outcome.Err(HookTokenError.KEYCHAIN_UNAVAILABLE)
            }

            is Outcome.Ok -> {
                val existing = found.value.stdout.trim()
                when {
                    found.value.isSuccess && existing.isNotEmpty() -> Outcome.Ok(HookToken(existing))
                    found.value.exitCode == NOT_FOUND_EXIT -> create()
                    else -> Outcome.Err(HookTokenError.KEYCHAIN_REJECTED)
                }
            }
        }

    private suspend fun create(): Outcome<HookToken, HookTokenError> {
        val token = newToken()
        val args = listOf("add-generic-password", "-s", SERVICE, "-a", ACCOUNT, "-w", token, "-U")
        return when (val added = process.run(command(args), OPTIONS)) {
            is Outcome.Err -> {
                Outcome.Err(HookTokenError.KEYCHAIN_UNAVAILABLE)
            }

            is Outcome.Ok -> {
                if (added.value.isSuccess) {
                    Outcome.Ok(
                        HookToken(token),
                    )
                } else {
                    Outcome.Err(HookTokenError.KEYCHAIN_REJECTED)
                }
            }
        }
    }

    private fun command(args: List<String>) = ShellCommand(listOf(SECURITY) + args, workDir)

    private companion object {
        const val SECURITY = "security"
        const val SERVICE = "Quayside"
        const val ACCOUNT = "hook-token"

        /** `security` exit code for "The specified item could not be found in the keychain". */
        const val NOT_FOUND_EXIT = 44
        val OPTIONS = RunOptions(timeout = 10.seconds, maxOutputBytes = 4096)
    }
}

private const val TOKEN_BYTES = 32

/** 256 random bits, URL-safe Base64 without padding (header-safe, no shell quoting needed). */
internal fun randomToken(): String {
    val bytes = ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
