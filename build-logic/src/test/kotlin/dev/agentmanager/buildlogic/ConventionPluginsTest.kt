package dev.agentmanager.buildlogic

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Applies each convention plugin to a throw-away single-module build and checks the behaviour
 * other modules rely on. These builds resolve real dependencies, so they need network access.
 */
class ConventionPluginsTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `am-kotlin compiles Kotlin and runs JUnit tests with Kotest assertions`() {
        givenModule("am.kotlin")
        write("src/main/kotlin/sample/Greeter.kt", "package sample\n\nfun greet(name: String) = \"Hello, \$name\"\n")
        write(
            "src/test/kotlin/sample/GreeterTest.kt",
            """
            package sample

            import io.kotest.matchers.shouldBe
            import org.junit.jupiter.api.Test

            class GreeterTest {
                @Test
                fun greets() {
                    greet("Agent") shouldBe "Hello, Agent"
                }
            }
            """.trimIndent(),
        )

        val result = build("test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertTrue(File(projectDir, "build/test-results/test/TEST-sample.GreeterTest.xml").exists())
    }

    @Test
    fun `am-feature-api enforces explicit API mode`() {
        givenModule("am.feature.api")
        write("src/main/kotlin/sample/api/SampleApi.kt", "package sample.api\n\ninterface SampleApi\n")

        val result = runner("compileKotlin").buildAndFail()

        assertTrue(
            result.output.contains("Visibility must be specified in explicit API mode"),
            "Expected explicit API error, got:\n${result.output}",
        )
    }

    @Test
    fun `am-feature-api compiles a contract with test fixtures`() {
        givenModule("am.feature.api")
        write("src/main/kotlin/sample/api/SampleApi.kt", "package sample.api\n\npublic interface SampleApi\n")
        write(
            "src/testFixtures/kotlin/sample/api/FakeSampleApi.kt",
            "package sample.api\n\npublic class FakeSampleApi : SampleApi\n",
        )

        val result = build("compileTestFixturesKotlin")

        assertEquals(TaskOutcome.SUCCESS, result.task(":compileTestFixturesKotlin")?.outcome)
    }

    @Test
    fun `am-feature-impl compiles a module`() {
        givenModule("am.feature.impl")
        write("src/main/kotlin/sample/impl/SampleImpl.kt", "package sample.impl\n\ninternal class SampleImpl\n")

        val result = build("compileKotlin")

        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlin")?.outcome)
    }

    @Test
    fun `am-feature-ui compiles a composable function`() {
        givenModule("am.feature.ui")
        write(
            "src/main/kotlin/sample/ui/Hello.kt",
            """
            package sample.ui

            import androidx.compose.foundation.text.BasicText
            import androidx.compose.runtime.Composable

            @Composable
            fun Hello() {
                BasicText("Hello")
            }
            """.trimIndent(),
        )

        val result = build("compileKotlin")

        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlin")?.outcome)
    }

    @Test
    fun `am-app compiles a composable function`() {
        givenModule("am.app")
        write(
            "src/main/kotlin/sample/app/Root.kt",
            """
            package sample.app

            import androidx.compose.foundation.text.BasicText
            import androidx.compose.runtime.Composable

            @Composable
            fun Root() {
                BasicText("Agent Manager")
            }
            """.trimIndent(),
        )

        val result = build("compileKotlin")

        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlin")?.outcome)
    }

    @Test
    fun `quickCheck rejects println through detekt`() {
        givenModule("am.kotlin")
        write("src/main/kotlin/sample/Noisy.kt", "package sample\n\nfun noisy() {\n    println(\"debug\")\n}\n")

        val result = runner("quickCheck").buildAndFail()

        assertTrue(result.output.contains("ForbiddenMethodCall"), "Expected detekt violation, got:\n${result.output}")
    }

    @Test
    fun `quickCheck passes on clean code`() {
        givenModule("am.kotlin")
        write("src/main/kotlin/sample/Quiet.kt", "package sample\n\nfun double(x: Int): Int = x * 2\n")

        val result = build("quickCheck")

        assertEquals(TaskOutcome.SUCCESS, result.task(":quickCheck")?.outcome)
    }

    private fun givenModule(pluginId: String) {
        val catalog = System.getProperty("am.versionCatalog").replace("\\", "/")
        write(
            "settings.gradle.kts",
            """
            dependencyResolutionManagement {
                repositories {
                    mavenCentral()
                    google()
                }
                versionCatalogs {
                    create("libs") { from(files("$catalog")) }
                }
            }
            rootProject.name = "sample"
            """.trimIndent() + "\n",
        )
        write("build.gradle.kts", "plugins {\n    id(\"$pluginId\")\n}\n")
        write("config/detekt/detekt.yml", File(System.getProperty("am.detektConfig")).readText())
    }

    private fun write(
        path: String,
        content: String,
    ) {
        File(projectDir, path).apply { parentFile.mkdirs() }.writeText(content)
    }

    private fun runner(vararg tasks: String): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*tasks, "--stacktrace")
            .forwardOutput()

    private fun build(vararg tasks: String): BuildResult = runner(*tasks).build()
}
