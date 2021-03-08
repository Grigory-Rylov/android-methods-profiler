package com.github.grishberg.profiler.chart.theme

import com.github.grishberg.profiler.ui.theme.Palette
import java.awt.Color
import javax.swing.UIManager

class DarkPalette : Palette {
    override val traceBackgroundColor = Color(65, 65, 65)
    override val previewBackgroundColor = UIManager.getColor("Panel.background")
}
