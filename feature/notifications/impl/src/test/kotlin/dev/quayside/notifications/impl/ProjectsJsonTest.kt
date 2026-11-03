package dev.quayside.notifications.impl

import dev.quayside.core.domain.ProjectId
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.ProjectNotifications
import dev.quayside.notifications.api.ProjectProfile
import dev.quayside.notifications.api.VoiceId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** The `projects` section of `notifications.json` (FR-N7): `{"<project id>": {"profile", "voice"}}`. */
class ProjectsJsonTest {
    private fun decode(data: String): NotificationSettings = Json.decodeFromString(NotificationSettingsSerializer, data)

    private fun encode(settings: NotificationSettings): String =
        Json.encodeToString(NotificationSettingsSerializer, settings)

    private fun entry(body: String): NotificationSettings = decode("""{"projects": {"$A": $body}}""")

    @Test
    fun `round trips project exceptions`() {
        val projects =
            mapOf(
                A to ProjectNotifications(ProjectProfile.ERRORS_ONLY, VoiceId("Anna")),
                B to ProjectNotifications(ProjectProfile.MUTED),
                C to ProjectNotifications(voice = VoiceId("Ewa")),
            )

        val text = encode(NotificationSettings(projects = projects))

        decode(text).projects shouldBe projects
        text shouldContain """"projects""""
        text shouldContain """"errorsOnly""""
        text shouldContain """"muted""""
        text shouldContain A.value
    }

    @Test
    fun `the exceptions do not disturb the other settings`() {
        val settings =
            NotificationSettings(
                voice = false,
                projects =
                    mapOf(A to ProjectNotifications(ProjectProfile.MUTED)),
            )

        // The serializer always writes the whole matrix, so compare without it.
        decode(encode(settings)).copy(matrix = emptyMap()) shouldBe settings
    }

    @Test
    fun `default exception is not written`() {
        val settings =
            NotificationSettings(
                projects = mapOf(A to ProjectNotifications(), B to ProjectNotifications(ProjectProfile.MUTED)),
            )

        val text = encode(settings)

        text shouldNotContain A.value
        text shouldContain B.value
        decode(text).projectFor(A) shouldBe ProjectNotifications()
        decode(text).projectFor(B) shouldBe ProjectNotifications(ProjectProfile.MUTED)
    }

    @Test
    fun `only default exceptions write no id`() {
        val text = encode(NotificationSettings(projects = mapOf(A to ProjectNotifications())))

        text shouldNotContain A.value
        text shouldNotContain "projects"
    }

    @Test
    fun `settings without exceptions write no projects section`() {
        encode(NotificationSettings()) shouldNotContain "projects"
    }

    @Test
    fun `missing projects gives no exceptions`() {
        decode("""{"popups": false}""").projects shouldBe emptyMap()
        decode("""{"projects": {}}""").projects shouldBe emptyMap()
    }

    @Test
    fun `unknown profile gives the default`() {
        entry("""{"profile": "future"}""").projectFor(A) shouldBe ProjectNotifications()
        entry("""{"profile": ""}""").projectFor(A) shouldBe ProjectNotifications()
    }

    @Test
    fun `unknown profile keeps the voice`() {
        entry("""{"profile": "future", "voice": "Anna"}""").projectFor(A) shouldBe
            ProjectNotifications(voice = VoiceId("Anna"))
    }

    @Test
    fun `missing profile or voice gives the defaults`() {
        entry("{}").projectFor(A) shouldBe ProjectNotifications()
        entry("""{"voice": "Anna"}""").projectFor(A) shouldBe ProjectNotifications(voice = VoiceId("Anna"))
        entry("""{"profile": "muted"}""").projectFor(A) shouldBe ProjectNotifications(ProjectProfile.MUTED)
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = ["", " ", "   "])
    fun `blank voice gives the global voice`(voice: String) {
        entry("""{"profile": "errorsOnly", "voice": "$voice"}""").projectFor(A) shouldBe
            ProjectNotifications(ProjectProfile.ERRORS_ONLY)
    }

    @Test
    fun `null voice is the global voice`() {
        entry("""{"profile": "muted", "voice": null}""").projectFor(A) shouldBe
            ProjectNotifications(ProjectProfile.MUTED)
    }

    @Test
    fun `unknown keys of an entry are skipped`() {
        entry("""{"profile": "muted", "future": 1}""").projectFor(A) shouldBe ProjectNotifications(ProjectProfile.MUTED)
    }

    @Test
    fun `an id that is not a uuid is kept as it is`() {
        val settings = decode("""{"projects": {"plantpulse-core": {"profile": "muted"}}}""")

        settings.projectFor(ProjectId("plantpulse-core")) shouldBe ProjectNotifications(ProjectProfile.MUTED)
    }

    @Test
    fun `a blank id is skipped without failing the file`() {
        val settings =
            decode(
                """{"projects": {"": {"profile": "muted"}, " ": {"profile": "muted"}, "$B": {"profile": "muted"}}}""",
            )

        settings.projects shouldBe mapOf(B to ProjectNotifications(ProjectProfile.MUTED))
    }

    @Test
    fun `decode drops default entries from the map`() {
        val settings =
            decode(
                """{"projects": {"$A": {}, "$B": {"profile": "future"},""" +
                    """ "$C": {"profile": "future", "voice": "Anna"}}}""",
            )

        settings.projects shouldBe mapOf(C to ProjectNotifications(voice = VoiceId("Anna")))
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = [
            """{"projects": []}""",
            """{"projects": "muted"}""",
            """{"projects": 1}""",
            """{"projects": null}""",
            """{"projects": {"2f6c1b9e-6d3a-4f43-9d7b-0a1c5e8b7a11": 1}}""",
            """{"projects": {"2f6c1b9e-6d3a-4f43-9d7b-0a1c5e8b7a11": "muted"}}""",
            """{"projects": {"2f6c1b9e-6d3a-4f43-9d7b-0a1c5e8b7a11": []}}""",
            """{"projects": {"2f6c1b9e-6d3a-4f43-9d7b-0a1c5e8b7a11": null}}""",
            """{"projects": {"2f6c1b9e-6d3a-4f43-9d7b-0a1c5e8b7a11": {"profile": 1}}}""",
            """{"projects": {"2f6c1b9e-6d3a-4f43-9d7b-0a1c5e8b7a11": {"profile": null}}}""",
            """{"projects": {"2f6c1b9e-6d3a-4f43-9d7b-0a1c5e8b7a11": {"profile": true}}}""",
            """{"projects": {"2f6c1b9e-6d3a-4f43-9d7b-0a1c5e8b7a11": {"profile": []}}}""",
            """{"projects": {"2f6c1b9e-6d3a-4f43-9d7b-0a1c5e8b7a11": {"voice": 5}}}""",
            """{"projects": {"2f6c1b9e-6d3a-4f43-9d7b-0a1c5e8b7a11": {"voice": true}}}""",
            """{"projects": {"2f6c1b9e-6d3a-4f43-9d7b-0a1c5e8b7a11": {"voice": ["Anna"]}}}""",
        ],
    )
    fun `wrong type in project exceptions throws`(data: String) {
        shouldThrow<SerializationException> { decode(data) }
    }

    @Test
    fun `the error of a wrong project value does not quote the id or the voice`() {
        val error =
            shouldThrow<SerializationException> {
                decode("""{"projects": {"$A": {"profile": "muted", "voice": 5}, "$B": 1}}""")
            }

        error.message.orEmpty() shouldNotContain A.value
        error.message.orEmpty() shouldNotContain B.value
    }

    private companion object {
        val A = ProjectId("2f6c1b9e-6d3a-4f43-9d7b-0a1c5e8b7a11")
        val B = ProjectId("9b1d7e42-0c55-4c0e-8f3a-5d2e6a4b9c22")
        val C = ProjectId("c4a8f0d1-73be-4a59-b6e2-1f9d8c3a5e33")
    }
}
