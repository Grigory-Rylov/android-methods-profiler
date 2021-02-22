package com.github.grishberg.profiler.androidstudio.adb

import com.android.ddmlib.IDevice
import com.android.ddmlib.IShellOutputReceiver
import com.github.grishberg.android.adb.ClientWrapper
import com.github.grishberg.android.adb.ConnectedDeviceWrapper
import com.github.grishberg.android.adb.ShellOutReceiver
import com.github.grishberg.profiler.common.AppLogger
import java.util.concurrent.TimeUnit

class AdbConnectedDeviceWrapper(
    private val device: IDevice,
    private val logger: AppLogger
) : ConnectedDeviceWrapper {
    override val serialNumber: String
        get() = device.serialNumber

    override fun executeShellCommand(cmd: String, receiver: ShellOutReceiver) {
        device.executeShellCommand(cmd, IShellOutReceiverAdapter(receiver))
    }

    override fun executeShellCommand(
        cmd: String,
        receiver: ShellOutReceiver,
        maxTimeToOutputResponse: Long,
        maxTimeUnits: TimeUnit
    ) {
        device.executeShellCommand(
            cmd, IShellOutReceiverAdapter(receiver), maxTimeToOutputResponse,
            maxTimeUnits
        )
    }

    override fun getClient(applicationName: String): ClientWrapper? {
        val client = device.getClient(applicationName) ?: return null
        return DdmClientWrapper(client, logger)
    }

    private class IShellOutReceiverAdapter(
        private val receiver: ShellOutReceiver
    ) : IShellOutputReceiver {
        override fun addOutput(data: ByteArray, offset: Int, length: Int) {
            receiver.addOutput(data, offset, length)
        }

        override fun flush() = receiver.flush()

        override fun isCancelled() = receiver.isCancelled()
    }
}
