package com.github.grishberg.profiler.ui.views

import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JTextField

class HistoryComboBox(private val initialModel: DefaultComboBoxModel<String>) : JComboBox<String>(initialModel) {

    private val editor = getEditor().editorComponent as JTextField

    val text: String
        get() {
            return editor.text.trim()
        }

    var columns: Int
        get() = editor.columns

        set(value) {
            editor.columns = value
        }

    fun setItems(items: List<String>) {
        initialModel.removeAllElements()
        initialModel.addAll(items)
        items.firstOrNull()?.let {
            initialModel.selectedItem = it
        }
    }

    init {
        setEditable(true)
    }

    companion object {

        fun create(initialItems: List<String> = emptyList()): HistoryComboBox {
            return HistoryComboBox(DefaultComboBoxModel(initialItems.toTypedArray()))
        }
    }
}
