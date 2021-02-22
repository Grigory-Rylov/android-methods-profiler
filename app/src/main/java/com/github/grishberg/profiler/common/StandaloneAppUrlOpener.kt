package com.github.grishberg.profiler.common

import java.awt.Desktop
import java.net.URI

class StandaloneAppUrlOpener : UrlOpener {
    override fun openUrl(uri: String) {
        val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
        if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
            try {
                desktop.browse(URI.create(uri))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
