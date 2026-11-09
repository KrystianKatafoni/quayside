package dev.quayside.appearance.api

import dev.quayside.core.designtokens.TypographyId
import dev.quayside.core.domain.Outcome
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Behaviour every [AppearanceApi] must have (ENG-T2). Runs on the fake and on the real module. */
public abstract class AppearanceApiContract {
    /** The API under test. */
    protected abstract fun api(): AppearanceApi

    /** Makes macOS look dark (true), light (false) or unknown (null) to [api], an API from [api]. */
    protected abstract fun setSystemDark(
        api: AppearanceApi,
        dark: Boolean?,
    )

    /** Two different themes of the catalog: [first] is the default one, [second] another. */
    private fun AppearanceApi.first(): SchemeId = ThemeCatalog.DEFAULT_SCHEME

    private fun AppearanceApi.second(): SchemeId = otherScheme()

    private fun AppearanceApi.otherScheme(): SchemeId =
        catalog.value.themes
            .first {
                it.id !=
                    ThemeCatalog.DEFAULT_SCHEME
            }.id

    private fun AppearanceApi.otherTypography(): TypographyId =
        catalog.value.typographies
            .first { it.id != ThemeCatalog.DEFAULT_TYPOGRAPHY }
            .id

    @Test
    public fun `catalog is the same on every read`() {
        val api = api()

        api.catalog.value shouldBeSameInstanceAs api.catalog.value
    }

    @Test
    public fun `catalog contains the default scheme and typography`() {
        val catalog = api().catalog.value

        catalog.theme(ThemeCatalog.DEFAULT_SCHEME).shouldNotBeNull().id shouldBe ThemeCatalog.DEFAULT_SCHEME
        catalog.typography(ThemeCatalog.DEFAULT_TYPOGRAPHY).shouldNotBeNull().id shouldBe
            ThemeCatalog.DEFAULT_TYPOGRAPHY
    }

    @Test
    public fun `settings start at the defaults`() {
        api().settings.value shouldBe AppearanceSettings()
    }

    @Test
    public fun `update publishes the new settings`() =
        runTest {
            val api = api()
            val wanted =
                AppearanceSettings(api.otherScheme(), api.otherTypography(), UiScale.L, ListDensity.COMPACT)

            val result = api.update { wanted }

            result shouldBe Outcome.Ok(wanted)
            api.settings.value shouldBe wanted
        }

    @Test
    public fun `update hands the current settings to the change`() =
        runTest {
            val api = api()
            val seen = mutableListOf<AppearanceSettings>()

            api.update { it.copy(uiScale = UiScale.S).also { _ -> seen += it } }
            api.update { it.copy(density = ListDensity.COMPACT).also { _ -> seen += it } }

            seen shouldBe
                listOf(
                    AppearanceSettings(),
                    AppearanceSettings(uiScale = UiScale.S),
                )
            api.settings.value shouldBe AppearanceSettings(uiScale = UiScale.S, density = ListDensity.COMPACT)
        }

    @Test
    public fun `update with an unknown scheme returns UnknownScheme and keeps settings`() =
        runTest {
            val api = api()
            val unknown = SchemeId("no-such-theme")

            val result = api.update { it.copy(scheme = unknown, uiScale = UiScale.L) }

            result.shouldBeInstanceOf<Outcome.Err<AppearanceError>>().error shouldBe
                AppearanceError.UnknownScheme(unknown)
            api.settings.value shouldBe AppearanceSettings()
        }

    @Test
    public fun `update with an unknown typography returns UnknownTypography and keeps settings`() =
        runTest {
            val api = api()
            val unknown = TypographyId("no-such-typography")

            val result = api.update { it.copy(typography = unknown, uiScale = UiScale.L) }

            result.shouldBeInstanceOf<Outcome.Err<AppearanceError>>().error shouldBe
                AppearanceError.UnknownTypography(unknown)
            api.settings.value shouldBe AppearanceSettings()
        }

    @Test
    public fun `update keeps fields the change does not touch`() =
        runTest {
            val api = api()
            api.update { it.copy(scheme = api.otherScheme(), typography = api.otherTypography()) }

            api.update { it.copy(uiScale = UiScale.S) }

            api.settings.value shouldBe
                AppearanceSettings(api.otherScheme(), api.otherTypography(), UiScale.S, ListDensity.COMFORTABLE)
        }

    @Test
    public fun `day night settings are published`() =
        runTest {
            val api = api()
            val wanted = AppearanceSettings(dayNight = DayNight(true, api.first(), api.second()))

            val result = api.update { wanted }

            result shouldBe Outcome.Ok(wanted)
            api.settings.value shouldBe wanted
        }

    @Test
    public fun `applied equals settings while day night is off`() =
        runTest {
            val api = api()
            setSystemDark(api, true)

            api.update { it.copy(scheme = api.second(), uiScale = UiScale.L) }

            api.applied.value shouldBe api.settings.value
        }

    @Test
    public fun `update with unknown night scheme keeps settings`() =
        runTest {
            val api = api()
            val unknown = SchemeId("no-such-night")

            val result = api.update { it.copy(dayNight = DayNight(true, api.first(), unknown)) }

            result.shouldBeInstanceOf<Outcome.Err<AppearanceError>>().error shouldBe
                AppearanceError.UnknownScheme(unknown)
            api.settings.value shouldBe AppearanceSettings()
            api.applied.value shouldBe AppearanceSettings()
        }

    @Test
    public fun `update with unknown day scheme keeps settings`() =
        runTest {
            val api = api()
            val unknown = SchemeId("no-such-day")

            val result = api.update { it.copy(dayNight = DayNight(true, unknown, api.first())) }

            result.shouldBeInstanceOf<Outcome.Err<AppearanceError>>().error shouldBe
                AppearanceError.UnknownScheme(unknown)
            api.settings.value shouldBe AppearanceSettings()
        }

    @Test
    public fun `applied uses the day theme when macOS is light`() =
        runTest {
            val api = api()
            api.update { it.copy(scheme = api.first(), dayNight = DayNight(true, api.second(), api.first())) }

            setSystemDark(api, false)

            api.applied.value.scheme shouldBe api.second()
            api.settings.value.scheme shouldBe api.first()
        }

    @Test
    public fun `applied uses the night theme when macOS is dark`() =
        runTest {
            val api = api()
            api.update { it.copy(scheme = api.second(), dayNight = DayNight(true, api.second(), api.first())) }

            setSystemDark(api, true)

            api.applied.value.scheme shouldBe api.first()
            api.settings.value.scheme shouldBe api.second()
        }

    @Test
    public fun `applied is the saved scheme while the mode is unknown`() =
        runTest {
            val api = api()
            api.update { it.copy(scheme = api.first(), dayNight = DayNight(true, api.second(), api.second())) }

            setSystemDark(api, null)

            api.applied.value.scheme shouldBe api.first()
        }

    @Test
    public fun `applied follows the system mode as it changes`() =
        runTest {
            val api = api()
            api.update { it.copy(scheme = api.first(), dayNight = DayNight(true, api.second(), api.first())) }

            setSystemDark(api, false)
            api.applied.value.scheme shouldBe api.second()
            setSystemDark(api, true)
            api.applied.value.scheme shouldBe api.first()
            setSystemDark(api, false)
            api.applied.value.scheme shouldBe api.second()
        }

    @Test
    public fun `applied follows a later update`() =
        runTest {
            val api = api()
            setSystemDark(api, true)
            api.update { it.copy(dayNight = DayNight(true, api.first(), api.second())) }
            api.applied.value.scheme shouldBe api.second()

            api.update { it.copy(dayNight = DayNight(true, api.first(), api.first())) }

            api.applied.value.scheme shouldBe api.first()
        }

    @Test
    public fun `applied keeps the other settings of the saved choice`() =
        runTest {
            val api = api()
            setSystemDark(api, true)
            api.update {
                it.copy(
                    uiScale = UiScale.L,
                    density = ListDensity.COMPACT,
                    dayNight = DayNight(true, api.first(), api.second()),
                )
            }

            api.applied.value shouldBe api.settings.value.copy(scheme = api.second())
        }

    private val mine = SampleSchemes.mine()

    @Test
    public fun `added scheme joins the catalog and the saved settings`() =
        runTest {
            val api = api()

            val added = api.addScheme(mine)

            added.shouldBeInstanceOf<Outcome.Ok<ThemeSpec>>().value.let {
                it.id shouldBe mine.id
                it.name shouldBe mine.name
                it.mode shouldBe mine.mode
                it.shape.radius shouldBe mine.radius
            }
            api.catalog.value
                .theme(mine.id)
                .shouldNotBeNull()
                .name shouldBe "Mine"
            api.settings.value.customSchemes shouldBe listOf(mine)
        }

    @Test
    public fun `scheme with a taken id is refused and changes nothing`() =
        runTest {
            val api = api()
            val before = api.catalog.value

            api.addScheme(mine.copy(id = ThemeCatalog.DEFAULT_SCHEME)) shouldBe
                Outcome.Err(SchemeError.Taken(ThemeCatalog.DEFAULT_SCHEME))

            api.addScheme(mine).shouldBeInstanceOf<Outcome.Ok<ThemeSpec>>()
            api.addScheme(mine) shouldBe Outcome.Err(SchemeError.Taken(mine.id))
            api.catalog.value.themes.size shouldBe before.themes.size + 1
            api.settings.value.customSchemes shouldBe listOf(mine)
        }

    @Test
    public fun `scheme with unreadable text is refused for contrast`() =
        runTest {
            val api = api()
            val grey = mine.copy(colors = mine.colors.copy(text = SampleSchemes.color("#222222")))

            api
                .addScheme(grey)
                .shouldBeInstanceOf<Outcome.Err<SchemeError>>()
                .error
                .shouldBeInstanceOf<SchemeError.LowContrast>()
                .problems
                .shouldNotBeEmpty()

            api.catalog.value.theme(mine.id) shouldBe null
            api.settings.value.customSchemes shouldBe emptyList()
        }

    @Test
    public fun `added scheme can be chosen and update keeps the custom schemes`() =
        runTest {
            val api = api()
            api.addScheme(mine)

            val result = api.update { it.copy(scheme = mine.id, customSchemes = emptyList()) }

            result.shouldBeInstanceOf<Outcome.Ok<AppearanceSettings>>().value.let {
                it.scheme shouldBe mine.id
                it.customSchemes shouldBe listOf(mine)
            }
            api.settings.value.customSchemes shouldBe listOf(mine)
        }
}
