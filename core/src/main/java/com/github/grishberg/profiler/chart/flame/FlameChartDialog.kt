package com.github.grishberg.profiler.chart.flame

import com.github.grishberg.profiler.chart.FoundInfoListener
import com.github.grishberg.profiler.comparator.aggregator.threads.AggregatedFlameThreadsViewDialog
import com.github.grishberg.profiler.comparator.aggregator.threads.FlameThreadItem
import com.github.grishberg.profiler.comparator.aggregator.threads.AggregatedFlameThreadSwitchController
import com.github.grishberg.profiler.ui.DurationPercentFormatter
import com.github.grishberg.profiler.ui.SwitchThreadButton
import com.github.grishberg.profiler.ui.TextUtils
import com.github.grishberg.profiler.ui.theme.Palette
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.AbstractAction
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.border.BevelBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

private const val PAN_STEP_PX = 50.0

class FlameChartDialog(
    private val controller: FlameChartController,
    private val palette: Palette,
    defaultFoundInfoMessage: String,
    private val threadSwitchController: AggregatedFlameThreadSwitchController? = null
) : JFrame("Flame chart"), FoundInfoListener<FlameRectangle>, DialogView {
    private val condition = JComponent.WHEN_IN_FOCUSED_WINDOW
    private val flameRacing = FlameRacingView(this, controller, palette)
    private val inputMap = flameRacing.panel.getInputMap(condition)
    private val actionMap = flameRacing.panel.actionMap
    private val keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    private val textUtils = TextUtils()
    private val hoverInfoPanel: FlameInfoPanel
    private val mainPanel = JPanel(BorderLayout())
    private val statusNameLabel = JLabel()
    private val statusTimeLabel = JLabel()
    private val findClassText = JTextField("").apply {
        toolTipText = "Use this field to find elements in trace"
        document.addDocumentListener(FindTextChangedEvent())
        addActionListener(FindInMethodsAction())
    }
    private val switchThreadButton = SwitchThreadButton()

    private val foundInfo = JLabel(defaultFoundInfoMessage)

    init {
        hoverInfoPanel = FlameInfoPanel(flameRacing.panel)
        rootPane.glassPane = hoverInfoPanel

        flameRacing.onElementSelected = { element ->
            statusNameLabel.text = "(${element.count}) ${element.name}"
            val start = element.getX()
            val end = element.maxX
            statusTimeLabel.text = " duration: ${DurationPercentFormatter.durationTextWithParentPercent(end - start, element.parentDuration)}"
        }
        flameRacing.onElementHovered = { element, point ->
            if (element != null) {
                hoverInfoPanel.setText(point, element)
            } else {
                hoverInfoPanel.hidePanel()
            }
        }
        controller.view = flameRacing
        createFindPanel()
        val chartWithPreview = JPanel(BorderLayout())
        chartWithPreview.add(flameRacing.view, BorderLayout.CENTER)
        chartWithPreview.add(flameRacing.previewPanel, BorderLayout.NORTH)
        mainPanel.add(chartWithPreview, BorderLayout.CENTER)
        flameRacing.previewPanel.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                flameRacing.renderPreview()
            }
        })
        createStatusBar()
        contentPane = mainPanel
        pack()

        val panLeftAction = PanLeftAction()
        val panRightAction = PanRightAction()

        addKeyMap(KeyEvent.VK_W, WAction())
        addKeyMap(KeyEvent.VK_S, SAction())
        addKeyMap(KeyEvent.VK_A, panLeftAction)
        addKeyMap(KeyEvent.VK_D, panRightAction)

        val focusNextAction = FocusNextAction()
        addKeyMap(KeyEvent.VK_E, focusNextAction)
        addKeyMap(KeyEvent.VK_ENTER, focusNextAction)
        addKeyMap(KeyEvent.VK_F3, focusNextAction)

        addKeyMap(KeyEvent.VK_Q, QAction())
        addKeyMap(KeyEvent.VK_ESCAPE, RemoveSelectionAction())

        addKeyMap(KeyEvent.VK_T, ThreadTimeModeAction())
        addKeyMap(KeyEvent.VK_G, GlobalTimeModeAction())

        addKeyMapWithCtrl(KeyEvent.VK_C, CopySelectedFullClassNameAction())
        addKeyMapWithCtrl(KeyEvent.VK_F, GoToSearchField())
        addKeyMap(KeyEvent.VK_C, CenterSelectedElementAction())
        addKeyMap(KeyEvent.VK_Z, ResetZoomAction())
        addKeyMap(KeyEvent.VK_F, FitSelectedElementAction())

        addKeyMapWithCtrl(KeyEvent.VK_P, GenerateReportsAction())
        addKeyMapWithCtrl(KeyEvent.VK_PLUS, ChangeFontSizeAction(true))
        addKeyMapWithCtrl(KeyEvent.VK_EQUALS, ChangeFontSizeAction(true))
        addKeyMapWithCtrl(KeyEvent.VK_MINUS, ChangeFontSizeAction(false))

        addKeyMapWithCtrlShift(KeyEvent.VK_C, CopySelectedShortClassNameAction())
        addKeyMapWithCtrlAlt(KeyEvent.VK_C, CopySelectedShortClassNameWithoutMethodAction())

        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(windowEvent: WindowEvent) {
                controller.onDialogClosed()
            }
        })
        flameRacing.requestPanelFocus()
    }

    private fun createFindPanel() {
        val findPanel = JPanel(BorderLayout(2, 2))
        if (threadSwitchController != null) {
            switchThreadButton.switchThread(FlameThreadItem.MAIN)
            switchThreadButton.addActionListener {
                showSwitchThreadDialog()
            }
            findPanel.add(switchThreadButton, BorderLayout.LINE_START)
        }
        findPanel.add(findClassText, BorderLayout.CENTER)
        findPanel.add(foundInfo, BorderLayout.LINE_END)
        mainPanel.add(findPanel, BorderLayout.NORTH)
    }

    private fun showSwitchThreadDialog() {
        val dialog = AggregatedFlameThreadsViewDialog(this, threadSwitchController!!)
        dialog.setLocationRelativeTo(flameRacing.view)
        dialog.isVisible = true
        val selected = dialog.selectedThreadItem
        if (selected != null) {
            switchThreadButton.switchThread(selected)
            threadSwitchController.onThreadSelected(selected)
        }
    }

    private fun createStatusBar() {
        val statusPanel = JPanel(BorderLayout(2, 2))
        statusPanel.border = BevelBorder(BevelBorder.LOWERED)
        mainPanel.add(statusPanel, BorderLayout.SOUTH)
        val font = statusNameLabel.font
        statusPanel.preferredSize = Dimension(width, font.size + 4)
        statusPanel.layout = BoxLayout(statusPanel, BoxLayout.X_AXIS)
        this.statusNameLabel.horizontalAlignment = SwingConstants.LEFT
        statusPanel.add(statusNameLabel, BorderLayout.LINE_START)
        statusPanel.add(statusTimeLabel, BorderLayout.LINE_END)
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

    private fun addKeyMap(keyCode: Int, modifiers: Int, action: AbstractAction) {
        val keyStroke: KeyStroke = KeyStroke.getKeyStroke(keyCode, modifiers)
        inputMap.put(keyStroke, keyStroke.toString())
        actionMap.put(keyStroke.toString(), action)
    }

    override fun exitFromSearching() {
        foundInfo.text = ""
    }

    override fun onFound(count: Int, selectedIndex: Int, profileData: FlameRectangle) {
        foundInfo.text = String.format("found %d, current %d", count, selectedIndex)
    }

    override fun onNotFound(text: String, ignoreCase: Boolean) {
        foundInfo.text = ""
        JOptionPane.showMessageDialog(flameRacing.view, "Not found `$text`")
    }

    override fun hideInfoPanel() {
        hoverInfoPanel.hidePanel()
    }

    fun refreshFlameChart() {
        flameRacing.refreshPalette()
    }

    private inner class WAction : SmartAction() {
        override fun actionPerformed() {
            flameRacing.zoomOut()
            hoverInfoPanel.hidePanel()
        }
    }

    private inner class SAction : SmartAction() {
        override fun actionPerformed() {
            flameRacing.zoomIn()
            hoverInfoPanel.hidePanel()
        }
    }

    private inner class PanLeftAction : SmartAction() {
        override fun actionPerformed() {
            flameRacing.panBy(-PAN_STEP_PX, 0.0)
            hoverInfoPanel.hidePanel()
        }
    }

    private inner class PanRightAction : SmartAction() {
        override fun actionPerformed() {
            flameRacing.panBy(PAN_STEP_PX, 0.0)
            hoverInfoPanel.hidePanel()
        }
    }

    private inner class GoToSearchField : SmartAction() {
        override fun actionPerformed() {
            findClassText.requestFocus()
        }
    }

    private inner class CopySelectedFullClassNameAction : SmartAction() {
        override fun actionPerformed() {
            controller.copySelectedToClipboard()
        }
    }

    private inner class CopySelectedShortClassNameAction : SmartAction() {
        override fun actionPerformed() {
            controller.copyShortClassNameToClipboard()
        }
    }

    private inner class CopySelectedShortClassNameWithoutMethodAction : SmartAction() {
        override fun actionPerformed() {
            controller.copyShortClassNameWithoutMethodToClipboard()
        }
    }

    private inner class RemoveSelectionAction : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
            controller.onEscape()
        }
    }

    private inner class QAction : SmartAction() {
        override fun actionPerformed() {
            controller.focusPrevFoundItem()
        }
    }

    private inner class FocusNextAction : SmartAction() {
        override fun actionPerformed() {
            controller.focusNextFoundItem()
        }
    }

    private inner class CenterSelectedElementAction : SmartAction() {
        override fun actionPerformed() {
            flameRacing.centerSelectedElement()
        }
    }

    private inner class ResetZoomAction : SmartAction() {
        override fun actionPerformed() {
            flameRacing.resetZoom()
        }
    }

    private inner class FitSelectedElementAction : SmartAction() {
        override fun actionPerformed() {
            flameRacing.fitSelectedElement()
        }
    }

    private inner class GenerateReportsAction : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
            //dialogDelegate.showReportsDialog()
        }
    }

    private inner class ChangeFontSizeAction(
        private val increase: Boolean
    ) : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
            if (increase) {
                flameRacing.increaseFontSize()
            } else {
                flameRacing.decreaseFontSize()
            }
        }
    }

    private inner class OpenRangeDialog : SmartAction() {
        override fun actionPerformed() {
            //dialogDelegate.showScaleRangeDialog()
        }
    }

    private inner class ThreadTimeModeAction : SmartAction() {
        override fun actionPerformed() {
            controller.switchTimeMode(true)
        }
    }

    private inner class GlobalTimeModeAction : SmartAction() {
        override fun actionPerformed() {
            controller.switchTimeMode(false)
        }
    }

    private abstract inner class SmartAction : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
            if (keyboardFocusManager.focusOwner is JTextField) return
            actionPerformed()
        }

        abstract fun actionPerformed()
    }

    private inner class FindTextChangedEvent : DocumentListener {
        override fun insertUpdate(e: DocumentEvent) = Unit

        override fun removeUpdate(e: DocumentEvent) {
            if (findClassText.text.isEmpty()) {
                controller.onSearchTextRemoved()
            }
        }

        override fun changedUpdate(e: DocumentEvent) = Unit
    }

    private inner class FindInMethodsAction : ActionListener {
        override fun actionPerformed(e: ActionEvent) {
            val textToFind: String = findClassText.text
            if (textToFind != null && textToFind.length > 0) {
                controller.onFindInMethodsPressed(textToFind)
                return
            }
        }
    }

}
