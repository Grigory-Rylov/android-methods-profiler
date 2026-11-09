package com.github.grishberg.profiler.chart.racing

import com.github.grishberg.profiler.core.AnalyzerResult
import com.github.grishberg.profiler.core.ProfileData
import kotlin.math.max

/**
 * Данные racing-вью для одного потока: полосы вызовов + размеры мира
 * + диапазон «вписать» (bounds потока, как fitZoom основного графика).
 */
class RacingViewData(
    val rows: List<CallRow>,
    val worldWidth: Double,
    val worldHeight: Double,
    val fitStart: Double,
    val fitEnd: Double,
    /** Реальное время = мировое X минус этот offset (см. [ViewState.timeOffset]). */
    val timeOffset: Double,
    val threadName: String,
)

/** Маппинг ProfileData одного потока в CallRow'и racing-панели. */
object RacingViewDataFactory {

    fun create(result: AnalyzerResult, threadId: Int, isThreadTime: Boolean): RacingViewData {
        val methods = result.data[threadId] ?: emptyList()
        val bounds = (if (isThreadTime) result.threadTimeBounds else result.globalTimeBounds)[threadId]

        var minStart = Double.MAX_VALUE
        var maxEnd = -Double.MAX_VALUE
        for (record in methods) {
            val start = if (isThreadTime) record.threadStartTimeInMillisecond
            else record.globalStartTimeInMillisecond
            val end = if (isThreadTime) record.threadEndTimeInMillisecond
            else record.globalEndTimeInMillisecond
            if (start < minStart) minStart = start
            if (end > maxEnd) maxEnd = end
        }
        if (methods.isEmpty()) {
            minStart = 0.0
            maxEnd = 0.0
        }

        // ViewState живёт в мировом X ∈ [0, worldWidth]; у thread-time бывает
        // min < 0 — сдвигаем весь набор в неотрицательную область, таймлайн
        // вычтет сдвиг из подписей тиков.
        val shift = if (minStart < 0.0) -minStart else 0.0

        val rows = methods.map { record ->
            val start = if (isThreadTime) record.threadStartTimeInMillisecond
            else record.globalStartTimeInMillisecond
            val end = if (isThreadTime) record.threadEndTimeInMillisecond
            else record.globalEndTimeInMillisecond
            CallRow(
                depth = record.level,
                name = record.name,
                startMs = start + shift,
                durMs = max(0.0, end - start),
                data = record,
            )
        }

        val boundsMin = bounds?.minTime ?: minStart
        val boundsMax = bounds?.maxTime ?: maxEnd
        val worldWidth = max(maxEnd, boundsMax) + shift
        val worldHeight = (result.maxLevel + 1).coerceAtLeast(1) * VirtualSpacePanel.ROW_H
        val threadName = result.threads.find { it.threadId == threadId }?.name ?: "thread $threadId"

        return RacingViewData(
            rows = rows,
            worldWidth = max(worldWidth, 1.0),
            worldHeight = max(worldHeight, VirtualSpacePanel.ROW_H),
            fitStart = (boundsMin + shift).coerceIn(0.0, max(0.0, maxEnd + shift)),
            fitEnd = (boundsMax + shift).coerceAtLeast(0.0),
            timeOffset = shift,
            threadName = threadName,
        )
    }
}
