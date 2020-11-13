package com.github.grishberg.profiler.chart.preview

import com.github.grishberg.android.profiler.core.AnalyzerResult
import com.github.grishberg.android.profiler.core.ProfileData
import com.github.grishberg.profiler.chart.highlighting.MethodsColor
import com.github.grishberg.profiler.chart.theme.Palette
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage

class PreviewImageFactoryImpl(
    private val palette: Palette,
    private val methodsColor: MethodsColor
) : PreviewImageFactory {
    private val methodHeight = 1

    override fun createPreview(
        width: Int,
        height: Int,
        result: AnalyzerResult,
        threadId: Int,
        isThreadTime: Boolean
    ): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

        val g = image.createGraphics()
        drawMethods(result, threadId, isThreadTime, g, width, height)
        return image
    }

    private fun drawMethods(
        result: AnalyzerResult,
        threadId: Int,
        isThreadTime: Boolean,
        g: Graphics2D, width: Int, height: Int
    ) {
        g.color = palette.traceBackgroundColor
        g.scale(1.0, 0.5)
        g.fillRect(0, 0, width, height)

        val maxRight = if (isThreadTime)
            result.threadTimeBounds.getValue(threadId).maxTime
        else
            result.globalTimeBounds.getValue(threadId).maxTime

        val widthFactor: Double = maxRight / width

        val methods = result.data.getValue(threadId)

        for (method in methods) {
            g.color = getColorForMethod(method)
            val top = method.level * methodHeight
            val bottom = top + methodHeight

            val left = if (isThreadTime)
                method.threadStartTimeInMillisecond
            else
                method.globalStartTimeInMillisecond

            val right = if (isThreadTime)
                method.threadEndTimeInMillisecond
            else
                method.globalEndTimeInMillisecond
            val w = (right - left) * widthFactor
            val h = bottom - top

            val l = left / widthFactor

            g.fillRect(l.toInt(), top, w.toInt(), h)
        }
    }

    private fun getColorForMethod(method: ProfileData): Color {
        return methodsColor.getColorForMethod(method.name)
    }
}
