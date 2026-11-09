package com.github.grishberg.profiler.chart.racing

import com.github.grishberg.profiler.core.ProfileData
import java.awt.Color

/**
 * Одна «вызванная» строка racing-трейса: полоса [startMs, startMs+durMs]
 * на глубине [depth]. startMs — мировое время X (мс, с учётом ViewState.timeOffset).
 * [data] — исходный элемент трейса: цвета и hover-панель работают по нему,
 * логика идентична CallTracePanel (MethodsColor + ExtendedData).
 */
data class CallRow(
    val depth: Int,
    val name: String,
    val startMs: Double,
    val durMs: Double,
    val data: ProfileData? = null,
)
