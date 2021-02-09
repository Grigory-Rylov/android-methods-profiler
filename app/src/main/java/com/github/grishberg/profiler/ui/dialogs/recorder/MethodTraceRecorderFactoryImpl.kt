package com.github.grishberg.profiler.ui.dialogs.recorder

import com.github.grishberg.android.adb.DdmAdbDebugWrapper
import com.github.grishberg.android.adb.DdmAdbWrapper
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.settings.SettingsFacade
import com.github.grishberg.tracerecorder.DeviceProviderImpl
import com.github.grishberg.tracerecorder.MethodTraceEventListener
import com.github.grishberg.tracerecorder.MethodTraceRecorder
import com.github.grishberg.tracerecorder.MethodTraceRecorderImpl
import com.github.grishberg.tracerecorder.SerialNumber


/**
 * Factory for MethodTraceRecorder implementation with ddmlib.
 */
internal class MethodTraceRecorderFactoryImpl(
    private val logger: AppLogger,
    private val settings: SettingsFacade
) : MethodTraceRecorderFactory {
    private val debugPort: Int = settings.debugPort

    override fun create(
        listener: MethodTraceEventListener,
        systrace: Boolean,
        serialNumber: SerialNumber?
    ): MethodTraceRecorder {
        val adbLogger = RecorderLoggerWrapper(
            logger
        )
        val adbWrapper = DdmAdbWrapper(
            logger = adbLogger,
            clientSupport = true,
            androidHome = settings.androidHome,
            forceNewBridge = false
        )
        val connectionStrategy = DeviceProviderImpl.ConnectStrategy.create(serialNumber)

        val deviceProvider = DeviceProviderImpl(adbWrapper, adbLogger, connectionStrategy)
        val adbDebugWrapper = DdmAdbDebugWrapper(adbLogger)

        return MethodTraceRecorderImpl(
            adb = adbWrapper,
            adbDebugWrapper = adbDebugWrapper,
            listener = listener,
            methodTrace = true,
            systrace = systrace,
            logger = adbLogger,
            deviceProvider = deviceProvider,
            debugPort = debugPort,
            applicationWaitPostTimeoutInMilliseconds = settings.timeoutBeforeRecording
        )
    }
}
