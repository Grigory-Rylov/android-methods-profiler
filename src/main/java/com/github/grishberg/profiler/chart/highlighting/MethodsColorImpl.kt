package com.github.grishberg.profiler.chart.highlighting

import com.github.grishberg.profiler.analyzer.ProfileData
import com.github.grishberg.profiler.common.AppLogger
import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonReader
import java.awt.Color
import java.io.BufferedWriter
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.FileReader
import java.io.IOException
import java.io.OutputStreamWriter

private const val TAG = "CustomColorsRepository"
private const val COLORS_FILE_NAME = "colors.json"
private const val FILTER_KEY = "filter"
private const val COLOR_IN_HEX_KEY = "color"

interface MethodsColor {
    fun getColorForMethod(profile: ProfileData): Color

    fun getColorForMethod(name: String): Color
}

class MethodsColorImpl(
    filesDir: String,
    private val log: AppLogger
) : MethodsColor {
    private val gson = GsonBuilder().enableComplexMapKeySerialization().setPrettyPrinting().create()
    private val colors: List<ColorInfo>

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

    init {
        val colorsFile = File(filesDir, COLORS_FILE_NAME)
        colors = try {
            val fileReader = FileReader(colorsFile)
            val reader = JsonReader(fileReader)
            val data: List<Map<String, String>> = gson.fromJson(reader, List::class.java)
            recognizeColors(data)
        } catch (e: FileNotFoundException) {
            log.d("$TAG: there is no $COLORS_FILE_NAME, create new")
            val colorsStub = listOf(mapOf(Pair(FILTER_KEY, "ru.yandex"), Pair(COLOR_IN_HEX_KEY, "FF9595")))
            createEmptyFile(colorsFile, colorsStub)
            recognizeColors(colorsStub)
        }
    }

    private fun recognizeColors(colors: List<Map<String, String>>): List<ColorInfo> {
        val result = mutableListOf<ColorInfo>()
        for (color in colors) {
            val filter = color[FILTER_KEY]
            val colorInHex = color[COLOR_IN_HEX_KEY]
            if (filter == null || filter == "" || colorInHex == null || colorInHex == "") {
                continue
            }
            try {
                val colorInt = Integer.parseInt(colorInHex, 16)
                val parsedColor = Color(colorInt) ?: continue
                result.add(ColorInfo(filter, parsedColor))
            } catch (e: NumberFormatException) {
                log.e("$TAG: recognize color error: $filter - $colorInHex")
            }
        }
        return result.toList()
    }


    private fun createEmptyFile(colorsFile: File, colorsStub: List<Any>) {
        colorsFile.createNewFile()
        var outputStream: FileOutputStream? = null
        try {
            outputStream = FileOutputStream(colorsFile)
            val bufferedWriter: BufferedWriter
            bufferedWriter = BufferedWriter(OutputStreamWriter(outputStream, "UTF-8"))
            gson.toJson(colorsStub, bufferedWriter)
            bufferedWriter.close()
            log.d("$TAG settings are saved")
        } catch (e: FileNotFoundException) {
            log.e("$TAG: save settings error", e)
        } catch (e: IOException) {
            log.e("$TAG: save settings error", e)
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.flush()
                    outputStream.close()
                } catch (e: IOException) {
                }
            }
        }
    }

    override fun getColorForMethod(profile: ProfileData): Color {
        return getColorForMethod(profile.name)
    }

    override fun getColorForMethod(name: String): Color {
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
        for (customColor in colors) {
            if (name.startsWith(customColor.filter)) {
                return customColor.color
            }
        }
        return otherColor
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
