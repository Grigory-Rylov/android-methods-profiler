package com.github.grishberg.profiler.chart.preview

import com.github.grishberg.android.profiler.core.AnalyzerResult
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.CoroutinesDispatchers
import com.github.grishberg.profiler.common.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage

const val PREVIEW_IMAGE_HEIGHT = 32
const val PREVIEW_IMAGE_WIDTH = 800

class PreviewImageRepository(
    private val imageFactory: PreviewImageFactory,
    private val settings: SettingsRepository,
    private val logger: AppLogger,
    private val coroutineScope: CoroutineScope,
    private val dispatchers: CoroutinesDispatchers
) {
    private val cache = mutableMapOf<ThreadInfoKey, BufferedImage>()

    fun resetCache() {
        cache.clear()
    }

    fun preparePreview(
        width: Int,
        result: AnalyzerResult,
        threadId: Int,
        isThreadTime: Boolean,
        callback: Callback
    ) {
        val key = ThreadInfoKey(threadId, isThreadTime)
        val image = cache[key]
        image?.let {
            callback.onImageReady(image, threadId, isThreadTime)
            return
        }

        coroutineScope.launch {
            val image = withContext(coroutineScope.coroutineContext + dispatchers.worker) {
                imageFactory.createPreview(PREVIEW_IMAGE_WIDTH, PREVIEW_IMAGE_HEIGHT, result, threadId, isThreadTime)
            }

            callback.onImageReady(image, threadId, isThreadTime)
        }
    }

    interface Callback {
        fun onImageReady(image: BufferedImage, threadId: Int, isThreadTime: Boolean)
    }

    private data class ThreadInfoKey(val threadId: Int, val isThreadTime: Boolean)
}
