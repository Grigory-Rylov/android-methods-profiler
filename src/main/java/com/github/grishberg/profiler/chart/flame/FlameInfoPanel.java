package com.github.grishberg.profiler.chart.flame;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.geom.Rectangle2D;

public class FlameInfoPanel extends JComponent {
    private static final int PADDING = 10;
    private static final int TEXT_INNER_SPACE = 4;
    private static final int TOP_PANEL_OFFSET = PADDING;
    private static final int LEFT_PANEL_OFFSET = PADDING;
    private final JPanel parent;
    private int x;
    private int y;
    private int textSize = 13;
    private String classNameText;
    private String rangeText;
    private String durationText;
    private final Color backgroundColor = new Color(47, 47, 47);
    private final Color labelColor = new Color(191, 198, 187);
    private final Font font;

    public FlameInfoPanel(JPanel chart) {
        parent = chart;
        hidePanel();
        font = new Font("TimesRoman", Font.BOLD, textSize);
    }

    public void hidePanel() {
        setVisible(false);
    }

    // use the xy coordinates to update the mouse cursor text/label
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics(font);

        Rectangle2D classNameRect = metrics.getStringBounds(classNameText, null);
        Rectangle2D rangeRect = metrics.getStringBounds(rangeText, null);
        Rectangle2D durationRect = metrics.getStringBounds(durationText, null);

        int rectHeight = PADDING * 2 + (int) classNameRect.getHeight()
                + (int) rangeRect.getHeight()
                + (int) durationRect.getHeight()
                + TEXT_INNER_SPACE * 2 + PADDING / 2;
        int rectWidth = PADDING + (int) Math.max(durationRect.getWidth(),
                Math.max(classNameRect.getWidth(), rangeRect.getWidth())) + PADDING;

        g.setColor(backgroundColor);
        g.fillRect(x, y, rectWidth, rectHeight);

        g.setColor(labelColor);

        int classNameY = this.y + textSize + PADDING;
        g.drawString(classNameText, x + PADDING, classNameY);

        int rangeTextY = classNameY + TEXT_INNER_SPACE + (int) classNameRect.getHeight();
        g.drawString(rangeText, x + PADDING, rangeTextY);

        int durationTextY = rangeTextY + TEXT_INNER_SPACE + (int) durationRect.getHeight();
        g.drawString(durationText, x + PADDING, durationTextY);
    }

    public void setText(Point point, FlameRectangle selectedData) {
        setVisible(true);
        Point parentLocation = parent.getLocation();
        int topOffset = parentLocation.y;
        int horizontalOffset = parentLocation.x;

        x = point.x + horizontalOffset + LEFT_PANEL_OFFSET;
        y = point.y + topOffset + TOP_PANEL_OFFSET;

        classNameText = "(" + selectedData.getCount() + ") " + selectedData.getName();

        double start = selectedData.getX();
        double end = selectedData.getMaxX();

        rangeText = String.format("%.3f ms - %.3f ms", start, end);
        durationText = String.format("%.3f ms", end - start);

        repaint();
    }
}
