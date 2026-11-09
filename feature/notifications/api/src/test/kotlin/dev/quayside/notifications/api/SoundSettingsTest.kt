package dev.quayside.notifications.api

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

/** [SoundSettings] and [SoundId]: the defaults and the limits (FR-N1). */
class SoundSettingsTest {
    @Test
    fun `defaults play at full volume with the default sounds`() {
        val s = SoundSettings()

        s.volume shouldBe 100
        s.sounds shouldBe emptyMap()
        NotificationSettings().sound shouldBe s
    }

    @ParameterizedTest(name = "{0} plays {1}")
    @MethodSource("defaults")
    fun `default sound of every kind`(
        kind: NotificationKind,
        name: String,
    ) {
        SoundSettings.defaultSound(kind) shouldBe SoundId(name)
        SoundSettings().soundFor(kind) shouldBe SoundId(name)
    }

    @Test
    fun `every kind has a default sound`() {
        defaults().map { it.get()[0] }.toSet() shouldBe NotificationKind.entries.toSet()
    }

    @Test
    fun `a chosen sound wins over the default and leaves other kinds alone`() {
        val s = SoundSettings(sounds = mapOf(NotificationKind.FAILED to SoundId("Funk")))

        s.soundFor(NotificationKind.FAILED) shouldBe SoundId("Funk")
        s.soundFor(NotificationKind.TURN_ENDED) shouldBe SoundId("Hero")
    }

    @ParameterizedTest(name = "volume {0} is accepted")
    @ValueSource(ints = [0, 1, 100])
    fun `volume inside the range`(volume: Int) {
        SoundSettings(volume = volume).volume shouldBe volume
    }

    @ParameterizedTest(name = "volume {0} is refused")
    @ValueSource(ints = [-1, 101])
    fun `volume outside the range`(volume: Int) {
        shouldThrow<IllegalArgumentException> { SoundSettings(volume = volume) }
    }

    @ParameterizedTest(name = "\"{0}\" is a valid name")
    @ValueSource(strings = ["Glass", "Sosumi 2", "a", "Ł-1", "x_y.z", "9"])
    fun `valid sound names`(name: String) {
        SoundId.isValid(name) shouldBe true
        SoundId(name).name shouldBe name
    }

    @ParameterizedTest(name = "\"{0}\" is not a valid name")
    @ValueSource(strings = ["", " ", "-v", ".hidden", "../Glass", "a/b", "a\\b", "a\nb", "a\u0000b"])
    fun `invalid sound names`(name: String) {
        SoundId.isValid(name) shouldBe false
        shouldThrow<IllegalArgumentException> { SoundId(name) }
        SoundId.parse(name) shouldBe null
    }

    @Test
    fun `name length is limited`() {
        SoundId.isValid("a".repeat(SoundId.MAX_LENGTH)) shouldBe true
        SoundId.isValid("a".repeat(SoundId.MAX_LENGTH + 1)) shouldBe false
    }

    @Test
    fun `parse trims the name`() {
        SoundId.parse("  Glass ") shouldBe SoundId("Glass")
    }

    companion object {
        @JvmStatic
        fun defaults(): List<Arguments> =
            listOf(
                arguments(NotificationKind.NEEDS_INPUT, "Glass"),
                arguments(NotificationKind.PERMISSION, "Ping"),
                arguments(NotificationKind.FAILED, "Basso"),
                arguments(NotificationKind.TURN_ENDED, "Hero"),
                arguments(NotificationKind.PULL_REQUEST, "Pop"),
                arguments(NotificationKind.RUNNING_LONG, "Submarine"),
                arguments(NotificationKind.TERMINAL_BELL, "Tink"),
            )
    }
}
