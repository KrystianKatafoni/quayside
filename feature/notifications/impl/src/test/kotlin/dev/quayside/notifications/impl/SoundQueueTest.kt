package dev.quayside.notifications.impl

import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationKind.FAILED
import dev.quayside.notifications.api.NotificationKind.NEEDS_INPUT
import dev.quayside.notifications.api.NotificationKind.PERMISSION
import dev.quayside.notifications.api.NotificationKind.PULL_REQUEST
import dev.quayside.notifications.api.NotificationKind.RUNNING_LONG
import dev.quayside.notifications.api.NotificationKind.TERMINAL_BELL
import dev.quayside.notifications.api.NotificationKind.TURN_ENDED
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** The pure reducer behind the sound cooldown (AM-812): one sound per 2 s, one waits, the most important wins. */
class SoundQueueTest {
    private val idle = SoundQueue()

    @Test
    fun `first sound plays at once`() {
        idle.offer(NEEDS_INPUT) shouldBe SoundStep(SoundQueue(cooling = true, pending = null), NEEDS_INPUT)
    }

    @Test
    fun `sound during cooldown waits`() {
        val cooling = SoundQueue(cooling = true)

        cooling.offer(TURN_ENDED) shouldBe SoundStep(SoundQueue(cooling = true, pending = TURN_ENDED), null)
    }

    @Test
    fun `more important kind replaces the waiting one`() {
        val waiting = SoundQueue(cooling = true, pending = TURN_ENDED)

        waiting.offer(FAILED) shouldBe SoundStep(SoundQueue(cooling = true, pending = FAILED), null)
    }

    @Test
    fun `less important kind is dropped`() {
        val waiting = SoundQueue(cooling = true, pending = FAILED)

        waiting.offer(TURN_ENDED) shouldBe SoundStep(waiting, null)
    }

    @Test
    fun `equal kind is dropped`() {
        val waiting = SoundQueue(cooling = true, pending = PERMISSION)

        waiting.offer(PERMISSION) shouldBe SoundStep(waiting, null)
    }

    @Test
    fun `cooldown with a waiting sound plays it and keeps cooling`() {
        SoundQueue(cooling = true, pending = PULL_REQUEST).cooldownEnded() shouldBe
            SoundStep(SoundQueue(cooling = true, pending = null), PULL_REQUEST)
    }

    @Test
    fun `cooldown with nothing waiting ends`() {
        SoundQueue(cooling = true).cooldownEnded() shouldBe SoundStep(SoundQueue(), null)
    }

    @Test
    fun `after the cooldown ended the next sound plays at once`() {
        val ended = SoundQueue(cooling = true).cooldownEnded().state

        ended.offer(TERMINAL_BELL).play shouldBe TERMINAL_BELL
    }

    @ParameterizedTest(name = "{0} beats {1}")
    @MethodSource("importance")
    fun `the more important kind wins in both orders`(
        higher: NotificationKind,
        lower: NotificationKind,
    ) {
        SoundQueue(cooling = true, pending = lower).offer(higher).state.pending shouldBe higher
        SoundQueue(cooling = true, pending = higher).offer(lower).state.pending shouldBe higher
    }

    @Test
    fun `the whole order of importance holds`() {
        val order = listOf(FAILED, PERMISSION, NEEDS_INPUT, PULL_REQUEST, TURN_ENDED, RUNNING_LONG, TERMINAL_BELL)

        order.zipWithNext().forEach { (higher, lower) ->
            SoundQueue(cooling = true, pending = lower).offer(higher).state.pending shouldBe higher
            SoundQueue(cooling = true, pending = higher).offer(lower).state.pending shouldBe higher
        }
        order.toSet() shouldBe NotificationKind.entries.toSet()
    }

    companion object {
        @JvmStatic
        fun importance(): List<Arguments> =
            listOf(
                arguments(FAILED, NEEDS_INPUT),
                arguments(PERMISSION, NEEDS_INPUT),
                arguments(NEEDS_INPUT, TURN_ENDED),
                arguments(TURN_ENDED, TERMINAL_BELL),
                arguments(PULL_REQUEST, TURN_ENDED),
                arguments(TURN_ENDED, RUNNING_LONG),
            )
    }
}
