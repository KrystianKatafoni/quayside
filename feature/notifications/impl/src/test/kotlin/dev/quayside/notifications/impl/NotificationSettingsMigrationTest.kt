package dev.quayside.notifications.impl

import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.storage.JsonStorage
import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationChannel.POPUP
import dev.quayside.notifications.api.NotificationChannel.SOUND
import dev.quayside.notifications.api.NotificationChannel.VOICE
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationSettingsError
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * `notifications.json` written before AM-812 (schema 1) holds the old default `turnEnded: ["popup"]` for everyone who
 * ever saved; the migration to schema 2 turns exactly that into "no deviation", so the new default applies.
 */
class NotificationSettingsMigrationTest {
    @TempDir
    lateinit var tempDir: Path

    private val file: Path get() = tempDir.resolve("notifications.json")

    private fun write(
        version: Int,
        data: String,
    ) = Files.writeString(file, """{"schemaVersion": $version, "data": $data}""")

    @Suppress("ForbiddenMethodCall", "InjectDispatcher")
    private fun open() = runBlocking { notificationSettingsModule(JsonStorage(tempDir, Dispatchers.IO), NoOpLogger) }

    private fun turnEnded(
        data: String,
        version: Int = 1,
    ): Set<NotificationChannel> {
        write(version, data)
        return open().settings.value.channelsFor(NotificationKind.TURN_ENDED)
    }

    @Test
    fun `the old default of a finished turn becomes the new default`() {
        turnEnded("""{"channels": {"turnEnded": ["popup"]}}""") shouldBe setOf(POPUP, SOUND)
    }

    @Test
    fun `a repeated popup is still the old default`() {
        turnEnded("""{"channels": {"turnEnded": ["popup", "popup"]}}""") shouldBe setOf(POPUP, SOUND)
    }

    @Test
    fun `a conscious choice with the voice is kept`() {
        turnEnded("""{"channels": {"turnEnded": ["popup", "voice"]}}""") shouldBe setOf(POPUP, VOICE)
    }

    @Test
    fun `an empty list that mutes the kind is kept`() {
        turnEnded("""{"channels": {"turnEnded": []}}""") shouldBe emptySet()
    }

    @Test
    fun `only the sound is kept`() {
        turnEnded("""{"channels": {"turnEnded": ["sound"]}}""") shouldBe setOf(SOUND)
    }

    @Test
    fun `the migration touches only the finished turn`() {
        write(1, """{"channels": {"turnEnded": ["popup"], "failed": ["popup"], "terminalBell": ["popup"]}}""")

        val settings = open().settings.value

        settings.channelsFor(NotificationKind.FAILED) shouldBe setOf(POPUP)
        settings.channelsFor(NotificationKind.TERMINAL_BELL) shouldBe setOf(POPUP)
        settings.channelsFor(NotificationKind.TURN_ENDED) shouldBe setOf(POPUP, SOUND)
    }

    @Test
    fun `a file without channels gives the new default`() {
        turnEnded("""{"voice": false}""") shouldBe setOf(POPUP, SOUND)
    }

    @Test
    fun `a file of schema 2 keeps a popup only choice`() {
        turnEnded("""{"channels": {"turnEnded": ["popup"]}}""", version = 2) shouldBe setOf(POPUP)
    }

    @Test
    fun `the migrated file is written as schema 2 and not migrated again`() {
        write(1, """{"channels": {"turnEnded": ["popup"]}}""")
        open()

        Files.readString(file) shouldContain Regex(""""schemaVersion"\s*:\s*2""")
        open().settings.value.channelsFor(NotificationKind.TURN_ENDED) shouldBe setOf(POPUP, SOUND)
    }

    @Test
    @Suppress("ForbiddenMethodCall")
    fun `a popup only choice made after the migration survives reopening`() {
        write(1, """{"channels": {"turnEnded": ["popup"]}}""")
        runBlocking { open().update { it.withChannel(NotificationKind.TURN_ENDED, SOUND, enabled = false) } }

        open().settings.value.channelsFor(NotificationKind.TURN_ENDED) shouldBe setOf(POPUP)
    }

    @Test
    fun `a file of the wrong shape goes to the broken file and gives the defaults`() {
        turnEnded("""{"channels": 5}""") shouldBe setOf(POPUP, SOUND)

        Files.exists(tempDir.resolve("notifications.json.broken")) shouldBe true
    }

    @Test
    @Suppress("ForbiddenMethodCall")
    fun `a file from a newer version is not touched and refuses updates`() {
        write(3, """{"channels": {"turnEnded": ["popup"]}}""")
        val before = Files.readString(file)
        val repo = open()

        runBlocking { repo.update { it.copy(voice = false) } } shouldBe
            Outcome.Err(NotificationSettingsError.NEWER_SCHEMA)

        Files.readString(file) shouldBe before
        repo.settings.value.channelsFor(NotificationKind.TURN_ENDED) shouldBe setOf(POPUP, SOUND)
    }

    @Test
    @Suppress("ForbiddenMethodCall")
    fun `only deviations from the defaults are written`() {
        runBlocking {
            open().update { it.withChannel(NotificationKind.FAILED, VOICE, enabled = false) }
        }

        val text = Files.readString(file)
        text shouldContain "failed"
        text shouldNotContain "turnEnded"
        text shouldNotContain "needsInput"
    }

    @Test
    @Suppress("ForbiddenMethodCall")
    fun `the default of a kind written by hand is not a deviation`() {
        runBlocking {
            open().update { it.withChannel(NotificationKind.TURN_ENDED, SOUND, enabled = true) }
        }

        Files.readString(file) shouldNotContain "turnEnded"
    }
}
