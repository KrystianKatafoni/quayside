package dev.quayside.accounts.api

import dev.quayside.accounts.api.AccountStatus.Available
import dev.quayside.accounts.api.AccountStatus.Limited
import dev.quayside.accounts.api.AccountStatus.LoggedOut
import dev.quayside.accounts.api.AccountStatus.Unknown
import dev.quayside.core.domain.AccountId
import dev.quayside.core.testing.T0
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.util.stream.Stream

/** The order of accounts is A, B, C and `now` is [T0]; every row has an answer that other rules would not give. */
class AccountSelectorTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    fun `choose picks an account for the new session`(row: Row) {
        AccountSelector.choose(row.accounts, row.statuses, row.preferred, T0) shouldBe row.expected
    }

    class Row(
        private val name: String,
        val accounts: List<Account>,
        val statuses: Map<AccountId, AccountStatus>,
        val preferred: AccountId?,
        val expected: AccountId?,
    ) {
        override fun toString(): String = name
    }

    companion object {
        private val a = AccountId("a")
        private val b = AccountId("b")
        private val c = AccountId("c")

        private fun acct(
            id: AccountId,
            enabled: Boolean = true,
            inAutomation: Boolean = true,
        ) = Account(id, id.value, Path.of("/h/${id.value}"), enabled, inAutomation)

        private val abc = listOf(acct(a), acct(b), acct(c))

        @Suppress("LongMethod")
        @JvmStatic
        fun rows(): Stream<Arguments> {
            val later = T0.plusSeconds(1)
            val earlier = T0.minusSeconds(1)
            val free = mapOf(a to Available, b to Available, c to Available)
            val bOut = abc.map { if (it.id == b) it.copy(inAutomation = false) else it }

            fun row(
                name: String,
                statuses: Map<AccountId, AccountStatus>,
                preferred: AccountId? = null,
                expected: AccountId? = null,
                accounts: List<Account> = abc,
            ) = Arguments.of(Row(name, accounts, statuses, preferred, expected))
            return Stream.of(
                row("preferred wins when all are available", free, b, b),
                row("preferred B logged out gives A", free + (b to LoggedOut), b, a),
                row(
                    "preferred B disabled gives A",
                    free,
                    b,
                    a,
                    abc.map {
                        if (it.id ==
                            b
                        ) {
                            it.copy(enabled = false)
                        } else {
                            it
                        }
                    },
                ),
                row("preferred B outside automation is still the user's choice", free, b, b, bOut),
                row("preferred B still limited gives A", free + (b to Limited(earlier, later)), b, a),
                row("preferred B with an over limit is used", free + (b to Limited(earlier, T0)), b, b),
                row("preferred missing from the list is ignored", free, AccountId("x"), a),
                row("preferred B unknown is used although A is available", free + (b to Unknown), b, b),
                row("preferred B without a status is used although A is available", mapOf(a to Available), b, b),
                row("no preference takes the first available, not the last", free + (a to LoggedOut), null, b),
                row("limited A gives B", free + (a to Limited(T0, later)), null, b),
                row("a limit that ends exactly now is over", free + (a to Limited(earlier, T0)), null, a),
                row(
                    "limit until unlock, B outside automation, C unknown",
                    mapOf(
                        a to Limited(T0, null),
                        b to Available,
                        c to Unknown,
                    ),
                    null,
                    c,
                    bOut,
                ),
                row("available comes before unknown", mapOf(a to Unknown, b to Available, c to Available), null, b),
                row("unknown ones keep their order", mapOf(a to LoggedOut, b to Unknown, c to Unknown), null, b),
                row("a missing status is unknown, so it is used", mapOf(b to LoggedOut, c to LoggedOut), null, a),
                row(
                    "all limited or logged out gives null",
                    mapOf(
                        a to Limited(T0, later),
                        b to LoggedOut,
                        c to Limited(T0, null),
                    ),
                    null,
                    null,
                ),
                row("an empty list gives null", emptyMap(), null, null, emptyList()),
                row(
                    "an account outside automation is not chosen without preference",
                    mapOf(
                        a to LoggedOut,
                        b to Available,
                        c to LoggedOut,
                    ),
                    null,
                    null,
                    bOut,
                ),
            )
        }
    }
}
