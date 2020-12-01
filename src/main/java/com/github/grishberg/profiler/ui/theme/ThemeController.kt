package com.github.grishberg.profiler.ui.theme

import javax.swing.JMenuBar

interface ThemeController {
    val palette: Palette
    fun applyTheme()
    fun addToMenu(menu: JMenuBar)
    fun addThemeSwitchedCallback(callback: Runnable)
    fun removeThemeSwitchedCallback(callback: Runnable)
}
