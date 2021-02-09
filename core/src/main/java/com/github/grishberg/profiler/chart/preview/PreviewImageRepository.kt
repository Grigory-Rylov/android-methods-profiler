package com.github.grishberg.profiler.chart.preview

import com.github.grishberg.android.profiler.core.AnalyzerResult
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.CoroutinesDispatchers
import com.github.grishberg.profiler.common.settings.SettingsFacade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage

const val PREVIEW_IMAGE_HEIGHT = 64
const val PREVIEW_IMAGE_WIDTH = 700

class PreviewImageRepository(
    private val imageFactory: PreviewImageFactory,
    private val settings: SettingsFacade,
    private val logger: AppLogger,
    private val coroutineScope: CoroutineScope,
    private val dispatchers: CoroutinesDispatchers
) {
    private val cache = mutableMapOf<ThreadInfoKey, BufferedImage>()
    private var traceData: AnalyzerResult? = null

    fun clear() {
        cache.clear()
    }

    fun setAnalyzerResult(newTrace: AnalyzerResult) {
        cache.clear()
        traceData = newTrace
    }

    fun preparePreview(
        threadId: Int,
        previewType: PreviewType,
        callback: Callback
    ): BufferedImage? {
        val result = traceData ?: return null
        val key = ThreadInfoKey(threadId, previewType)
        val image = cache[key]
        image?.let {
            return image
        }

        coroutineScope.launch {
            val image = withContext(coroutineScope.coroutineContext + dispatchers.worker) {
                imageFactory.createPreview(PREVIEW_IMAGE_WIDTH, PREVIEW_IMAGE_HEIGHT, result, threadId, previewType)
            }

            cache[key] = image
            callback.onImageReady(image, threadId)
        }
        return null
    }

    interface Callback {
        fun onImageReady(image: BufferedImage, threadId: Int)
    }

    private data class ThreadInfoKey(val threadId: Int, val previewType: PreviewType)
}
