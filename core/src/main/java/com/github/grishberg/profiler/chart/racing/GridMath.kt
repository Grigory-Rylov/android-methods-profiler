package com.github.grishberg.profiler.chart.racing

import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

/** Выбор «красивого» шага сетки и делений. Чистые функции. */
object GridMath {
    private const val LN10 = 2.302585092994046

    /**
     * Наименьший шаг вида d·10^n, d ∈ {1,2,5}, что step·pxPerUnit ≥ minPx.
     * Степень подбирается по log10 с запасом к floating-point: берутся кандидаты
     * {1,2,5}×{10^f, 10^(f+1)} — этого достаточно, чтобы покрыть ±1 степень
     * от погрешности log/pow.
     */
    fun niceStep(pxPerUnit: Double, minPx: Double = 70.0): Double {
        require(pxPerUnit > 0.0 && pxPerUnit.isFinite()) { "pxPerUnit must be > 0" }
        val raw = minPx / pxPerUnit
        val f = floor(ln(raw) / LN10 - 1e-9).toInt()
        val eps = raw * 1e-9
        var best = Double.MAX_VALUE
        for (e in f..f + 1) {
            val p = 10.0.pow(e)
            for (d in intArrayOf(1, 2, 5)) {
                val c = d * p
                if (c + eps >= raw && c < best) best = c
            }
        }
        if (best == Double.MAX_VALUE) best = 10.0.pow(f + 2.0) // страховка от краёв log
        return best
    }

    /** Ведущая цифра «красивого» числа (1, 2 или 5). */
    fun leadingDigit(step: Double): Int {
        val e = floor(ln(step) / LN10 + 1e-9).toInt()
        var d = (step / 10.0.pow(e) + 0.05).toInt()
        if (d >= 10) d = 1
        return d.coerceAtLeast(1)
    }

    /** 5 minor-делений для d ∈ {1,5}, 4 для d = 2. */
    fun minorDivisions(step: Double): Int = if (leadingDigit(step) == 2) 4 else 5
}
