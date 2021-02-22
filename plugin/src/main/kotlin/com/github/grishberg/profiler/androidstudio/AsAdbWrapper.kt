package com.github.grishberg.profiler.androidstudio

import com.github.grishberg.android.adb.AdbWrapper
import com.github.grishberg.android.adb.ConnectedDeviceWrapper
import com.github.grishberg.android.adb.DeviceChangedListener
import com.github.grishberg.profiler.androidstudio.adb.AdbConnectedDeviceWrapper
import com.github.grishberg.profiler.common.AppLogger
import com.intellij.openapi.project.Project
import org.jetbrains.android.sdk.AndroidSdkUtils

class AsAdbWrapper(
    project: Project,
    private val logger: AppLogger
) : AdbWrapper {
    private val androidBridge = AndroidSdkUtils.getDebugBridge(project)

    override fun addDeviceChangedListener(listener: DeviceChangedListener) {
        // not used.
    }

    override fun allowedToConnectAndStop(): Boolean = false

    override fun connect() = Unit

    override fun connect(remoterAddress: String) = Unit

    override fun deviceList(): List<ConnectedDeviceWrapper> {
        val devices = androidBridge?.devices?.asList() ?: emptyList()

        val wrappers = mutableListOf<ConnectedDeviceWrapper>()
        devices.forEach {
            wrappers.add(AdbConnectedDeviceWrapper(it, logger))
        }
        return wrappers
    }

    override fun hasInitialDeviceList(): Boolean = androidBridge?.hasInitialDeviceList() ?: false

    override fun isConnected(): Boolean {
        if (androidBridge == null) {
            return false
        }

        return androidBridge.isConnected && androidBridge.hasInitialDeviceList()
    }

    override fun removeDeviceChangedListener(listener: DeviceChangedListener) {
    }

    override fun stop() = Unit
}
