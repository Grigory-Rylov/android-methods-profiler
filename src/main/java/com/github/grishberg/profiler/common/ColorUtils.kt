package com.github.grishberg.profiler.common

import java.awt.Color

fun hexToColor(colorInHex: String): Color {
    val colorInt = Integer.parseInt(colorInHex, 16)
    return Color(colorInt, true)
}

fun hexToColor(colorInHex: String?, default: Color): Color {
    if (colorInHex == null) {
        return default
    }
    val colorInt = Integer.parseInt(colorInHex, 16)
    return Color(colorInt, false)
}

fun Color.toHex(): String {
    return "%x%x%x".format(this.red, this.green, this.blue)
}

fun colorToHex(color: Color): String {
    return "%x%x%x%x".format(color.alpha, color.red, color.green, color.blue)
}
