package com.github.grishberg.profiler.chart

import com.github.grishberg.profiler.common.CoroutinesDispatchers
import com.github.grishberg.profiler.core.AnalyzerResult
import com.github.grishberg.profiler.core.ProfileData
import com.github.grishberg.profiler.core.ThreadItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Finder(
    private val coroutineScope: CoroutineScope,
    private val dispatchers: CoroutinesDispatchers
) {
    interface FindResultListener {
        fun onFindDone(findResult: FindResult)
    }

    var listener: FindResultListener? = null

    var isSearchingModeEnabled = false
        private set

    private var currentSelectedThreadIndex: Int = -1

    private var currentFindResult: FindResult = FindResult(emptyList())

    fun getSearchResultThreadsCount(): Int {
        return currentFindResult.threadResults.size
    }

    fun getCurrentThreadResult(): ThreadFindResult = currentFindResult.threadResults[currentSelectedThreadIndex]

    fun getResultForThread(threadItem: ThreadItem): ThreadFindResult? {
        for (i in currentFindResult.threadResults.indices) {
            if (currentFindResult.threadResults[i].threadItem == threadItem) {
                return currentFindResult.threadResults[i]
            }
        }
        return null
    }

    fun setCurrentThreadResultForThread(threadItem: ThreadItem) {
        for (i in currentFindResult.threadResults.indices) {
            if (currentFindResult.threadResults[i].threadItem == threadItem) {
                currentSelectedThreadIndex = i
                return
            }
        }
    }

    fun getNextThread(): ThreadItem {
        val index = getNextThreadIndex()
        return currentFindResult.threadResults[index].threadItem
    }

    fun getPreviousThread(): ThreadItem {
        val index = getPreviousThreadIndex()
        return currentFindResult.threadResults[index].threadItem
    }

    fun switchNextThread() {
        currentSelectedThreadIndex = getNextThreadIndex()
    }

    fun disableSearching() {
        isSearchingModeEnabled = false
        currentSelectedThreadIndex = -1
        currentFindResult = FindResult(emptyList())
    }

    private fun getNextThreadIndex(): Int {
        if (currentSelectedThreadIndex == -1) {
            return -1
        }
        return if (currentSelectedThreadIndex + 1 >= currentFindResult.threadResults.size) {
            0
        } else {
            currentSelectedThreadIndex + 1
        }
    }

    fun switchPreviousThread() {
        currentSelectedThreadIndex = getPreviousThreadIndex()
    }

    private fun getPreviousThreadIndex(): Int {
        if (currentSelectedThreadIndex == -1) {
            return -1
        }

        return if (currentSelectedThreadIndex - 1 < 0) {
            currentFindResult.threadResults.size - 1
        } else {
            currentSelectedThreadIndex - 1
        }
    }

    fun findMethods(
        analyzerResult: AnalyzerResult,
        textToFind: String,
        ignoreCase: Boolean,
    ) {
        isSearchingModeEnabled = true
        currentSelectedThreadIndex = -1
        coroutineScope.launch {
            val data = withContext(dispatchers.worker) {
                findInThread(analyzerResult, textToFind, ignoreCase)
            }
            withContext(dispatchers.ui) {
                currentFindResult = data
                if (data.threadResults.isNotEmpty()) {
                    currentSelectedThreadIndex = 0
                }
                listener?.onFindDone(data)
            }
        }
    }

    private fun findInThread(
        analyzerResult: AnalyzerResult,
        textToFind: String,
        ignoreCase: Boolean,
        exceptThreadId: Int = -1
    ): FindResult {
        val result = mutableListOf<ThreadFindResult>()
        for (threadData in analyzerResult.data) {
            val threadId = threadData.key
            if (exceptThreadId == threadId) {
                continue
            }
            val profileList = threadData.value

            val shouldEndsWithText: Boolean = textToFind.endsWith("()")
            val targetString = prepareTextToFind(shouldEndsWithText, textToFind, ignoreCase)

            var totalGlobalDuration = 0.0
            var totalThreadDuration = 0.0
            val foundMethods = mutableSetOf<ProfileData>()
            for (i in profileList.indices) {
                val currentMethod = profileList[i]
                val lowerCasedName: String = if (ignoreCase) currentMethod.name.toLowerCase() else currentMethod.name
                val isEquals: Boolean =
                    if (shouldEndsWithText) lowerCasedName.endsWith(targetString) else lowerCasedName.contains(
                        targetString
                    )
                if (isEquals) {
                    foundMethods.add(currentMethod)
                    totalGlobalDuration = currentMethod.globalEndTimeInMillisecond - currentMethod.globalStartTimeInMillisecond
                    totalThreadDuration = currentMethod.threadEndTimeInMillisecond - currentMethod.threadStartTimeInMillisecond
                }
            }
            if (foundMethods.isNotEmpty()) {
                val threadItem = analyzerResult.threads.find { it.threadId == threadId }!!
                result.add(ThreadFindResult(foundMethods, threadId, threadItem, totalGlobalDuration, totalThreadDuration))
            }
        }

        return FindResult(result)
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

    data class FindResult(val threadResults: List<ThreadFindResult>) {
        fun generateFoundThreadNames(): String {
            if (threadResults.isEmpty()) {
                return ""
            }
            val threadNames = threadResults.map { it.threadItem.name }
            return threadNames.joinToString("\n")
        }

        fun getResultForThread(threadId: Int): ThreadFindResult? {
            return threadResults.find { it.threadId == threadId }
        }

        fun getThreadResult(threadItem: ThreadItem): ThreadFindResult =
            threadResults.find { it.threadItem == threadItem }!!

        fun getThreadList(): List<ThreadItem> = threadResults.map { it.threadItem }
    }

    data class ThreadFindResult(
        val foundResult: Set<ProfileData>,
        val threadId: Int,
        val threadItem: ThreadItem,
        val totalGlobalDuration: Double,
        val totalThreadDuration: Double
    ) {
        fun hasMethod(method: ProfileData) = foundResult.contains(method)
    }
}
