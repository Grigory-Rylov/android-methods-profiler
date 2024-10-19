package com.github.grishberg.profiler.plugins.stages.constructors

import com.github.grishberg.profiler.chart.stages.methods.StagesFacade
import com.github.grishberg.profiler.common.CoroutinesDispatchers
import com.github.grishberg.profiler.common.settings.SettingsFacade
import com.github.grishberg.profiler.core.ProfileData
import com.github.grishberg.profiler.plugins.stages.MethodNavigationAction
import com.github.grishberg.profiler.plugins.stages.Stage
import com.github.grishberg.profiler.plugins.stages.StagesFactory
import com.github.grishberg.profiler.plugins.stages.methods.StagesProvider
import com.github.grishberg.profiler.ui.dialogs.info.FocusElementDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.awt.Dialog
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

interface EarlyConstructorsUi {
    val owner: Dialog
    fun updateTitle(title: String)

    fun setStages(stages: List<Stage>)

    fun onCalculated()
}

class EarlyConstructorsLogicImpl(
    private val settings: SettingsFacade,
    private val coroutineScope: CoroutineScope,
    private val dispatchers: CoroutinesDispatchers,
    private val focusElementDelegate: FocusElementDelegate,
    private val stagesFactory: StagesFactory,
    private val stagesFacade: StagesFacade,
    allMethods: Map<Int, List<ProfileData>>,
    currentThreadId: Int,
    methods: List<ProfileData>,
) {

    private val analyzer = EarlyConstructorsAnalyzer(stagesFacade, allMethods, currentThreadId, methods)
    private var stageFile: File? = null
    var ui: EarlyConstructorsUi? = null

    fun startSearch(
        stage: Stage,
        methodMask: String,
        parentMethodMask: String,
        searchInOtherThreads: Boolean,
    ) {
        val selectedStagesFile = stageFile
        if (selectedStagesFile == null && !stagesFactory.hasLocalConfiguration()) {
            throw IllegalStateException("Started analyze without configuration or opened file")
        }

        val stagesProvider: StagesProvider = if (selectedStagesFile != null) {
            { stagesFactory.loadFromFile(selectedStagesFile) }
        } else {
            { stagesFactory.createFromLocalConfiguration()!! }
        }

        coroutineScope.launch {
            val result = coroutineScope.async(dispatchers.worker) {
                val stages = stagesProvider.invoke()
                analyzer.analyze(
                    stage, stages, methodMask, parentMethodMask, shouldFilterInnerClasses = true,
                    searchInOtherThreads = searchInOtherThreads,
                )
            }.await()

            val owner = ui?.owner ?: return@launch

            val dialog = EarlyConstructorsResultDialog(owner)
            dialog.dialogListener = object : MethodNavigationAction {
                override fun onProfileDataSelected(method: ProfileData) {
                    focusElementDelegate.focusProfileElement(method)
                }
            }

            dialog.show(owner)
            dialog.setData(result)
            ui?.onCalculated()

        }
    }

    fun openStagesFile() {
        val owner = ui?.owner ?: return

        val fileChooser = JFileChooser(settings.stagesFileDialogDir)
        fileChooser.fileFilter = FileNameExtensionFilter("Stage file, json", "json")

        val returnVal: Int = fileChooser.showOpenDialog(owner)

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            stageFile = fileChooser.selectedFile
            stageFile?.let {
                ui?.updateTitle("Stage file: ${it.name}")
                settings.stagesFileDialogDir = it.parent
            }

            val selectedStagesFile = stageFile
            val stagesProvider: StagesProvider = if (selectedStagesFile != null) {
                { stagesFactory.loadFromFile(selectedStagesFile) }
            } else {
                { stagesFactory.createFromLocalConfiguration()!! }
            }

            ui?.setStages(stagesProvider.invoke().stagesAsList)
        }
    }
}
