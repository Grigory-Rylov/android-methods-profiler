package com.github.grishberg.profiler.ui

import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener

open class SimpleComponentListener : ComponentListener {
    override fun componentResized(e: ComponentEvent) = Unit
    override fun componentShown(e: ComponentEvent)  = Unit
    override fun componentMoved(e: ComponentEvent)  = Unit
    override fun componentHidden(e: ComponentEvent) = Unit
}