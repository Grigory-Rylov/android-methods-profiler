package com.github.grishberg.profiler.chart

import com.github.grishberg.profiler.core.ThreadItem
import com.github.grishberg.profiler.core.AnalyzerResult
import com.github.grishberg.profiler.core.ProfileData

class Finder {
    private val currentFindResult = mutableListOf<FindResult>()

    fun lastFindResult(): List<FindResult> = currentFindResult

    fun findInThread(
        analyzerResult: AnalyzerResult,
        textToFind: String,
        ignoreCase: Boolean,
        exceptThreadId: Int = -1
    ): List<FindResult> {
        val result = mutableListOf<FindResult>()
        for (threadData in analyzerResult.data) {
            val threadId = threadData.key
            if (exceptThreadId == threadId) {
                continue
            }
            val profileList = threadData.value

            val shouldEndsWithText: Boolean = textToFind.endsWith("()")
            val targetString = prepareTextToFind(shouldEndsWithText, textToFind, ignoreCase)

            val foundMethods = mutableListOf<ProfileData>()
            for (i in profileList.indices) {
                val currentMethod = profileList[i]
                val lowerCasedName: String = if (ignoreCase) currentMethod.name.toLowerCase() else currentMethod.name
                val isEquals: Boolean =
                    if (shouldEndsWithText) lowerCasedName.endsWith(targetString) else lowerCasedName.contains(
                        targetString
                    )
                if (isEquals) {
                    foundMethods.add(currentMethod)
                }
            }
            if (foundMethods.isNotEmpty()) {
                val threadItem = analyzerResult.threads.find { it.threadId == threadId }!!
                result.add(FindResult(foundMethods, threadId, threadItem))
            }
        }

        currentFindResult.clear()
        currentFindResult.addAll(currentFindResult)
        return result
    }

    fun generateFoundThreadNames(): String {
        if (currentFindResult.isEmpty()) {
            return ""
        }
        val threadNames = currentFindResult.map { it.threadItem.name }
        return threadNames.joinToString("\n")
    }

    fun getResultForThread(threadId: Int): FindResult? {
        return currentFindResult.find { it.threadId == threadId }
    }

    private fun prepareTextToFind(
        shouldEndsWithText: Boolean,
        textToFind: String,
        ignoreCase: Boolean
    ): String {
        val text = if (shouldEndsWithText) {
            textToFind.substring(0, textToFind.length - 2)
        } else {
            textToFind
        }
        val targetString = if (ignoreCase) text.toLowerCase() else text
        return targetString
    }

    data class FindResult(
        val foundResult: List<ProfileData>,
        val threadId: Int,
        val threadItem: ThreadItem
    )
}
