package dev.quayside.terminal.impl.replyspike

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds

private const val ESC = "\u001b"

/**
 * Sending part of `HiddenReply`: the fake TUI on `PATH` draws a recorded frame, the test reads what it received.
 * `HiddenReply` renders through the real PTY and the emulator, so the frames also pass the classifier here.
 */
@Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class HiddenReplyTest {
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
    fun `text reply pastes the answer and enter once the prompt shows`() =
        realTimeTest {
            val outcome =
                env.hiddenReply.reply(
                    env.attach("text.ansi", delaySeconds = "0.3"),
                    Reply.Text("tak"),
                    KeyVariant(),
                    Detach.HANGUP,
                    FakeTuiEnvironment.SHORT,
                )

            val acknowledged = outcome.shouldBeInstanceOf<ReplyOutcome.Acknowledged>()
            acknowledged.ready shouldBeGreaterThanOrEqualTo 200.milliseconds
            env.typedBeforeFrame().shouldBeEmpty()
            env.typed() shouldBe "$ESC[200~tak$ESC[201~\r"
        }

    @Test
    fun `option reply sends the digit`() =
        realTimeTest {
            val outcome =
                env.hiddenReply.reply(
                    env.attach("options.ansi"),
                    Reply.Option(2),
                    KeyVariant(),
                    Detach.HANGUP,
                    FakeTuiEnvironment.SHORT,
                )

            outcome.shouldBeInstanceOf<ReplyOutcome.Acknowledged>()
            env.typed() shouldBe "2"
        }

    @Test
    fun `option reply with arrows moves down and confirms`() =
        realTimeTest {
            val outcome =
                env.hiddenReply.reply(
                    env.attach("options.ansi"),
                    Reply.Option(3),
                    KeyVariant(choice = ChoiceKeys.ARROWS),
                    Detach.HANGUP,
                    FakeTuiEnvironment.SHORT,
                )

            outcome.shouldBeInstanceOf<ReplyOutcome.Acknowledged>()
            env.typed() shouldBe "$ESC[B$ESC[B\r"
        }

    @ParameterizedTest(name = "allow = {0}")
    @CsvSource("true,1", "false,ESC")
    fun `permission allow and deny send their keys`(
        allow: Boolean,
        keys: String,
    ) = realTimeTest {
        val outcome =
            env.hiddenReply.reply(
                env.attach("permission.ansi"),
                Reply.Permission(allow),
                KeyVariant(),
                Detach.HANGUP,
                FakeTuiEnvironment.SHORT,
            )

        outcome.shouldBeInstanceOf<ReplyOutcome.Acknowledged>()
        env.typed() shouldBe keys.replace("ESC", ESC)
    }

    @Test
    fun `nothing is typed before the prompt appears`() =
        realTimeTest {
            val outcome =
                env.hiddenReply.reply(
                    env.attach("text.ansi", delaySeconds = "0.6"),
                    Reply.Text("tak"),
                    KeyVariant(),
                    Detach.HANGUP,
                    FakeTuiEnvironment.SHORT.copy(ready = 3_000.milliseconds),
                )

            outcome.shouldBeInstanceOf<ReplyOutcome.Acknowledged>()
            env.typedBeforeFrame().shouldBeEmpty()
            env.frameWasDrawn() shouldBe true
        }

    @Test
    fun `a prompt that never shows is NotReady and types nothing`() =
        realTimeTest {
            val outcome =
                env.hiddenReply.reply(
                    env.attach(frame = null),
                    Reply.Text("tak"),
                    KeyVariant(),
                    Detach.HANGUP,
                    FakeTuiEnvironment.SHORT.copy(ready = 500.milliseconds),
                )

            outcome shouldBe ReplyOutcome.NotReady(Prompt.None)
            env.typedBeforeFrame().shouldBeEmpty()
            env.typed() shouldBe ""
        }

    @Test
    fun `a permission prompt for a text reply is KindMismatch and types nothing`() =
        realTimeTest {
            val outcome =
                env.hiddenReply.reply(
                    env.attach("permission.ansi"),
                    Reply.Text("tak"),
                    KeyVariant(),
                    Detach.HANGUP,
                    FakeTuiEnvironment.SHORT,
                )

            outcome shouldBe ReplyOutcome.KindMismatch(Prompt.Permission("Bash"))
            env.typed() shouldBe ""
        }

    @Test
    fun `option beyond the shown count is KindMismatch`() =
        realTimeTest {
            val outcome =
                env.hiddenReply.reply(
                    env.attach("options.ansi"),
                    Reply.Option(4),
                    KeyVariant(),
                    Detach.HANGUP,
                    FakeTuiEnvironment.SHORT,
                )

            outcome shouldBe ReplyOutcome.KindMismatch(Prompt.Options(3))
            env.typed() shouldBe ""
        }

    @Test
    fun `a prompt that stays is NotAcknowledged`() =
        realTimeTest {
            val outcome =
                env.hiddenReply.reply(
                    env.attach("text.ansi", options = TuiOptions(stuck = true)),
                    Reply.Text("tak"),
                    KeyVariant(),
                    Detach.HANGUP,
                    FakeTuiEnvironment.SHORT.copy(ack = 500.milliseconds),
                )

            outcome shouldBe ReplyOutcome.NotAcknowledged
            env.typed() shouldBe "$ESC[200~tak$ESC[201~\r"
        }

    @Test
    fun `an empty input box after the text sat in it acknowledges`() =
        realTimeTest {
            val outcome =
                env.hiddenReply.reply(
                    env.attach("text.ansi", options = TuiOptions(sentFrame = "text-sent-quiet.ansi")),
                    Reply.Text("tak"),
                    KeyVariant(),
                    Detach.HANGUP,
                    FakeTuiEnvironment.SHORT,
                )

            outcome.shouldBeInstanceOf<ReplyOutcome.Acknowledged>()
        }

    @Test
    fun `the input box stays visible so an unchanged empty box is not an acknowledgement`() =
        realTimeTest {
            val outcome =
                env.hiddenReply.reply(
                    env.attach("text.ansi", options = TuiOptions(stuck = true)),
                    Reply.Text("tak"),
                    KeyVariant(text = TextKeys.TYPE),
                    Detach.HANGUP,
                    FakeTuiEnvironment.SHORT.copy(ack = 500.milliseconds),
                )

            outcome shouldBe ReplyOutcome.NotAcknowledged
        }

    @Test
    fun `a busy screen is NotReady and types nothing`() =
        realTimeTest {
            val outcome =
                env.hiddenReply.reply(
                    env.attach("text-sent.ansi"),
                    Reply.Text("tak"),
                    KeyVariant(),
                    Detach.HANGUP,
                    FakeTuiEnvironment.SHORT.copy(ready = 500.milliseconds),
                )

            outcome shouldBe ReplyOutcome.NotReady(Prompt.Busy)
            env.typed() shouldBe ""
        }

    @Test
    fun `a draft in the input box is KindMismatch and types nothing`() =
        realTimeTest {
            val outcome =
                env.hiddenReply.reply(
                    env.attach("text-filled.ansi"),
                    Reply.Text("tak"),
                    KeyVariant(),
                    Detach.HANGUP,
                    FakeTuiEnvironment.SHORT,
                )

            outcome shouldBe ReplyOutcome.KindMismatch(Prompt.TextFilled)
            env.typed() shouldBe ""
        }

    @Test
    fun `a missing shell is StartFailed`() =
        realTimeTest {
            val reply =
                HiddenReply(Path.of("/nonexistent/sh"), mapOf("HOME" to home.toString()), env.logger)

            val outcome =
                reply.reply(env.attach("text.ansi"), Reply.Text("tak"), KeyVariant(), Detach.HANGUP)

            outcome shouldBe ReplyOutcome.StartFailed
        }
}
