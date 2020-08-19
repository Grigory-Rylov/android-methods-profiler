package com.github.grishberg.profiler.chart

import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.SortedList
import com.github.grishberg.profiler.common.settings.SettingsRepository
import com.github.grishberg.profiler.ui.BookMarkInfo
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import java.io.*
import java.lang.reflect.Type


private const val TAG = "Bookmarks"
private const val BOOKMARKS_DIR = "bookmarks"
const val BOOKMARK_EXTENSION = "bookmark"

class Bookmarks {
    private val gson = GsonBuilder().enableComplexMapKeySerialization().setPrettyPrinting().create()

    private val bookmarkFileName: String?
    private val settings: SettingsRepository

    @JvmField
    val bookmarks = SortedList<BookmarksRectangle>()
    private val logger: AppLogger

    @JvmField
    var currentSelectedBookmark: BookmarksRectangle? = null

    /**
     * Try to read bookmarks from "bookmarks" folder.
     *
     * @param traceFileName
     * @param settings
     * @param logger
     */
    constructor(traceFileName: String?, settings: SettingsRepository, logger: AppLogger) {
        bookmarkFileName = traceFileName
        this.settings = settings
        this.logger = logger
        load()
    }

    constructor(bookmarksFileName: File, settings: SettingsRepository, logger: AppLogger) {
        this.logger = logger
        readBookmarksFromFile(bookmarksFileName)
        this.settings = settings
        bookmarkFileName = bookmarksFileName.name
    }

    constructor(settings: SettingsRepository, logger: AppLogger) {
        this.settings = settings
        this.logger = logger
        bookmarkFileName = null
    }

    private fun load() {
        val currentDir = File(settings.filesDir(), "bookmarks")
        if (!currentDir.exists()) {
            return
        }
        val bookmarksFromFile = File(currentDir, "$bookmarkFileName.bookmark")
        if (!bookmarksFromFile.exists()) {
            logger.d("No bookmark file for$bookmarkFileName")
            return
        }
        readBookmarksFromFile(bookmarksFromFile)
        if (!bookmarks.isEmpty()) {
            currentSelectedBookmark = bookmarks[bookmarks.size - 1]
        }
    }

    fun setup(height: Double, isThreadTime: Boolean) {
        bookmarks.forEach {
            it.setup(height, isThreadTime)
        }
    }

    fun size(): Int {
        return bookmarks.size
    }

    operator fun iterator(): Iterator<BookmarksRectangle?> {
        return bookmarks.iterator()
    }

    fun bookmarkAt(index: Int): BookmarksRectangle {
        return bookmarks[index]
    }

    fun add(bookmark: BookmarksRectangle) {
        if (bookmarks.isEmpty()) {
            currentSelectedBookmark = bookmark
        }
        bookmarks.add(bookmark)
        save()
    }

    fun remove(bookmarksRectangle: BookmarksRectangle) {
        var selectedIndex = bookmarks.indexOf(currentSelectedBookmark)
        bookmarks.remove(bookmarksRectangle)
        if (bookmarks.isEmpty()) {
            currentSelectedBookmark = null
        }
        if (bookmarksRectangle === currentSelectedBookmark) {
            if (selectedIndex > bookmarks.size - 1) {
                selectedIndex = bookmarks.size - 1
            }
            currentSelectedBookmark = bookmarks[selectedIndex]
        }
        save()
    }

    fun removeCurrentBookmark() {
        if (currentSelectedBookmark != null) {
            var selectedIndex = bookmarks.indexOf(currentSelectedBookmark)
            bookmarks.remove(currentSelectedBookmark)
            if (selectedIndex > bookmarks.size - 1) {
                selectedIndex = bookmarks.size - 1
            }
            currentSelectedBookmark = bookmarks[selectedIndex]
        }
        if (bookmarks.isEmpty()) {
            currentSelectedBookmark = null
        }
    }

    private fun readBookmarksFromFile(saveFileName: File) {
        val listType: Type = object : TypeToken<List<BookmarksRectangle.BookmarksSerializableModel>>() {}.type
        try {
            val fileReader = FileReader(saveFileName)
            val reader = JsonReader(fileReader)
            val loadedMarkers: List<BookmarksRectangle.BookmarksSerializableModel> = gson.fromJson(reader, listType)
            bookmarks.clear()

            for (bookmark in loadedMarkers) {
                bookmarks.add(BookmarksRectangle.fromBookmarksSerializableModel(bookmark))
            }
            currentSelectedBookmark = bookmarks.first()
        } catch (e: FileNotFoundException) {
            logger.d("$TAG: there is no bookmarks file.")
        } catch (e: Exception) {
            logger.e("$TAG: Cant load bookmarks", e)
        }
    }

    fun save() {
        if (bookmarks.isEmpty()) {
            return
        }
        val currentDir = File(settings.filesDir(), BOOKMARKS_DIR)
        if (!currentDir.exists()) {
            currentDir.mkdirs()
        }
        val saveFileName = File(currentDir, "$bookmarkFileName.$BOOKMARK_EXTENSION")

        var outputStream: FileOutputStream? = null
        try {
            outputStream = FileOutputStream(saveFileName)
            val bufferedWriter = BufferedWriter(OutputStreamWriter(outputStream, "UTF-8"))

            gson.toJson(getSerializableBookmarks(), bufferedWriter)
            bufferedWriter.close()
        } catch (e: FileNotFoundException) {
            logger.e("$TAG: save bookmarks error", e)
        } catch (e: IOException) {
            logger.e("$TAG: save bookmarks error", e)
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.flush()
                    outputStream.close()
                } catch (e: IOException) {
                }
            }
        }
    }

    private fun getSerializableBookmarks(): List<BookmarksRectangle.BookmarksSerializableModel>? {
        val result = mutableListOf<BookmarksRectangle.BookmarksSerializableModel>()
        for (b in bookmarks) {
            result.add(b.toBookmarksSerializableModel())
        }
        return result
    }

    val file: File
        get() {
            val currentDir = File(settings.filesDir(), BOOKMARKS_DIR)
            return File(currentDir, "$bookmarkFileName.$BOOKMARK_EXTENSION")
        }

    fun focusPreviousBookmark(): BookmarksRectangle? {
        if (bookmarks.isEmpty()) {
            return null
        }
        var selectedIndex = bookmarks.indexOf(currentSelectedBookmark)
        selectedIndex--
        if (selectedIndex < 0) {
            selectedIndex = bookmarks.size - 1
        }
        currentSelectedBookmark = bookmarks[selectedIndex]
        return currentSelectedBookmark
    }

    fun focusNextBookmark(): BookmarksRectangle? {
        if (bookmarks.isEmpty()) {
            return null
        }
        var selectedIndex = bookmarks.indexOf(currentSelectedBookmark)
        selectedIndex++
        if (selectedIndex >= bookmarks.size) {
            selectedIndex = 0
        }
        currentSelectedBookmark = bookmarks[selectedIndex]
        return currentSelectedBookmark
    }

    fun updateBookmark(selectedBookmark: BookmarksRectangle, result: BookMarkInfo) {
        selectedBookmark.updateNameAndColor(result)
        save()
    }
}
