package com.github.grishberg.profiler.plugins.stages

import com.github.grishberg.android.profiler.core.ProfileData
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
    private val stagesState: StagesState,
    // method name : Stage
    private val methodStages: Map<String, Stage?>,
    private val packages: List<String>
) {
    val currentStage: Stage?
        get() = stagesState.currentStage

    val stagesList: List<Stage>
        get() = stagesState.stagesList

    fun init() {
        stagesState.initialState()
    }

    /**
     * Sets current stage by given method.
     */
    fun updateCurrentStage(method: ProfileData) {
        stagesState.updateCurrentStage(method.name)
    }

    fun getMethodsStage(method: ProfileData): Stage? {
        return methodStages[method.name]
    }

    fun isMethodAvailable(method: ProfileData): Boolean {
        return isMethodAvailable(method, packages)
    }

    fun shouldMethodStageBeLaterThenCurrent(methodStage: Stage?): Boolean =
        stagesState.shouldMethodStageBeLaterThenCurrent(methodStage)


    private data class StagesModel(
        val stages: List<Stage>,
        val methods: List<String>,
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

                val methodsStagesMapping = mutableMapOf<String, Stage?>()

                val stagesState = StagesState(stagesModel.stages)

                for (method in stagesModel.methods) {
                    stagesState.updateCurrentStage(method)
                    methodsStagesMapping[method] = stagesState.currentStage
                }
                return Stages(StagesState(stagesModel.stages), methodsStagesMapping, stagesModel.packages)
            } catch (e: FileNotFoundException) {
                logger.d("$TAG: there is no bookmarks file.")
            } catch (e: Exception) {
                logger.e("$TAG: Cant load stages", e)
            }
            return Stages(StagesState(emptyList()), emptyMap(), emptyList())
        }

        fun createFromStagesListAndMethods(
            methods: List<ProfileData>,
            stagesList: List<Stage>,
            logger: AppLogger
        ): Stages {
            val stagesState = StagesState(stagesList)
            val methodsStagesMapping = mutableMapOf<String, Stage?>()

            for (method in methods) {
                stagesState.updateCurrentStage(method.name)
                methodsStagesMapping[method.name] = stagesState.currentStage
            }
            return Stages(StagesState(stagesList), methodsStagesMapping, emptyList())
        }

        fun saveTemplateToFile(
            fileToSave: File,
            input: List<ProfileData>,
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
                val methodsList = generateMethodList(input, packages)
                gson.toJson(StagesModel(stages, methodsList, packages), bufferedWriter)
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

        fun saveToFile(
            fileToSave: File,
            input: List<ProfileData>,
            stages: List<Stage>,
            packages: List<String>,
            logger: AppLogger
        ) {
            var outputStream: FileOutputStream? = null
            try {
                outputStream = FileOutputStream(fileToSave)
                val bufferedWriter = BufferedWriter(OutputStreamWriter(outputStream, "UTF-8"))

                val methodsList = generateMethodList(input, packages)
                gson.toJson(StagesModel(stages, methodsList, packages), bufferedWriter)
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
            input: List<ProfileData>,
            packages: List<String>
        ): List<String> {
            val result = mutableListOf<String>()
            for (method in input) {
                if (isMethodAvailable(method, packages)) {
                    result.add(method.name)
                }
            }
            return result
        }

        private fun isMethodAvailable(method: ProfileData, packages: List<String>): Boolean {
            if (isExcludedPackagePrefix(method)) return false

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

        private fun isExcludedPackagePrefix(method: ProfileData): Boolean {
            for (pkg in IgnoredMethods.exceptions) {
                if (method.name.startsWith(pkg)) {
                    return true
                }
            }
            return false
        }
    }
}
