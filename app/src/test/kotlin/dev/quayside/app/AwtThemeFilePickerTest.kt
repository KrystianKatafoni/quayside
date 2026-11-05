package dev.quayside.app

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The switch of `apple.awt.fileDialogForDirectories` around the file dialog of themes (plan AM-804, risks): `main` sets
 * it to `true` for the folder dialog of projects, so the theme dialog must switch it off and put it back. The dialog
 * itself is not shown.
 */
class AwtThemeFilePickerTest {
    private var before: String? = null

    @BeforeEach
    fun remember() {
        before = System.getProperty(DIRECTORIES_PROPERTY)
    }

    @AfterEach
    fun restore() {
        before?.let { System.setProperty(DIRECTORIES_PROPERTY, it) } ?: System.clearProperty(DIRECTORIES_PROPERTY)
    }

    @Test
    fun `restores fileDialogForDirectories after the dialog`() {
        System.setProperty(DIRECTORIES_PROPERTY, "true")
        var during: String? = null

        val result =
            withFileDialogForFiles {
                during = System.getProperty(DIRECTORIES_PROPERTY)
                42
            }

        result shouldBe 42
        during shouldBe "false"
        System.getProperty(DIRECTORIES_PROPERTY) shouldBe "true"
    }

    @Test
    fun `restores the property when the dialog fails`() {
        System.setProperty(DIRECTORIES_PROPERTY, "true")

        shouldThrow<IllegalStateException> { withFileDialogForFiles { error("boom") } }

        System.getProperty(DIRECTORIES_PROPERTY) shouldBe "true"
    }

    @Test
    fun `a property that was not set stays not set`() {
        System.clearProperty(DIRECTORIES_PROPERTY)
        var during: String? = null

        withFileDialogForFiles { during = System.getProperty(DIRECTORIES_PROPERTY) }

        during shouldBe "false"
        System.getProperty(DIRECTORIES_PROPERTY) shouldBe null
    }

    @Test
    fun `a property that was false stays false`() {
        System.setProperty(DIRECTORIES_PROPERTY, "false")

        withFileDialogForFiles { }

        System.getProperty(DIRECTORIES_PROPERTY) shouldBe "false"
    }
}
