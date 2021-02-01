package com.github.grishberg.profiler.chart.flame;

import javax.swing.*;
import java.awt.*;
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
    private int rectHeight;
    private int rectWidth;
    private Rectangle2D classNameRect = new Rectangle2D.Double();
    private Rectangle2D rangeRect = new Rectangle2D.Double();
    private Rectangle2D durationRect = new Rectangle2D.Double();
    private final FontMetrics fontMetrics;

    public FlameInfoPanel(JPanel chart) {
        parent = chart;
        hidePanel();
        font = new Font("Arial", Font.BOLD, textSize);
        fontMetrics = getFontMetrics(font);
    }

    public void hidePanel() {
        setVisible(false);
    }

    // use the xy coordinates to update the mouse cursor text/label
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setFont(font);
        if (rectWidth == 0 || rectHeight == 0) {
            return;
        }

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
        Point parentLocation = parent.getLocation();
        int topOffset = parentLocation.y;
        int horizontalOffset = parentLocation.x;

        double start = selectedData.getX();
        double end = selectedData.getMaxX();

        rangeText = String.format("%.3f ms - %.3f ms", start, end);
        durationText = String.format("%.3f ms", end - start);

        classNameText = "(" + selectedData.getCount() + ") " + selectedData.getName();

        classNameRect = fontMetrics.getStringBounds(classNameText, null);
        rangeRect = fontMetrics.getStringBounds(rangeText, null);
        durationRect = fontMetrics.getStringBounds(durationText, null);

        rectHeight = PADDING * 2 + (int) classNameRect.getHeight()
                + (int) rangeRect.getHeight()
                + (int) durationRect.getHeight()
                + TEXT_INNER_SPACE * 2 + PADDING / 2;
        rectWidth = PADDING + (int) Math.max(durationRect.getWidth(),
                Math.max(classNameRect.getWidth(), rangeRect.getWidth())) + PADDING;

        x = point.x + horizontalOffset + LEFT_PANEL_OFFSET;
        y = point.y + topOffset + TOP_PANEL_OFFSET;

        if ((point.x - (rectWidth + horizontalOffset + LEFT_PANEL_OFFSET) > 0) ||
                (x + rectWidth - horizontalOffset > parent.getWidth() &&
                        (point.x + horizontalOffset) > parent.getWidth() * 0.66)) {
            x = point.x - (rectWidth + horizontalOffset + LEFT_PANEL_OFFSET);
        }

        if (y + rectHeight - topOffset > parent.getHeight()) {
            y = point.y + topOffset - rectHeight;
        }
        setVisible(true);
        repaint();
    }
}
