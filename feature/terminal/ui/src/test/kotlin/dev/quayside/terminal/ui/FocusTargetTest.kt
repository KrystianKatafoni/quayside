package dev.quayside.terminal.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextField

class FocusTargetTest {
    private val widget = JPanel()
    private val inside = JButton().also { widget.add(it) }
    private val outside = JTextField()

    @Test
    fun `no widget takes the focus while a modal layer is shown`() {
        focusTarget(modalActive = true, widget = widget) shouldBe null
        focusTarget(modalActive = false, widget = widget) shouldBe widget
    }

    @Test
    fun `the focus returns to the widget that held it before the layer`() {
        focusReturnTarget(inside, listOf(JPanel(), widget)) shouldBe widget
    }

    @Test
    fun `the focus does not go to a widget when it was elsewhere or nowhere`() {
        focusReturnTarget(outside, listOf(widget)) shouldBe null
        focusReturnTarget(null, listOf(widget)) shouldBe null
    }
}
