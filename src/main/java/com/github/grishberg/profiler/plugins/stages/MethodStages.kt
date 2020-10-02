package com.github.grishberg.profiler.plugins.stages

import com.github.grishberg.profiler.common.AppLogger
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader
import java.lang.reflect.Type

class MethodStages {
    companion object {
        private const val TAG = "MethodStages"
        private val gson = GsonBuilder().enableComplexMapKeySerialization().setPrettyPrinting().create()

        fun loadFromJson(
            fn: File,
            stages: List<Stage>,
            logger: AppLogger
        ): Map<String, Stage> {
            val listType: Type = object : TypeToken<Map<String, List<String>>>() {}.type
            try {
                val fileReader = FileReader(fn)
                val reader = JsonReader(fileReader)
                // stage : [methods]
                val stagesList: Map<String, List<String>> = gson.fromJson(reader, listType)

                val result = mutableMapOf<String, Stage>()

                for (entry in stagesList.entries) {
                    // find stage by name
                    val stage = stages.filter { it.name == entry.key }.firstOrNull()
                    stage?.let {
                        for (method in entry.value) {
                            result[method] = it
                        }
                    }
                }

                return result
            } catch (e: FileNotFoundException) {
                logger.d("$TAG: there is no bookmarks file.")
            } catch (e: Exception) {
                logger.e("$TAG: Cant load bookmarks", e)
            }
            return emptyMap()
        }
    }
}
