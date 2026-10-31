package dev.agentmanager.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import dev.agentmanager.commandpalette.api.ChordKey
import dev.agentmanager.commandpalette.api.KeyChord
import dev.agentmanager.commandpalette.api.KeyModifier
import dev.agentmanager.commandpalette.api.Shortcut
import dev.agentmanager.commandpalette.api.letterChord
import dev.agentmanager.commandpalette.ui.toKeyShortcut
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

/** The one source of the key bindings of the window menu and of the palette rows (AM-408, decision 2). */
class AppShortcutsTest {
    @Test
    fun `shortcuts are the documented ones`() {
        AppShortcuts.ADD_PROJECT shouldBe Shortcut.of(letterChord('o', KeyModifier.META, KeyModifier.SHIFT))
        AppShortcuts.REMOVE_PROJECT shouldBe Shortcut.of(KeyChord(ChordKey.Named.BACKSPACE, setOf(KeyModifier.META)))
        AppShortcuts.PALETTE shouldBe Shortcut.of(letterChord('k', KeyModifier.META))
    }

    @Test
    fun `every shortcut has a menu binding`() {
        AppShortcuts.ADD_PROJECT.toKeyShortcut() shouldBe KeyShortcut(Key.O, meta = true, shift = true)
        AppShortcuts.REMOVE_PROJECT.toKeyShortcut() shouldBe KeyShortcut(Key.Backspace, meta = true)
        AppShortcuts.PALETTE.toKeyShortcut() shouldBe KeyShortcut(Key.K, meta = true)
    }

    @Test
    fun `window key shortcuts are j k and g p`() {
        AppShortcuts.NEXT_SESSION shouldBe Shortcut.of(letterChord('j'))
        AppShortcuts.PREVIOUS_SESSION shouldBe Shortcut.of(letterChord('k'))
        AppShortcuts.SWITCH_PROJECT shouldBe Shortcut.of(letterChord('g'), letterChord('p'))
    }

    @Test
    fun `window key shortcut stop is x`() {
        AppShortcuts.STOP_SESSION shouldBe Shortcut.of(letterChord('x'))
    }

    @Test
    fun `window key shortcut preview is space`() {
        AppShortcuts.PREVIEW_SESSION shouldBe Shortcut.of(KeyChord(ChordKey.Named.SPACE))
    }

    @Test
    fun `window key shortcut attach is Enter`() {
        AppShortcuts.ATTACH_SESSION shouldBe Shortcut.of(KeyChord(ChordKey.Named.ENTER))
        // Not the maximize chord ⌘⇧⏎.
        AppShortcuts.ATTACH_SESSION shouldNotBe AppShortcuts.MAXIMIZE_PANE
    }

    @Test
    fun `window key shortcut reply is r`() {
        AppShortcuts.REPLY_SESSION shouldBe Shortcut.of(letterChord('r'))
    }

    @Test
    fun `window key shortcuts have no meta`() {
        // Shortcuts with ⌘ belong to MenuBar; the window keys must not run twice (ADR 0011).
        val windowKeys =
            listOf(
                AppShortcuts.NEXT_SESSION,
                AppShortcuts.PREVIOUS_SESSION,
                AppShortcuts.STOP_SESSION,
                AppShortcuts.PREVIEW_SESSION,
                AppShortcuts.ATTACH_SESSION,
                AppShortcuts.REPLY_SESSION,
                AppShortcuts.SWITCH_PROJECT,
            )

        windowKeys.flatMap { it.chords }.none { KeyModifier.META in it.modifiers } shouldBe true
        windowKeys.forEach { it.toKeyShortcut() shouldBe null }
    }

    @Test
    fun `all shortcuts are unique`() {
        val all =
            listOf(
                AppShortcuts.ADD_PROJECT,
                AppShortcuts.REMOVE_PROJECT,
                AppShortcuts.PALETTE,
                AppShortcuts.NEXT_SESSION,
                AppShortcuts.PREVIOUS_SESSION,
                AppShortcuts.STOP_SESSION,
                AppShortcuts.PREVIEW_SESSION,
                AppShortcuts.ATTACH_SESSION,
                AppShortcuts.REPLY_SESSION,
                AppShortcuts.SWITCH_PROJECT,
            )

        all.toSet().size shouldBe all.size
    }
}
