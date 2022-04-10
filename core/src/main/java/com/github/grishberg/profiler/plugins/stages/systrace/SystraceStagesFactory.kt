package com.github.grishberg.profiler.plugins.stages.systrace

import com.github.grishberg.profiler.core.ProfileData
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.plugins.stages.MethodsAvailability
import com.github.grishberg.profiler.plugins.stages.Stage
import com.github.grishberg.profiler.plugins.stages.Stages
import com.github.grishberg.profiler.plugins.stages.StagesFactory
import com.github.grishberg.profiler.plugins.stages.methods.StagesRelatedToMethods
import com.github.grishberg.profiler.plugins.stages.methods.StagesState
import com.github.grishberg.tracerecorder.SystraceRecord
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader
import java.lang.reflect.Type

private const val TAG = "SystraceStagesFactory"

class SystraceStagesFactory(
    private val methodsProvider: () -> Iterator<ProfileData>,
    private val recordsList: List<SystraceRecord>,
    private val methodsAvailability: MethodsAvailability,
    private val logger: AppLogger
) : StagesFactory {
    private val gson = GsonBuilder().enableComplexMapKeySerialization().setPrettyPrinting().create()

    override fun loadFromFile(file: File): Stages {
        val listType: Type = object : TypeToken<StagesModel>() {}.type
        try {
            val fileReader = FileReader(file)
            val reader = JsonReader(fileReader)
            val stagesModel: StagesModel = gson.fromJson(reader, listType)

            val stagesList = mutableListOf<StageFromSystrace>()
            for (record in recordsList) {
                stagesList.add(StageFromSystrace(record))
            }

            val methodsStagesMapping = mutableMapOf<String, Stage?>()
            for (entry in stagesModel.methodStages.entries) {
                // find stage by name
                val stage = stagesList.firstOrNull { it.name == entry.key }
                stage?.let {
                    for (method in entry.value) {
                        methodsStagesMapping[method] = it
                    }
                }
            }
            return StagesFromSystrace(
                SystraceStagesState(recordsList),
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
        val stagesList = mutableListOf<StageFromSystrace>()
        for (record in recordsList) {
            stagesList.add(StageFromSystrace(record))
        }

        val methodsStagesMapping = mutableMapOf<String, Stage?>()
        val alreadyAddedMethods = mutableSetOf<String>()

        val tempStageState = SystraceStagesState(recordsList)

        val methods = methodsProvider.invoke()
        for (method in methods) {
            val methodName = method.name

            tempStageState.updateCurrentStage(method)
            val currentStage = tempStageState.currentStage

            if (!methodsAvailability.isMethodAvailable(method) || alreadyAddedMethods.contains(methodName)) {
                continue
            }
            methodsStagesMapping[methodName] = currentStage
            alreadyAddedMethods.add(methodName)
        }

        return StagesFromSystrace(
            SystraceStagesState(recordsList),
            methodsStagesMapping,
            methodsAvailability,
            logger
        )
    }

    override fun hasLocalConfiguration(): Boolean = recordsList.isNotEmpty()

    data class StagesModel(
        val methodStages: Map<String, Set<String>>
    )
}
