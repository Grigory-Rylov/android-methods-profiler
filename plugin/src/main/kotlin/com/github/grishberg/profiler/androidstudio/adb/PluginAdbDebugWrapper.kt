package com.github.grishberg.profiler.androidstudio.adb

import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.DdmPreferences
import com.github.grishberg.android.adb.AdbDebugWrapper
import com.github.grishberg.android.adb.MethodProfilingHandler
import com.github.grishberg.profiler.common.AppLogger

class PluginAdbDebugWrapper(
    private val logger: AppLogger
): AdbDebugWrapper {
    override fun getSelectedDebugPort(): Int = 8700

    override fun setDebugPortBase(port: Int) {// not used
    }

    override fun setMethodProfilingHandler(handler: MethodProfilingHandler) {
        ClientData.setMethodProfilingHandler(MethodProfilingHandlerAdapter(handler, logger))
    }

    override fun setProfilerBufferSizeMb(sizeInMb: Int) {
        DdmPreferences.setProfilerBufferSizeMb(sizeInMb)
    }

    private class MethodProfilingHandlerAdapter(
        private val innerHandler: MethodProfilingHandler,
        private val logger: AppLogger
    ) : ClientData.IMethodProfilingHandler {
        override fun onSuccess(remoteFilePath: String, client: Client) {
            innerHandler.onSuccess(remoteFilePath, DdmClientWrapper(client, logger))
        }

        override fun onSuccess(data: ByteArray, client: Client) {
            innerHandler.onSuccess(data, DdmClientWrapper(client, logger))
        }

        override fun onStartFailure(client: Client, message: String) {
            innerHandler.onStartFailure(DdmClientWrapper(client, logger), message)
        }

        override fun onEndFailure(client: Client, message: String) {
            innerHandler.onEndFailure(DdmClientWrapper(client, logger), message)
        }
    }
}
