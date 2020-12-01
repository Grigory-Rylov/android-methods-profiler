package com.github.grishberg.profiler.chart.preview

import com.github.grishberg.android.profiler.core.AnalyzerResult
import com.github.grishberg.android.profiler.core.ProfileData
import com.github.grishberg.profiler.chart.Bookmarks
import com.github.grishberg.profiler.chart.highlighting.MethodsColor
import com.github.grishberg.profiler.ui.theme.Palette
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import kotlin.math.ceil

class PreviewImageFactoryImpl(
    private val palette: Palette,
    private val methodsColor: MethodsColor,
    private val bookmarks: Bookmarks
) : PreviewImageFactory {
    private val methodHeight = 2

    override fun createPreview(
        width: Int,
        height: Int,
        result: AnalyzerResult,
        threadId: Int,
        previewType: PreviewType
    ): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

        val g = image.createGraphics()
        drawMethods(result, threadId, previewType, g, width, height)
        return image
    }

    private fun drawMethods(
        result: AnalyzerResult,
        threadId: Int,
        previewType: PreviewType,
        g: Graphics2D, width: Int, height: Int
    ) {
        g.color = palette.traceBackgroundColor
        g.fillRect(0, 0, width, height)

        g.scale(1.0, 0.5)
        val maxRight = when (previewType) {
            PreviewType.PREVIEW_GLOBAL -> result.globalTimeBounds.getValue(threadId).maxTime
            PreviewType.PREVIEW_THREAD -> result.threadTimeBounds.getValue(threadId).maxTime
            PreviewType.THREAD_SWITCHER -> result.globalTimeBounds.getValue(result.mainThreadId).maxTime
        }

        val widthFactor: Double = maxRight / width

        val methods = result.data.getValue(threadId)

        for (method in methods) {
            g.color = getColorForMethod(method)
            val top = method.level * methodHeight
            val bottom = top + methodHeight

            val left = when (previewType) {
                PreviewType.PREVIEW_GLOBAL -> method.globalStartTimeInMillisecond
                PreviewType.PREVIEW_THREAD -> method.threadStartTimeInMillisecond
                PreviewType.THREAD_SWITCHER -> method.globalStartTimeInMillisecond
            }

            val right = when (previewType) {
                PreviewType.PREVIEW_THREAD -> method.threadEndTimeInMillisecond
                PreviewType.PREVIEW_GLOBAL -> method.globalEndTimeInMillisecond
                PreviewType.THREAD_SWITCHER -> method.globalEndTimeInMillisecond
            }

            val w = (right - left) / widthFactor
            val h = bottom - top

            val l = left / widthFactor

            g.fillRect(l.toInt(), top, w.toInt(), h)
        }

        for (i in bookmarks.size() - 1 downTo 0) {
            val bookmark = bookmarks.bookmarkAt(i)
            val left = when (previewType) {
                PreviewType.PREVIEW_THREAD -> bookmark.threadTimeStart
                PreviewType.PREVIEW_GLOBAL -> bookmark.globalTimeTimeStart
                PreviewType.THREAD_SWITCHER -> bookmark.globalTimeTimeStart
            }

            val right = when (previewType) {
                PreviewType.PREVIEW_THREAD -> bookmark.threadTimeEnd
                PreviewType.PREVIEW_GLOBAL -> bookmark.globalTimeTimeEnd
                PreviewType.THREAD_SWITCHER -> bookmark.globalTimeTimeEnd
            }

            val w = (right - left) / widthFactor
            val h = result.maxLevel * methodHeight

            val l = left / widthFactor
            g.color = bookmark.color
            g.fillRect(l.toInt(), 0, ceil(w).toInt(), h)
        }
    }

    private fun getColorForMethod(method: ProfileData): Color {
        return methodsColor.getColorForMethod(method.name)
    }
}
