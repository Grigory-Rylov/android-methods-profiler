package com.github.grishberg.profiler.plugins.stages

import com.github.grishberg.android.profiler.core.AnalyzerResult
import com.github.grishberg.android.profiler.core.ProfileData
import com.github.grishberg.android.profiler.core.ThreadItem
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.CoroutinesDispatchers
import com.github.grishberg.profiler.ui.dialogs.info.FocusElementDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.*
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.filechooser.FileNameExtensionFilter

private const val TAG = "StagesAnalyzerLogic"

class StagesAnalyzerLogic(
    private val owner: JFrame,
    private val ui: StageAnalyzerDialog,
    private val logger: AppLogger,
    private val input: AnalyzerResult,
    private val thread: ThreadItem,
    private val focusElementDelegate: FocusElementDelegate,
    private val coroutineScope: CoroutineScope,
    private val dispatchers: CoroutinesDispatchers
) : DialogListener {
    private var stageFile: File? = null
    private val cachedResult = mutableListOf<WrongStage>()

    init {
        ui.dialogListener = this
        ui.isVisible = true
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
        val selectedStages = stageFile ?: return
        val stages = Stages.loadFromJson(selectedStages, logger)
        val analyzer = StagesAnalyzer(stages)

        coroutineScope.launch {
            val result = coroutineScope.async(dispatchers.worker) {
                analyzer.analyze(input, thread)
            }.await()

            cachedResult.clear()
            cachedResult.addAll(result)
            ui.showResult(result)
            ui.enableCopyAndExportButtons()
        }
    }

    override fun openStagesFile() {
        val fileChooser = JFileChooser()
        fileChooser.fileFilter = FileNameExtensionFilter("Stage file, json", "json")

        val returnVal: Int = fileChooser.showOpenDialog(ui)

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            stageFile = fileChooser.selectedFile
            ui.enableStartButton()
            startAnalyze()
        }
    }

    override fun onGenerateStagesPressed() {
        val generateStagesDialog = GenerateStagesDialog(owner, input, thread, logger)
        generateStagesDialog.isVisible = true
    }

    override fun saveToFile() {
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
        logger.d("${TAG}: generating data: methods size = ${data.size}")
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
        logger.d("${TAG}: generating data: methods size = ${data.size}")
    }
}
