package dev.quayside.accounts.api

import dev.quayside.core.domain.AccountId
import java.nio.file.Path
import java.text.Normalizer
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

/** Pure rules of the account list (ADR 0035). */
public object AccountRules {
    /** Cooldown assigned to a new account. */
    public val DEFAULT_COOLDOWN: Duration = 5.hours

    /** Allowed cooldown values, inclusive. */
    public val COOLDOWN_RANGE: ClosedRange<Duration> = 1.hours..24.hours

    /** Maximum length of an account id. */
    public const val MAX_ID: Int = 32

    private const val AUTO = "auto"
    private val RESERVED = setOf("default", AUTO)
    private val SLUG = Regex("[a-z0-9-]{1,$MAX_ID}")
    private val MARKS = Regex("\\p{M}+")
    private val OTHERS = Regex("[^a-z0-9]+")

    /** Lowercase, Polish letters to ASCII, other characters to `-`, trimmed; null when empty or reserved. */
    public fun slug(label: String): AccountId? {
        val ascii = Normalizer.normalize(label.lowercase().replace('ł', 'l'), Normalizer.Form.NFD).replace(MARKS, "")
        val slug =
            ascii
                .replace(OTHERS, "-")
                .trim('-')
                .take(MAX_ID)
                .trimEnd('-')
        return slug.takeIf { it.isNotEmpty() && it !in RESERVED }?.let(::AccountId)
    }

    /** True for a slug `[a-z0-9-]{1,32}` (`auto` passes here; [validate] rejects it). */
    public fun isValidId(value: String): Boolean = SLUG.matches(value)

    /** The first broken invariant of [state] for the user home [home], or null. */
    public fun validate(
        state: AccountsState,
        home: Path,
    ): AccountProblem? =
        AccountProblem.RESERVED_ID.takeIf { state.accounts.any { account -> account.id.value == AUTO } }
            ?: defaultProblem(state.accounts, home)
            ?: duplicateProblem(state.accounts)
            ?: labelProblem(state.accounts)
            ?: AccountProblem.COOLDOWN_OUT_OF_RANGE.takeIf { state.cooldown !in COOLDOWN_RANGE }

    /** The status after a limit hit at [now]: until = now + [cooldown]. */
    public fun cooldownLimit(
        now: Instant,
        cooldown: Duration,
    ): AccountStatus.Limited = AccountStatus.Limited(now, now.plus(cooldown.toJavaDuration()))

    private fun defaultProblem(
        accounts: List<Account>,
        home: Path,
    ): AccountProblem? {
        val default = accounts.firstOrNull { it.isDefault } ?: return AccountProblem.DEFAULT_MISSING
        return AccountProblem.DEFAULT_DIR_CHANGED.takeIf { differs(default.configDir, home.resolve(".claude")) }
    }

    private fun differs(
        a: Path,
        b: Path,
    ): Boolean = a.normalize() != b.normalize()

    private fun duplicateProblem(accounts: List<Account>): AccountProblem? {
        val dirs = accounts.map { it.configDir.normalize() }
        return when {
            accounts.map { it.id }.toSet().size != accounts.size -> AccountProblem.DUPLICATE_ID
            accounts.any { !it.configDir.isAbsolute } -> AccountProblem.RELATIVE_DIR
            dirs.toSet().size != dirs.size -> AccountProblem.DUPLICATE_DIR
            else -> null
        }
    }

    private fun labelProblem(accounts: List<Account>): AccountProblem? =
        AccountProblem.BLANK_LABEL.takeIf {
            accounts.any { account ->
                if (account.isDefault) account.label?.isBlank() == true else account.label.isNullOrBlank()
            }
        }
}
