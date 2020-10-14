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
private const val SYSTRACE_STAGE_PREFIX_SETTINGS = "$SETTINGS_ROOT.systraceStagePrefix"
private const val RECORD_MODE_SAMPLE_SETTINGS = "$SETTINGS_ROOT.recordModeSample"
private const val REMOTE_DEVICE_ADDRESS_SETTINGS = "$SETTINGS_ROOT.remoteDeviceAddress"
private const val WAIT_FOR_RESULT_TIMEOUT_SETTINGS = "$SETTINGS_ROOT.waitForResultTimeoutInSeconds"
private const val DEFAULT_WAIT_FOR_APPLICATION_TIMEOUT = 120
private const val DEFAULT_WAIT_FOR_RESULT_TIMEOUT = 20

interface JavaMethodsRecorderDialogView {
    var packageName: String
    var activityName: String
    var fileNamePrefix: String
    var sampling: Int
    var recordMode: RecordMode
    var profilerBufferSizeInMb: Int
    var remoteDeviceAddress: String
    var isSystraceStageEnabled: Boolean
    var systraceStagePrefix: String?

    fun showErrorDialog(message: String, title: String = "Method trace recording error")
    fun showInfoDialog(message: String)
    fun setStatusTextAndColor(text: String, color: Color)
    fun focusStopButton()
    fun initialState()
    fun inProgressState()
    fun closeDialog()
    fun enableStopButton(enabled: Boolean)
    fun enableSampling()
    fun disableSampling()
}

class JavaMethodsDialogLogic(
    private val view: JavaMethodsRecorderDialogView,
    private val settings: SettingsRepository,
    private val logger: AppLogger,
    private val recorderFactory: MethodTraceRecorderFactory = MethodTraceRecorderFactoryImpl(logger, settings)
) : MethodTraceEventListener {
    var selectedMode: RecordMode = RecordMode.METHOD_TRACES
        set(value) {
            field = value
            if (selectedMode == RecordMode.METHOD_SAMPLE) {
                view.enableSampling()
            } else {
                view.disableSampling()
            }
        }

    var result: RecordedResult? = null
        private set

    private var job: Job? = null
    private var methodTraceRecorder: MethodTraceRecorder = MethodTraceRecorderStub()

    private var state: State = Idle(this, view, settings, logger)

    private var currentPackageName: String = ""
    private var currentActivityName: String? = null

    init {
        prepareFolder()

        view.packageName = settings.getStringValueOrDefault(PKG_NAME_SETTINGS, "")
        view.activityName = settings.getStringValueOrDefault(ACTIVITY_NAME_SETTINGS, "")
        view.sampling = settings.getIntValueOrDefault(SAMPLING_NAME_SETTINGS, 60)
        view.profilerBufferSizeInMb = settings.getIntValueOrDefault(PROFILER_BUFFER_SIZE_SETTINGS, 8)
        view.fileNamePrefix = settings.getStringValueOrDefault(FILE_NAME_PREFIX_SETTINGS, "")
        view.systraceStagePrefix = settings.getStringValueOrDefault(SYSTRACE_STAGE_PREFIX_SETTINGS, "")
        selectedMode = if (settings.getBoolValueOrDefault(
                RECORD_MODE_SAMPLE_SETTINGS,
                true
            )
        ) RecordMode.METHOD_SAMPLE else RecordMode.METHOD_TRACES
        view.recordMode = selectedMode
        view.remoteDeviceAddress = settings.getStringValueOrDefault(REMOTE_DEVICE_ADDRESS_SETTINGS, "")
    }

    fun onDialogShown() {
        result = null

        prepareFolder()
        logger.d("$TAG: show dialog")
        idleState()
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
        state.onStartPressed(recorderFactory)
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
        state.onStartedRecording()
    }

    override fun onStartWaitingForApplication() {
        state.onStartWaitingForApplication()
    }

    override fun onStartWaitingForDevice() {
        state.onStartWaitingForDevice()
    }

    override fun onSystraceReceived(values: List<SystraceRecord>) {
        logger.d("onSystraceReceived: ")
        for (record in values) {
            logger.d("${record.name} - ${String.format("%.06f", record.endTime - record.startTime)}")
        }
        state.onSystraceReceived(values)
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

    private fun changeState(newState: State) {
        state = newState
    }

    private fun idleState() {
        state = Idle(this, view, settings, logger)
    }

    private class Idle(
        private val stateMachine: JavaMethodsDialogLogic,
        private val view: JavaMethodsRecorderDialogView,
        private val settings: SettingsRepository,
        private val logger: AppLogger
    ) : State {
        override fun onStartPressed(methodTraceRecorderFactory: MethodTraceRecorderFactory) {
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

            logger.d("$TAG: start recording pkg=$pkgName, activity=$activity, mode=${stateMachine.selectedMode}")
            settings.setStringValue(PKG_NAME_SETTINGS, pkgName)
            settings.setStringValue(ACTIVITY_NAME_SETTINGS, view.activityName)
            settings.setIntValue(SAMPLING_NAME_SETTINGS, sampling)
            settings.setStringValue(FILE_NAME_PREFIX_SETTINGS, fileNamePrefix)
            settings.setBoolValue(RECORD_MODE_SAMPLE_SETTINGS, stateMachine.selectedMode == RecordMode.METHOD_SAMPLE)
            settings.setIntValue(PROFILER_BUFFER_SIZE_SETTINGS, bufferSizeInMb)
            settings.setStringValue(REMOTE_DEVICE_ADDRESS_SETTINGS, view.remoteDeviceAddress)
            settings.setStringValue(SYSTRACE_STAGE_PREFIX_SETTINGS, view.systraceStagePrefix ?: "")
            view.inProgressState()
            val fileName = stateMachine.createFileName()

            val errorHandler = CoroutineExceptionHandler { _, throwable ->
                GlobalScope.launch(Dispatchers.Swing) {
                    logger.e("$TAG failed while starting recording", throwable)
                    stateMachine.state.onRecordingFailed(throwable)
                }
            }

            view.focusStopButton()
            activity?.let {
                view.setStatusTextAndColor("Start activity $it", Color.BLACK)
            }
            val waitForApplicationTimeout = if (pkgName.isEmpty()) DEFAULT_WAIT_FOR_APPLICATION_TIMEOUT else 5

            val methodTraceRecorder = methodTraceRecorderFactory.create(stateMachine, view.isSystraceStageEnabled)
            stateMachine.methodTraceRecorder = methodTraceRecorder
            stateMachine.currentPackageName = pkgName
            stateMachine.currentActivityName = activity
            stateMachine.changeState(
                PendingRecording(
                    stateMachine,
                    view,
                    methodTraceRecorder,
                    settings,
                    logger,
                    view.isSystraceStageEnabled,
                    view.systraceStagePrefix
                )
            )
            stateMachine.job = GlobalScope.launch(errorHandler) {
                methodTraceRecorder.reconnect(remoteAddress)
                methodTraceRecorder.startRecording(
                    fileName, pkgName,
                    activity, stateMachine.selectedMode, sampling, bufferSizeInMb,
                    waitForApplicationTimeoutInSeconds = waitForApplicationTimeout,
                    remoteDeviceAddress = remoteAddress
                )
            }
        }

        override fun onRecordingFailed(throwable: Throwable) = Unit
    }

    private class Recording(
        private val stateMachine: JavaMethodsDialogLogic,
        private val view: JavaMethodsRecorderDialogView,
        private val methodTraceRecorder: MethodTraceRecorder,
        private val settings: SettingsRepository,
        private val logger: AppLogger,
        private val shouldWaitForSystrace: Boolean,
        private val systracePrefix: String?
    ) : State {
        override fun onStopPressed() {
            if (shouldWaitForSystrace) {
                stateMachine.changeState(
                    WaitForRecordingResultWithSystrace(
                        stateMachine,
                        view,
                        methodTraceRecorder,
                        settings,
                        logger,
                        systracePrefix
                    )
                )
            } else {
                stateMachine.changeState(
                    WaitForRecordingResult(
                        stateMachine,
                        view,
                        methodTraceRecorder,
                        settings,
                        logger
                    )
                )
            }
            logger.d("$TAG: stop button pressed")

            view.enableStopButton(false)
            view.setStatusTextAndColor("Waiting for result...", Color.RED)
            val timeout: Long = (1000L * settings.getIntValueOrDefault(
                WAIT_FOR_RESULT_TIMEOUT_SETTINGS,
                DEFAULT_WAIT_FOR_RESULT_TIMEOUT
            ))
            Timer().schedule(
                object : TimerTask() {
                    override fun run() {
                        SwingUtilities.invokeLater {
                            logger.d("$TAG on timeout invoked, state is ${stateMachine.state.javaClass.simpleName}")
                            stateMachine.state.onWaitForResultTimeout()
                        }
                    }
                },
                timeout
            )
            methodTraceRecorder.stopRecording()
        }

        override fun onRecordingFailed(throwable: Throwable) {
            stateMachine.idleState()
            stateMachine.fail(throwable)
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

    private class WaitingForApplication(
        private val stateMachine: JavaMethodsDialogLogic,
        private val view: JavaMethodsRecorderDialogView,
        private val methodTraceRecorder: MethodTraceRecorder,
        private val settings: SettingsRepository,
        private val logger: AppLogger,
        private val shouldWaitForSystrace: Boolean,
        private val systracePrefix: String?
    ) : State {

        override fun onStartedRecording() {
            view.setStatusTextAndColor("Recording...", Color.RED)
            stateMachine.changeState(
                Recording(
                    stateMachine,
                    view,
                    methodTraceRecorder,
                    settings,
                    logger,
                    shouldWaitForSystrace,
                    systracePrefix
                )
            )
        }

        override fun onStopPressed() {
            stateMachine.cancelRecordingJob()
            methodTraceRecorder.disconnect()
            view.initialState()
            stateMachine.idleState()
        }

        override fun onDialogClosed() {
            stateMachine.cancelRecordingJob()
            methodTraceRecorder.disconnect()
            view.initialState()
            stateMachine.idleState()
        }

        override fun onRecordingFailed(throwable: Throwable) {
            stateMachine.idleState()
            methodTraceRecorder.stopRecording()
            methodTraceRecorder.disconnect()

            logger.e("$TAG: fail while recording: ${throwable.message}", throwable)
            view.initialState()

            if (throwable is AppTimeoutException) {
                val msg = if (stateMachine.currentActivityName == null) {
                    "Did you started application manually? Or enter activity name and try again"
                } else {
                    "This can happens when Android Studio is opened, try to close it and try again."
                }
                view.showErrorDialog(message = msg, title = "Cant find process '${stateMachine.currentPackageName}'")
                return
            }

            view.showErrorDialog(if (throwable.message != null) throwable.message!! else "Unknown errror")
        }
    }

    private open class WaitForRecordingResult(
        private val stateMachine: JavaMethodsDialogLogic,
        private val view: JavaMethodsRecorderDialogView,
        private val methodTraceRecorder: MethodTraceRecorder,
        private val settings: SettingsRepository,
        private val logger: AppLogger,
        private val systraceResult: List<SystraceRecord> = emptyList()
    ) : State {
        override fun onTraceResultReceived(file: File) {
            logger.d("onMethodTraceReceived: $file")
            methodTraceRecorder.disconnect()
            stateMachine.result = RecordedResult(file, systraceResult)
            view.initialState()
            view.closeDialog()
            stateMachine.idleState()
        }

        override fun onLocalTraceReceived(remoteFilePath: String) {
            stateMachine.idleState()
            methodTraceRecorder.disconnect()
            logger.d("$TAG: onMethodTraceReceived $remoteFilePath")
            view.initialState()
            view.showInfoDialog("trace stored to remote device at $remoteFilePath")
        }

        override fun onWaitForResultTimeout() {
            stateMachine.idleState()
            stateMachine.cancelRecordingJob()
            methodTraceRecorder.disconnect()

            logger.d("$TAG: StopButtonTimeout invoked")
            view.showErrorDialog("Method trace recording error")
            view.initialState()
        }

        override fun onDialogClosed() {
            stateMachine.cancelRecordingJob()
            methodTraceRecorder.disconnect()
            view.initialState()
            stateMachine.idleState()
        }

        override fun onRecordingFailed(throwable: Throwable) {
            stateMachine.idleState()
            stateMachine.fail(throwable)
        }
    }

    private class WaitForRecordingResultWithSystrace(
        private val stateMachine: JavaMethodsDialogLogic,
        private val view: JavaMethodsRecorderDialogView,
        private val methodTraceRecorder: MethodTraceRecorder,
        private val settings: SettingsRepository,
        private val logger: AppLogger,
        private val systracePrefix: String?
    ) : WaitForRecordingResult(stateMachine, view, methodTraceRecorder, settings, logger) {

        override fun onTraceResultReceived(file: File) {
            logger.d("state is ${stateMachine.state.javaClass.simpleName}: onMethodTraceReceived: $file")
            stateMachine.changeState(
                WaitForSystraceResult(
                    stateMachine, view, methodTraceRecorder, settings, logger, file, systracePrefix
                )
            )
        }

        override fun onSystraceReceived(values: List<SystraceRecord>) {
            logger.d("onSystraceReceived: ${values.size}")
            stateMachine.changeState(
                WaitForRecordingResult(
                    stateMachine, view, methodTraceRecorder, settings, logger,
                    if (systracePrefix != null) values.filter { it.name.startsWith(systracePrefix) } else values
                )
            )
        }
    }

    private class WaitForSystraceResult(
        private val stateMachine: JavaMethodsDialogLogic,
        private val view: JavaMethodsRecorderDialogView,
        private val methodTraceRecorder: MethodTraceRecorder,
        private val settings: SettingsRepository,
        private val logger: AppLogger,
        private val traceFile: File,
        private val systracePrefix: String?
    ) : WaitForRecordingResult(stateMachine, view, methodTraceRecorder, settings, logger) {
        override fun onSystraceReceived(values: List<SystraceRecord>) {
            logger.d("onSystraceReceived: ${values.size}")
            methodTraceRecorder.disconnect()

            stateMachine.result = RecordedResult(
                traceFile,
                if (systracePrefix != null) values.filter { it.name.startsWith(systracePrefix) } else values
            )
            view.initialState()
            view.closeDialog()
            stateMachine.idleState()
        }
    }

    private class WaitForDevice(
        private val stateMachine: JavaMethodsDialogLogic,
        private val view: JavaMethodsRecorderDialogView,
        private val methodTraceRecorder: MethodTraceRecorder,
        private val settings: SettingsRepository,
        private val logger: AppLogger,
        private val shouldWaitForSystrace: Boolean,
        private val systracePrefix: String?
    ) : State {
        private val waitForApplicationColor = Color(0xD2691A)

        override fun onStartWaitingForApplication() {
            view.setStatusTextAndColor("Waiting for application...", waitForApplicationColor)
            stateMachine.changeState(
                WaitingForApplication(
                    stateMachine, view, methodTraceRecorder, settings, logger,
                    shouldWaitForSystrace, systracePrefix
                )
            )
        }

        override fun onStopPressed() {
            stateMachine.cancelRecordingJob()
            methodTraceRecorder.disconnect()
            view.initialState()
            stateMachine.idleState()
        }

        override fun onDialogClosed() {
            stateMachine.cancelRecordingJob()
            methodTraceRecorder.disconnect()
            view.initialState()
            stateMachine.idleState()
        }

        override fun onRecordingFailed(throwable: Throwable) {
            stateMachine.idleState()
            stateMachine.fail(throwable)
        }
    }

    private class PendingRecording(
        private val stateMachine: JavaMethodsDialogLogic,
        private val view: JavaMethodsRecorderDialogView,
        private val methodTraceRecorder: MethodTraceRecorder,
        private val settings: SettingsRepository,
        private val logger: AppLogger,
        private val shouldWaitForSystrace: Boolean,
        private val systracePrefix: String?
    ) : State {
        override fun onStopPressed() {
            stateMachine.cancelRecordingJob()
            methodTraceRecorder.disconnect()
            view.initialState()
            stateMachine.idleState()
        }

        override fun onDialogClosed() {
            stateMachine.cancelRecordingJob()
            methodTraceRecorder.disconnect()
            view.initialState()
            stateMachine.idleState()
        }

        override fun onRecordingFailed(throwable: Throwable) {
            stateMachine.idleState()
            stateMachine.fail(throwable)
        }

        override fun onStartWaitingForDevice() {
            view.setStatusTextAndColor("Wait for device...", Color.DARK_GRAY)
            stateMachine.changeState(
                WaitForDevice(
                    stateMachine, view, methodTraceRecorder, settings, logger,
                    shouldWaitForSystrace, systracePrefix
                )
            )
        }
    }

    private interface State {
        fun onStopPressed() = Unit
        fun onStartPressed(methodTraceRecorderFactory: MethodTraceRecorderFactory) = Unit
        fun onWaitForResultTimeout() = Unit
        fun onTraceResultReceived(file: File) = Unit
        fun onLocalTraceReceived(remoteFilePath: String) = Unit
        fun onDialogClosed() = Unit
        fun onRecordingFailed(throwable: Throwable)
        fun onSystraceReceived(values: List<SystraceRecord>) = Unit
        fun onStartedRecording() = Unit
        fun onStartWaitingForApplication() = Unit
        fun onStartWaitingForDevice() = Unit
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
