package com.github.grishberg.profiler.ui.dialogs.highlighting

import com.github.grishberg.profiler.chart.highlighting.ColorInfo
import com.github.grishberg.profiler.chart.highlighting.MethodsColorRepository
import com.github.grishberg.profiler.ui.dialogs.CloseByEscapeDialog
import com.github.grishberg.profiler.ui.dialogs.ElementWithColorDialogFactory
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Frame
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel


private const val BUTTON_TITLE_ADD = "+"
private const val BUTTON_TITLE_REMOVE = "-"

class HighlightDialog(
    owner: Frame,
    methodsColorRepository: MethodsColorRepository,
    private val dialogFactory: ElementWithColorDialogFactory
) : CloseByEscapeDialog(
    owner,
    "Highlight settings",
    true
), HighlightDialogView {
    private val logic = HighlightListDialogLogic(methodsColorRepository)
    private val listModel = DefaultListModel<ColorInfo>()
    private val list = JList(listModel)
    private val addButton = JButton(BUTTON_TITLE_ADD).also {
        isEnabled = true
    }
    private val removeButton = JButton(BUTTON_TITLE_REMOVE)
    private var eventListener: HighlightDialogView.EventListener? = null

    override val selectedItem: Int
        get() = list.selectedIndex

    init {
        logic.view = this
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.cellRenderer = ColorInfoCellRenderer()
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(evt: MouseEvent) {
                if (evt.clickCount == 2) {
                    eventListener?.onClickedToItem(list.locationToIndex(evt.point))
                }
            }
        })
        val listScrollPane = JScrollPane(list)

        val okButton = JButton("Ok")
        okButton.addActionListener { isVisible = false }

        val buttonPane = JPanel()
        buttonPane.layout = BoxLayout(
            buttonPane,
            BoxLayout.LINE_AXIS
        )
        buttonPane.add(removeButton)
        buttonPane.add(Box.createHorizontalStrut(5))
        buttonPane.add(addButton)
        buttonPane.add(okButton)
        buttonPane.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

        addButton.actionCommand = BUTTON_TITLE_ADD
        addButton.addActionListener { eventListener?.onClickedAddButton() }

        removeButton.actionCommand = BUTTON_TITLE_REMOVE
        removeButton.addActionListener { eventListener?.onClickedRemoveButton() }

        add(listScrollPane, BorderLayout.CENTER)
        add(buttonPane, BorderLayout.PAGE_END)
        preferredSize = Dimension(640, 480)
        pack()
        requestFocus()
    }

    override fun showList(colors: List<ColorInfo>) {
        listModel.clear()
        for (i in colors.indices) {
            listModel.add(i, colors[i])
        }
        list.invalidate()
    }

    override fun setEventListener(listener: HighlightDialogView.EventListener) {
        eventListener = listener
    }

    override fun enableRemoveButton() {
        removeButton.isEnabled = true
    }

    override fun disableRemoveButton() {
        removeButton.isEnabled = false
    }

    override fun requestColorInfoFromDialog(selectedItem: ColorInfo?): ColorInfo? {
        val title = if (selectedItem == null) "New highlight rule" else "Edit rule"
        val dialog = dialogFactory.createElementWithColorDialog(this, title)
        if (selectedItem == null) {
            dialog.showDialog(rootPane)
        } else {
            dialog.showForEditMode(selectedItem.color, selectedItem.filter, rootPane)
        }

        val result = dialog.result ?: return null
        return ColorInfo(result.title, result.color)
    }
}
