package com.github.grishberg.profiler.chart.preview

import com.github.grishberg.android.profiler.core.AnalyzerResult
import java.awt.image.BufferedImage

interface PreviewImageFactory {
    fun createPreview(
        width: Int, height: Int,
        result: AnalyzerResult,
        threadId: Int,
        previewType: PreviewType
    ): BufferedImage
}
