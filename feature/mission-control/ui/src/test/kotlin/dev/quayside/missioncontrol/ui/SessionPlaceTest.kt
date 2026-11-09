package dev.quayside.missioncontrol.ui

import dev.quayside.projects.api.Project
import dev.quayside.projects.api.SubfolderName
import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path

/**
 * The place badge of a session in a group (FR-P7, FR-S9, AM-310): the subfolder it runs in, else "folder główny"
 * (the project directory, an unchecked subdirectory, `software` next to `soft`); no badge at all for a project without
 * subfolders. `UiText` keys `row.place.subfolder` ("folder: {0}", the reader) and `row.place.root`.
 */
internal class SessionPlaceTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("places")
    fun `place of a session`(
        @Suppress("UNUSED_PARAMETER") title: String,
        project: Project,
        cwd: String,
        expected: UiText?,
        reads: String?,
    ) {
        val place = SessionPlace.of(project, Path.of(cwd))

        place shouldBe expected
        place?.let(MissionControlStrings::format) shouldBe reads
    }

    companion object {
        private val plant =
            aProject(
                id = "plant",
                dir = Path.of("/p/plant"),
                displayName = "plant",
                subfolders = subfolderNames("soft", "docs"),
            )
        private val plain = aProject(id = "plain", dir = Path.of("/p/plant"), displayName = "plain")

        /** A name with a quote and a placeholder: it must reach the text as it is (MessageFormat cannot eat it). */
        private val odd =
            aProject(
                id = "odd",
                dir = Path.of("/p/plant"),
                displayName = "odd",
                subfolders = listOf(SubfolderName("a'b{0}")),
            )

        private val root = UiText("row.place.root")

        private fun sub(name: String) = UiText("row.place.subfolder", listOf(name))

        @JvmStatic
        fun places(): List<Arguments> =
            listOf(
                arguments("inside a subfolder", plant, "/p/plant/soft/api", sub("soft"), "folder: soft"),
                arguments("the subfolder itself", plant, "/p/plant/docs", sub("docs"), "folder: docs"),
                arguments("the project directory", plant, "/p/plant", root, "folder główny"),
                arguments("an unchecked subdirectory", plant, "/p/plant/marketing/x", root, "folder główny"),
                arguments("soft is not software", plant, "/p/plant/software", root, "folder główny"),
                arguments("outside the project", plant, "/p/other/soft", root, "folder główny"),
                arguments("a relative path", plant, "soft", root, "folder główny"),
                arguments("no subfolders, no badge", plain, "/p/plant/soft", null, null),
                arguments("no subfolders, root, no badge", plain, "/p/plant", null, null),
                arguments("a quote and a placeholder", odd, "/p/plant/a'b{0}/x", sub("a'b{0}"), "folder: a'b{0}"),
            )
    }
}
