package dev.quayside.app

import dev.quayside.core.domain.SessionId
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The Attach action of a popup reaches the window only while the window sets a handler (AM-702). */
class AttachBridgeTest {
    private val bridge = AttachBridge()
    private val attached = mutableListOf<SessionId>()

    @Test
    fun `attach without a handler does nothing`() {
        bridge.attach(SessionId("s1"))

        attached.shouldBeEmpty()
    }

    @Test
    fun `attach reaches the handler of the window`() {
        bridge.handler = { attached += it }

        bridge.attach(SessionId("s1"))

        attached shouldBe listOf(SessionId("s1"))
    }

    @Test
    fun `attach after the window cleared the handler does nothing`() {
        bridge.handler = { attached += it }
        bridge.handler = null

        bridge.attach(SessionId("s1"))

        attached.shouldBeEmpty()
    }
}
