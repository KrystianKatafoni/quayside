package dev.quayside.sessions.api

import dev.quayside.core.domain.ProjectId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path

class SessionRulesTest {
    private val projects = mapOf(ProjectId("a") to Path.of("/p/a"), ProjectId("b") to Path.of("/p/a/sub"))

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "/p/a, a",
        "/p/a/x, a",
        "/p/a/subway, a",
        "/p/a/sub, b",
        "/p/a/sub/y, b",
        "/p/ab,",
        "/p,",
        "/q,",
        "/p/a/../b,",
        "/p/a/sub/../x, a",
        "p/a,",
    )
    fun `session belongs to the deepest project containing its directory`(
        cwd: String,
        expected: String?,
    ) {
        SessionRules.projectFor(Path.of(cwd), projects) shouldBe expected?.let(::ProjectId)
    }

    @Test
    fun `no projects means no project`() {
        SessionRules.projectFor(Path.of("/p/a"), emptyMap()) shouldBe null
    }

    @Test
    fun `order of the projects does not matter`() {
        val reversed = projects.entries.reversed().associate { it.key to it.value }

        SessionRules.projectFor(Path.of("/p/a/sub/y"), reversed) shouldBe ProjectId("b")
    }
}
