package dev.quayside.appearance.api

import dev.quayside.core.designtokens.TypographyId
import dev.quayside.core.domain.Outcome
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** Behaviour every [ThemeFiles] must have (ENG-T2). Runs on the fake and on the real files. */
public abstract class ThemeFilesContract {
    /** The files under test. */
    protected abstract fun files(): ThemeFiles

    /** A path inside a directory that is empty at the start of the test; [name] is a plain file name. */
    protected abstract fun path(name: String): Path

    /** Plants a theme file at [path] that was written by a newer app version ([found] is its schema version). */
    protected abstract fun plantNewer(
        path: Path,
        found: Int,
    )

    /** Plants a theme file at [path] that names a theme outside the built-in catalog. */
    protected abstract fun plantUnknownScheme(path: Path)

    private val custom =
        AppearanceSettings(
            scheme = SchemeId("paper"),
            typography = TypographyId("geist"),
            uiScale = UiScale.L,
            density = ListDensity.COMPACT,
            dayNight = DayNight(enabled = true, day = SchemeId("paper"), night = SchemeId("plex")),
        )

    @Test
    public fun `written file reads back equal`() =
        runTest {
            val files = files()
            val target = path("theme.json")

            files.write(custom, target) shouldBe Outcome.Ok(Unit)

            files.read(target) shouldBe Outcome.Ok(custom)
        }

    @Test
    public fun `default settings read back equal`() =
        runTest {
            val files = files()
            val target = path("defaults.json")

            files.write(AppearanceSettings(), target) shouldBe Outcome.Ok(Unit)

            files.read(target) shouldBe Outcome.Ok(AppearanceSettings())
        }

    @Test
    public fun `missing file is ReadFailed`() =
        runTest {
            files().read(path("nothing-here.json")) shouldBe Outcome.Err(ThemeFileError.ReadFailed)
        }

    @Test
    public fun `newer schema is NewerVersion`() =
        runTest {
            val files = files()
            val target = path("newer.json")
            plantNewer(target, found = 7)

            files.read(target) shouldBe Outcome.Err(ThemeFileError.NewerVersion(7))
        }

    @Test
    public fun `unknown id is InvalidValue`() =
        runTest {
            val files = files()
            val target = path("unknown.json")
            plantUnknownScheme(target)

            files.read(target) shouldBe Outcome.Err(ThemeFileError.InvalidValue)
        }

    @Test
    public fun `write replaces an existing file`() =
        runTest {
            val files = files()
            val target = path("theme.json")
            files.write(AppearanceSettings(uiScale = UiScale.S), target) shouldBe Outcome.Ok(Unit)

            files.write(custom, target) shouldBe Outcome.Ok(Unit)

            files.read(target) shouldBe Outcome.Ok(custom)
        }

    @Test
    public fun `files at different paths do not mix`() =
        runTest {
            val files = files()
            val first = path("first.json")
            val second = path("second.json")

            files.write(custom, first)
            files.write(AppearanceSettings(density = ListDensity.COMPACT), second)

            files.read(first) shouldBe Outcome.Ok(custom)
            files.read(second) shouldBe Outcome.Ok(AppearanceSettings(density = ListDensity.COMPACT))
        }
}
