package com.github.grishberg.profiler.chart

import com.github.grishberg.profiler.ui.dialogs.NewBookmarkDialog
import javax.swing.JMenuItem
import javax.swing.JPopupMenu

class BookmarkPopupMenu(
    private val chart: CallTracePanel,
    private val newBookmarkDialog: NewBookmarkDialog,
    private val selectedBookmark: BookmarksRectangle
) : JPopupMenu() {
    private val editMenuItem = JMenuItem("Edit")
    private val deleteMenuItem = JMenuItem("Delete")

    init {
        add(editMenuItem)
        add(deleteMenuItem)
        editMenuItem.addActionListener {
            newBookmarkDialog.showEditBookmarkDialog(selectedBookmark)
            val result = newBookmarkDialog.bookMarkInfo
            if (result != null) {
                chart.updateBookmark(selectedBookmark, result)
            }
        }
        deleteMenuItem.addActionListener {
            chart.removeBookmark(selectedBookmark)
        }
    }
}
