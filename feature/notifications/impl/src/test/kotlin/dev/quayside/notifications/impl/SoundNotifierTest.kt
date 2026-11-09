package dev.quayside.notifications.impl

import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.Outcome
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationKind.FAILED
import dev.quayside.notifications.api.NotificationKind.NEEDS_INPUT
import dev.quayside.notifications.api.NotificationKind.TURN_ENDED
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.QuietHours
import dev.quayside.notifications.api.SoundId
import dev.quayside.notifications.api.SoundSettings
import dev.quayside.sessions.api.aSession
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** [SoundNotifier] on virtual time: the 2 s cooldown, the one waiting sound and the second check of quiet hours. */
@OptIn(ExperimentalCoroutinesApi::class)
class SoundNotifierTest {
    private class RecordingSoundPort(
        private val result: Outcome<Unit, SoundError> = Outcome.Ok(Unit),
        private val duration: Duration = Duration.ZERO,
    ) : SoundPort {
        val played = mutableListOf<SoundId>()
        val volumes = mutableListOf<Int>()
        var running = 0
        var maxRunning = 0

        override suspend fun play(
            sound: SoundId,
            volume: Int,
        ): Outcome<Unit, SoundError> {
            played += sound
            volumes += volume
            running++
            maxRunning = maxOf(maxRunning, running)
            try {
                if (duration > Duration.ZERO) delay(duration)
            } finally {
                running--
            }
            return result
        }
    }

    private val port = RecordingSoundPort()
    private val logger = RecordingLogger()
    private val settings = MutableStateFlow(NotificationSettings())
    private var context = ctx()

    private fun TestScope.notifier(
        sound: SoundPort = port,
        scope: CoroutineScope = backgroundScope,
    ) = SoundNotifier(sound, settings, scope, { context }, logger)

    private fun delivery(kind: NotificationKind) =
        Delivery(NotificationChannel.SOUND, kind, aSession(id = "s-$kind", name = kind.name.lowercase()))

    private fun TestScope.pass(time: Duration) {
        advanceTimeBy(time)
        runCurrent()
    }

    @Test
    fun `plays a sound at once`() =
        runTest {
            val notifier = notifier()

            notifier.offer(delivery(NEEDS_INPUT))
            runCurrent()

            port.played shouldBe listOf(SoundId("Glass"))
        }

    @Test
    fun `plays the chosen sound of the kind at the chosen volume`() =
        runTest {
            settings.value =
                NotificationSettings(
                    sound = SoundSettings(volume = 30, sounds = mapOf(NEEDS_INPUT to SoundId("Funk"))),
                )
            val notifier = notifier()

            notifier.offer(delivery(NEEDS_INPUT))
            runCurrent()

            port.played shouldBe listOf(SoundId("Funk"))
            port.volumes shouldBe listOf(30)
        }

    @Test
    fun `a change of the settings reaches the next sound`() =
        runTest {
            val notifier = notifier()
            notifier.offer(delivery(NEEDS_INPUT))
            pass(3.seconds)

            settings.value = NotificationSettings(sound = SoundSettings(volume = 10))
            notifier.offer(delivery(NEEDS_INPUT))
            runCurrent()

            port.volumes shouldBe listOf(100, 10)
        }

    @Test
    fun `second sound within 2 s waits for the cooldown`() =
        runTest {
            val notifier = notifier()
            notifier.offer(delivery(NEEDS_INPUT))
            runCurrent()

            pass(500.milliseconds)
            notifier.offer(delivery(FAILED))
            pass(1.seconds + 499.milliseconds)
            port.played shouldBe listOf(SoundId("Glass"))
            pass(1.milliseconds)

            port.played shouldBe listOf(SoundId("Glass"), SoundId("Basso"))
        }

    @Test
    fun `the cooldown counts from the start of a play`() =
        runTest {
            val notifier = notifier()
            notifier.offer(delivery(NEEDS_INPUT))
            runCurrent()
            pass(1.seconds)
            notifier.offer(delivery(FAILED))
            pass(1.seconds)
            port.played.size shouldBe 2

            notifier.offer(delivery(TURN_ENDED))
            pass(1.seconds + 999.milliseconds)
            port.played.size shouldBe 2
            pass(1.milliseconds)

            port.played.last() shouldBe SoundId("Hero")
        }

    @Test
    fun `three sounds within 2 s play the first and the most important`() =
        runTest {
            val notifier = notifier()
            notifier.offer(delivery(TURN_ENDED))
            runCurrent()

            notifier.offer(delivery(NEEDS_INPUT))
            notifier.offer(delivery(FAILED))
            notifier.offer(delivery(TURN_ENDED))
            pass(2.seconds)

            port.played shouldBe listOf(SoundId("Hero"), SoundId("Basso"))
            pass(10.seconds)
            port.played.size shouldBe 2
        }

    @Test
    fun `sound after the cooldown plays at once`() =
        runTest {
            val notifier = notifier()
            notifier.offer(delivery(NEEDS_INPUT))
            pass(2.seconds)

            notifier.offer(delivery(FAILED))
            runCurrent()

            port.played shouldBe listOf(SoundId("Glass"), SoundId("Basso"))
        }

    @Test
    fun `a waiting sound is played once`() =
        runTest {
            val notifier = notifier()
            notifier.offer(delivery(NEEDS_INPUT))
            runCurrent()
            notifier.offer(delivery(FAILED))

            pass(30.seconds)

            port.played shouldBe listOf(SoundId("Glass"), SoundId("Basso"))
        }

    @Test
    fun `sounds never overlap`() =
        runTest {
            val slow = RecordingSoundPort(duration = 3.seconds)
            val notifier = notifier(slow)
            notifier.offer(delivery(NEEDS_INPUT))
            runCurrent()
            notifier.offer(delivery(FAILED))

            pass(2.seconds)
            slow.maxRunning shouldBe 1
            pass(10.seconds)

            slow.played shouldBe listOf(SoundId("Glass"), SoundId("Basso"))
            slow.maxRunning shouldBe 1
        }

    @Test
    fun `waiting sound is dropped when quiet hours started`() =
        runTest {
            settings.value = NotificationSettings(quietHours = QuietHours(enabled = true))
            val notifier = notifier()
            notifier.offer(delivery(NEEDS_INPUT))
            runCurrent()
            notifier.offer(delivery(FAILED))

            context = ctx(localTime = LocalTime.of(23, 0))
            pass(5.seconds)

            port.played shouldBe listOf(SoundId("Glass"))
        }

    @Test
    fun `a sound queued behind a long play is dropped when quiet hours started`() =
        runTest {
            settings.value = NotificationSettings(quietHours = QuietHours(enabled = true))
            val slow = RecordingSoundPort(duration = 5.seconds)
            val notifier = notifier(slow)
            notifier.offer(delivery(NEEDS_INPUT))
            runCurrent()
            notifier.offer(delivery(FAILED))
            pass(2.seconds)

            context = ctx(localTime = LocalTime.of(23, 0))
            pass(10.seconds)

            slow.played shouldBe listOf(SoundId("Glass"))
        }

    @Test
    fun `waiting sound is dropped when a Focus started`() =
        runTest {
            val notifier = notifier()
            notifier.offer(delivery(NEEDS_INPUT))
            runCurrent()
            notifier.offer(delivery(FAILED))

            context = ctx(focusActive = true)
            pass(5.seconds)

            port.played shouldBe listOf(SoundId("Glass"))
        }

    @Test
    fun `waiting sound is played when the hours do not cover the time`() =
        runTest {
            settings.value = NotificationSettings(quietHours = QuietHours(enabled = true))
            val notifier = notifier()
            notifier.offer(delivery(NEEDS_INPUT))
            runCurrent()
            notifier.offer(delivery(FAILED))

            pass(2.seconds)

            port.played shouldBe listOf(SoundId("Glass"), SoundId("Basso"))
        }

    @Test
    fun `after a sound dropped by quiet hours the next one plays at once`() =
        runTest {
            settings.value = NotificationSettings(quietHours = QuietHours(enabled = true))
            val notifier = notifier()
            notifier.offer(delivery(NEEDS_INPUT))
            runCurrent()
            notifier.offer(delivery(FAILED))
            context = ctx(localTime = LocalTime.of(23, 0))
            pass(2.seconds)

            context = ctx(localTime = LocalTime.of(7, 0))
            notifier.offer(delivery(TURN_ENDED))
            runCurrent()

            port.played shouldBe listOf(SoundId("Glass"), SoundId("Hero"))
        }

    @Test
    fun `failed play does not block the next sound`() =
        runTest {
            val failing = RecordingSoundPort(Outcome.Err(SoundError.Unavailable))
            val notifier = notifier(failing)
            notifier.offer(delivery(NEEDS_INPUT))
            pass(2.seconds)

            notifier.offer(delivery(FAILED))
            runCurrent()

            failing.played shouldBe listOf(SoundId("Glass"), SoundId("Basso"))
        }

    @Test
    fun `failed play does not drop the waiting sound`() =
        runTest {
            val failing = RecordingSoundPort(Outcome.Err(SoundError.Failed(1)))
            val notifier = notifier(failing)
            notifier.offer(delivery(NEEDS_INPUT))
            runCurrent()
            notifier.offer(delivery(FAILED))

            pass(2.seconds)

            failing.played shouldBe listOf(SoundId("Glass"), SoundId("Basso"))
        }

    @ParameterizedTest(name = "{1}")
    @MethodSource("errors")
    fun `error is logged with the category only`(
        errorName: String,
        category: String,
    ) = runTest {
        val error =
            when (errorName) {
                "unavailable" -> SoundError.Unavailable
                "failed" -> SoundError.Failed(7)
                else -> SoundError.TimedOut
            }
        val failing = RecordingSoundPort(Outcome.Err(error))
        val notifier = notifier(failing)

        notifier.offer(delivery(FAILED))
        runCurrent()

        val warnings = logger.entriesAt(LogLevel.WARN)
        warnings.size shouldBe 1
        warnings.single().message shouldBe "sound failed"
        warnings.single().fields shouldBe mapOf("reason" to category)
        val logged = warnings.joinToString { "${it.message} ${it.fields} ${it.cause}" }
        logged shouldNotContain "Basso"
        logged shouldNotContain ".aiff"
        logged shouldNotContain "/System"
        logged shouldNotContain "FAILED"
    }

    @Test
    fun `a successful play logs nothing`() =
        runTest {
            val notifier = notifier()

            notifier.offer(delivery(FAILED))
            runCurrent()

            logger.entries shouldBe emptyList()
        }

    @Test
    fun `cancelling the scope stops waiting`() =
        runTest {
            val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
            val notifier = notifier(scope = scope)
            notifier.offer(delivery(NEEDS_INPUT))
            runCurrent()
            notifier.offer(delivery(FAILED))

            scope.cancel()
            pass(60.seconds)

            port.played shouldBe listOf(SoundId("Glass"))
        }

    companion object {
        @JvmStatic
        fun errors(): List<Arguments> =
            listOf(
                arguments("unavailable", "unavailable"),
                arguments("failed", "failed"),
                arguments("timed-out", "timed-out"),
            )
    }
}
