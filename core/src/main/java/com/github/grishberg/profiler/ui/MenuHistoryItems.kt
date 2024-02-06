package com.github.grishberg.profiler.ui

import com.github.grishberg.profiler.common.settings.SettingsFacade
import java.io.File
import java.util.LinkedList
import javax.swing.JMenu
import javax.swing.JMenuItem

private const val MAX_HISTORY_SIZE = 10
private const val FILE_MENU_ITEMS_COUNT_BEFORE_HISTORY = 14


class MenuHistoryItems(
    private val menu: JMenu,
    private val settings: SettingsFacade,
    private val openTraceFileDelegate: OpenTraceFileDelegate
) {
    private val recentFiles = LinkedList<String>()
    private var currentOpenedFile: File? = null

    init {
        recentFiles.addAll(settings.recentFiles)
        for (recentFileName in recentFiles) {
            val recentFile = File(recentFileName)
            val recentFileMenuItem = JMenuItem(createShortFileName(recentFile))
            recentFileMenuItem.addActionListener { openTraceFileDelegate.openTraceFile(recentFile) }
            menu.add(recentFileMenuItem)
        }
    }

    fun addToFileHistory(file: File) {
        recentFiles.add(0, file.absolutePath)
        val size: Int = recentFiles.size
        if (size > MAX_HISTORY_SIZE) {
            for (i in MAX_HISTORY_SIZE until size) {
                recentFiles.removeLast()
            }
        }
        settings.recentFiles = recentFiles

        if (currentOpenedFile != null) {
            addHistoryMenuItem(currentOpenedFile!!)
        }
        currentOpenedFile = file
    }

    private fun addHistoryMenuItem(file: File) {
        val recentFileMenuItem = JMenuItem(createShortFileName(file))
        recentFileMenuItem.addActionListener { openTraceFileDelegate.openTraceFile(file) }
        menu.insert(recentFileMenuItem, FILE_MENU_ITEMS_COUNT_BEFORE_HISTORY)

        val recentItemsCount = menu.itemCount - FILE_MENU_ITEMS_COUNT_BEFORE_HISTORY
        if (recentItemsCount > MAX_HISTORY_SIZE) {
            val delta = recentItemsCount - MAX_HISTORY_SIZE
            for (i in delta downTo 1) {
                menu.remove(FILE_MENU_ITEMS_COUNT_BEFORE_HISTORY + MAX_HISTORY_SIZE + delta - 1)
            }
        }
    }

    private fun createShortFileName(recentFile: File): String {
        return recentFile.name
    }

    fun remove(currentOpenedFile: File) {
        recentFiles.remove(currentOpenedFile.absolutePath)
        settings.recentFiles = recentFiles
        for (i in 0 until menu.itemCount) {
            if (menu.getItem(i).text == currentOpenedFile.name) {
                menu.remove(i)
                return
            }
        }
    }
}
