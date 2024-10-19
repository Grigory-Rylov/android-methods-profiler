package com.github.grishberg.profiler.plugins.stages.constructors

import com.github.grishberg.profiler.plugins.stages.WrongStage
import javax.swing.table.DefaultTableModel
import kotlin.math.roundToInt

class EarlyConstructorsTableModel : DefaultTableModel(arrayOf("Method name", "Start time", "Duration"), 0) {
    var methodDataList: List<WrongStage> = emptyList()
        private set

    fun setData(newValues: List<WrongStage>) {
        methodDataList = newValues
    }

    override fun getColumnClass(columnIndex: Int): Class<*> = String::class.java

    override fun getRowCount(): Int {
        return if (methodDataList == null) 0 else methodDataList.size
    }

    override fun getValueAt(row: Int, column: Int): Any {
        val methodData = methodDataList[row]
        val method = methodData.method
        return when (column) {
            0 -> method.name
            1 -> "%.3f".format(method.globalEndTimeInMillisecond - method.globalStartTimeInMillisecond)
            2 -> {
                String.format("%03f", method.globalStartTimeInMillisecond)
            }

            else -> throw IllegalStateException()
        }
    }
}
