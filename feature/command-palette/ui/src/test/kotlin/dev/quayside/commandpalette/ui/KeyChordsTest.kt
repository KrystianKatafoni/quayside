package dev.quayside.commandpalette.ui

import androidx.compose.ui.input.key.Key
import dev.quayside.commandpalette.api.ChordKey
import dev.quayside.commandpalette.api.KeyChord
import dev.quayside.commandpalette.api.KeyModifier
import dev.quayside.commandpalette.api.letterChord
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** Compose key to [KeyChord], the inverse of `toKeyShortcut` (AM-409 plan, table 2). */
class KeyChordsTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `of maps a key with modifiers to a chord`(
        @Suppress("UNUSED_PARAMETER") name: String,
        keyCode: Long,
        modifiers: Set<KeyModifier>,
        expected: KeyChord?,
    ) {
        KeyChords.of(Key(keyCode), modifiers) shouldBe expected
    }

    companion object {
        @JvmStatic
        fun cases(): List<Arguments> =
            listOf(
                arguments("J", Key.J.keyCode, emptySet<KeyModifier>(), letterChord('j')),
                arguments("shift G", Key.G.keyCode, setOf(KeyModifier.SHIFT), letterChord('g', KeyModifier.SHIFT)),
                arguments("0", Key.Zero.keyCode, emptySet<KeyModifier>(), letterChord('0')),
                arguments("Escape", Key.Escape.keyCode, emptySet<KeyModifier>(), KeyChord(ChordKey.Named.ESCAPE)),
                arguments("Space", Key.Spacebar.keyCode, emptySet<KeyModifier>(), KeyChord(ChordKey.Named.SPACE)),
                arguments("cmd K", Key.K.keyCode, setOf(KeyModifier.META), letterChord('k', KeyModifier.META)),
                arguments("left shift alone", Key.ShiftLeft.keyCode, emptySet<KeyModifier>(), null),
                arguments("F1", Key.F1.keyCode, emptySet<KeyModifier>(), null),
            )
    }
}
