package com.github.grishberg.profiler.plugins.stages

import com.github.grishberg.android.profiler.core.AnalyzerResult
import com.github.grishberg.android.profiler.core.ProfileData
import com.github.grishberg.android.profiler.core.ThreadItem
import com.github.grishberg.profiler.common.AppLogger
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import java.io.*
import java.lang.reflect.Type

/**
 * Stages of application run.
 * Each stage has methods, when this methods are called necessary times, current stage is started.
 */
class Stages(
    private val stagesList: List<Stage>,
    // method name : Stage
    private val methodStages: Map<String, Stage>,
    private val packages: List<String>
) {
    var currentStage: Stage? = null
        private set

    private var nextStageIndex: Int = 0

    /**
     * Sets current stage by given method.
     */
    fun updateCurrentStage(method: ProfileData) {
        if (nextStageIndex >= stagesList.size) {
            return
        }
        val current = stagesList[nextStageIndex]
        current.onMethodCalled(method)

        if (current.isStarted) {
            currentStage = current
            nextStageIndex++
        }
        if (current.methods.isEmpty()) {
            updateCurrentStage(method)
        }
    }

    fun getMethodsStage(method: ProfileData): Stage? {
        return methodStages[method.name]
    }

    fun isMethodAvailable(method: ProfileData): Boolean {
        return isMethodAvailable(method, packages)
    }

    fun shouldMethodStageBeLaterThenCurrent(methodStage: Stage?): Boolean {
        if (methodStage == null) {
            return false
        }
        val current = currentStage ?: return false
        val currentPosition = stagesList.indexOf(current)
        val methodStagePosition = stagesList.indexOf(methodStage)
        return methodStagePosition > currentPosition
    }

    private data class StagesModel(
        val stages: List<Stage>,
        val methodStages: Map<String, List<String>>,
        val packages: List<String>
    )

    companion object {
        private const val TAG = "Stages"
        private val gson = GsonBuilder().enableComplexMapKeySerialization().setPrettyPrinting().create()

        fun loadFromJson(fn: File, logger: AppLogger): Stages {
            val listType: Type = object : TypeToken<StagesModel>() {}.type
            try {
                val fileReader = FileReader(fn)
                val reader = JsonReader(fileReader)
                val stagesModel: StagesModel = gson.fromJson(reader, listType)

                val result = mutableMapOf<String, Stage>()

                for (entry in stagesModel.methodStages.entries) {
                    // find stage by name
                    val stage = stagesModel.stages.firstOrNull { it.name == entry.key }
                    stage?.let {
                        for (method in entry.value) {
                            result[method] = it
                        }
                    }
                }
                return Stages(stagesModel.stages, result, stagesModel.packages)
            } catch (e: FileNotFoundException) {
                logger.d("$TAG: there is no bookmarks file.")
            } catch (e: Exception) {
                logger.e("$TAG: Cant load stages", e)
            }
            return Stages(emptyList(), emptyMap(), emptyList())
        }

        fun saveToFile(
            fileToSave: File,
            input: AnalyzerResult,
            thread: ThreadItem,
            packages: List<String>,
            logger: AppLogger
        ) {
            var outputStream: FileOutputStream? = null
            try {
                outputStream = FileOutputStream(fileToSave)
                val bufferedWriter = BufferedWriter(OutputStreamWriter(outputStream, "UTF-8"))

                val stages = listOf(
                    Stage("stage1", listOf(MethodWithIndex("foo"))),
                    Stage("stage2", listOf(MethodWithIndex("boo"))),
                    Stage("stage3", listOf(MethodWithIndex("moo")))
                )
                val methodsList = generateMethodList(input, thread, packages)
                val methodsByStages = mutableMapOf<String, List<String>>()
                methodsByStages["stage1"] = methodsList
                gson.toJson(StagesModel(stages, methodsByStages, packages), bufferedWriter)
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

        private fun generateMethodList(
            input: AnalyzerResult,
            thread: ThreadItem,
            packages: List<String>
        ): List<String> {
            val result = mutableListOf<String>()
            val methodsForThread = input.data[thread.threadId] ?: emptyList()
            for (method in methodsForThread) {
                if (isMethodAvailable(method, packages)) {
                    result.add(method.name)
                }
            }
            return result
        }

        private fun isMethodAvailable(method: ProfileData, packages: List<String>): Boolean {
            if (packages.isEmpty()) {
                return true
            }

            for (pkg in packages) {
                if (method.name.startsWith(pkg)) {
                    return true
                }
            }
            return false
        }
    }
}
