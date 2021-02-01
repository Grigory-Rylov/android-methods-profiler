package com.github.grishberg.profiler.ui.theme

import com.github.grishberg.profiler.chart.theme.DarkPalette
import com.github.grishberg.profiler.chart.theme.LightPalette
import com.github.grishberg.profiler.common.settings.SettingsRepository
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem

const val SETTINGS_THEME = "Main.theme"

enum class Theme(val palette: Palette) {
    LIGHT(DarkPalette()),
    DARK(LightPalette())
}

class StandaloneAppThemeController(
    private val settings: SettingsRepository
) : ThemeController {

    override val palette: PaletteProxy
    private var theme: Theme
    private val themeSwitchCallbacks = mutableListOf<Runnable>()

    init {
        val themeName = settings.getStringValueOrDefault(SETTINGS_THEME, Theme.LIGHT.name)
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
        if (set) {
            //TODO: UIManager.setLookAndFeel(FlatDarculaLaf())
            return
        }
        //TODO: UIManager.setLookAndFeel(FlatDarculaLaf())
        //TODO: SwingUtilities.updateComponentTreeUI(owner)
        settings.setStringValue(SETTINGS_THEME, theme.name)
        themeSwitchCallbacks.forEach { it.run() }
    }

    private fun setLiteTheme(set: Boolean = false) {
        theme = Theme.LIGHT
        palette.currentPalette = LightPalette()
        if (set) {
            //TODO: UIManager.setLookAndFeel(FlatLightLaf())
            return
        }
        //TODO: UIManager.setLookAndFeel(FlatLightLaf())
        //TODO: SwingUtilities.updateComponentTreeUI(owner)
        settings.setStringValue(SETTINGS_THEME, theme.name)
        themeSwitchCallbacks.forEach { it.run() }
    }
}
