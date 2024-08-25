package com.github.grishberg.profiler.ui

import com.github.grishberg.profiler.chart.theme.DarkPalette
import com.github.grishberg.profiler.chart.theme.LightPalette
import com.github.grishberg.profiler.ui.theme.Palette
import com.github.grishberg.profiler.ui.theme.ThemeController
import com.intellij.ui.JBColor
import javax.swing.JMenuBar

class PluginThemeController : ThemeController {
    private val dark = !JBColor.isBright()

    override val palette: Palette = if (dark) DarkPalette() else LightPalette()

    override fun applyTheme() = Unit

    override fun addToMenu(menu: JMenuBar) = Unit

    override fun addThemeSwitchedCallback(callback: Runnable) = Unit

    override fun removeThemeSwitchedCallback(callback: Runnable) = Unit
}
