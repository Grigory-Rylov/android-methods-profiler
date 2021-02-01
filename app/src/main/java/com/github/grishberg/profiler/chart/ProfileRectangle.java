package com.github.grishberg.profiler.chart;

import com.github.grishberg.android.profiler.core.ProfileData;

import java.awt.Color;
import java.awt.geom.Rectangle2D;

public class ProfileRectangle extends Rectangle2D.Double {
    public final ProfileData profileData;
    public boolean isFoundElement;
    private Color color = null;

    public ProfileRectangle(double startThreadTime,
                            double y,
                            double threadDuration,
                            double h,
                            ProfileData profileData) {
        super(startThreadTime, y, threadDuration, h);
        this.profileData = profileData;
    }

    public boolean isInside(double cx, double cy) {
        return cx >= x && cx <= x + width && cy >= y && cy <= y + height;
    }

    public boolean isInScreen(double screenLeft, double screenTop, double screenRight, double screenBottom) {
        if (x < screenLeft && x + width < screenLeft) {
            return false;
        }

        if (x > screenRight && x + width > screenRight) {
            return false;
        }

        if (y < screenTop && y + height < screenTop) {
            return false;
        }

        if (y > screenBottom && y + height > screenBottom) {
            return false;
        }
        return true;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color newColor) {
        color = newColor;
    }

    @Override
    public String toString() {
        return "ProfileRectangle{" +
                "name=" + profileData.getName() +
                ", x=" + x +
                ", y=" + y +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ProfileRectangle)) return false;
        ProfileRectangle rect = (ProfileRectangle) obj;

        return profileData == rect.profileData && super.equals(obj);
    }
}
