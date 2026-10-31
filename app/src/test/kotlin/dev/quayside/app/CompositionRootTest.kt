package dev.quayside.app

import dev.quayside.core.domain.SystemClock
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.Properties

class CompositionRootTest {
    @Test
    @Suppress("InjectDispatcher") // Asserts the one place that picks real dispatchers.
    fun `production dependencies use the real clock, dispatchers and SLF4J`() {
        val core = productionCoreDeps()

        core.clock shouldBe SystemClock
        core.dispatchers.io shouldBe Dispatchers.IO
        core.dispatchers.default shouldBe Dispatchers.Default
        core.logger.shouldBeInstanceOf<Slf4jLogger>()
    }

    @Suppress("LongMethod") // a table of keys
    @Test
    fun `every UI text exists and is not blank`() {
        Strings.keys shouldContainAll
            setOf(
                "app.title",
                "menu.projects",
                "menu.addProject",
                "menu.renameProject",
                "menu.removeProject",
                "menu.view",
                "menu.palette",
                "command.goToProject",
                "command.nextSession",
                "command.previousSession",
                "command.stopSession",
                "command.respawnSession",
                "command.removeSession",
                "command.previewSession",
                "command.closePreview",
                "command.attachSession",
                "command.replySession",
                "terminal.attachTab",
                "command.switchProject",
                "command.filterAll",
                "command.filterActive",
                "command.filterFinished",
                "command.view.waiting",
                "command.view.working",
                "command.view.failed",
                "command.terminalSettings",
                "picker.title",
            )
        Strings.keys.forEach { Strings[it].shouldNotBeBlank() }
    }

    /** Paths reach logs only at DEBUG (AM-304 plan, decision 5), so the shipped config pins INFO. */
    @Test
    fun `production logger has debug disabled`() {
        val config = javaClass.getResourceAsStream("/simplelogger.properties").shouldNotBeNull()
        val properties = config.use { stream -> Properties().apply { load(stream) } }

        properties.getProperty("org.slf4j.simpleLogger.defaultLogLevel") shouldBe "info"
        LoggerFactory.getLogger("am").isDebugEnabled shouldBe false
    }

    @Test
    fun `project list lives in Application Support`() {
        appSupportDir(Path.of("/Users/test")) shouldBe
            Path.of("/Users/test/Library/Application Support/Quayside")
    }
}
