package dev.quayside.accounts.api

import dev.quayside.core.domain.AccountId
import dev.quayside.core.testing.T0
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/** Pure rules of the account list: slugs, invariants and the cooldown (ADR 0035). */
class AccountRulesTest {
    private val home = Path.of("/Users/test")

    @ParameterizedTest
    @CsvSource(
        "Praca,praca",
        "'Praca Zdalna!!',praca-zdalna",
        "'Żółć łąka',zolc-laka",
        "ŁÓDŹ ŚĆŃ,lodz-scn",
        "'ąęćńóśźż',aecnoszz",
        "--a--b--,a-b",
        "Konto 2,konto-2",
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa,aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    )
    fun `slug turns a name into an id`(
        label: String,
        expected: String,
    ) {
        AccountRules.slug(label) shouldBe AccountId(expected)
    }

    @ParameterizedTest
    @ValueSource(strings = ["   ", "", "!!!", "🦊", "default", "Auto", " DEFAULT "])
    fun `slug is null for an empty or reserved name`(label: String) {
        AccountRules.slug(label) shouldBe null
    }

    @Test
    fun `slug cuts at 32 characters and does not leave a trailing dash`() {
        AccountRules.slug("a".repeat(31) + " b") shouldBe AccountId("a".repeat(31))
    }

    @ParameterizedTest
    @CsvSource("a,true", "praca-2,true", "0,true", "'',false", "Praca,false", "a_b,false", "a b,false", "żółw,false")
    fun `isValidId accepts only slugs`(
        value: String,
        valid: Boolean,
    ) {
        AccountRules.isValidId(value) shouldBe valid
    }

    @Test
    fun `isValidId allows 32 characters and not 33`() {
        AccountRules.isValidId("a".repeat(32)) shouldBe true
        AccountRules.isValidId("a".repeat(33)) shouldBe false
    }

    private fun state(
        vararg extra: Account,
        cooldown: Duration = AccountRules.DEFAULT_COOLDOWN,
        default: Account? = defaultAccount(home),
    ) = AccountsState(listOfNotNull(default) + extra, cooldown)

    private fun problem(state: AccountsState) = AccountRules.validate(state, home)

    @Test
    fun `a default account alone is valid`() {
        problem(state()) shouldBe null
    }

    @Test
    fun `accounts in any order with the default second are valid`() {
        val s = AccountsState(listOf(anAccount("a", home), defaultAccount(home), anAccount("b", home)))

        problem(s) shouldBe null
    }

    @Test
    fun `without the default account it is DEFAULT_MISSING`() {
        problem(state(anAccount("a", home), default = null)) shouldBe AccountProblem.DEFAULT_MISSING
    }

    @Test
    fun `the default account in another dir is DEFAULT_DIR_CHANGED`() {
        val moved = defaultAccount(home).copy(configDir = home.resolve(".claude-other"))

        problem(state(default = moved)) shouldBe AccountProblem.DEFAULT_DIR_CHANGED
    }

    @Test
    fun `the default dir given with dot dot is the same dir`() {
        val same = defaultAccount(home).copy(configDir = home.resolve("x").resolve("..").resolve(".claude"))

        problem(state(default = same)) shouldBe null
    }

    @Test
    fun `a repeated id is DUPLICATE_ID`() {
        val twin = anAccount("a", home).copy(configDir = home.resolve("elsewhere"))

        problem(state(anAccount("a", home), twin)) shouldBe AccountProblem.DUPLICATE_ID
    }

    @Test
    fun `the reserved id auto is RESERVED_ID`() {
        problem(state(anAccount("auto", home))) shouldBe AccountProblem.RESERVED_ID
    }

    @Test
    fun `ids next to the reserved one are valid`() {
        problem(state(anAccount("autos", home), anAccount("auto-2", home))) shouldBe null
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "a", "0", "praca-2", "default", "auto", "-", "-a", "a-", "--",
            "Praca", "A", "a_b", "a b", "żółw", "a/b", "", " ",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        ],
    )
    fun `isValidId and AccountId accept and reject the same values`(value: String) {
        val accepted = runCatching { AccountId(value) }.isSuccess

        AccountRules.isValidId(value) shouldBe accepted
    }

    @Test
    fun `a repeated dir is DUPLICATE_DIR`() {
        val twin = anAccount("b", home).copy(configDir = anAccount("a", home).configDir)

        problem(state(anAccount("a", home), twin)) shouldBe AccountProblem.DUPLICATE_DIR
    }

    @Test
    fun `a dir with a trailing slash or dot dot is the same dir`() {
        val a = anAccount("a", home)
        val slash = anAccount("b", home).copy(configDir = Path.of("${a.configDir}/"))
        val dots = anAccount("c", home).copy(configDir = a.configDir.resolve("x").resolve(".."))

        problem(state(a, slash)) shouldBe AccountProblem.DUPLICATE_DIR
        problem(state(a, dots)) shouldBe AccountProblem.DUPLICATE_DIR
    }

    @Test
    fun `a dir equal to the default dir is DUPLICATE_DIR`() {
        val inDefault = anAccount("a", home).copy(configDir = home.resolve(".claude"))

        problem(state(inDefault)) shouldBe AccountProblem.DUPLICATE_DIR
    }

    @Test
    fun `a relative dir is RELATIVE_DIR`() {
        val relative = anAccount("a", home).copy(configDir = Path.of("accounts/a"))

        problem(state(relative)) shouldBe AccountProblem.RELATIVE_DIR
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   "])
    fun `a blank label of an added account is BLANK_LABEL`(label: String) {
        problem(state(anAccount("a", home).copy(label = label))) shouldBe AccountProblem.BLANK_LABEL
    }

    @Test
    fun `a missing label is allowed only for the default account`() {
        problem(state(default = defaultAccount(home, label = null))) shouldBe null
        problem(state(anAccount("a", home).copy(label = null))) shouldBe AccountProblem.BLANK_LABEL
        problem(state(default = defaultAccount(home, label = " "))) shouldBe AccountProblem.BLANK_LABEL
    }

    @ParameterizedTest
    @CsvSource("60,true", "300,true", "1440,true", "59,false", "1441,false", "0,false", "-60,false")
    fun `the cooldown must be 1 to 24 hours`(
        minutes: Int,
        valid: Boolean,
    ) {
        val expected = if (valid) null else AccountProblem.COOLDOWN_OUT_OF_RANGE

        problem(state(cooldown = minutes.minutes)) shouldBe expected
    }

    @Test
    fun `cooldownLimit adds the cooldown to now`() {
        val limited = AccountRules.cooldownLimit(T0, 2.hours)

        limited.since shouldBe T0
        limited.until shouldBe T0.plus(2.hours.toJavaDuration())
    }

    @Test
    fun `the defaults are five hours within one to twenty four`() {
        AccountRules.DEFAULT_COOLDOWN shouldBe 5.hours
        AccountRules.COOLDOWN_RANGE shouldBe 1.hours..24.hours
        AccountRules.MAX_ID shouldBe 32
    }
}
