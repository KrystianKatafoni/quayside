package dev.quayside.projects.ui

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

/** Texts of the hooks warning in the remove dialog (AM-610, NFR-10): present, filled in, without paths or names. */
class ProjectsRemoveHooksStringsTest {
    private val keys =
        listOf(
            "remove.checkingHooks",
            "remove.hooks.present",
            "remove.hooks.unknown",
            "remove.hooks.uninstall",
            "remove.hooks.uninstalled",
            "remove.hooks.failed.unreadable",
            "remove.hooks.failed.writeFailed",
        )

    @Test
    fun `every new remove key has a text`() {
        ProjectsStrings.keys shouldContainAll keys
        keys.forEach { key -> withClue(key) { ProjectsStrings[key].shouldNotBeBlank() } }
    }

    @Test
    fun `the button says what it does`() {
        ProjectsStrings["remove.hooks.uninstall"] shouldBe "Odinstaluj hooki"
    }

    @Test
    fun `the warning names the file and says the hooks stay`() {
        ProjectsStrings["remove.hooks.present"] shouldContain ".claude/settings.local.json"
        ProjectsStrings["remove.hooks.present"] shouldContain "Hooki Claude Code"
    }

    @Test
    fun `texts hold no placeholder for a path or a name`() {
        keys.forEach { key -> withClue(key) { ProjectsStrings[key] shouldNotContain "{" } }
    }
}
