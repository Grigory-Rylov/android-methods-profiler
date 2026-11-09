package com.github.grishberg.profiler.chart.racing

import com.github.grishberg.profiler.ui.theme.Palette
import java.awt.Color

/**
 * Цвета racing-панели и таймлайн-заголовка. Мутируемый общий объект: панель,
 * заголовок и контейнер держат один экземпляр, [applyPalette] обновляет
 * поля на месте при переключении темы — пересоздавать компоненты не нужно.
 */
data class RacingColorScheme(
    var background: Color,
    var stripe: Color,
    var gridMinor: Color,
    var gridMajor: Color,
    var gridAccent: Color,
    var headerBackground: Color,
    var headerText: Color,
    var headerTick: Color,
    var headerMinorTick: Color,
) {
    /**
     * Пересобрать цвета из палитры YAMP ([Palette.traceBackgroundColor]) — racing-вью
     * следует теме приложения независимо от L&F (на aqua панель осталась бы белой).
     */
    fun applyPalette(palette: Palette) {
        val bg = palette.traceBackgroundColor
        val dark = bg.red * 0.299 + bg.green * 0.587 + bg.blue * 0.114 < 128
        val fg = if (dark) DARK.headerText else Color(0x303030)

        background = bg
        stripe = if (dark) blend(bg, Color.BLACK, 0.35f) else blend(bg, Color.BLACK, 0.05f)
        gridMinor = blend(bg, fg, 0.10f)
        gridMajor = blend(bg, fg, 0.22f)
        gridAccent = if (dark) DARK.gridAccent else blend(bg, fg, 0.55f)
        headerBackground = bg
        headerText = fg
        headerTick = blend(bg, fg, 0.35f)
        headerMinorTick = blend(bg, fg, 0.15f)
    }

    companion object {
        /** Дефолт компонента — тёмная схема. */
        val DARK = RacingColorScheme(
            background = Color(0x14151A),
            stripe = Color(0x191A22),
            gridMinor = Color(0x22242C),
            gridMajor = Color(0x333744),
            gridAccent = Color(0x4D78CC),
            headerBackground = Color(0x26272E),
            headerText = Color(0xB8BEC9),
            headerTick = Color(0x333744),
            headerMinorTick = Color(0x2D303A),
        )

        fun forPalette(palette: Palette): RacingColorScheme = DARK.copy().apply { applyPalette(palette) }

        fun blend(from: Color, to: Color, fraction: Float): Color = Color(
            (from.red + (to.red - from.red) * fraction).toInt().coerceIn(0, 255),
            (from.green + (to.green - from.green) * fraction).toInt().coerceIn(0, 255),
            (from.blue + (to.blue - from.blue) * fraction).toInt().coerceIn(0, 255),
        )
    }
}
