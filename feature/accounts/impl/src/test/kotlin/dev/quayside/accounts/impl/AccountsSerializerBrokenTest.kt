package dev.quayside.accounts.impl

import dev.quayside.accounts.api.defaultState
import dev.quayside.core.domain.LogLevel
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.util.stream.Stream

/** Files of `accounts.json` that must be refused: set aside as `.broken`, the defaults apply (ADR 0035 p.3). */
class AccountsSerializerBrokenTest : AccountsFsTest() {
    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenFiles")
    fun `a refused file is moved to broken and gives the default account`(
        @Suppress("UNUSED_PARAMETER") name: String,
        data: String,
    ) = runTest {
        env.writeFile(data)

        val state =
            env
                .open()
                .repo.state.value

        state shouldBe defaultState(env.home)
        Files.exists(env.support.resolve("accounts.json.broken")) shouldBe true
        Files.exists(env.file) shouldBe false
    }

    @Test
    fun `broken message never quotes the file`() =
        runTest {
            env.writeFile(
                """{"accounts": [{"id": "praca", "label": "SEKRET-ETYKIETA",
                    "configDir": "/Users/sekret-user/x", "enabled": "true"}]}""",
            )

            env.open()

            val logged = env.logger.entries.joinToString { it.message + it.fields + it.cause?.message }
            env.logger.entries.shouldNotBeEmpty()
            logged shouldNotContain "SEKRET"
            logged shouldNotContain "sekret-user"
        }

    @Test
    fun `a broken file is logged as a warning without details`() =
        runTest {
            env.writeFile("""{"cooldownMinutes": 30}""")

            env.open()

            env.logger.entriesAt(LogLevel.WARN).shouldNotBeEmpty()
        }

    companion object {
        private const val DIR = "/Users/x/.claude-quayside"

        private fun case(
            name: String,
            data: String,
        ) = Arguments.of(name, data)

        /** One entry of `accounts`; a null [label] or [dir] leaves the key out, [more] is raw JSON. */
        private fun acct(
            id: String = "praca",
            label: String? = "P",
            dir: String? = "$DIR/$id",
            more: String = "",
        ): String {
            val parts =
                listOfNotNull(
                    "\"id\": \"$id\"",
                    label?.let { "\"label\": \"$it\"" },
                    dir?.let { "\"configDir\": \"$it\"" },
                    more.ifEmpty { null },
                )
            return parts.joinToString(prefix = "{", postfix = "}")
        }

        private fun accounts(vararg entries: String) = entries.joinToString(prefix = "{\"accounts\": [", postfix = "]}")

        @Suppress("LongMethod")
        @JvmStatic
        fun brokenFiles(): Stream<Arguments> =
            Stream.of(
                case("default entry with configDir", accounts(acct("default", null))),
                case("duplicate id", accounts(acct(), acct(dir = "$DIR/q"))),
                case("duplicate configDir", accounts(acct("a", dir = "$DIR/z"), acct("b", dir = "$DIR/z"))),
                case("bad slug", accounts(acct("Praca X"))),
                case("reserved id auto", accounts(acct("auto"))),
                case("slug of 33 characters", accounts(acct("a".repeat(33)))),
                case("cooldown 30", """{"cooldownMinutes": 30}"""),
                case("cooldown 59", """{"cooldownMinutes": 59}"""),
                case("cooldown 1441", """{"cooldownMinutes": 1441}"""),
                case("cooldown as a string", """{"cooldownMinutes": "120"}"""),
                case("enabled true as a string", accounts(acct(more = "\"enabled\": \"true\""))),
                case("inAutomation as a number", accounts(acct(more = "\"inAutomation\": 1"))),
                case("relative configDir", accounts(acct(dir = "accounts/p"))),
                case("added account without configDir", accounts(acct(dir = null))),
                case("added account without label", accounts(acct(label = null))),
                case("added account with a blank label", accounts(acct(label = " "))),
                case("accounts is not an array", """{"accounts": {"id": "praca"}}"""),
                case("entry without id", """{"accounts": [{"label": "P", "configDir": "$DIR/p"}]}"""),
                case("data is not an object", "[]"),
            )
    }
}
