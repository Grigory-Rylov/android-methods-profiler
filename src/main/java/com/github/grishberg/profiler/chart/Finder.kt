package com.github.grishberg.profiler.chart

import com.github.grishberg.android.profiler.core.ProfileData
import com.github.grishberg.profiler.analyzer.AnalyzerResultImpl

class Finder(
    private val analyzerResult: AnalyzerResultImpl
) {
    fun findInThread(textToFind: String, ignoreCase: Boolean, exceptThreadId: Int = -1): FindResult {
        for (currentData in analyzerResult.data) {
            val threadId = currentData.key
            if (exceptThreadId == threadId) {
                continue
            }
            val profileList = currentData.value


            val shouldEndsWithText: Boolean = textToFind.endsWith("()")
            val targetString = prepareTextToFind(shouldEndsWithText, textToFind, ignoreCase)

            for (i in profileList.indices) {
                val profileData = profileList[i]
                val lowerCasedName: String = if (ignoreCase) profileData.name.toLowerCase() else profileData.name
                val isEquals: Boolean =
                    if (shouldEndsWithText) lowerCasedName.endsWith(targetString) else lowerCasedName.contains(
                        targetString
                    )
                if (isEquals) {
                    return FindResult(listOf(profileData), threadId)
                }
            }
        }

        return FindResult(emptyList(), -1)
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

    data class FindResult(val foundResult: List<ProfileData>, val threadId: Int)
}
