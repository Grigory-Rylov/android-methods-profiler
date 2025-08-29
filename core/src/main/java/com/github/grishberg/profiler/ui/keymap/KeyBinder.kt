package com.github.grishberg.profiler.ui.keymap

import com.github.grishberg.profiler.chart.CallTracePanel
import com.github.grishberg.profiler.ui.InfoPanel
import com.github.grishberg.profiler.ui.Main
import com.github.grishberg.profiler.ui.ShowDialogDelegate
import com.github.grishberg.profiler.ui.TextUtils
import com.github.grishberg.profiler.ui.dialogs.NewBookmarkDialog
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.KeyStroke

class KeyBinder(
    private val profilerView: CallTracePanel,
    private val copySource: JTextField,
    private val searchField: JTextField,
    private val dialogDelegate: ShowDialogDelegate,
    private val newBookmarkDialog: NewBookmarkDialog,
    private val hoverInfoPanel: InfoPanel,
    private val main: Main,
    private val keymapConfig: KeymapConfig,
) {
    val condition = JComponent.WHEN_IN_FOCUSED_WINDOW
    val inputMap = profilerView.getInputMap(condition)
    val actionMap = profilerView.actionMap
    private val keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    private val textUtils = TextUtils()

    fun setUpKeyBindings() {
        val panLeftAction = PanLeftAction()
        val panRightAction = PanRightAction()

        addKeyMap(keymapConfig.zoomOutKeyCode, ZoomOutAction())
        addKeyMap(keymapConfig.zoomInActionKeyCode, ZoomInAction())
        addKeyMap(keymapConfig.panLeftActionKeyCode, panLeftAction)
        addKeyMap(keymapConfig.panLeftActionKeyCode, panLeftAction)
        addKeyMap(keymapConfig.panRightActionKeyCode, panRightAction)
        addKeyMap(keymapConfig.panRightActionKeyCode, panRightAction)
        addKeyMap(keymapConfig.upActionKeyCode, UpAction())
        addKeyMap(keymapConfig.downActionKeyCode, DownAction())

        val focusNextAction = FocusNextAction()
        addKeyMap(KeyEvent.VK_ENTER, focusNextAction)
        addKeyMap(KeyEvent.VK_F3, focusNextAction)
        addKeyMap(keymapConfig.focusNextFoundItemActionKeyCode, focusNextAction)
        addKeyMap(keymapConfig.focusPrevFoundItemActionKeyCode, FocusPrevFoundItemAction())
        addKeyMap(keymapConfig.removeSelectionActionKeyCode, RemoveSelectionAction())
        addKeyMap(keymapConfig.theadTimeModeActionKeyCode, TheadTimeModeAction())
        addKeyMap(keymapConfig.globalTimeModeActionKeyCode, GlobalTimeModeAction())
        addKeyMap(keymapConfig.toggleBookmarkModeActionKeyCode, ToggleBookmarkModeAction())
        addKeyMapWithCtrl(keymapConfig.goToFindActionKeyCode, GoToFindAction())
        addKeyMapWithCtrlShift(keymapConfig.goToFindActionKeyCode, GoToExtendedFindAction())
        addKeyMapWithCtrl(keymapConfig.copyStackTraceActionKeyCode, CopyStackTraceAction())
        addKeyMapWithCtrl(keymapConfig.openFileDialogActionKeyCode, OpenFileDialogAction())
        addKeyMapWithCtrl(keymapConfig.newTraceActionKeyCode, NewTraceAction())
        addKeyMapWithCtrlShift(keymapConfig.openFileDialogNewWindowActionKeyCode, OpenFileDialogNewWindowAction())
        addKeyMapWithCtrlShift(keymapConfig.newTraceNewWindowActionKeyCode, NewTraceNewWindowAction())
        addKeyMapWithCtrl(keymapConfig.foundToMarkerKeyCode, FoundToMarker())
        addKeyMapWithCtrl(keymapConfig.showThreadSwitcherKeyCode, ShowThreadSwitcher())
        addKeyMapWithCtrl(keymapConfig.switchToMainThreadKeyCode, SwitchToMainThread())
        addKeyMapWithCtrl(keymapConfig.findAllChildrenKeyCode, FindAllChildren())
        addKeyMap(keymapConfig.addBookmarkActionKeyCode, AddBookmarkAction())
        addKeyMapWithCtrl(keymapConfig.copySelectedFullClassNameActionKeyCode, CopySelectedFullClassNameAction())
        addKeyMapWithCtrl(keymapConfig.removeCurrentBookmarkActionKeyCode, RemoveCurrentBookmarkAction())
        addKeyMap(keymapConfig.centerSelectedElementActionKeyCode, CenterSelectedElementAction())
        addKeyMap(keymapConfig.resetZoomActionKeyCode, ResetZoomAction())
        addKeyMap(keymapConfig.fitSelectedElementActionKeyCode, FitSelectedElementAction())
        addKeyMap(keymapConfig.nextBookmarkActionKeyCode, NextBookmarkAction())
        addKeyMap(keymapConfig.prevBookmarkActionKeyCode, PrevBookmarkAction())
        addKeyMap(keymapConfig.deleteCurrentFile, DeleterCurrentFileAction())
        addKeyMapWithCtrl(keymapConfig.clearAllBookmarksActionKeyCode, ClearAllBookmarksAction())
        addKeyMapWithCtrl(keymapConfig.generateReportsActionKeyCode, GenerateReportsAction())
        addKeyMapWithCtrl(keymapConfig.changeFontSizeActionTrueKeyCode, ChangeFontSizeAction(true))
        addKeyMapWithCtrl(keymapConfig.changeFontSizeActionTrueKeyCode, ChangeFontSizeAction(true))
        addKeyMapWithCtrl(keymapConfig.changeFontSizeActionFalseKeyCode, ChangeFontSizeAction(false))
        addKeyMapWithCtrlShift(keymapConfig.copySelectedShortClassNameActionKeyCode, CopySelectedShortClassNameAction())
        addKeyMapWithCtrlShift(keymapConfig.exportTraceWithBookmarksActionKeyCode, ExportTraceWithBookmarksAction())
        addKeyMapWithCtrlAlt(keymapConfig.copySelectedShortClassNameWithoutMethodActionKeyCode, CopySelectedShortClassNameWithoutMethodAction())
        addKeyMapWithShift(keymapConfig.openRangeDialogKeyCode, OpenRangeDialog())
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


    private inner class ZoomOutAction : SmartAction() {
        override fun actionPerformed() {
            profilerView.zoomOut()
            hoverInfoPanel.hidePanel()
        }
    }

    private inner class ZoomInAction : SmartAction() {
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
            searchField.selectAll()
        }
    }

    private inner class GoToExtendedFindAction : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
            main.startExtendedSearch()
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
            main.exitFromSearching(true)
        }
    }

    private inner class FocusPrevFoundItemAction : SmartAction() {
        override fun actionPerformed() {
            profilerView.focusPrevFoundItem()
            hoverInfoPanel.hidePanel()
        }
    }

    private inner class FocusNextAction : SmartAction() {
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

    private inner class ClearAllBookmarksAction : SmartAction() {
        override fun actionPerformed() {
            profilerView.clearBookmarks()
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

    private inner class SwitchToMainThread : SmartAction() {
        override fun actionPerformed() {
            main.switchMainThread()
        }
    }

    private inner class ShowThreadSwitcher : SmartAction() {
        override fun actionPerformed() {
            main.showThreadsDialog()
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
    private inner class DeleterCurrentFileAction : SmartAction() {
        override fun actionPerformed() {
            main.deleteCurrentFile()
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
