package com.github.grishberg.profiler.ui.dialogs.recorder

import com.github.grishberg.android.adb.AdbWrapper
import com.github.grishberg.profiler.androidstudio.adb.PluginAdbDebugWrapper
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.settings.SettingsFacade
import com.github.grishberg.tracerecorder.MethodTraceEventListener
import com.github.grishberg.tracerecorder.MethodTraceRecorder
import com.github.grishberg.tracerecorder.MethodTraceRecorderImpl
import com.github.grishberg.tracerecorder.SerialNumber

class PluginMethodTraceRecorderFactory(
    private val adbWrapper: AdbWrapper,
    private val logger: AppLogger,
    private val settings: SettingsFacade
) : MethodTraceRecorderFactory {
    override fun create(
        listener: MethodTraceEventListener,
        systrace: Boolean,
        serialNumber: SerialNumber?
    ): MethodTraceRecorder {
        val adbLogger = RecorderLoggerWrapper(
            logger
        )
        val adbDebugWrapper = PluginAdbDebugWrapper(logger)
        return MethodTraceRecorderImpl(
            adb = adbWrapper,
            adbDebugWrapper = adbDebugWrapper,
            listener = listener,
            methodTrace = true,
            systrace = systrace,
            logger = adbLogger,
            applicationWaitPostTimeoutInMilliseconds = settings.timeoutBeforeRecording
        )
    }
}
