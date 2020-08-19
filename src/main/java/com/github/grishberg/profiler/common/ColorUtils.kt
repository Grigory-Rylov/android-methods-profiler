package com.github.grishberg.profiler.common

import java.awt.Color

fun hexToColor(colorInHex: String): Color {
    val colorInt = Integer.parseInt(colorInHex, 16)
    return Color(colorInt, true)
}

fun colorToHex(color: Color): String {
    return "%x%x%x%x".format(color.alpha, color.red, color.green, color.blue)
}
