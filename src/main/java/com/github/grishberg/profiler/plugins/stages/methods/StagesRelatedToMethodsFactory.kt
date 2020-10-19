package com.github.grishberg.profiler.plugins.stages.methods

import com.github.grishberg.android.profiler.core.ProfileData
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.plugins.stages.MethodsAvailability
import com.github.grishberg.profiler.plugins.stages.Stages
import com.github.grishberg.profiler.plugins.stages.StagesFactory
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader
import java.lang.reflect.Type

private const val TAG = "StagesRelatedToMethodsFactory"

class StagesRelatedToMethodsFactory(
    private val stagesList: List<StageRelatedToMethods>,
    private val methods: Iterator<ProfileData>,
    private val methodsAvailability: MethodsAvailability,
    private val logger: AppLogger,
    private val stagesLoadedAction: StagesLoadedFromFileAction? = null
) : StagesFactory {
    private val gson = GsonBuilder().enableComplexMapKeySerialization().setPrettyPrinting().create()

    override fun loadFromFile(file: File): Stages {
        val listType: Type = object : TypeToken<StagesModel>() {}.type
        try {
            val fileReader = FileReader(file)
            val reader = JsonReader(fileReader)
            val stagesModel: StagesModel = gson.fromJson(reader, listType)

            val methodsStagesMapping = mutableMapOf<String, StageRelatedToMethods?>()
            for (entry in stagesModel.methodStages.entries) {
                // find stage by name
                val stage = stagesModel.stages.firstOrNull { it.name == entry.key }
                stage?.let {
                    for (method in entry.value) {
                        methodsStagesMapping[method] = it
                    }
                }
            }
            stagesLoadedAction?.onStagesLoaded(stagesModel.stages)

            return StagesRelatedToMethods(
                StagesState(stagesModel.stages),
                methodsStagesMapping,
                methodsAvailability,
                logger
            )
        } catch (e: FileNotFoundException) {
            logger.d("$TAG: there is no bookmarks file.")
        } catch (e: Exception) {
            logger.e("$TAG: Cant load stages", e)
        }
        return StagesRelatedToMethods(StagesState(emptyList()), emptyMap(), methodsAvailability, logger)
    }

    override fun createFromLocalConfiguration(): Stages? {
        val stagesState = StagesState(stagesList)
        val methodsStagesMapping = mutableMapOf<String, StageRelatedToMethods?>()
        val alreadyAddedMethods = mutableSetOf<String>()

        for (method in methods) {
            val methodName = method.name
            stagesState.updateCurrentStage(methodName)

            if (!methodsAvailability.isMethodAvailable(method) || alreadyAddedMethods.contains(methodName)) {
                continue
            }
            methodsStagesMapping[methodName] = stagesState.currentStage
            alreadyAddedMethods.add(methodName)
        }
        return StagesRelatedToMethods(StagesState(stagesList), methodsStagesMapping, methodsAvailability, logger)
    }

    override fun hasLocalConfiguration(): Boolean = stagesList.isNotEmpty()

    class StagesModel(
        val stages: List<StageRelatedToMethods>,
        val methodStages: Map<String, Set<String>>,
        val packages: List<String>
    )
}
