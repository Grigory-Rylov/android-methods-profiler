package com.github.grishberg.profiler.chart.highlighting

import com.github.grishberg.profiler.chart.ProfileRectangle
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.comparator.MarkType
import java.awt.Color

interface MethodsColor {
    fun getColorForMethod(profile: ProfileRectangle): Color

    fun getColorForMethod(name: String): Color

    fun getColorForCompare(markType: MarkType): Color
}

class MethodsColorImpl(
    private val methodsColors: MethodsColorRepository
) : MethodsColor {
    private val colors = mutableListOf<ColorInfo>()

    private val otherColor = Color(254, 204, 130)
    private val androidColor = Color(255, 191, 160)
    private val javaLangColor = Color(239, 255, 189)
    private val onLayoutColor = Color(201, 137, 255)
    private val doFrameColor = Color(130, 127, 250)
    private val inflateColor = Color(116, 158, 250)
    private val drawColor = Color(250, 169, 218)
    private val measureColor = Color(150, 219, 204)
    private val activityCreateColor = Color(219, 189, 160)
    private val activityStartColor = Color(197, 219, 187)
    private val activityResumeColor = Color(211, 216, 255)
    private val requestLayoutColor = Color(0xFFA7A3)

    private val compareNoneColor = Color(0x999999)
    private val compareComparedColor = Color(0xCCCCCC)
    private val compareOldColor = Color(255, 90, 0)
    private val compareNewColor = Color(0, 255, 0)
    private val compareChangeOrderColor = Color(0, 120, 255)
    private val compareSuspiciousColor = Color(255, 191, 160)

    init {
        colors.addAll(methodsColors.getColors())
    }

    /**
     * Must be called when need to invalidate colors.
     */
    fun updateColors() {
        colors.clear()
        colors.addAll(methodsColors.getColors())
    }

    override fun getColorForMethod(profile: ProfileRectangle): Color {
        return getColorForMethod(profile.profileData.name)
    }

    override fun getColorForMethod(name: String): Color {
        for (customColor in colors) {
            if (name.startsWith(customColor.filter)) {
                return customColor.color
            }
        }
        if (name == "android.view.Choreographer.doFrame") {
            return doFrameColor
        }

        if (isMeasureMethod(name)) {
            return measureColor
        }

        if (isLayoutMethod(name)) {
            return onLayoutColor
        }
        if (isInflateMethod(name)) {
            return inflateColor
        }

        if (isDrawMethod(name)) {
            return drawColor
        }

        if (isActivityCreateMethod(name)) {
            return activityCreateColor
        }

        if (isActivityStartMethod(name)) {
            return activityStartColor
        }

        if (isActivityResumeMethod(name)) {
            return activityResumeColor
        }

        if (isRequestLayout(name)) {
            return requestLayoutColor
        }

        if (name.startsWith("androidx.")) {
            return androidColor
        }
        if (name.startsWith("java.lang.")) {
            return javaLangColor
        }
        return otherColor
    }

    override fun getColorForCompare(markType: MarkType): Color {
        return when (markType) {
            MarkType.NONE -> compareNoneColor
            MarkType.OLD -> compareOldColor
            MarkType.NEW -> compareNewColor
            MarkType.SUSPICIOUS -> compareSuspiciousColor
            MarkType.CHANGE_ORDER -> compareChangeOrderColor
            MarkType.COMPARED -> compareComparedColor
        }
    }


    private fun isDrawMethod(name: String): Boolean {
        return name == "android.view.ViewRootImpl.performDraw" || name == "android.view.ViewRootImpl.draw"
    }

    private fun isActivityCreateMethod(name: String): Boolean {
        return name == "android.app.Activity.performCreate" || name == "android.app.Instrumentation.callActivityOnCreate"
    }

    private fun isActivityStartMethod(name: String): Boolean {
        return name == "android.app.Activity.performStart" ||
                name == "android.app.Instrumentation.callActivityOnStart" ||
                name == "android.app.ActivityThread.handleStartActivity"
    }

    private fun isActivityResumeMethod(name: String): Boolean {
        return name == "android.app.Activity.performResume" ||
                name == "android.app.ActivityThread.performResumeActivity"
    }

    private fun isInflateMethod(name: String): Boolean {
        return name.startsWith("android.view.LayoutInflater")
    }

    private fun isLayoutMethod(name: String): Boolean {
        if (isStartsWithAndroidPackage(name)) {
            if (name.endsWith(".onLayout")
                || name.endsWith(".onLayoutChild")
                || name.endsWith(".layoutChildren")
                || name.endsWith(".layout")
            ) {
                return true
            }
        }
        return false
    }

    private fun isRequestLayout(name: String): Boolean {
        return name == "android.view.View.requestLayout"
    }

    private fun isMeasureMethod(name: String): Boolean {
        if (isStartsWithAndroidPackage(name)) {
            if (name.endsWith(".onMeasure") || name.endsWith(".measure")) {
                return true
            }
        }
        return name == "android.view.View.measure"
    }

    private fun isStartsWithAndroidPackage(name: String): Boolean {
        return name.startsWith("android.") || name.startsWith("com.android.") || name.startsWith("androidx.")
    }

}
