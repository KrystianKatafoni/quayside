package dev.quayside.notifications.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.QuietHours
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalTime
import java.util.stream.Stream

/**
 * `draftOf`, `settingsOf` and `parseTime` for the do-not-disturb part (plan AM-706, table 1): pure, times stay text.
 */
class QuietDraftTest {
    private val defaults = NotificationSettings()

    private fun quietDraft(
        enabled: Boolean = false,
        from: String = "22:00",
        to: String = "07:00",
        respectFocus: Boolean = true,
    ) = QuietDraft(enabled, from, to, respectFocus)

    private fun withQuiet(quiet: QuietDraft): NotificationDraft = draftOf(defaults).copy(quiet = quiet)

    private fun saved(draft: NotificationDraft): QuietHours =
        (settingsOf(draft, defaults) as Outcome.Ok).value.quietHours

    private fun refusal(draft: NotificationDraft): Outcome<NotificationSettings, InvalidField> =
        settingsOf(draft, defaults)

    @Test
    fun `draft shows the saved quiet hours as HH MM`() {
        val quiet = QuietHours(true, LocalTime.of(7, 5), LocalTime.of(22, 30), respectFocus = false)

        draftOf(defaults.copy(quietHours = quiet)).quiet shouldBe QuietDraft(true, "07:05", "22:30", false)
    }

    @ParameterizedTest
    @MethodSource("acceptedTimes")
    fun `time text inside the day is accepted`(
        text: String,
        expected: LocalTime,
    ) {
        parseTime(text) shouldBe expected
    }

    @ParameterizedTest
    @MethodSource("refusedTimes")
    fun `time text outside the day or format is refused`(text: String) {
        parseTime(text) shouldBe null
    }

    @Test
    fun `the draft of the defaults saves the default quiet hours`() {
        // The serializer writes the section only for non-default hours, so the draft must not invent a difference.
        saved(draftOf(defaults)) shouldBe QuietHours()
        settingsOf(draftOf(defaults), defaults) shouldBe Outcome.Ok(defaults)
    }

    @Test
    fun `non default quiet hours survive a round trip through the draft`() {
        val quiet = QuietHours(true, LocalTime.of(21, 30), LocalTime.of(6, 15), respectFocus = false)
        val current = defaults.copy(quietHours = quiet)

        settingsOf(draftOf(current), current) shouldBe Outcome.Ok(current)
    }

    @Test
    fun `typed times are saved as the quiet hours`() {
        val draft = withQuiet(quietDraft(enabled = true, from = "23:00", to = " 6:30 "))

        saved(draft) shouldBe QuietHours(true, LocalTime.of(23, 0), LocalTime.of(6, 30), respectFocus = true)
    }

    @Test
    fun `quiet switches are taken from the draft`() {
        saved(withQuiet(quietDraft(enabled = true, respectFocus = false))) shouldBe
            QuietHours(enabled = true, respectFocus = false)
        saved(withQuiet(quietDraft(enabled = false, respectFocus = true))) shouldBe
            QuietHours(enabled = false, respectFocus = true)
    }

    @Test
    fun `a wrong start is refused as QUIET_FROM and a wrong end as QUIET_TO`() {
        refusal(withQuiet(quietDraft(from = "24:00"))) shouldBe
            Outcome.Err(InvalidField(NotificationField.QUIET_FROM))
        refusal(withQuiet(quietDraft(to = "7:60"))) shouldBe
            Outcome.Err(InvalidField(NotificationField.QUIET_TO))
        refusal(withQuiet(quietDraft(enabled = true, from = "", to = "07:00"))) shouldBe
            Outcome.Err(InvalidField(NotificationField.QUIET_FROM))
    }

    @Test
    fun `equal times are refused when quiet hours are on`() {
        refusal(withQuiet(quietDraft(enabled = true, from = "08:00", to = "08:00"))) shouldBe
            Outcome.Err(InvalidField(NotificationField.QUIET_TO))
        // the same minute written two ways is still equal
        refusal(withQuiet(quietDraft(enabled = true, from = "8:00", to = "08:00"))) shouldBe
            Outcome.Err(InvalidField(NotificationField.QUIET_TO))
    }

    @Test
    fun `equal times are kept when quiet hours are off`() {
        saved(withQuiet(quietDraft(enabled = false, from = "08:00", to = "08:00"))) shouldBe
            QuietHours(enabled = false, from = LocalTime.of(8, 0), to = LocalTime.of(8, 0))
    }

    @Test
    fun `a wrong format is refused also when quiet hours are off`() {
        refusal(withQuiet(quietDraft(enabled = false, to = "abc"))) shouldBe
            Outcome.Err(InvalidField(NotificationField.QUIET_TO))
    }

    @Test
    fun `quiet fields are checked after the templates`() {
        val draft = withQuiet(quietDraft(from = "xx", to = "yy"))
        val badTemplate = draft.speech.templates + (NotificationKind.FAILED to "[[")

        // a bad template wins over bad hours
        refusal(draft.copy(speech = draft.speech.copy(templates = badTemplate))) shouldBe
            Outcome.Err(InvalidField(NotificationField.TEMPLATE, NotificationKind.FAILED))
        // and a bad threshold wins over both
        refusal(draft.copy(runningLongMinutes = "0")) shouldBe
            Outcome.Err(InvalidField(NotificationField.RUNNING_LONG_MINUTES))
        // with both hours wrong the start is named
        refusal(draft) shouldBe Outcome.Err(InvalidField(NotificationField.QUIET_FROM))
    }

    companion object {
        @JvmStatic
        fun acceptedTimes(): Stream<Arguments> =
            Stream.of(
                Arguments.of("22:00", LocalTime.of(22, 0)),
                Arguments.of("7:00", LocalTime.of(7, 0)),
                Arguments.of(" 07:30 ", LocalTime.of(7, 30)),
                Arguments.of("00:00", LocalTime.of(0, 0)),
                Arguments.of("23:59", LocalTime.of(23, 59)),
            )

        @JvmStatic
        fun refusedTimes(): Stream<String> =
            Stream.of(
                "24:00",
                "7:60",
                "07:5",
                "7",
                "abc",
                "",
                "07:00:30",
                "07.00",
                "+7:00",
                "٠٧:٠٠",
            )
    }
}
