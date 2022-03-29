package com.github.grishberg.profiler.ui.dialogs

import com.github.grishberg.profiler.common.settings.SettingsFacade
import com.github.grishberg.profiler.ui.Main
import java.awt.FlowLayout
import javax.swing.*

/**
 * Enter ANDROID_HOME.
 */
class SetAndroidHomeDialog(
    owner: JFrame,
    private val settings: SettingsFacade,
    modal: Boolean,
) : CloseByEscapeDialog(owner, "Set path to Android SDK", modal) {
    private val androidHomeField = JTextField(25)

    init {
        val content = JPanel()
        content.layout = FlowLayout()

        content.add(JLabel("Android SDK (ANDROID_HOME)"))
        content.add(androidHomeField)
        androidHomeField.addActionListener { storeValueAndClose() }

        val okButton = JButton("OK")
        okButton.addActionListener { storeValueAndClose() }
        content.add(okButton)
        contentPane = content
        androidHomeField.text = settings.androidHome
        pack()
    }

    private fun storeValueAndClose() {
        val androidHome = androidHomeField.text.trim()
        if (androidHome.isEmpty()) {
            return
        }
        settings.androidHome = androidHome
        isVisible = false
    }
}
