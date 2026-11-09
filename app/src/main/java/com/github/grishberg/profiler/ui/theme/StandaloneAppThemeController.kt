package com.github.grishberg.profiler.ui.theme

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLightLaf
import com.github.grishberg.profiler.chart.theme.DarkPalette
import com.github.grishberg.profiler.chart.theme.LightPalette
import com.github.grishberg.profiler.common.settings.SettingsFacade
import java.awt.Frame
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.SwingUtilities
import javax.swing.UIManager

enum class Theme(val palette: Palette) {
    LIGHT(LightPalette()),
    DARK(DarkPalette())
}

class StandaloneAppThemeController(
    private val settings: SettingsFacade
) : ThemeController {

    override val palette: PaletteProxy
    private var theme: Theme
    private val themeSwitchCallbacks = mutableListOf<Runnable>()

    init {
        val themeName = settings.theme
        theme = Theme.valueOf(themeName)
        palette = PaletteProxy(theme.palette)
    }

    override fun applyTheme() {
        when (theme) {
            Theme.DARK -> setDarkTheme(true)
            else -> setLiteTheme(true)
        }
    }

    override fun addToMenu(menu: JMenuBar) {
        val menuTheme = JMenu("Themes")

        val lite = JMenuItem()
        lite.action = object : AbstractAction("Light") {
            override fun actionPerformed(e: ActionEvent) {
                setLiteTheme()
            }
        }
        menuTheme.add(lite)

        val jmarsDark = JMenuItem()
        jmarsDark.action = object : AbstractAction("Dark") {
            override fun actionPerformed(e: ActionEvent) {
                setDarkTheme()
            }
        }
        menuTheme.add(jmarsDark)

        menu.add(menuTheme)
    }

    override fun addThemeSwitchedCallback(callback: Runnable) {
        themeSwitchCallbacks.add(callback)
    }

    override fun removeThemeSwitchedCallback(callback: Runnable) {
        themeSwitchCallbacks.remove(callback)
    }

    private fun setDarkTheme(set: Boolean = false) {
        theme = Theme.DARK
        palette.currentPalette = DarkPalette()
        applyLookAndFeel(dark = true)
        if (set) return
        settings.theme = theme.name
        themeSwitchCallbacks.forEach { it.run() }
    }

    private fun setLiteTheme(set: Boolean = false) {
        theme = Theme.LIGHT
        palette.currentPalette = LightPalette()
        applyLookAndFeel(dark = false)
        if (set) return
        settings.theme = theme.name
        themeSwitchCallbacks.forEach { it.run() }
    }

    /** Тёмный/светлый FlatLaf на всё приложение + перерисовка открытых окон. */
    private fun applyLookAndFeel(dark: Boolean) {
        val laf = if (dark) FlatDarkLaf() else FlatLightLaf()
        UIManager.setLookAndFeel(laf)
        for (frame in Frame.getFrames()) {
            SwingUtilities.updateComponentTreeUI(frame)
            frame.invalidate()
            frame.validate()
            frame.repaint()
        }
    }
}
