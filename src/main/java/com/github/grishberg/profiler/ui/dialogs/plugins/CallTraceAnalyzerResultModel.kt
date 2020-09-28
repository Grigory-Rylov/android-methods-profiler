package com.github.grishberg.profiler.ui.dialogs.plugins

import com.github.grishberg.android.profiler.plugins.AnalyzerMethodsInfo
import javax.swing.table.AbstractTableModel

class CallTraceAnalyzerResultModel : AbstractTableModel() {
    private val columnNames = arrayOf(
        "Name", "Global time", "Thread time", "Self time (global)",
        "Self time(thread)", "description"
    )
    private val columnClass = arrayOf<Class<*>>(
        String::class.java,
        Double::class.java,
        Double::class.java,
        Double::class.java,
        Double::class.java,
        String::class.java
    )
    private val items = mutableListOf<AnalyzerMethodsInfo>()

    override fun getRowCount(): Int = items.size
    override fun getColumnCount(): Int = columnClass.size
    override fun getColumnName(column: Int) = columnNames[column]
    override fun getColumnClass(columnIndex: Int) = columnClass[columnIndex]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val currentElement = items[rowIndex].method
        return when (columnIndex) {
            1 -> currentElement.globalEndTimeInMillisecond - currentElement.globalStartTimeInMillisecond
            2 -> currentElement.threadEndTimeInMillisecond - currentElement.threadStartTimeInMillisecond
            3 -> currentElement.globalSelfTime
            4 -> currentElement.threadSelfTime
            5 -> items[rowIndex].message
            else -> currentElement.name
        }
    }

    fun setItems(newItems: List<AnalyzerMethodsInfo>) {
        items.clear()
        items.addAll(newItems)
        fireTableDataChanged()
    }

    fun itemAt(index: Int): AnalyzerMethodsInfo = items[index]
}
