package com.github.grishberg.profiler.chart

import com.github.grishberg.profiler.core.ProfileData
import com.github.grishberg.profiler.common.colorToHex
import com.github.grishberg.profiler.common.hexToColor
import com.github.grishberg.profiler.ui.BookMarkInfo
import java.awt.Color
import java.awt.geom.Rectangle2D

class BookmarksRectangle(
    var name: String,
    var color: Color,
    val threadTimeStart: kotlin.Double,
    val threadTimeEnd: kotlin.Double,
    val globalTimeTimeStart: kotlin.Double,
    val globalTimeTimeEnd: kotlin.Double,
    private val level: Int,
    private val threadId: Int,
    newHeight: kotlin.Double = -1.0,
    isThreadMode: Boolean = false
) : Rectangle2D.Double(), Comparable<BookmarksRectangle> {
    var headerColor = Color(color.rgb)
        private set
    var headerTitleColor = titleColor()
        private set

    var shouldShow: Boolean = true
        private set

    init {
        if (newHeight > 0) {
            setup(newHeight, isThreadMode)
        }
    }

    private fun titleColor(): Color {
        val colorWithoutAlpha = Color(color.rgb)
        val y =
            (299 * colorWithoutAlpha.red + 587 * colorWithoutAlpha.green + 114 * colorWithoutAlpha.blue) / 1000.toDouble()
        return if (y >= 128) Color.black else Color.white
    }

    /**
     * Should call after creating Bookmark.
     */
    fun setup(height: kotlin.Double, isThreadMode: Boolean) {
        if (isThreadMode) {
            setRect(threadTimeStart, 0.0, threadTimeEnd - threadTimeStart, height)
        } else {
            setRect(globalTimeTimeStart, 0.0, globalTimeTimeEnd - globalTimeTimeStart, height)
        }
    }

    fun switchThread(selectedThreadId: Int, threadTimeMode: Boolean) {
        if (threadTimeMode) {
            setRect(threadTimeStart, 0.0, threadTimeEnd - threadTimeStart, height)
            shouldShow = selectedThreadId == threadId
            return
        }
        setRect(globalTimeTimeStart, 0.0, globalTimeTimeEnd - globalTimeTimeStart, height)
        shouldShow = true
    }

    fun isForElement(profileData: ProfileData): Boolean {
        return profileData.globalStartTimeInMillisecond == globalTimeTimeStart
                && profileData.globalEndTimeInMillisecond == globalTimeTimeEnd
                && profileData.level == level
    }

    override fun compareTo(other: BookmarksRectangle): Int {
        return globalTimeTimeStart.compareTo(other.globalTimeTimeStart)
    }

    internal fun toBookmarksSerializableModel(): BookmarksSerializableModel {
        return BookmarksSerializableModel(
            name = name,
            color = colorToHex(color),
            threadTimeStart = threadTimeStart,
            threadTimeEnd = threadTimeEnd,
            globalTimeTimeStart = globalTimeTimeStart,
            globalTimeTimeEnd = globalTimeTimeEnd,
            threadId = threadId,
            level = level
        )
    }

    override fun toString(): String {
        return "BookmarksRectangle[name = $name, " +
                "globalTimeTimeStart = $globalTimeTimeStart," +
                "globalTimeTimeEnd = $globalTimeTimeEnd," +
                "threadTimeStart = $threadTimeStart," +
                "threadTimeEnd = $threadTimeEnd]"
    }

    fun updateNameAndColor(result: BookMarkInfo) {
        name = result.title
        color = result.color
        headerColor = Color(color.rgb)
        headerTitleColor = titleColor()
    }

    companion object {
        internal fun fromBookmarksSerializableModel(model: BookmarksSerializableModel): BookmarksRectangle {
            return BookmarksRectangle(
                name = model.name,
                color = hexToColor(model.color),
                threadTimeStart = model.threadTimeStart,
                threadTimeEnd = model.threadTimeEnd,
                globalTimeTimeStart = model.globalTimeTimeStart,
                globalTimeTimeEnd = model.globalTimeTimeEnd,
                threadId = model.threadId,
                level = model.level
            )
        }
    }

    internal data class BookmarksSerializableModel(
        val name: String,
        val color: String,
        val threadTimeStart: kotlin.Double,
        val threadTimeEnd: kotlin.Double,
        val globalTimeTimeStart: kotlin.Double,
        val globalTimeTimeEnd: kotlin.Double,
        val level: Int,
        val threadId: Int
    )
}
