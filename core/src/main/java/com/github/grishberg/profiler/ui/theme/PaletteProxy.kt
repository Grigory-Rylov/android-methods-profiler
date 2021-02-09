package com.github.grishberg.profiler.ui.theme

import java.awt.Color

class PaletteProxy(
    var currentPalette: Palette
) : Palette {
    override val traceBackgroundColor: Color
        get() = currentPalette.traceBackgroundColor
}
