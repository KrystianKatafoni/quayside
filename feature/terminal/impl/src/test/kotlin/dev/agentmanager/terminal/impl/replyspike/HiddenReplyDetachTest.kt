package dev.agentmanager.terminal.impl.replyspike

import dev.agentmanager.core.domain.LogLevel
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds

private const val ESC = "\u001b"
private const val SECRET_ANSWER = "sekretna-odpowiedz-123"

/** Detaching and logging part of `HiddenReply`; same fake TUI as `HiddenReplyTest`. */
@Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class HiddenReplyDetachTest {
    @TempDir
    lateinit var home: Path

    private lateinit var env: FakeTuiEnvironment

    @BeforeEach
    fun setUp() {
        env = FakeTuiEnvironment(home)
    }

    @AfterEach
    fun tearDown() {
        env.cleanUp()
    }

    @Test
    fun `hangup detach leaves a process outside the client alive`() =
        realTimeTest {
            val outcome =
                env.hiddenReply.reply(
                    env.attach("text.ansi", options = TuiOptions(withChild = true)),
                    Reply.Text("tak"),
                    KeyVariant(),
                    Detach.HANGUP,
                    FakeTuiEnvironment.SHORT,
                )

            outcome.shouldBeInstanceOf<ReplyOutcome.Acknowledged>()
            val client = checkNotNull(env.clientPid()) { "client pid not written" }
            val child = checkNotNull(env.childPid()) { "child pid not written" }
            env.eventually {
                ProcessHandle
                    .of(client)
                    .map { it.isAlive }
                    .orElse(false)
                    .not()
            } shouldBe true
            ProcessHandle.of(child).map { it.isAlive }.orElse(false) shouldBe true
        }

    @Test
    fun `key detach sends the key`() =
        realTimeTest {
            val outcome =
                env.hiddenReply.reply(
                    env.attach("text.ansi"),
                    Reply.Text("tak"),
                    KeyVariant(),
                    Detach.KEY,
                    FakeTuiEnvironment.SHORT,
                )

            outcome.shouldBeInstanceOf<ReplyOutcome.Acknowledged>()
            val expected = "$ESC[200~tak$ESC[201~\r" + DETACH_KEY
            env.eventually { env.typed() == expected } shouldBe true
            env.typed() shouldBe expected
        }

    @Test
    fun `key detach after NotReady types nothing`() =
        realTimeTest {
            val outcome =
                env.hiddenReply.reply(
                    env.attach(frame = null),
                    Reply.Text("tak"),
                    KeyVariant(),
                    Detach.KEY,
                    FakeTuiEnvironment.SHORT.copy(ready = 300.milliseconds),
                )

            outcome shouldBe ReplyOutcome.NotReady(Prompt.None)
            env.typed() shouldBe ""
        }

    @Test
    fun `key detach after KindMismatch types nothing`() =
        realTimeTest {
            val outcome =
                env.hiddenReply.reply(
                    env.attach("permission.ansi"),
                    Reply.Text("tak"),
                    KeyVariant(),
                    Detach.KEY,
                    FakeTuiEnvironment.SHORT,
                )

            outcome shouldBe ReplyOutcome.KindMismatch(Prompt.Permission("Bash"))
            env.typed() shouldBe ""
        }

    @Test
    fun `hangup of a client that ends is a clean detach`() =
        realTimeTest {
            val outcome = replyAndDetach(Detach.HANGUP, TuiOptions())

            outcome.cleanDetach shouldBe true
        }

    @Test
    fun `hangup of a client that ignores the signal is not a clean detach`() =
        realTimeTest {
            val outcome = replyAndDetach(Detach.HANGUP, TuiOptions(ignoreHangup = true))

            outcome.cleanDetach shouldBe false
        }

    @Test
    fun `key detach of a client that exits on the key is a clean detach`() =
        realTimeTest {
            val outcome = replyAndDetach(Detach.KEY, TuiOptions(detachExits = true))

            outcome.cleanDetach shouldBe true
        }

    @Test
    fun `key detach of a client that ignores the key is not a clean detach`() =
        realTimeTest {
            val outcome = replyAndDetach(Detach.KEY, TuiOptions())

            outcome.cleanDetach shouldBe false
        }

    private suspend fun replyAndDetach(
        detach: Detach,
        options: TuiOptions,
    ): ReplyOutcome.Acknowledged =
        env.hiddenReply
            .reply(
                env.attach("text.ansi", options = options),
                Reply.Text("tak"),
                KeyVariant(),
                detach,
                FakeTuiEnvironment.SHORT,
            ).shouldBeInstanceOf<ReplyOutcome.Acknowledged>()

    @Test
    fun `logs carry kind and outcome only`() =
        realTimeTest {
            env.hiddenReply.reply(
                env.attach("text.ansi"),
                Reply.Text(SECRET_ANSWER),
                KeyVariant(),
                Detach.HANGUP,
                FakeTuiEnvironment.SHORT,
            )

            val logged = env.logger.entries
            logged.shouldNotBeEmpty()
            logged.forEach { entry ->
                val text = "${entry.message} ${entry.fields}"
                text shouldNotContain SECRET_ANSWER
                // Text of the frame: a question and the box of the TUI.
                text shouldNotContain "Jakie słowo"
                text shouldNotContain "answer.txt"
                entry.level shouldBeLessThanOrEqualTo LogLevel.INFO
            }
            val all = logged.joinToString("\n") { "${it.message} ${it.fields}" }
            all.lowercase() shouldContain "text"
            all.lowercase() shouldContain "acknowledged"
        }
}
