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
        val methodStages: Map<String, Set<String>>,
        val packages: List<String>
    )

    companion object {

        private const val EMPTY_STAGE_NAME = "unknown"
        private const val TAG = "Stages"
        private val gson = GsonBuilder().enableComplexMapKeySerialization().setPrettyPrinting().create()

        fun loadFromJson(fn: File, logger: AppLogger): Stages {
            val listType: Type = object : TypeToken<StagesModel>() {}.type
            try {
                val fileReader = FileReader(fn)
                val reader = JsonReader(fileReader)
                val stagesModel: StagesModel = gson.fromJson(reader, listType)

                val methodsStagesMapping = mutableMapOf<String, Stage?>()
                for (entry in stagesModel.methodStages.entries) {
                    // find stage by name
                    val stage = stagesModel.stages.firstOrNull { it.name == entry.key }
                    stage?.let {
                        for (method in entry.value) {
                            methodsStagesMapping[method] = it
                        }
                    }
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
            methods: Iterator<ProfileData>,
            stagesList: List<Stage>,
            logger: AppLogger
        ): Stages {
            val stagesState = StagesState(stagesList)
            val methodsStagesMapping = mutableMapOf<String, Stage?>()
            val alreadyAddedMethods = mutableSetOf<String>()

            for (method in methods) {
                val methodName = method.name
                stagesState.updateCurrentStage(methodName)

                if (!isMethodAvailable(method, emptyList()) || alreadyAddedMethods.contains(methodName)) {
                    continue
                }
                methodsStagesMapping[methodName] = stagesState.currentStage
                alreadyAddedMethods.add(methodName)
            }
            return Stages(StagesState(stagesList), methodsStagesMapping, emptyList())
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

                val methodsByStages = mutableMapOf<String, MutableSet<String>>()
                val stagesState = StagesState(stages)
                var currentStageName = EMPTY_STAGE_NAME
                val alreadyAddedMethods = mutableSetOf<String>()

                for (method in input) {
                    val methodName = method.name
                    if (stagesState.updateCurrentStage(methodName)) {
                        currentStageName = stagesState.currentStage?.name ?: EMPTY_STAGE_NAME
                    }

                    if (!isMethodAvailable(method, packages) || alreadyAddedMethods.contains(methodName)) {
                        continue
                    }

                    val methodsForStage = methodsByStages.getOrPut(currentStageName, { mutableSetOf() })
                    methodsForStage.add(methodName)
                    alreadyAddedMethods.add(methodName)
                }

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
