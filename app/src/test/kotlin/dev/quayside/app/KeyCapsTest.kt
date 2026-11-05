package dev.quayside.app

import dev.quayside.commandpalette.api.ChordKey
import dev.quayside.commandpalette.api.KeyChord
import dev.quayside.commandpalette.api.KeyModifier
import dev.quayside.commandpalette.api.Shortcut
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** The shortcut marks next to the actions (plan AM-809): macOS order of modifiers, capital letter with a modifier. */
internal class KeyCapsTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("shortcuts")
    fun `key caps of the window shortcuts`(
        @Suppress("UNUSED_PARAMETER") name: String,
        shortcut: Shortcut,
        expected: String,
    ) {
        keyCapOf(shortcut) shouldBe expected
    }

    @Test
    fun `window key caps are those of the window shortcuts`() {
        val caps = windowKeyCaps()

        caps.attach shouldBe "⏎"
        caps.reply shouldBe "r"
        caps.stop shouldBe "x"
        caps.newAgent shouldBe "⌘N"
    }

    companion object {
        private fun letter(
            char: Char,
            vararg modifiers: KeyModifier,
        ) = KeyChord(ChordKey.Letter(char), modifiers.toSet())

        @JvmStatic
        fun shortcuts(): List<Arguments> =
            listOf(
                arguments("attach", AppShortcuts.ATTACH_SESSION, "⏎"),
                arguments("reply, a letter without a modifier stays small", AppShortcuts.REPLY_SESSION, "r"),
                arguments("stop", AppShortcuts.STOP_SESSION, "x"),
                arguments("new agent, a letter with a modifier is capital", AppShortcuts.NEW_AGENT, "⌘N"),
                arguments("add project, shift comes before command", AppShortcuts.ADD_PROJECT, "⇧⌘O"),
                arguments(
                    "all four modifiers in macOS order",
                    Shortcut.of(
                        letter('k', KeyModifier.META, KeyModifier.ALT, KeyModifier.CTRL, KeyModifier.SHIFT),
                    ),
                    "⌃⌥⇧⌘K",
                ),
                arguments("space", AppShortcuts.PREVIEW_SESSION, "␣"),
                arguments("two chords are joined by a space", AppShortcuts.SWITCH_PROJECT, "g p"),
            )
    }
}
