package com.github.grishberg.profiler.androidstudio

import com.github.grishberg.profiler.common.settings.SettingsFacade
import com.intellij.openapi.project.Project
import java.io.File

private const val PLUGIN_DIR = "captures/YAMP"

class PluginState : SettingsFacade {
    private var baseDir: File = File(PLUGIN_DIR)

    /**
     * Should be called after plugin started.
     */
    fun updateBaseDir(project: Project) {
        if (project.basePath != null) {
            baseDir = File(project.basePath, PLUGIN_DIR)
            if (!baseDir.exists()) {
                baseDir.mkdirs()
            }
        }
    }

    override fun filesDir(): String = baseDir.path
    var methodColors = "[]"
    override var timeoutBeforeRecording: Long = 10
    override var showBookmarks: Boolean = true
    override var threadTimeMode: Boolean = false
    override var theme: String = "" // not used
    override var waitForResultTimeout: Int = 60
    override var deviceSerialNumber: String = ""
    override var deviceAddress: String = ""
    override var samplingRecordModeEnabled: Boolean = true
    override var systraceStagePrefix: String = ""
    override var fileNamePrefix: String = ""
    override var bufferSize: Int = 64
    override var sampling: Int = 60
    override var activityName: String = ""
    override var packageName: String = ""
    override var androidHome: String = "<not_used>"
    override var recentFiles: List<String> = mutableListOf()
    override var traceFileDialogDir: String? = null
    override var reportsFileDialogDir: String? = null
    override var stagesFileDialogDir: String? = null
    override var mappingFileDir: String? = null
    override var fontName: String = "Arial"
    override var fontSize: Int = 12
    override var shouldHideMethodsWithUnknownStages: Boolean = false
    override var isCheckForUpdatesEnabled: Boolean = false
    override var debugPort: Int = 0 // not used
    override var hierarchicalStagesMode: Boolean = true
    override var shouldShowToolbar: Boolean = true
    override var caseSensitive: Boolean = false
    var lastVersion: String = ""
}
