package com.github.grishberg.profiler.ui.dialogs.info

import com.github.grishberg.profiler.core.ProfileData
import com.github.grishberg.profiler.chart.DependenciesFoundAction
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.settings.SettingsFacade
import com.github.grishberg.profiler.ui.Main
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private const val TAG = "DependenciesDialogLogic"

class DependenciesDialogLogic(
    owner: Frame,
    private val settings: SettingsFacade,
    private val focusElementDelegate: FocusElementDelegate,
    private val logger: AppLogger
) : DependenciesFoundAction {
    private val dialog = DependenciesListDialog(owner, this)
    private var dependencies: List<ProfileData>? = null

    fun onProfileDataSelected(profileData: ProfileData) {
        focusElementDelegate.selectProfileElement(profileData)
    }

    override fun onDependenciesFound(dependencies: List<ProfileData>) {
        this.dependencies = dependencies
        dialog.setDependencies(dependencies)
        dialog.isVisible = true
    }

    fun copyToClipboard() {
        dependencies?.let {
            val stringSelection = StringSelection(it.joinToString("\n") { it.name })
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(stringSelection, null)
        }
    }

    fun saveToFile() {
        val fileChooser = JFileChooser(settings.reportsFileDialogDir)
        fileChooser.dialogTitle = "Specify a file to save"
        val filter = FileNameExtensionFilter("Text files", "txt")
        fileChooser.fileFilter = filter
        val userSelection = fileChooser.showSaveDialog(dialog)
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            var fileToSave = fileChooser.selectedFile
            if (fileToSave.extension.toLowerCase() != "txt") {
                fileToSave = File(fileToSave.absolutePath + ".txt")
                settings.reportsFileDialogDir =  fileToSave.parent
                logger.d("$TAG: Saving methods info int file '$fileToSave'")
                dependencies?.let {
                    generate(fileToSave, it)
                }
            }
        }
    }

    private fun generate(file: File, data: List<ProfileData>) {
        logger.d("$TAG: generating data: methods size = ${data.size}")
        val fos = FileOutputStream(file)

        BufferedWriter(OutputStreamWriter(fos)).use { bw ->
            bw.write("name\tglobal time\tthread time\tglobal self time\tthread self time")
            bw.newLine()
            data.forEach {
                val threadDuration =
                    it.threadEndTimeInMillisecond - it.threadStartTimeInMillisecond
                val globalDuration =
                    it.globalEndTimeInMillisecond - it.globalStartTimeInMillisecond
                bw.write(
                    String.format(
                        "%s\t%.3f\t%.3f\t%.3f\t%.3f",
                        it.name, globalDuration, threadDuration,
                        it.globalSelfTime, it.threadSelfTime
                    )
                )
                bw.newLine()
            }
        }
        logger.d("$TAG: generating data: methods size = ${data.size}")
    }
}
