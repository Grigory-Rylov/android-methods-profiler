package com.github.grishberg.profiler.common.settings

import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.updates.SETTINGS_CHECK_FOR_UPDATES
import com.github.grishberg.profiler.ui.theme.Theme
import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.FileReader
import java.io.IOException
import java.io.OutputStreamWriter


private const val TAG = "JsonSettings"

const val SETTINGS_ANDROID_HOME = "androidHome"
private const val SETTINGS_HISTORY = "Main.history"
private const val SETTINGS_STAGES_FILE_DIALOG_DIR = "Plugins.stagesFileDialogDirectory"
private const val SETTINGS_STAGES_HIDE_UNKNOWN = "Plugins.stagesHideUnknown"
private const val SETTINGS_STAGES_HIERARCHICAL = "Plugins.stagesHierarchical"
private const val SETTINGS_THREAD_TIME_MODE = "Main.threadTimeEnabled"
private const val SETTINGS_TRACES_FILE_DIALOG_DIRECTORY = "Main.tracesFileDialogDirectory"
private const val SETTINGS_MAPPINGS_FILE_DIALOG_DIRECTORY = "Main.mappingsFileDialogDirectory"
private const val SETTINGS_REPORTS_FILE_DIALOG_DIRECTORY = "Main.reportsFileDialogDirectory"
private const val SETTINGS_SHOW_BOOKMARKS = "Char.showBookmarks"
private const val SETTINGS_GRID = "Main.enableGrid"
private const val SETTINGS_THEME = "Main.theme"

private const val TRACE_SETTINGS_ROOT = "MethodTraceRecordDialog"
private const val PKG_NAME_SETTINGS = "$TRACE_SETTINGS_ROOT.package"
private const val ACTIVITY_NAME_SETTINGS = "$TRACE_SETTINGS_ROOT.activity"
private const val SAMPLING_NAME_SETTINGS = "$TRACE_SETTINGS_ROOT.sampling"
private const val PROFILER_BUFFER_SIZE_SETTINGS = "$TRACE_SETTINGS_ROOT.profilerBufferSizeInMb"
private const val FILE_NAME_PREFIX_SETTINGS = "$TRACE_SETTINGS_ROOT.fileNamePrefix"
private const val SYSTRACE_STAGE_PREFIX_SETTINGS = "$TRACE_SETTINGS_ROOT.systraceStagePrefix"
private const val RECORD_MODE_SAMPLE_SETTINGS = "$TRACE_SETTINGS_ROOT.recordModeSample"
private const val REMOTE_DEVICE_ADDRESS_SETTINGS = "$TRACE_SETTINGS_ROOT.remoteDeviceAddress"
private const val WAIT_FOR_RESULT_TIMEOUT_SETTINGS = "$TRACE_SETTINGS_ROOT.waitForResultTimeoutInSeconds"
private const val SERIAL_NUMBER_SETTINGS = "$TRACE_SETTINGS_ROOT.serialNumber"

private const val DEFAULT_WAIT_FOR_RESULT_TIMEOUT = 20
private const val SETTINGS_FONT_NAME = "Chart.fontName"
private const val SETTINGS_CELL_FONT_SIZE = "Chart.cellsFontSize"
private const val DEFAULT_CELL_FONT_SIZE = 12

private const val RECORDER_SETTINGS_ROOT = "MethodTraceRecordDialog"
private const val DEBUG_PORT_SETTINGS = "$RECORDER_SETTINGS_ROOT.debugPort"
private const val DEFAULT_DEBUG_PORT = 8699
private const val TIMEOUT_BEFORE_RECORDING = "$RECORDER_SETTINGS_ROOT.timeoutBeforeRecording"
private const val DEFAULT_TIMEOUT_BEFORE_RECORDING = 10

class JsonSettings(
    private val filesDirName: String,
    private val log: AppLogger
) : SettingsFacade {
    private val gson = GsonBuilder().enableComplexMapKeySerialization().setPrettyPrinting().create()

    private val settingsMap = mutableMapOf<String, Any>()
    private val settingsFile =
        File(filesDirName + File.separator + ".android-methods-profiler-settings.json")

    init {
        createHomeDirIfNeeded()

        try {
            val fileReader = FileReader(settingsFile)
            val reader = JsonReader(fileReader)
            val map: Map<String, Any>? = gson.fromJson(reader, MutableMap::class.java)
            if (map != null) {
                settingsMap.putAll(map)
            }
        } catch (e: FileNotFoundException) {
            log.d("$TAG: there is no settings file.")
        } catch (e: Exception) {
            log.e("$TAG: read settings error", e)
        }

        initWithDefaults()
    }

    private fun createHomeDirIfNeeded() {
        val filesDir = File(filesDirName)
        if (!filesDir.exists()) {
            filesDir.mkdir()
        }
    }

    override var traceFileDialogDir: String?
        get() = getStringValue(SETTINGS_TRACES_FILE_DIALOG_DIRECTORY)
        set(value) {
            setStringValue(SETTINGS_TRACES_FILE_DIALOG_DIRECTORY, value!!)
        }
    override var reportsFileDialogDir: String?
        get() = getStringValue(SETTINGS_REPORTS_FILE_DIALOG_DIRECTORY)
        set(value) {
            setStringValue(SETTINGS_REPORTS_FILE_DIALOG_DIRECTORY, value!!)
        }

    override var stagesFileDialogDir: String?
        get() = getStringValue(SETTINGS_STAGES_FILE_DIALOG_DIR)
        set(value) {
            setStringValue(SETTINGS_STAGES_FILE_DIALOG_DIR, value!!)
        }

    override var mappingFileDir: String?
        get() = getStringValue(SETTINGS_MAPPINGS_FILE_DIALOG_DIRECTORY)
        set(value) {
            setStringValue(SETTINGS_MAPPINGS_FILE_DIALOG_DIRECTORY, value!!)
        }

    override var fontName: String
        get() = getStringValueOrDefault(SETTINGS_FONT_NAME, "Arial")
        set(value) {}

    override var fontSize: Int
        get() = getIntValueOrDefault(SETTINGS_CELL_FONT_SIZE, DEFAULT_CELL_FONT_SIZE)
        set(value) {
            setIntValue(SETTINGS_CELL_FONT_SIZE, value)
        }

    override var shouldHideMethodsWithUnknownStages: Boolean
        get() = getBoolValueOrDefault(SETTINGS_STAGES_HIDE_UNKNOWN, false)
        set(value) {}

    override var hierarchicalStagesMode: Boolean
        get() = getBoolValueOrDefault(SETTINGS_STAGES_HIERARCHICAL, true)
        set(value) {}

    fun getBoolValueOrDefault(name: String, default: Boolean): Boolean {
        val boolVal = settingsMap[name]
        if (boolVal is Boolean) {
            return boolVal
        }
        return default
    }

    private fun getIntValueOrDefault(name: String, default: Int): Int {
        val value = settingsMap[name]
        if (value is Double) {
            return value.toInt()
        }
        if (value is Int) {
            return value
        }
        return default
    }

    @Suppress("UNCHECKED_CAST")
    private fun getStringList(name: String): List<String> {
        val value = settingsMap[name]
        if (value is List<*>) {
            return value as List<String>
        }
        return listOf()
    }

    private fun setBoolValue(name: String, value: Boolean) {
        settingsMap[name] = value
    }

    private fun setIntValue(name: String, value: Int) {
        settingsMap[name] = value
    }

    private fun setStringValue(name: String, value: String) {
        settingsMap[name] = value
    }

    private fun getStringValueOrDefault(name: String, default: String): String {
        val value = settingsMap[name]
        if (value is String) {
            return value
        }
        return default
    }

    private fun getStringValue(name: String): String? {
        val value = settingsMap[name]
        if (value is String) {
            return value
        }
        return null
    }

    private fun setStringList(name: String, value: List<String>) {
        settingsMap[name] = value
    }

    override fun save() {
        createHomeDirIfNeeded()
        settingsFile.createNewFile()
        var outputStream: FileOutputStream? = null
        try {
            outputStream = FileOutputStream(settingsFile)
            val bufferedWriter: BufferedWriter
            bufferedWriter = BufferedWriter(OutputStreamWriter(outputStream, "UTF-8"))
            gson.toJson(settingsMap, bufferedWriter)
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

    override var recentFiles: List<String>
        get() = getStringList(SETTINGS_HISTORY)
        set(value) {
            setStringList(SETTINGS_HISTORY, value)
        }

    override var packageName: String
        get() = getStringValueOrDefault(PKG_NAME_SETTINGS, "")
        set(value) {
            setStringValue(PKG_NAME_SETTINGS, value)
        }

    override var activityName: String
        get() = getStringValueOrDefault(ACTIVITY_NAME_SETTINGS, "")
        set(value) {
            setStringValue(ACTIVITY_NAME_SETTINGS, value)
        }

    override var sampling: Int
        get() = getIntValueOrDefault(SAMPLING_NAME_SETTINGS, 60)
        set(value) {
            setIntValue(SAMPLING_NAME_SETTINGS, value)
        }

    override var androidHome: String?
        get() = getStringValue(SETTINGS_ANDROID_HOME)
        set(value) {
            setStringValue(SETTINGS_ANDROID_HOME, value ?: "")
        }

    override var bufferSize: Int
        get() = getIntValueOrDefault(PROFILER_BUFFER_SIZE_SETTINGS, 8)
        set(value) {
            setIntValue(PROFILER_BUFFER_SIZE_SETTINGS, value)
        }

    override var fileNamePrefix: String
        get() = getStringValueOrDefault(FILE_NAME_PREFIX_SETTINGS, "")
        set(value) {
            setStringValue(FILE_NAME_PREFIX_SETTINGS, value)
        }

    override var systraceStagePrefix: String
        get() = getStringValueOrDefault(SYSTRACE_STAGE_PREFIX_SETTINGS, "")
        set(value) {
            setStringValue(SYSTRACE_STAGE_PREFIX_SETTINGS, value)
        }

    override var samplingRecordModeEnabled: Boolean
        get() = getBoolValueOrDefault(
            RECORD_MODE_SAMPLE_SETTINGS,
            true
        )
        set(value) {
            setBoolValue(RECORD_MODE_SAMPLE_SETTINGS, value)
        }

    override var deviceAddress: String
        get() = getStringValueOrDefault(REMOTE_DEVICE_ADDRESS_SETTINGS, "")
        set(value) {
            setStringValue(REMOTE_DEVICE_ADDRESS_SETTINGS, value)
        }

    override var deviceSerialNumber: String
        get() = getStringValueOrDefault(SERIAL_NUMBER_SETTINGS, "")
        set(value) {
            setStringValue(SERIAL_NUMBER_SETTINGS, value)
        }

    override var waitForResultTimeout: Int
        get() = getIntValueOrDefault(
            WAIT_FOR_RESULT_TIMEOUT_SETTINGS,
            DEFAULT_WAIT_FOR_RESULT_TIMEOUT
        )
        set(value) {
            setIntValue(WAIT_FOR_RESULT_TIMEOUT_SETTINGS, value)
        }

    override var theme: String
        get() = getStringValueOrDefault(SETTINGS_THEME, Theme.LIGHT.name)
        set(value) {
            setStringValue(SETTINGS_THEME, value)
        }

    override var threadTimeMode: Boolean
        get() = getBoolValueOrDefault(SETTINGS_THREAD_TIME_MODE, false)
        set(value) {
            setBoolValue(SETTINGS_THREAD_TIME_MODE, value)
        }

    override var showBookmarks: Boolean
        get() = getBoolValueOrDefault(SETTINGS_SHOW_BOOKMARKS, true)
        set(value) {
            setBoolValue(SETTINGS_SHOW_BOOKMARKS, value)
        }

    override var isCheckForUpdatesEnabled: Boolean
        get() = getBoolValueOrDefault(SETTINGS_CHECK_FOR_UPDATES, true)
        set(value) {
            setBoolValue(SETTINGS_CHECK_FOR_UPDATES, value)
        }

    override var debugPort: Int
        get() = getIntValueOrDefault(
            DEBUG_PORT_SETTINGS,
            DEFAULT_DEBUG_PORT
        )
        set(value) {
            setIntValue(DEBUG_PORT_SETTINGS, value)
        }

    override var timeoutBeforeRecording: Long
        get() = getIntValueOrDefault(TIMEOUT_BEFORE_RECORDING, DEFAULT_TIMEOUT_BEFORE_RECORDING).toLong()
        set(value) {
            setIntValue(TIMEOUT_BEFORE_RECORDING, value.toInt())
        }

    override fun filesDir() = filesDirName

    private fun initWithDefaults() {
        initWithDefaultStringValue(SETTINGS_FONT_NAME, "Arial")
        initWithDefaultIntValue(SETTINGS_CELL_FONT_SIZE, DEFAULT_CELL_FONT_SIZE)
        initWithDefaultIntValue(DEBUG_PORT_SETTINGS, DEFAULT_DEBUG_PORT)
    }

    private fun initWithDefaultStringValue(key: String, default: String) {
        setStringValue(key, getStringValueOrDefault(key, default))
    }

    private fun initWithDefaultIntValue(key: String, default: Int) {
        setIntValue(key, getIntValueOrDefault(key, default))
    }
}
