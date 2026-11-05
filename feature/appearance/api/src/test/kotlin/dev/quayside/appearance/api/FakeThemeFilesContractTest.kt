package dev.quayside.appearance.api

import dev.quayside.core.domain.Outcome
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** The fake passes the same contract as the real files (ENG-T2). */
class FakeThemeFilesContractTest : ThemeFilesContract() {
    private val fake = FakeThemeFiles()

    override fun files(): ThemeFiles = fake

    override fun path(name: String): Path = Path.of("/fake").resolve(name)

    override fun plantNewer(
        path: Path,
        found: Int,
    ) = fake.putNewer(path, found)

    override fun plantUnknownScheme(path: Path) = fake.putInvalidValue(path)

    @Test
    fun `scripted read failure wins over a planted file and is recorded`() =
        runTest {
            val target = path("a.json")
            fake.put(target, AppearanceSettings())
            fake.readFailure = ThemeFileError.NotThemeFile

            fake.read(target) shouldBe Outcome.Err(ThemeFileError.NotThemeFile)
            fake.reads shouldBe listOf(target)
        }

    @Test
    fun `scripted write failure writes nothing`() =
        runTest {
            fake.writeFailure = ThemeFileError.WriteFailed

            fake.write(AppearanceSettings(), path("a.json")) shouldBe Outcome.Err(ThemeFileError.WriteFailed)
            fake.written shouldBe emptyMap()
        }

    @Test
    fun `written settings are recorded by path`() =
        runTest {
            val settings = AppearanceSettings(uiScale = UiScale.S)

            fake.write(settings, path("a.json"))

            fake.written shouldBe mapOf(path("a.json") to settings)
        }
}
