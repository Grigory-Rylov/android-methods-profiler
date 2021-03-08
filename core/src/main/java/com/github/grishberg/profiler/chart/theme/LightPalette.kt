package com.github.grishberg.profiler.chart.theme

import com.github.grishberg.profiler.ui.theme.Palette
import java.awt.Color
import javax.swing.UIManager

class LightPalette : Palette {
    override val traceBackgroundColor = Color.WHITE
    override val previewBackgroundColor = UIManager.getColor("Panel.background")
}
