package com.github.grishberg.profiler.common.settings

interface SettingsFacade {
    fun filesDir(): String
    fun save() = Unit

    var timeoutBeforeRecording: Long
    var showBookmarks: Boolean
    var threadTimeMode: Boolean
    var theme: String
    var waitForResultTimeout: Int
    var deviceSerialNumber: String
    var deviceAddress: String
    var samplingRecordModeEnabled: Boolean
    var systraceStagePrefix: String
    var fileNamePrefix: String
    var bufferSize: Int
    var sampling: Int
    var activityName: String
    var packageName: String
    var androidHome: String
    var recentFiles: List<String>
    var traceFileDialogDir: String?
    var reportsFileDialogDir: String?
    var stagesFileDialogDir: String?
    var mappingFileDir: String?
    var fontName: String
    var fontSize: Int
    var shouldHideMethodsWithUnknownStages: Boolean
    var hierarchicalStagesMode: Boolean
    var isCheckForUpdatesEnabled: Boolean
    var debugPort: Int
    var shouldShowToolbar: Boolean
    var caseSensitive: Boolean
    var isErgonomicKeymapEnabled: Boolean
}
