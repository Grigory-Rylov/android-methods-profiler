package com.github.grishberg.profiler.plugins.stages.methods

import com.github.grishberg.profiler.plugins.stages.WrongStage
import javax.swing.table.AbstractTableModel

class MethodsWithStageModel : AbstractTableModel() {
    private val columnNames = arrayOf("Current Stage", "Method name", "Global time", "Thread time", "Valid Stage")
    val columnClass = arrayOf<Class<*>>(
        String::class.java,
        String::class.java,
        Double::class.java,
        Double::class.java,
        String::class.java
    )
    private val items = mutableListOf<WrongStage>()

    override fun getRowCount(): Int = items.size
    override fun getColumnCount(): Int = columnClass.size
    override fun getColumnName(column: Int) = columnNames[column]
    override fun getColumnClass(columnIndex: Int) = columnClass[columnIndex]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val currentElement = items[rowIndex]
        val method = currentElement.method
        return when (columnIndex) {
            1 -> method.name
            2 -> method.globalEndTimeInMillisecond - method.globalStartTimeInMillisecond
            3 -> method.threadEndTimeInMillisecond - method.threadStartTimeInMillisecond
            4 -> currentElement.correctStage ?: "unknown"
            else -> currentElement.currentStage ?: ""
        }
    }

    fun setItems(newItems: List<WrongStage>) {
        items.clear()
        items.addAll(newItems)
        fireTableDataChanged()
    }

    fun itemAt(index: Int): WrongStage = items[index]
}
