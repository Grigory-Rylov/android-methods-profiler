package com.github.grishberg.profiler.plugins.stages.systrace

import com.github.grishberg.android.profiler.core.ProfileData
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.plugins.stages.EMPTY_STAGE_NAME
import com.github.grishberg.profiler.plugins.stages.MethodsAvailability
import com.github.grishberg.profiler.plugins.stages.Stage
import com.github.grishberg.profiler.plugins.stages.Stages
import com.google.gson.GsonBuilder
import java.io.BufferedWriter
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter

private const val TAG = "StagesFromSystrace"

class StagesFromSystrace(
    private val stagesState: SystraceStagesState,
    private val methodStages: Map<String, Stage?>,
    private val methodsAvailability: MethodsAvailability,
    private val logger: AppLogger,
) : Stages {
    override val currentStage: Stage?
        get() = stagesState.currentStage

    override fun init() {
        stagesState.init()
    }

    override fun updateCurrentStage(method: ProfileData) {
        stagesState.updateCurrentStage(method)
    }

    override fun getMethodsStage(method: ProfileData): Stage? = methodStages[method.name]

    override fun shouldMethodStageBeLaterThenCurrent(methodStage: Stage?): Boolean =
        stagesState.shouldMethodStageBeLaterThenCurrent(methodStage)

    override fun saveToFile(file: File, input: List<ProfileData>) {
        val gson = GsonBuilder().enableComplexMapKeySerialization().setPrettyPrinting().create()

        var outputStream: FileOutputStream? = null
        try {
            outputStream = FileOutputStream(file)
            val bufferedWriter = BufferedWriter(OutputStreamWriter(outputStream, "UTF-8"))

            val methodsByStages = mutableMapOf<String, MutableSet<String>>()

            val alreadyAddedMethods = mutableSetOf<String>()
            val tempStagesState = stagesState.clone()
            for (method in input) {
                val methodName = method.name

                tempStagesState.updateCurrentStage(method)
                val currentStage = tempStagesState.currentStage
                val currentStageName = currentStage?.name ?: EMPTY_STAGE_NAME

                if (!methodsAvailability.isMethodAvailable(method) || alreadyAddedMethods.contains(methodName)) {
                    continue
                }

                val methodsForStage = methodsByStages.getOrPut(currentStageName, { mutableSetOf() })
                methodsForStage.add(methodName)
                alreadyAddedMethods.add(methodName)
            }

            gson.toJson(
                SystraceStagesFactory.StagesModel(methodsByStages),
                bufferedWriter
            )
            bufferedWriter.close()
        } catch (e: FileNotFoundException) {
            logger.e("${TAG}: save bookmarks error", e)
        } catch (e: IOException) {
            logger.e("${TAG}: save bookmarks error", e)
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
}
