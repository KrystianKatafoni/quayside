package dev.quayside.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import dev.quayside.appearance.api.FakeAppearanceApi
import dev.quayside.appearance.impl.builtInCatalog
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * The preview of the Appearance dialog shows the status bar too (AM-816, ADR 0022): Mission Control no longer draws
 * it, so the preview slot of `app` adds it, or the preview would show a look the window does not have.
 */
@OptIn(ExperimentalTestApi::class)
class AppearancePreviewBarWindowTest {
    @TempDir
    lateinit var root: Path

    private val api = FakeAppearanceApi(builtInCatalog())

    @Test
    fun `appearance preview shows the status bar`() =
        runComposeUiTest {
            // Given the window with the Appearance dialog open
            val project =
                aProject(id = "project0001", dir = Files.createDirectory(root.resolve("q")), displayName = "Q")
            val palette =
                showWindow(
                    FakeProjectsApi(initial = listOf(project)),
                    settings = SettingsRepos().also { it.appearance = api },
                )
            runOnIdle { palette.open() }
            await(WindowTags.PALETTE_FIELD).performTextInput(Strings["command.appearance"])
            await(WindowTags.paletteRow("appearance.open")).performClick()
            waitUntilDoesNotExist(hasTestTag(WindowTags.PALETTE))
            await(WindowTags.APPEARANCE_DIALOG)
            await(WindowTags.APPEARANCE_PREVIEW)

            // Then the preview has its own status bar with the mode badge, next to the one of the window
            val inPreview =
                hasTestTag(WindowTags.MODE_BAR) and hasAnyAncestor(hasTestTag(WindowTags.APPEARANCE_PREVIEW))
            waitUntil { onAllNodes(inPreview).fetchSemanticsNodes().size == 1 }
            onAllNodes(hasText("NORMAL", substring = true) and hasAnyAncestor(inPreview))
                .fetchSemanticsNodes()
                .isNotEmpty() shouldBe true
            onAllNodes(hasTestTag(WindowTags.MODE_BAR) and !hasAnyAncestor(hasTestTag(WindowTags.APPEARANCE_PREVIEW)))
                .fetchSemanticsNodes()
                .size shouldBe 1
        }
}
