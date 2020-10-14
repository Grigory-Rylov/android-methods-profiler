package com.github.grishberg.profiler.plugins.stages.methods

import com.github.grishberg.android.profiler.core.ProfileData
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.CoroutinesDispatchers
import com.github.grishberg.profiler.common.settings.SettingsRepository
import com.github.grishberg.profiler.plugins.stages.DialogListener
import com.github.grishberg.profiler.plugins.stages.MethodsAvailability
import com.github.grishberg.profiler.plugins.stages.StageAnalyzerDialog
import com.github.grishberg.profiler.plugins.stages.Stages
import com.github.grishberg.profiler.plugins.stages.StagesAnalyzer
import com.github.grishberg.profiler.plugins.stages.StagesFactory
import com.github.grishberg.profiler.plugins.stages.WrongStage
import com.github.grishberg.profiler.ui.dialogs.info.FocusElementDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private const val TAG = "StagesAnalyzerLogic"
private const val SETTINGS_STAGES_FILE_DIALOG_DIR = "Plugins.stagesFileDialogDirectory"

typealias StagesProvider = () -> Stages

interface StagesLoadedFromFileAction {
    fun onStagesLoaded(stagesList: List<StageRelatedToMethods>)
}

class StagesAnalyzerLogic(
    private val analyzer: StagesAnalyzer,
    private val ui: StageAnalyzerDialog,
    private val settings: SettingsRepository,
    private val methods: List<ProfileData>,
    private val focusElementDelegate: FocusElementDelegate,
    private val coroutineScope: CoroutineScope,
    private val dispatchers: CoroutinesDispatchers,
    private val stagesFactory: StagesFactory,
    private val methodsAvailability: MethodsAvailability,
    private val logger: AppLogger
) : DialogListener {
    private var stageFile: File? = null
    private val cachedResult = mutableListOf<WrongStage>()

    init {
        ui.dialogListener = this
        if (stagesFactory.hasLocalConfiguration()) {
            ui.enableSaveStagesButton()
            ui.enableStartButton()
        }
        ui.showDialog()
    }

    override fun copyToClipboard() {
        val baos = ByteArrayOutputStream()
        writeResultToOutputStream(baos, cachedResult)

        val str = baos.toString()
        val stringSelection = StringSelection(str)
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(stringSelection, null)
    }

    override fun onProfileDataSelected(method: ProfileData) {
        focusElementDelegate.focusProfileElement(method)
    }

    override fun startAnalyze() {
        ui.showProgress()
        val selectedStagesFile = stageFile
        val stagesProvider: StagesProvider
        if (selectedStagesFile == null && !stagesFactory.hasLocalConfiguration()) {
            throw IllegalStateException("Started analyze without configuration or opened file")
        }

        stagesProvider = if (selectedStagesFile != null) {
            { stagesFactory.loadFromFile(selectedStagesFile) }
        } else {
            { stagesFactory.createFromLocalConfiguration()!! }
        }

        coroutineScope.launch {
            val result = coroutineScope.async(dispatchers.worker) {
                val stages =  stagesProvider.invoke()
                analyzer.analyze(stages, methodsAvailability, methods)
            }.await()

            cachedResult.clear()
            cachedResult.addAll(result)
            ui.showResult(cachedResult)
            ui.enableExportButtons()
        }
    }

    override fun openStagesFile() {
        val fileChooser = JFileChooser(settings.getStringValue(SETTINGS_STAGES_FILE_DIALOG_DIR))
        fileChooser.fileFilter = FileNameExtensionFilter("Stage file, json", "json")

        val returnVal: Int = fileChooser.showOpenDialog(ui)

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            stageFile = fileChooser.selectedFile
            stageFile?.let {
                ui.updateTitle("Stage file: ${it.name}")
                settings.setStringValue(SETTINGS_STAGES_FILE_DIALOG_DIR, it.parent)
            }
            ui.enableStartButton()
            startAnalyze()
        }
    }

    override fun onSaveStagesClicked() {
        val fileChooser = JFileChooser(settings.getStringValue(SETTINGS_STAGES_FILE_DIALOG_DIR))
        fileChooser.dialogTitle = "Specify a file to save stages"
        val filter = FileNameExtensionFilter("Stages json", "json")
        fileChooser.fileFilter = filter

        val userSelection = fileChooser.showSaveDialog(ui)

        if (userSelection == JFileChooser.APPROVE_OPTION) {
            var fileToSave = fileChooser.selectedFile
            if (fileToSave.extension.toLowerCase() != "json") {
                fileToSave = File(fileToSave.absolutePath + ".json")
            }
            settings.setStringValue(SETTINGS_STAGES_FILE_DIALOG_DIR, fileToSave.parent)
            ui.disableSaveStagesButton()

            coroutineScope.launch {
                withContext(dispatchers.worker){
                    stagesFactory.createFromLocalConfiguration()?.saveToFile(fileToSave, methods)
                }
                ui.enableSaveStagesButton()
            }
        }
    }

    override fun onExportReportToFileClicked() {
        val fileChooser = JFileChooser()
        fileChooser.dialogTitle = "Specify a file to save"
        val filter = FileNameExtensionFilter("Text files", "txt")
        fileChooser.fileFilter = filter
        val userSelection = fileChooser.showSaveDialog(ui)
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            var fileToSave = fileChooser.selectedFile
            if (fileToSave.extension.toLowerCase() != "txt") {
                fileToSave = File(fileToSave.absolutePath + ".txt")
                logger.d("$TAG: Saving analyze result info int file '$fileToSave'")
                generate(fileToSave, cachedResult)
            }
        }
    }

    private fun generate(file: File, data: List<WrongStage>) {
        logger.d("$TAG: generating data: methods size = ${data.size}")
        val fos = FileOutputStream(file)

        writeResultToOutputStream(fos, data)
    }

    private fun writeResultToOutputStream(
        fos: OutputStream,
        data: List<WrongStage>
    ) {
        BufferedWriter(OutputStreamWriter(fos)).use { bw ->
            bw.write("Current Stage\tMethod name\tGlobal time\tThread time\tValid Stage")
            bw.newLine()
            data.forEach {
                val method = it.method
                val threadDuration =
                    method.threadEndTimeInMillisecond - method.threadStartTimeInMillisecond
                val globalDuration =
                    method.globalEndTimeInMillisecond - method.globalStartTimeInMillisecond
                bw.write(
                    String.format(
                        "%s\t%s\t%.3f\t%.3f\t%s",
                        it.currentStage?.name ?: "-",
                        method.name, globalDuration, threadDuration,
                        it.correctStage?.name ?: "<unknown>"
                    )
                )
                bw.newLine()
            }
        }
        logger.d("$TAG: generating data: methods size = ${data.size}")
    }
}
