package com.github.grishberg.profiler.common.updates

import com.github.grishberg.profiler.common.SimpleMouseListener
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.ActionListener
import java.awt.event.MouseEvent
import java.awt.geom.Rectangle2D
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Timer
import kotlin.math.max

private const val PADDING = 10
private const val TEXT_INNER_SPACE = 4

class UpdatesInfoPanel(
    private val parent: JPanel,
    private val version: ReleaseVersion,
    private val closeCallback: Runnable,
    private val linkClickCallback: Runnable
) : JComponent() {
    private val backgroundColor = Color(47, 47, 47)
    private val labelColor = Color(191, 198, 187)
    private val urlColor = Color(0x6F95C2)
    private val textSize = 13
    private val fontMetrics: FontMetrics
    private var rectHeight = 0
    private var rectWidth = 0
    private var text = ""
    private var bottomText = ""
    private var locationX = 0
    private var locationY = 0
    private val messageRect: Rectangle2D = Rectangle2D.Double()
    private val urlRect: Rectangle2D = Rectangle2D.Double()
    private val panelRect = Rectangle()

    init {
        font = Font("Arial", Font.BOLD, textSize)
        fontMetrics = getFontMetrics(font)

        addMouseListener(object : SimpleMouseListener() {
            override fun mouseClicked(e: MouseEvent) {
                if (!isVisible) {
                    return
                }
                if (panelRect.contains(e.point)) {
                    hidePanel()
                    linkClickCallback.run()
                }
            }
        })
    }

    fun showUpdate() {
        val parentLocation = parent.location
        val topOffset = parentLocation.y
        val horizontalOffset = parentLocation.x
        text = "New version is available ${version.versionName}"
        bottomText = "menu/Help/Visit YAMP home page"
        messageRect.setRect(fontMetrics.getStringBounds(text, null))
        urlRect.setRect(fontMetrics.getStringBounds(bottomText, null))

        rectHeight = (PADDING * 2 +
                messageRect.height +
                urlRect.height +
                TEXT_INNER_SPACE + PADDING / 2).toInt()
        rectWidth = PADDING + max(
            messageRect.width,
            max(urlRect.width, messageRect.width)
        ).toInt() + PADDING

        locationY = topOffset + parent.height - rectHeight
        locationX = horizontalOffset + parent.width - rectWidth

        panelRect.setBounds(locationX, locationY, rectWidth, rectHeight)

        val timer = Timer(3000, ActionListener {
            hidePanel()
        })
        timer.isRepeats = false
        timer.start()

        isVisible = true
        repaint()
    }

    fun hidePanel() {
        isVisible = false
        closeCallback.run()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponents(g)
        g.font = font

        g.color = backgroundColor
        g.fillRect(locationX, locationY, rectWidth, rectHeight)
        g.color = labelColor
        val messageY = locationY + textSize + PADDING
        g.drawString(text, locationX + PADDING, messageY)

        g.color = urlColor
        val urlY = messageY + textSize + TEXT_INNER_SPACE
        g.drawString(bottomText, locationX + PADDING, urlY)
    }
}
