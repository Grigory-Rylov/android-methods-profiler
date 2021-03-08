package com.github.grishberg.profiler.ui.dialogs

import java.awt.Dialog
import javax.swing.JFrame

interface ElementWithColorDialogFactory {
    fun createElementWithColorDialog(owner: JFrame, title: String): ElementWithColorDialog
    fun createElementWithColorDialog(owner: Dialog, title: String): ElementWithColorDialog
}
