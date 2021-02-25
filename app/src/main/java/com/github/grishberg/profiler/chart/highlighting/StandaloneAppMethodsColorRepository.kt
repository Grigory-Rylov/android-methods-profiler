package com.github.grishberg.profiler.chart.highlighting

import com.github.grishberg.profiler.common.AppLogger
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
    colorsInfoAdapter: ColorInfoAdapter,
    private val log: AppLogger
) : MethodsColorRepository {
    private val gson = GsonBuilder().enableComplexMapKeySerialization().setPrettyPrinting().create()
    private val colors: List<ColorInfo>

    init {
        val colorsFile = File(filesDir, COLORS_FILE_NAME)
        colors = try {
            val fileReader = FileReader(colorsFile)
            val reader = JsonReader(fileReader)
            val data: List<Map<String, String>> = gson.fromJson(reader, List::class.java)
            colorsInfoAdapter.stringsToColorInfo(data)
        } catch (e: FileNotFoundException) {
            log.d("$TAG: there is no $COLORS_FILE_NAME, create new")
            val colorsStub = listOf(mapOf(Pair(FILTER_KEY, "ru.yandex"), Pair(COLOR_IN_HEX_KEY, "FF9595")))
            createEmptyFile(colorsFile, colorsStub)
            colorsInfoAdapter.stringsToColorInfo(colorsStub)
        }
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

    override fun getColors(): List<ColorInfo> = colors

    override fun updateColors(colors: List<ColorInfo>) = Unit
}
