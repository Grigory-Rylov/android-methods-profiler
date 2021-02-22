package com.github.grishberg.profiler.ui

import java.awt.Frame
import javax.swing.JLabel

class PluginAppIconDelegate : AppIconDelegate {
    override fun updateFrameIcon(frame: Frame) = Unit

    override fun updateLoadingIcon(label: JLabel) = Unit
}
