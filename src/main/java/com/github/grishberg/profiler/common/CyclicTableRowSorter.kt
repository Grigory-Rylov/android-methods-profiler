package com.github.grishberg.profiler.common

import javax.swing.SortOrder
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter

class CyclicTableRowSorter(model: TableModel) : TableRowSorter<TableModel>(model) {

    override fun toggleSortOrder(column: Int) {
        val sortKeys: List<SortKey?> = sortKeys
        if (sortKeys.isNotEmpty()) {
            if (sortKeys[0]?.sortOrder === SortOrder.DESCENDING) {
                setSortKeys(null)
                return
            }
        }
        super.toggleSortOrder(column)
    }
}
