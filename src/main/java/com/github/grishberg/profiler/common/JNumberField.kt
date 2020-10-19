package com.github.grishberg.profiler.common

import java.awt.Toolkit
import java.awt.event.KeyEvent
import javax.swing.JTextField

class JNumberField(columns: Int) : JTextField(columns) {
    var value: Int
        get() {
            try {
                return Integer.valueOf(text)
            } catch (e: NumberFormatException) {
                e.printStackTrace()
                return 0
            }
        }
        set(value) {
            text = value.toString()
        }

    override fun processKeyEvent(ev: KeyEvent) {
        val c = ev.getKeyChar()
        val keyCode = ev.keyCode
        if (!(Character.isDigit(c) || keyCode == KeyEvent.VK_BACK_SPACE || keyCode == KeyEvent.VK_ENTER ||
                    keyCode == KeyEvent.VK_DELETE || keyCode == KeyEvent.VK_LEFT ||
                    keyCode == KeyEvent.VK_RIGHT || keyCode == KeyEvent.VK_META ||
                    keyCode == KeyEvent.VK_SHIFT || keyCode == KeyEvent.VK_CONTROL ||
                    ((keyCode == KeyEvent.VK_C || keyCode == KeyEvent.VK_V ||
                            keyCode == KeyEvent.VK_A || keyCode == KeyEvent.VK_X) &&
                            (ev.modifiers == Toolkit.getDefaultToolkit().menuShortcutKeyMask)))
        ) {
            ev.consume()
        }
        super.processKeyEvent(ev)
        return
    }
}
