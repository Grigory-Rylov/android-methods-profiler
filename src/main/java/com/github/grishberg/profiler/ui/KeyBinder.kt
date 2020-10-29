package com.github.grishberg.profiler.ui

import com.github.grishberg.profiler.chart.ProfilerPanel
import com.github.grishberg.profiler.ui.dialogs.NewBookmarkDialog
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.KeyStroke

class KeyBinder(
    private val profilerView: ProfilerPanel,
    private val copySource: JTextField,
    private val searchField: JTextField,
    private val dialogDelegate: ShowDialogDelegate,
    private val newBookmarkDialog: NewBookmarkDialog,
    private val hoverInfoPanel: InfoPanel,
    private val main: Main
) {
    val condition = JComponent.WHEN_IN_FOCUSED_WINDOW
    val inputMap = profilerView.getInputMap(condition)
    val actionMap = profilerView.actionMap
    private val keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    private val textUtils = TextUtils()

    fun setUpKeyBindings() {
        val panLeftAction = PanLeftAction()
        val panRightAction = PanRightAction()

        addKeyMap(KeyEvent.VK_W, WAction())
        addKeyMap(KeyEvent.VK_S, SAction())
        addKeyMap(KeyEvent.VK_A, panLeftAction)
        addKeyMap(KeyEvent.VK_LEFT, panLeftAction)
        addKeyMap(KeyEvent.VK_D, panRightAction)
        addKeyMap(KeyEvent.VK_RIGHT, panRightAction)
        addKeyMap(KeyEvent.VK_UP, UpAction())
        addKeyMap(KeyEvent.VK_DOWN, DownAction())

        val focusNextAction = EAction()
        addKeyMap(KeyEvent.VK_E, focusNextAction)
        addKeyMap(KeyEvent.VK_ENTER, focusNextAction)
        addKeyMap(KeyEvent.VK_Q, QAction())
        addKeyMap(KeyEvent.VK_ESCAPE, RemoveSelectionAction())

        addKeyMap(KeyEvent.VK_T, TheadTimeModeAction())
        addKeyMap(KeyEvent.VK_G, GlobalTimeModeAction())
        addKeyMap(KeyEvent.VK_B, ToggleBookmarkModeAction())

        addKeyMapWithCtrl(KeyEvent.VK_F, GoToFindAction())
        addKeyMapWithCtrl(KeyEvent.VK_S, CopyStackTraceAction())
        addKeyMapWithCtrl(KeyEvent.VK_O, OpenFileDialogAction())
        addKeyMapWithCtrl(KeyEvent.VK_N, NewTraceAction())
        addKeyMapWithCtrlShift(KeyEvent.VK_O, OpenFileDialogNewWindowAction())
        addKeyMapWithCtrlShift(KeyEvent.VK_N, NewTraceNewWindowAction())

        addKeyMapWithCtrl(KeyEvent.VK_M, FoundToMarker())
        addKeyMapWithCtrl(KeyEvent.VK_I, FindAllChildren())
        addKeyMap(KeyEvent.VK_M, AddBookmarkAction())
        addKeyMapWithCtrl(KeyEvent.VK_C, CopySelectedFullClassNameAction())
        addKeyMapWithCtrl(KeyEvent.VK_R, RemoveCurrentBookmarkAction())
        addKeyMap(KeyEvent.VK_C, CenterSelectedElementAction())
        addKeyMap(KeyEvent.VK_Z, ResetZoomAction())
        addKeyMap(KeyEvent.VK_F, FitSelectedElementAction())

        addKeyMap(KeyEvent.VK_E, InputEvent.SHIFT_MASK, NextBookmarkAction())
        addKeyMap(KeyEvent.VK_Q, InputEvent.SHIFT_MASK, PrevBookmarkAction())

        addKeyMapWithCtrl(KeyEvent.VK_P, GenerateReportsAction())
        addKeyMapWithCtrl(KeyEvent.VK_PLUS, ChangeFontSizeAction(true))
        addKeyMapWithCtrl(KeyEvent.VK_EQUALS, ChangeFontSizeAction(true))
        addKeyMapWithCtrl(KeyEvent.VK_MINUS, ChangeFontSizeAction(false))

        addKeyMapWithCtrlShift(KeyEvent.VK_C, CopySelectedShortClassNameAction())
        addKeyMapWithCtrlShift(KeyEvent.VK_E, ExportTraceWithBookmarksAction())
        addKeyMapWithCtrlAlt(KeyEvent.VK_C, CopySelectedShortClassNameWithoutMethodAction())

        addKeyMapWithShift(KeyEvent.VK_R, OpenRangeDialog())
    }

    private fun addKeyMap(keyCode: Int, action: AbstractAction) {
        val keyStroke: KeyStroke = KeyStroke.getKeyStroke(keyCode, 0)
        inputMap.put(keyStroke, keyStroke.toString())
        actionMap.put(keyStroke.toString(), action)
    }

    private fun addKeyMapWithCtrl(keyCode: Int, action: AbstractAction) {
        addKeyMap(keyCode, Toolkit.getDefaultToolkit().menuShortcutKeyMask, action)
    }

    private fun addKeyMapWithCtrlShift(keyCode: Int, action: AbstractAction) {
        addKeyMap(keyCode, Toolkit.getDefaultToolkit().menuShortcutKeyMask + ActionEvent.SHIFT_MASK, action)
    }

    private fun addKeyMapWithCtrlAlt(keyCode: Int, action: AbstractAction) {
        addKeyMap(keyCode, Toolkit.getDefaultToolkit().menuShortcutKeyMask + ActionEvent.ALT_MASK, action)
    }

    private fun addKeyMapWithShift(keyCode: Int, action: AbstractAction) {
        addKeyMap(keyCode, ActionEvent.SHIFT_MASK, action)
    }

    private fun addKeyMap(keyCode: Int, modifiers: Int, action: AbstractAction) {
        val keyStroke: KeyStroke = KeyStroke.getKeyStroke(keyCode, modifiers)
        inputMap.put(keyStroke, keyStroke.toString())
        actionMap.put(keyStroke.toString(), action)
    }


    private inner class WAction : SmartAction() {
        override fun actionPerformed() {
            profilerView.zoomOut()
            hoverInfoPanel.hidePanel()
        }
    }

    private inner class SAction : SmartAction() {
        override fun actionPerformed() {
            profilerView.zoomIn()
            hoverInfoPanel.hidePanel()
        }
    }

    private inner class PanLeftAction : SmartAction() {
        override fun actionPerformed() {
            profilerView.scrollRight()
            hoverInfoPanel.hidePanel()
        }
    }

    private inner class PanRightAction : SmartAction() {
        override fun actionPerformed() {
            profilerView.scrollLeft()
            hoverInfoPanel.hidePanel()
        }
    }


    private inner class UpAction : SmartAction() {
        override fun actionPerformed() {
            profilerView.scrollUp()
            hoverInfoPanel.hidePanel()
        }
    }

    private inner class DownAction : SmartAction() {
        override fun actionPerformed() {
            profilerView.scrollDown()
        }
    }

    private inner class GoToFindAction : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
            searchField.requestFocus();
        }
    }

    private inner class CopySelectedFullClassNameAction : SmartAction() {
        override fun actionPerformed() {
            val stringSelection = StringSelection(copySource.text)
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(stringSelection, null)
        }
    }

    private inner class CopySelectedShortClassNameAction : SmartAction() {
        override fun actionPerformed() {
            val stringSelection = StringSelection(textUtils.shortClassMethodName(copySource.text))
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(stringSelection, null)
        }
    }

    private inner class CopySelectedShortClassNameWithoutMethodAction : SmartAction() {
        override fun actionPerformed() {
            val stringSelection = StringSelection(textUtils.shortClassName(copySource.text))
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(stringSelection, null)
        }
    }

    private inner class CopyStackTraceAction : SmartAction() {
        override fun actionPerformed() {
            val stackTrace: String? = profilerView.copySelectedStacktrace()
            if (stackTrace != null) {
                val stringSelection = StringSelection(stackTrace)
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(stringSelection, null)
            }
        }
    }

    private inner class RemoveSelectionAction : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
            profilerView.disableSearching()
            profilerView.removeSelection()
            profilerView.requestFocus()
            main.exitFromSearching()
        }
    }

    private inner class QAction : SmartAction() {
        override fun actionPerformed() {
            profilerView.focusPrevFoundItem()
            hoverInfoPanel.hidePanel()
        }
    }

    private inner class EAction : SmartAction() {
        override fun actionPerformed() {
            profilerView.focusNextFoundItem()
            hoverInfoPanel.hidePanel()
        }
    }

    private inner class PrevBookmarkAction : SmartAction() {
        override fun actionPerformed() {
            profilerView.focusPrevMarker()
            hoverInfoPanel.hidePanel()
        }
    }

    private inner class NextBookmarkAction : SmartAction() {
        override fun actionPerformed() {
            profilerView.focusNextMarker()
            hoverInfoPanel.hidePanel()
        }
    }

    private inner class AddBookmarkAction : SmartAction() {
        override fun actionPerformed() {
            main.addBookmark()
        }
    }

    private inner class FoundToMarker : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
            if (!profilerView.isSearchingInProgress) {
                return
            }
            newBookmarkDialog.clearAndHide()
            newBookmarkDialog.setLocationRelativeTo(profilerView)
            newBookmarkDialog.isVisible = true
            val result = newBookmarkDialog.bookMarkInfo
            if (result != null) {
                profilerView.addBookmarkAtFocusedFound(result)
            }
        }
    }

    private inner class RemoveCurrentBookmarkAction : SmartAction() {
        override fun actionPerformed() {
            profilerView.removeCurrentBookmark()
        }
    }

    private inner class CenterSelectedElementAction : SmartAction() {
        override fun actionPerformed() {
            profilerView.centerSelectedElement()
        }
    }

    private inner class ResetZoomAction : SmartAction() {
        override fun actionPerformed() {
            profilerView.resetZoom()
        }
    }

    private inner class FitSelectedElementAction : SmartAction() {
        override fun actionPerformed() {
            profilerView.fitSelectedElement()
        }
    }

    private inner class OpenFileDialogAction : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
            dialogDelegate.showOpenFileChooser()
        }
    }

    private inner class NewTraceAction : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
            dialogDelegate.showNewTraceDialog()
        }
    }

    private inner class OpenFileDialogNewWindowAction : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
            dialogDelegate.showOpenFileChooser(inNewWindow = true)
        }
    }

    private inner class NewTraceNewWindowAction : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
            dialogDelegate.showNewTraceDialog(inNewWindow = true)
        }
    }

    private inner class GenerateReportsAction : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
            dialogDelegate.showReportsDialog()
        }
    }

    private inner class ChangeFontSizeAction(
        private val increase: Boolean
    ) : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
            if (increase) {
                profilerView.increaseFontSize()
            } else {
                profilerView.decreaseFontSize()
            }
        }
    }

    private inner class OpenRangeDialog : SmartAction() {
        override fun actionPerformed() {
            dialogDelegate.showScaleRangeDialog()
        }
    }

    private inner class ExportTraceWithBookmarksAction : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
            main.exportTraceWithBookmarks()
        }
    }

    private inner class TheadTimeModeAction : SmartAction() {
        override fun actionPerformed() {
            main.switchTimeMode(true)
        }
    }

    private inner class FindAllChildren : SmartAction() {
        override fun actionPerformed() {
            main.findAllChildren()
        }
    }

    private inner class GlobalTimeModeAction : SmartAction() {
        override fun actionPerformed() {
            main.switchTimeMode(false)
        }
    }

    private inner class ToggleBookmarkModeAction : SmartAction() {
        override fun actionPerformed() {
            main.toggleBookmarkMode(true)
        }
    }

    private abstract inner class SmartAction : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
            if (shouldSkip()) return
            actionPerformed()
        }

        abstract fun actionPerformed()
    }

    private fun shouldSkip(): Boolean {
        val focused = keyboardFocusManager.focusOwner
        if (focused is JTextField) {
            return true
        }
        return false
    }
}
