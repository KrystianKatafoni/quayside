package dev.agentmanager.terminal.impl.replyspike

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

/** Unique session names and the work directory of the runner (review: no fixed `/tmp/am507`, no name clashes). */
internal class ScenariosNamesTest {
    @TempDir
    lateinit var parent: Path

    @Test
    fun `session name carries kind and trial number`() {
        sessionName(Kind.TEXT, 3, race = false, Random(1)) shouldStartWith "am507-text-3"
    }

    @Test
    fun `names of two calls with the same arguments differ`() {
        val random = Random(1)

        sessionName(Kind.OPTIONS, 2, false, random) shouldNotBe sessionName(Kind.OPTIONS, 2, false, random)
    }

    @Test
    fun `a race trial does not share the name of the measured trial with the same number`() {
        val measured = sessionName(Kind.PERMISSION, 4, race = false, Random(7))
        val race = sessionName(Kind.PERMISSION, 4, race = true, Random(7))

        race shouldNotBe measured
        race.contains("race") shouldBe true
    }

    @Test
    fun `session names are safe command arguments`() {
        Regex("[A-Za-z0-9-]+").matches(sessionName(Kind.TEXT, 1, true, Random(3))) shouldBe true
    }

    @Test
    fun `work root is a fresh temp directory`() {
        val first = newWorkRoot(parent)
        val second = newWorkRoot(parent)

        Files.isDirectory(first) shouldBe true
        first.parent shouldBe parent
        first.fileName.toString() shouldStartWith "am507"
        first shouldNotBe second
        first shouldNotBe Path.of("/tmp/am507")
    }
}
