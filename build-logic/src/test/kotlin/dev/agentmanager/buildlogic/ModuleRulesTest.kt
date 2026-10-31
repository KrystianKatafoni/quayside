package dev.agentmanager.buildlogic

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ModuleRulesTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `rules classify module paths`() {
        assertTrue(ModuleRules.violatesImplRule(":feature:projects:impl"))
        assertTrue(ModuleRules.violatesImplRule(":feature:projects:ui"))
        assertFalse(ModuleRules.violatesImplRule(":feature:projects:api"))
        assertFalse(ModuleRules.violatesImplRule(":core:process"))

        assertTrue(ModuleRules.violatesUiRule(":feature:sessions:impl"))
        assertFalse(ModuleRules.violatesUiRule(":feature:sessions:api"))

        assertFalse(ModuleRules.violatesApiRule(":core:domain"))
        assertTrue(ModuleRules.violatesApiRule(":feature:projects:api"))
        assertTrue(ModuleRules.violatesApiRule(":core:process"))
    }

    @Test
    fun `impl depending on another impl fails the build`() {
        givenModules(
            ":feature:a:impl" to "am.feature.impl",
            ":feature:b:impl" to "am.feature.impl",
        )
        dependsOn(":feature:b:impl", ":feature:a:impl")

        val output = runner().buildAndFail().output

        assertTrue(
            output.contains(
                "Module rule 'impl never depends on impl or ui' violated: :feature:b:impl -> :feature:a:impl",
            ),
            output,
        )
    }

    @Test
    fun `api depending on another feature api fails the build`() {
        givenModules(
            ":feature:a:api" to "am.feature.api",
            ":feature:b:api" to "am.feature.api",
        )
        dependsOn(":feature:b:api", ":feature:a:api")

        val output = runner().buildAndFail().output

        assertTrue(output.contains("violated: :feature:b:api -> :feature:a:api"), output)
    }

    @Test
    fun `allowed dependencies configure fine`() {
        givenModules(
            ":core:domain" to "am.feature.api",
            ":feature:a:api" to "am.feature.api",
            ":feature:a:impl" to "am.feature.impl",
        )
        dependsOn(":feature:a:api", ":core:domain")
        dependsOn(":feature:a:impl", ":feature:a:api")

        val result = runner().build()

        assertEquals(true, result.output.contains("BUILD SUCCESSFUL"))
    }

    private fun givenModules(vararg modules: Pair<String, String>) {
        val catalog = System.getProperty("am.versionCatalog").replace("\\", "/")
        val includes = modules.joinToString("\n") { "include(\"${it.first}\")" }
        write(
            "settings.gradle.kts",
            """
            dependencyResolutionManagement {
                repositories { mavenCentral(); google() }
                versionCatalogs { create("libs") { from(files("$catalog")) } }
            }
            rootProject.name = "sample"
            $includes
            """.trimIndent(),
        )
        modules.forEach { (path, plugin) -> write(buildFile(path), "plugins {\n    id(\"$plugin\")\n}\n") }
    }

    private fun dependsOn(
        from: String,
        to: String,
    ) {
        File(projectDir, buildFile(from)).appendText("\ndependencies {\n    \"implementation\"(project(\"$to\"))\n}\n")
    }

    private fun buildFile(path: String) = path.removePrefix(":").replace(':', '/') + "/build.gradle.kts"

    private fun write(
        path: String,
        content: String,
    ) {
        File(projectDir, path).apply { parentFile.mkdirs() }.writeText(content)
    }

    private fun runner(): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("help", "--stacktrace")
}
