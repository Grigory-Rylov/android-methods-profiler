package com.github.grishberg.profiler.common

import java.awt.Toolkit
import java.awt.event.ActionEvent
import javax.swing.KeyStroke

fun createAccelerator(keyChar: Char): KeyStroke {
    return KeyStroke.getKeyStroke(keyChar)
}

fun createControlAccelerator(keyChar: Char): KeyStroke {
    return KeyStroke.getKeyStroke(keyChar, Toolkit.getDefaultToolkit().menuShortcutKeyMask)
}

fun createControlShiftAccelerator(keyChar: Char): KeyStroke {
    return KeyStroke.getKeyStroke(keyChar, Toolkit.getDefaultToolkit().menuShortcutKeyMask + ActionEvent.SHIFT_MASK)
}

fun createShiftAccelerator(keyChar: Char): KeyStroke {
    return KeyStroke.getKeyStroke(keyChar, ActionEvent.SHIFT_MASK)
}