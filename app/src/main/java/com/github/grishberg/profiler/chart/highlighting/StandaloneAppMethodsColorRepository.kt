package com.github.grishberg.profiler.chart.highlighting

import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.hexToColor
import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.FileReader
import java.io.IOException
import java.io.OutputStreamWriter

private const val TAG = "CustomColorsRepository"
private const val COLORS_FILE_NAME = "colors.json"

class StandaloneAppMethodsColorRepository(
    filesDir: String,
    private val colorsInfoAdapter: ColorInfoAdapter,
    private val log: AppLogger
) : MethodsColorRepository {
    private val gson = GsonBuilder().enableComplexMapKeySerialization().setPrettyPrinting().create()
    private val colors = mutableListOf<ColorInfo>()
    private val colorsFile = File(filesDir, COLORS_FILE_NAME)

    init {
        val readColors: List<ColorInfo> = try {
            val fileReader = FileReader(colorsFile)
            val reader = JsonReader(fileReader)
            val data: List<Map<String, String>> = gson.fromJson(reader, List::class.java)
            colorsInfoAdapter.stringsToColorInfo(data)
        } catch (e: FileNotFoundException) {
            log.d("$TAG: there is no $COLORS_FILE_NAME, create new")
            val colorsStub = listOf(ColorInfo(filter = "ru.yandex", color = hexToColor("FF9595")))
            createColorsFile(colorsFile, colorsStub)
            colorsStub
        }
        colors.addAll(readColors)
    }

    private fun createColorsFile(colorsFile: File, colors: List<ColorInfo>) {
        if (colorsFile.exists()) {
            colorsFile.delete()
        }
        colorsFile.createNewFile()
        val data = colorsInfoAdapter.colorInfoToStrings(colors)
        var outputStream: FileOutputStream? = null
        try {
            outputStream = FileOutputStream(colorsFile)
            val bufferedWriter = BufferedWriter(OutputStreamWriter(outputStream, "UTF-8"))
            gson.toJson(data, bufferedWriter)
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

    override fun getColors(): List<ColorInfo> = colors

    override fun updateColors(newColors: List<ColorInfo>) {
        colors.clear()
        colors.addAll(newColors)
        createColorsFile(colorsFile, newColors)
    }
}
