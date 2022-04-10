package com.github.grishberg.profiler.plugins.stages.methods

import com.github.grishberg.profiler.core.ProfileData
import com.github.grishberg.expandabletree.AbstractTreeTableModel
import com.github.grishberg.expandabletree.TreeTableModel
import com.github.grishberg.profiler.plugins.stages.WrongStage

class MethodsWithStageModel(roots: List<WrongStage>) : AbstractTreeTableModel(roots), TreeTableModel {
    private val columnNames = arrayOf("Method name", "Current Stage", "Global time", "Thread time", "Valid Stage")
    val columnClass = arrayOf<Class<*>>(
        TreeTableModel::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java
    )

    fun setValues(newValues: List<WrongStage>) {
        root = newValues
    }

    //
    // The TreeModel interface
    //
    override fun getChildCount(node: Any): Int {
        if (node is List<*>) {
            return node.size
        }
        if (node is WrongStage) {
            return node.method.children.size
        }
        return if (node is ProfileData) {
            node.children.size
        } else 0
    }

    override fun getChild(node: Any, i: Int): Any? {
        if (node is List<*>) {
            return node[i]
        }
        if (node is WrongStage) {
            return node.method.children[i]
        }
        return if (node is ProfileData) {
            node.children[i]
        } else null
    }

    // The superclass's implementation would work, but this is more efficient.
    override fun isLeaf(node: Any?): Boolean {
        //return getContribution(node).isFile();
        return super.isLeaf(node)
    }

    //
    //  The TreeTableNode interface.
    //
    override fun getColumnCount(): Int = columnClass.size
    override fun getColumnName(column: Int) = columnNames[column]
    override fun getColumnClass(columnIndex: Int) = columnClass[columnIndex]

    override fun getValueAt(node: Any, column: Int): Any? {
        if (node is WrongStage) {
            val method = node.method
            return when (column) {
                0 -> method.name
                1 -> node.currentStage ?: ""
                2 -> "%.3f".format(method.globalEndTimeInMillisecond - method.globalStartTimeInMillisecond)
                3 -> "%.3f".format(method.threadEndTimeInMillisecond - method.threadStartTimeInMillisecond)
                4 -> node.correctStage ?: "unknown"
                else -> throw IllegalStateException()
            }
        }
        if (node is ProfileData) {
            return when (column) {
                0 -> node.name
                1 -> ""
                2 -> "%.3f".format(node.globalEndTimeInMillisecond - node.globalStartTimeInMillisecond)
                3 -> "%.3f".format(node.threadEndTimeInMillisecond - node.threadStartTimeInMillisecond)
                4 -> ""
                else -> throw IllegalStateException()
            }
        }
        return null
    }
}
