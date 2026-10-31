package dev.quayside.projects.api

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.nio.file.Path

class ProjectErrorTest {
    private val dir = Path.of("/Users/jane/secret-project")

    @Test
    fun `toString hides the path`() {
        val errors = listOf(ProjectError.NotAbsolute(dir), ProjectError.NotFound(dir), ProjectError.NotADirectory(dir))

        errors.map { it.toString() } shouldBe
            listOf("NotAbsolute(dir=***)", "NotFound(dir=***)", "NotADirectory(dir=***)")
        errors.forEach { it.toString() shouldNotContain "jane" }
    }

    @Test
    fun `equality still compares the path`() {
        ProjectError.NotFound(dir) shouldBe ProjectError.NotFound(Path.of("/Users/jane/secret-project"))
    }

    @Test
    fun `storage failure carries a fixed category`() {
        ProjectError.StorageFailed(StorageProblem.WRITE_FAILED).toString() shouldBe
            "StorageFailed(problem=WRITE_FAILED)"
    }
}
