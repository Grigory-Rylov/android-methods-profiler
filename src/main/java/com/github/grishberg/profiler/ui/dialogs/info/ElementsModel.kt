package com.github.grishberg.profiler.ui.dialogs.info

import com.github.grishberg.profiler.analyzer.ProfileData
import javax.swing.table.AbstractTableModel

class ElementsModel : AbstractTableModel() {
    private val columnNames = arrayOf("Name", "Global time", "Thread time", "Self time (global)", "Self time(thread)")
    private val columnClass = arrayOf<Class<*>>(
        String::class.java,
        Double::class.java,
        Double::class.java,
        Double::class.java,
        Double::class.java
    )
    private val items = mutableListOf<ProfileData>()

    override fun getRowCount(): Int = items.size
    override fun getColumnCount(): Int = 5
    override fun getColumnName(column: Int) = columnNames[column]
    override fun getColumnClass(columnIndex: Int) = columnClass[columnIndex]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val currentElement = items[rowIndex]
        return when (columnIndex) {
            1 -> currentElement.globalEndTimeInMillisecond - currentElement.globalStartTimeInMillisecond
            2 -> currentElement.threadEndTimeInMillisecond - currentElement.threadStartTimeInMillisecond
            3 -> currentElement.globalSelfTime
            4 -> currentElement.threadSelfTime
            else -> currentElement.name
        }
    }

    fun setItems(newItems: List<ProfileData>) {
        items.clear()
        items.addAll(newItems)
        fireTableDataChanged()
    }

    fun itemAt(index: Int): ProfileData = items[index]
}
