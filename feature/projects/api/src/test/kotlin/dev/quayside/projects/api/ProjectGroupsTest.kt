package dev.quayside.projects.api

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path

/**
 * Pure rules of groups (FR-P7, AM-306): which subfolder of a project a working directory belongs to. The rules
 * compare path components and know nothing about the file system, so `Soft` and `soft` are different here.
 */
class ProjectGroupsTest {
    private val plant =
        aProject(id = "plant", dir = Path.of("/p/plant-puls"), subfolders = subfolderNames("docs", "soft"))

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("cwds")
    fun `cwd belongs to a subfolder by path components`(
        cwd: String,
        expected: String?,
    ) {
        ProjectGroups.subfolderOf(plant, Path.of(cwd)) shouldBe expected?.let(::SubfolderName)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cwds")
    fun `a project without subfolders owns no subfolder of any cwd`(
        cwd: String,
        @Suppress("UNUSED_PARAMETER") expected: String?,
    ) {
        ProjectGroups.subfolderOf(plant.copy(subfolders = emptyList()), Path.of(cwd)) shouldBe null
    }

    @Test
    fun `dirOf resolves the name under the project directory`() {
        ProjectGroups.dirOf(plant, SubfolderName("soft")) shouldBe Path.of("/p/plant-puls/soft")
    }

    @Test
    fun `a nested subfolder name is resolved as given`() {
        ProjectGroups.dirOf(plant, SubfolderName("docs")) shouldBe plant.dir.resolve("docs")
    }

    companion object {
        @JvmStatic
        fun cwds(): List<Arguments> =
            listOf(
                arguments("/p/plant-puls/soft", "soft"),
                arguments("/p/plant-puls/soft/api/x", "soft"),
                arguments("/p/plant-puls/soft/", "soft"),
                arguments("/p/plant-puls/docs", "docs"),
                arguments("/p/plant-puls/docs/../soft/a", "soft"),
                arguments("/p/plant-puls/./soft", "soft"),
                arguments("/p/plant-puls/../plant-puls/docs/x", "docs"),
                arguments("/p/plant-puls/software", null),
                arguments("/p/plant-puls/softx/y", null),
                arguments("/p/plant-puls/soft-extra", null),
                arguments("/p/plant-puls/Soft/a", null),
                arguments("/p/plant-puls", null),
                arguments("/p/plant-puls/soft/..", null),
                arguments("/p/plant-puls/marketing", null),
                arguments("/p/plant-puls/marketing/soft", null),
                arguments("/p/other/soft", null),
                arguments("/p/plant-puls-2/soft", null),
                arguments("/", null),
                arguments("soft/a", null),
                arguments("plant-puls/soft", null),
            )
    }
}
