package com.github.grishberg.profiler.ui.dialogs.recorder

import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.settings.SettingsRepository
import com.github.grishberg.tracerecorder.MethodTraceEventListener
import com.github.grishberg.tracerecorder.MethodTraceRecorder
import com.github.grishberg.tracerecorder.RecordMode
import com.github.grishberg.tracerecorder.SystraceRecord
import com.github.grishberg.tracerecorder.exceptions.AppTimeoutException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import java.awt.Color
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Timer
import java.util.TimerTask
import javax.swing.SwingUtilities

private const val TRACE_FOLDER = "trace"

private const val TAG = "MethodTraceRecordDialog"

private const val SETTINGS_ROOT = "MethodTraceRecordDialog"
private const val PKG_NAME_SETTINGS = "$SETTINGS_ROOT.package"
private const val ACTIVITY_NAME_SETTINGS = "$SETTINGS_ROOT.activity"
private const val SAMPLING_NAME_SETTINGS = "$SETTINGS_ROOT.sampling"
private const val PROFILER_BUFFER_SIZE_SETTINGS = "$SETTINGS_ROOT.profilerBufferSizeInMb"
private const val FILE_NAME_PREFIX_SETTINGS = "$SETTINGS_ROOT.fileNamePrefix"
private const val RECORD_MODE_SAMPLE_SETTINGS = "$SETTINGS_ROOT.recordModeSample"
private const val REMOTE_DEVICE_ADDRESS_SETTINGS = "$SETTINGS_ROOT.remoteDeviceAddress"
private const val WAIT_FOR_RESULT_TIMEOUT_SETTINGS = "$SETTINGS_ROOT.waitForResultTimeoutInSeconds"
private const val DEFAULT_WAIT_FOR_APPLICATION_TIMEOUT = 120
private const val DEFAULT_WAIT_FOR_RESULT_TIMEOUT = 20

interface SampleJavaMethodsDialogView {
    var packageName: String
    var activityName: String
    var fileNamePrefix: String
    var sampling: Int
    var recordMode: RecordMode
    var profilerBufferSizeInMb: Int
    var remoteDeviceAddress: String

    fun showErrorDialog(message: String, title: String = "Method trace recording error")
    fun showInfoDialog(message: String)
    fun setStatusTextAndColor(text: String, color: Color)
    fun focusStopButton()
    fun initialState()
    fun inProgressState()
    fun closeDialog()
    fun enableStopButton(enabled: Boolean)
}

class SampleJavaMethodsDialogLogic(
    private val view: SampleJavaMethodsDialogView,
    private val settings: SettingsRepository,
    private val logger: AppLogger,
    private val recorderFactory: MethodTraceRecorderFactory = MethodTraceRecorderFactoryImpl(logger, settings)
) : MethodTraceEventListener {
    val waitForApplicationColor = Color(0xD2691A)

    var selectedMode: RecordMode = RecordMode.METHOD_TRACES
    var traceFile: File? = null
        private set

    private var job: Job? = null
    private var methodTraceRecorder: MethodTraceRecorder = MethodTraceRecorderStub()

    private val idle = Idle()
    private val recording = Recording()
    private val waitForApp = WaitingForApplication()
    private val waitForResult = WaitForRecordingResult()
    private val waitForDevice = WaitForDevice()
    private val pendingRecording = PendingRecording()
    private var state: State = idle

    private var currentPackageName: String = ""
    private var currentActivityName: String? = null

    init {
        prepareFolder()

        view.packageName = settings.getStringValueOrDefault(PKG_NAME_SETTINGS, "")
        view.activityName = settings.getStringValueOrDefault(ACTIVITY_NAME_SETTINGS, "")
        view.sampling = settings.getIntValueOrDefault(SAMPLING_NAME_SETTINGS, 60)
        view.profilerBufferSizeInMb = settings.getIntValueOrDefault(PROFILER_BUFFER_SIZE_SETTINGS, 8)
        view.fileNamePrefix = settings.getStringValueOrDefault(FILE_NAME_PREFIX_SETTINGS, "")
        selectedMode = if (settings.getBoolValueOrDefault(
                RECORD_MODE_SAMPLE_SETTINGS,
                true
            )
        ) RecordMode.METHOD_SAMPLE else RecordMode.METHOD_TRACES
        view.recordMode = selectedMode
        view.remoteDeviceAddress = settings.getStringValueOrDefault(REMOTE_DEVICE_ADDRESS_SETTINGS, "")
    }

    fun onDialogShown() {
        traceFile = null
        prepareFolder()
        logger.d("$TAG: show dialog")
        try {
            methodTraceRecorder = recorderFactory.create(this)
            logger.d("$TAG recorder created $methodTraceRecorder")
        } catch (e: Throwable) {
            logger.e("create recorder: ", e)
        }
    }

    fun onDialogClosed() {
        logger.d("$TAG: close dialog state=${state::class.java.simpleName}")
        state.onDialogClosed()
    }

    fun onStopPressed() {
        logger.d("$TAG: stop button pressed, state = ${state.javaClass.simpleName}")
        state.onStopPressed()
    }

    fun onStartPressed() {
        state.onStartPressed()
    }

    override fun fail(throwable: Throwable) {
        methodTraceRecorder.stopRecording()
        methodTraceRecorder.disconnect()

        logger.e("$TAG: fail while recording: ${throwable.message}", throwable)
        view.initialState()

        view.showErrorDialog(if (throwable.message != null) throwable.message!! else "Unknown errror")
    }

    override fun onMethodTraceReceived(traceFile: File) {
        state.onTraceResultReceived(traceFile)
    }

    override fun onMethodTraceReceived(remoteFilePath: String) {
        state.onLocalTraceReceived(remoteFilePath)
    }

    override fun onStartedRecording() {
        view.setStatusTextAndColor("Recording...", Color.RED)
        state = recording
    }

    override fun onStartWaitingForApplication() {
        view.setStatusTextAndColor("Waiting for application...", waitForApplicationColor)
        state = waitForApp
    }

    override fun onStartWaitingForDevice() {
        view.setStatusTextAndColor("Wait for device...", Color.DARK_GRAY)
        state = waitForDevice
    }

    override fun onSystraceReceived(values: List<SystraceRecord>) {
        logger.d("onSystraceReceived: ")
        for (record in values) {
            logger.d("${record.name} - ${String.format("%.06f", record.endTime - record.startTime)}")
        }
    }

    private fun createFileName(): String {
        val fileNamePrefix = view.fileNamePrefix
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss")
        val formattedTime = sdf.format(Date())
        if (fileNamePrefix.isEmpty()) {
            return "${settings.filesDir()}/$TRACE_FOLDER/$formattedTime.trace"
        }
        return "${settings.filesDir()}/$TRACE_FOLDER/${fileNamePrefix}_$formattedTime.trace"
    }

    private fun prepareFolder() {
        val folder = File(
            settings.filesDir(),
            TRACE_FOLDER
        )
        if (!folder.exists()) {
            folder.mkdir()
        }
    }

    private inner class Idle : State {
        override fun onStartPressed() {
            val pkgName = view.packageName
            val activity: String? = if (view.activityName.isEmpty()) null else view.activityName
            val remoteAddress: String? = if (view.remoteDeviceAddress.isEmpty()) null else view.remoteDeviceAddress
            if (pkgName.isEmpty()) {
                logger.d("$TAG: onStartPressed: package name is empty")
                view.showErrorDialog("Enter package name.")
                return
            }
            val sampling = view.sampling
            val bufferSizeInMb = view.profilerBufferSizeInMb
            val fileNamePrefix = view.fileNamePrefix

            logger.d("$TAG: start recording pkg=$pkgName, activity=$activity, mode=$selectedMode")
            settings.setStringValue(PKG_NAME_SETTINGS, pkgName)
            settings.setStringValue(ACTIVITY_NAME_SETTINGS, view.activityName)
            settings.setIntValue(SAMPLING_NAME_SETTINGS, sampling)
            settings.setStringValue(FILE_NAME_PREFIX_SETTINGS, fileNamePrefix)
            settings.setBoolValue(RECORD_MODE_SAMPLE_SETTINGS, selectedMode == RecordMode.METHOD_SAMPLE)
            settings.setIntValue(PROFILER_BUFFER_SIZE_SETTINGS, bufferSizeInMb)
            settings.setStringValue(REMOTE_DEVICE_ADDRESS_SETTINGS, view.remoteDeviceAddress)
            view.inProgressState()
            val fileName = createFileName()

            val errorHandler = CoroutineExceptionHandler { _, throwable ->
                GlobalScope.launch(Dispatchers.Swing) {
                    logger.e("$TAG failed while starting recording", throwable)
                    state.onRecordingFailed(throwable)
                }
            }

            view.focusStopButton()
            activity?.let {
                view.setStatusTextAndColor("Start activity $it", Color.BLACK)
            }
            val waitForApplicationTimeout = if (pkgName.isEmpty()) DEFAULT_WAIT_FOR_APPLICATION_TIMEOUT else 5

            currentPackageName = pkgName
            currentActivityName = activity
            state = pendingRecording
            job = GlobalScope.launch(errorHandler) {
                methodTraceRecorder.reconnect(remoteAddress)
                methodTraceRecorder.startRecording(
                    fileName, pkgName,
                    activity, selectedMode, sampling, bufferSizeInMb,
                    waitForApplicationTimeoutInSeconds = waitForApplicationTimeout,
                    remoteDeviceAddress = remoteAddress
                )
            }
        }

        override fun onRecordingFailed(throwable: Throwable) = Unit
    }

    private inner class Recording : State {
        override fun onStopPressed() {
            state = waitForResult
            logger.d("$TAG: stop button pressed")
            methodTraceRecorder.stopRecording()
            view.enableStopButton(false)
            view.setStatusTextAndColor("Waiting for result...", Color.RED)
            val timeout: Long = (1000L * settings.getIntValueOrDefault(
                WAIT_FOR_RESULT_TIMEOUT_SETTINGS,
                DEFAULT_WAIT_FOR_RESULT_TIMEOUT
            )).toLong()
            Timer().schedule(
                object : TimerTask() {
                    override fun run() {
                        SwingUtilities.invokeLater {
                            logger.d("$TAG on timeout invoked, state is ${state.javaClass.simpleName}")
                            state.onWaitForResultTimeout()
                        }
                    }
                },
                timeout
            )
        }

        override fun onRecordingFailed(throwable: Throwable) {
            state = idle
            fail(throwable)
        }
    }

    private fun cancelRecordingJob() {
        job?.let {
            if (it.isActive) {
                it.cancel()
            }
        }
        job = null
    }

    private inner class WaitingForApplication : State {
        override fun onStopPressed() {
            cancelRecordingJob()
            methodTraceRecorder.disconnect()
            view.initialState()
            state = idle
        }

        override fun onDialogClosed() {
            cancelRecordingJob()
            methodTraceRecorder.disconnect()
            view.initialState()
            state = idle
        }

        override fun onRecordingFailed(throwable: Throwable) {
            state = idle
            methodTraceRecorder.stopRecording()
            methodTraceRecorder.disconnect()

            logger.e("$TAG: fail while recording: ${throwable.message}", throwable)
            view.initialState()

            if (throwable is AppTimeoutException) {
                val msg = if (currentActivityName == null) {
                    "Did you started application manually? Or enter activity name and try again"
                } else {
                    "This can happens when Android Studio is opened, try to close it and try again."
                }
                view.showErrorDialog(message = msg, title = "Cant find process '${currentPackageName}'")
                return
            }

            view.showErrorDialog(if (throwable.message != null) throwable.message!! else "Unknown errror")
        }
    }

    private inner class WaitForRecordingResult : State {
        override fun onTraceResultReceived(file: File) {
            logger.d("onMethodTraceReceived: $file")
            methodTraceRecorder.disconnect()
            traceFile = file
            view.initialState()
            view.closeDialog()
            state = idle
        }

        override fun onLocalTraceReceived(remoteFilePath: String) {
            state = idle
            methodTraceRecorder.disconnect()
            logger.d("$TAG: onMethodTraceReceived $remoteFilePath")
            view.initialState()
            view.showInfoDialog("trace stored to remote device at $remoteFilePath")
        }

        override fun onWaitForResultTimeout() {
            state = idle
            cancelRecordingJob()
            methodTraceRecorder.disconnect()

            logger.d("$TAG: StopButtonTimeout invoked")
            view.showErrorDialog("Method trace recording error")
            view.initialState()
        }

        override fun onDialogClosed() {
            cancelRecordingJob()
            methodTraceRecorder.disconnect()
            view.initialState()
            state = idle
        }

        override fun onRecordingFailed(throwable: Throwable) {
            state = idle
            fail(throwable)
        }
    }

    private inner class WaitForDevice : State {
        override fun onStopPressed() {
            cancelRecordingJob()
            methodTraceRecorder.disconnect()
            view.initialState()
            state = idle
        }

        override fun onDialogClosed() {
            cancelRecordingJob()
            methodTraceRecorder.disconnect()
            view.initialState()
            state = idle
        }

        override fun onRecordingFailed(throwable: Throwable) {
            state = idle
            fail(throwable)
        }
    }

    private inner class PendingRecording : State {
        override fun onStopPressed() {
            cancelRecordingJob()
            methodTraceRecorder.disconnect()
            view.initialState()
            state = idle
        }

        override fun onDialogClosed() {
            cancelRecordingJob()
            methodTraceRecorder.disconnect()
            view.initialState()
            state = idle
        }

        override fun onRecordingFailed(throwable: Throwable) {
            state = idle
            fail(throwable)
        }
    }

    private interface State {
        fun onStopPressed() = Unit
        fun onStartPressed() = Unit
        fun onWaitForResultTimeout() = Unit
        fun onTraceResultReceived(file: File) = Unit
        fun onLocalTraceReceived(remoteFilePath: String) = Unit
        fun onDialogClosed() = Unit
        fun onRecordingFailed(throwable: Throwable)
    }

    private class MethodTraceRecorderStub : MethodTraceRecorder {
        override fun disconnect() = Unit
        override fun stopRecording() = Unit
        override fun reconnect(remoteDeviceAddress: String?) = Unit

        override fun startRecording(
            outputFileName: String,
            packageName: String,
            startActivityName: String?,
            mode: RecordMode,
            samplingIntervalInMicroseconds: Int,
            profilerBufferSizeInMb: Int,
            waitForApplicationTimeoutInSeconds: Int,
            remoteDeviceAddress: String?
        ) = Unit
    }
}
