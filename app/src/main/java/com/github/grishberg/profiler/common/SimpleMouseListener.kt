package com.github.grishberg.profiler.common

import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.SwingUtilities

abstract class SimpleMouseListener : MouseListener {
    override fun mouseClicked(e: MouseEvent) {
        if (SwingUtilities.isRightMouseButton(e)) {
            mouseRightClicked(e)
            return
        }
        if (SwingUtilities.isLeftMouseButton(e)) {
            mouseLeftClicked(e)
        }
    }

    open fun mouseLeftClicked(e: MouseEvent) = Unit
    open fun mouseRightClicked(e: MouseEvent) = Unit

    override fun mouseReleased(e: MouseEvent) = Unit

    override fun mouseEntered(e: MouseEvent) = Unit

    override fun mouseExited(e: MouseEvent) = Unit

    override fun mousePressed(e: MouseEvent) = Unit
}
