package com.github.grishberg.profiler.plugins.stages.methods

import com.github.grishberg.profiler.core.ProfileData
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.plugins.stages.MethodsAvailability
import com.github.grishberg.profiler.plugins.stages.MethodsAvailabilityImpl
import com.github.grishberg.profiler.plugins.stages.Stage
import com.github.grishberg.profiler.plugins.stages.Stages
import com.google.gson.GsonBuilder
import java.io.BufferedWriter
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter

private const val EMPTY_STAGE_NAME = "unknown"
private const val TAG = "StagesRelatedToMethods"

/**
 * Stages of application run.
 * Each stage has methods, when this methods are called necessary times, current stage is started.
 */
class StagesRelatedToMethods(
    private val stagesState: StagesState,
    // method name : Stage
    private val methodStages: Map<String, StageRelatedToMethods?>,
    private val methodsAvailability: MethodsAvailability,
    private val logger: AppLogger
) : Stages {
    override val currentStage: Stage?
        get() = stagesState.currentStage

    val stagesList: List<StageRelatedToMethods>
        get() = stagesState.stagesList

    override val stagesAsList: List<Stage>
        get() = ArrayList(stagesList)

    override fun init() {
        stagesState.initialState()
    }

    override fun updateCurrentStage(method: ProfileData) {
        stagesState.updateCurrentStage(method.name)
    }

    override fun getMethodsStage(method: ProfileData): Stage? {
            return methodStages[method.name]
    }

    override fun shouldMethodStageBeLaterThenCurrent(methodStage: Stage?): Boolean =
        stagesState.shouldMethodStageBeLaterThenCurrent(methodStage)

    override fun saveToFile(
        fileToSave: File,
        input: List<ProfileData>
    ) {
        val gson = GsonBuilder().enableComplexMapKeySerialization().setPrettyPrinting().create()

        var outputStream: FileOutputStream? = null
        try {
            outputStream = FileOutputStream(fileToSave)
            val bufferedWriter = BufferedWriter(OutputStreamWriter(outputStream, "UTF-8"))

            val methodsByStages = mutableMapOf<String, MutableSet<String>>()
            val stagesState = StagesState(stagesList)
            var currentStageName = EMPTY_STAGE_NAME
            val alreadyAddedMethods = mutableSetOf<String>()

            for (method in input) {
                val methodName = method.name
                if (stagesState.updateCurrentStage(methodName)) {
                    currentStageName = stagesState.currentStage?.name ?: EMPTY_STAGE_NAME
                }

                if (!methodsAvailability.isMethodAvailable(method) || alreadyAddedMethods.contains(methodName)) {
                    continue
                }

                val methodsForStage = methodsByStages.getOrPut(currentStageName, { mutableSetOf() })
                methodsForStage.add(methodName)
                alreadyAddedMethods.add(methodName)
            }

            gson.toJson(
                StagesRelatedToMethodsFactory.StagesModel(stagesList, methodsByStages, emptyList()),
                bufferedWriter
            )
            bufferedWriter.close()
        } catch (e: FileNotFoundException) {
            logger.e("$TAG: save bookmarks error", e)
        } catch (e: IOException) {
            logger.e("$TAG: save bookmarks error", e)
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.flush()
                    outputStream.close()
                } catch (e: IOException) {
                }
            }
        }
    }

    companion object {
        fun createFromStagesListAndMethods(
            methods: Iterator<ProfileData>,
            stagesList: List<StageRelatedToMethods>,
            logger: AppLogger
        ): StagesRelatedToMethods {
            val methodsAvailability = MethodsAvailabilityImpl()
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
    }
}
