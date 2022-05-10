package com.github.grishberg.profiler.comparator.aggregator.threads

import javax.swing.table.DefaultTableModel

class AggregatedFlameThreadListModel : DefaultTableModel() {
    private val data = mutableListOf<FlameThreadItem>()

    fun setData(threads: List<FlameThreadItem>) {
        data.clear()
        data.addAll(threads)
    }

    override fun getRowCount(): Int {
        return if (null == data) 0 else data.size
    }

    override fun getColumnCount(): Int {
        return 1
    }

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return String::class.java
    }

    override fun getColumnName(column: Int): String {
        return "Thread name"
    }

    override fun getValueAt(row: Int, column: Int): Any {
        return data[row].name
    }

    fun getThreadInfo(index: Int): FlameThreadItem {
        return data[index]
    }
}
