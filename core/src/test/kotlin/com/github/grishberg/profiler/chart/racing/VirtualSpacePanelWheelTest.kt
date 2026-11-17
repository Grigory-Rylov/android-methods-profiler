package com.github.grishberg.profiler.chart.racing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JPanel

internal class VirtualSpacePanelWheelTest {

    private fun wheel(shift: Boolean, rotation: Int): MouseWheelEvent =
        MouseWheelEvent(
            /* source = */ JPanel(),
            /* id = */ MouseEvent.MOUSE_WHEEL,
            /* when = */ 0L,
            /* modifiers = */ if (shift) InputEvent.SHIFT_DOWN_MASK else 0,
            /* x = */ 100, /* y = */ 100,
            /* clickCount = */ 0, /* popupTrigger = */ false,
            /* scrollType = */ MouseWheelEvent.WHEEL_UNIT_SCROLL,
            /* scrollAmount = */ 1, /* wheelRotation = */ rotation,
        )

    @Test
    fun `vertical wheel pans only y`() {
        val panel = VirtualSpacePanel()
        panel.dispatchEvent(wheel(shift = false, rotation = 3))
        assertNotEquals(0.0, panel.state.viewY)
        assertEquals(0.0, panel.state.viewX, 1e-9)
    }

    @Test
    fun `shift wheel pans only x`() {
        val panel = VirtualSpacePanel()
        panel.dispatchEvent(wheel(shift = true, rotation = 3))

        assertNotEquals(0.0, panel.state.viewX)
        assertEquals(0.0, panel.state.viewY, 1e-9)
    }
}
