package com.github.grishberg.profiler.common

import java.awt.Color

fun hexToColor(colorInHex: String): Color {
    val colorInt = java.lang.Long.parseLong(colorInHex, 16)
    return Color(colorInt.toInt(), true)
}

fun hexToColor(colorInHex: String?, default: Color): Color {
    if (colorInHex == null) {
        return default
    }
    val colorInt = java.lang.Long.parseLong(colorInHex, 16)
    return Color(colorInt.toInt(), false)
}

fun Color.toHex(): String {
    return "%x%x%x".format(this.red, this.green, this.blue)
}

fun colorToHex(color: Color): String {
    return "%x%x%x%x".format(color.alpha, color.red, color.green, color.blue)
}

fun contrastColor(c: Color): Color {
    val y = (299 * c.red + 587 * c.green + 114 * c.blue) / 1000.toDouble()
    return if (y >= 128) Color.black else Color.white
}
