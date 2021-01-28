package com.github.grishberg.profiler.chart;

import java.awt.*;

public class ElementColor {
    public Color fillColor = Color.WHITE;
    public Color borderColor = Color.BLACK;

    public ElementColor(Color fillColor, Color borderColor) {
        this.fillColor = fillColor;
        this.borderColor = borderColor;
    }

    public ElementColor() {
        this(Color.WHITE, Color.BLACK);
    }

    public void set(Color fill, Color border) {
        fillColor = fill;
        borderColor = border;
    }
}
