package com.github.grishberg.profiler.analyzer

import com.android.tools.profilers.cpu.CaptureNode
import com.android.tools.profilers.cpu.CpuCapture
import com.android.tools.profilers.cpu.CpuThreadInfo
import com.android.tools.profilers.cpu.nodemodel.CppFunctionModel
import com.android.tools.profilers.cpu.nodemodel.JavaMethodModel
import com.android.tools.profilers.cpu.nodemodel.NoSymbolModel
import com.android.tools.profilers.cpu.nodemodel.SyscallModel
import com.android.tools.profilers.cpu.simpleperf.SimpleperfTraceParser
import com.github.grishberg.profiler.analyzer.converter.NameConverter
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.core.AnalyzerResult
import com.github.grishberg.profiler.core.ExtendedData
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class SimplePerfAnalyzer(
    private val log: AppLogger,
    private val nameConverter: NameConverter,
) {

    fun analyze(traceFile: File): AnalyzerResult {
        val parser = SimpleperfTraceParser()
        val traceId = Random.nextLong()
        try {
            return captureToAnalyzerResult(parser.parse(traceFile, traceId))
        } catch (e: Exception) {
            throw WrongFormatException()
        }
    }

    private fun captureToAnalyzerResult(capture: CpuCapture): AnalyzerResult {
        val threads = mapThreads(capture.threads)

        val data = mutableMapOf<Int, List<ProfileDataImpl>>()
        var maxLevel = 0
        val threadTimeBounds = mutableMapOf<Int, ThreadTimeBoundsImpl>()
        val globalTimeBounds = mutableMapOf<Int, ThreadTimeBoundsImpl>()

        var minThreadTime = Long.MAX_VALUE
        var minGlobalTime = Long.MAX_VALUE

        for (thread in threads) {
            val currentThreadRoot = capture.getCaptureNode(thread.threadId) ?: continue
            val profileDataList = mutableListOf<ProfileDataImpl>()
            for (threadChild in currentThreadRoot.children) {
                val newRoot = mapCpuNode(threadChild)
                val level = processChildren(profileDataList, threadChild, newRoot)
                maxLevel = max(maxLevel, level)
            }
            threadTimeBounds[thread.threadId] = ThreadTimeBoundsImpl(
                minTime = convertTime(currentThreadRoot.startThread), maxTime = convertTime(currentThreadRoot.endThread)
            )
            globalTimeBounds[thread.threadId] = ThreadTimeBoundsImpl(
                minTime = convertTime(currentThreadRoot.startGlobal), maxTime = convertTime(currentThreadRoot.endGlobal)
            )

            minThreadTime = min(minThreadTime, currentThreadRoot.startThread)
            minGlobalTime = min(minGlobalTime, currentThreadRoot.startGlobal)
            data[thread.threadId] = profileDataList
        }

        return AnalyzerResultImpl(
            threadTimeBounds = threadTimeBounds,
            globalTimeBounds = globalTimeBounds,
            maxLevel = maxLevel,
            mutableData = data,
            threads = threads,
            mainThreadId = capture.mainThreadId,
            startTimeUs = -1,
            convertTime(minThreadTime),
            convertTime(minGlobalTime),
        )
    }

    private fun processChildren(allNodes: MutableList<ProfileDataImpl>, node: CaptureNode, newRoot: ProfileDataImpl): Int {
        var maxLevel = node.depth - 1
        node.children.forEachIndexed { index, captureNode ->
            val profileDataChild = mapCpuNode(captureNode)
            newRoot.addChild(profileDataChild)
            allNodes.add(newRoot)

            val level = processChildren(allNodes, captureNode, profileDataChild)
            maxLevel = max(maxLevel, level)

        }
        return maxLevel
    }

    private fun mapThreads(threads: Set<CpuThreadInfo>): List<ThreadItemImpl> {
        return threads.map { ThreadItemImpl(it.name, it.id) }.sortedBy { it.threadId }
    }

    private fun mapCpuNode(cpuNode: CaptureNode): ProfileDataImpl {
        val data = cpuNode.data
        val profileData = ProfileDataImpl(
            name = data.fullName,
            level = cpuNode.depth - THREAD_DEPTH_OFFSET,
            threadStartTimeInMillisecond = convertTime(cpuNode.startThread),
            threadEndTimeInMillisecond = convertTime(cpuNode.endThread),
            globalStartTimeInMillisecond = convertTime(cpuNode.startGlobal),
            globalEndTimeInMillisecond = convertTime(cpuNode.endGlobal),
        )
        when (data) {
            is CppFunctionModel -> {
                profileData.extendedData = ExtendedData.CppFunctionData(
                    tag = data.tag,
                    id = data.id,
                    fullName = data.fullName,
                    classOrNamespace = data.classOrNamespace,
                    parameters = data.parameters,
                    isUserCode = data.isUserCode,
                    fileName = data.fileName,
                    vAddress = data.vAddress,
                )
            }

            is JavaMethodModel -> {
                profileData.extendedData = ExtendedData.JavaMethodData(
                    tag = data.tag,
                    id = data.id,
                    fullName = data.fullName,
                    className = data.className,
                    signature = data.signature,
                )
            }

            is NoSymbolModel -> {
                profileData.extendedData = ExtendedData.NoSymbolData(
                    tag = data.tag,
                    id = data.id,
                    fullName = data.fullName,
                    isKernel = data.isKernel,
                )
            }

            is SyscallModel -> {
                profileData.extendedData = ExtendedData.SyscallData(
                    tag = data.tag,
                    id = data.id,
                    fullName = data.fullName,
                )
            }
        }

        return profileData
    }

    private fun convertTime(time: Long): Double {
        return time.toDouble() / 1000.0
    }

    private companion object {
        private const val THREAD_DEPTH_OFFSET = 1
    }
}
