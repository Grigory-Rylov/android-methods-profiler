package com.github.grishberg.profiler.ui.dialogs

import org.apache.commons.io.IOUtils
import java.awt.Dimension
import java.awt.Frame
import java.io.IOException
import java.net.URISyntaxException
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextPane
import javax.swing.text.DefaultCaret

class KeymapDialog(owner: Frame) : CloseByEscapeDialog(owner, "Keymap", false) {
    init {
        val htmlStream = resourceToString("help.html")
        val html = JTextPane()
        html.contentType = "text/html"
        html.isEditable = false
        html.background = null
        html.border = null
        val caret = html.caret as DefaultCaret
        caret.updatePolicy = DefaultCaret.NEVER_UPDATE
        html.text = htmlStream

        val content = JPanel()
        content.layout = BoxLayout(content, BoxLayout.PAGE_AXIS)
        content.add(JScrollPane(html))
        content.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        contentPane = content
        content.preferredSize = Dimension(800, 600)
        pack()
    }

    @Throws(IOException::class, URISyntaxException::class)
    private fun resourceToString(filePath: String): String? {
        this.javaClass.classLoader.getResourceAsStream(filePath)
            .use { inputStream -> return IOUtils.toString(inputStream) }
    }
}