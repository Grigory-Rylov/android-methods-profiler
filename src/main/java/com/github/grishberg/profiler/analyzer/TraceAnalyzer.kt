package com.github.grishberg.profiler.analyzer

import com.android.tools.perflib.vmtrace.MethodInfo
import com.android.tools.perflib.vmtrace.TraceAction
import com.android.tools.perflib.vmtrace.VmTraceHandler
import com.android.tools.perflib.vmtrace.VmTraceParser
import com.github.grishberg.profiler.analyzer.converter.NameConverter
import com.github.grishberg.profiler.analyzer.converter.NoOpNameConverter
import com.github.grishberg.profiler.common.AppLogger
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class TraceAnalyzer(
    private val log: AppLogger
) {
    var nameConverter: NameConverter = NoOpNameConverter

    fun analyze(traceFile: File): AnalyzerResultImpl {
        val vmTraceHandler = OnVmTraceHandler(log, nameConverter)
        VmTraceParser(traceFile, vmTraceHandler).parse()

        for (threadId in vmTraceHandler.threads) {
            val traceDataForThread = vmTraceHandler.traceData.getOrDefault(threadId.key, mutableListOf())

            for (duration in traceDataForThread) {
                if (duration.threadEndTimeInMillisecond == 0.0) {
                    duration.threadEndTimeInMillisecond =
                        vmTraceHandler.threadTimeBounds.getOrDefault(threadId.key, ThreadTimeBoundsImpl()).maxTime
                }
                if (duration.globalEndTimeInMillisecond == 0.0) {
                    duration.globalEndTimeInMillisecond =
                        vmTraceHandler.globalTimeBounds.getOrDefault(threadId.key, ThreadTimeBoundsImpl()).maxTime
                }
            }

            for (duration in traceDataForThread) {
                updateSelfTime(duration)
            }
        }

        val sortedThreads = ArrayList<ThreadItemImpl>()

        var threadIndex = 0
        for (e in vmTraceHandler.threads) {
            val threadName = if (e.value == null) "Thread-$threadIndex" else e.value!!
            if (e.key != vmTraceHandler.mainThreadId) {
                sortedThreads.add(ThreadItemImpl(threadName, e.key))
                threadIndex++
            }
        }
        sortedThreads.sortBy { it.name }

        sortedThreads.add(
            0,
            ThreadItemImpl(
                vmTraceHandler.threads.getOrDefault(vmTraceHandler.mainThreadId, "main")!!,
                vmTraceHandler.mainThreadId
            )
        )

        return AnalyzerResultImpl(
            vmTraceHandler.threadTimeBounds,
            vmTraceHandler.globalTimeBounds,
            vmTraceHandler.maxLevel,
            vmTraceHandler.traceData,
            sortedThreads,
            vmTraceHandler.mainThreadId
        )
    }

    private fun updateSelfTime(current: ProfileDataImpl) {
        if (current.threadSelfTime > 0.0 && current.globalSelfTime > 0.0) {
            return
        }
        current.apply {
            threadSelfTime = threadEndTimeInMillisecond - threadStartTimeInMillisecond
            globalSelfTime = globalEndTimeInMillisecond - globalStartTimeInMillisecond
            for (child in children) {
                threadSelfTime -= child.threadEndTimeInMillisecond - child.threadStartTimeInMillisecond
                globalSelfTime -= child.globalEndTimeInMillisecond - child.globalStartTimeInMillisecond
                updateSelfTime(child)
            }
        }
    }

    internal class OnVmTraceHandler(
        private val log: AppLogger,
        private val nameConverter: NameConverter
    ) : VmTraceHandler {
        private var version: Int = -1

        var mainThreadIndex = 0
        val threads = mutableMapOf<Int, String?>()

        val properties = mutableMapOf<String?, String?>()

        val methodsAndClasses = MethodsAndClasses()
        val methods = mutableMapOf<Long, MethodInfo?>()

        val threadTimeBounds = mutableMapOf<Int, ThreadTimeBoundsImpl>()
        val globalTimeBounds = mutableMapOf<Int, ThreadTimeBoundsImpl>()
        var mainThreadId = -1
        var maxLevel = 0

        val traceData = mutableMapOf<Int, MutableList<ProfileDataImpl>>()
        val methodsStacksForThread = mutableMapOf<Int, MutableMap<Long, Stack<ProfileDataImpl>>>()
        val level = mutableMapOf<Int, Int>()
        val parents = mutableMapOf<Int, Stack<ProfileDataImpl>>()
        private var startTimeUs: Long = -1

        override fun setVersion(version: Int) {
            this.version = version
        }

        override fun setProperty(key: String?, value: String?) {
            properties[key] = value
        }

        override fun addThread(id: Int, name: String?) {
            threads[id] = name
            if (name == "main") {
                mainThreadId = id
                mainThreadIndex = threads.size - 1
            }
        }

        override fun addMethod(id: Long, info: MethodInfo?) {
            methods[id] = info
            info?.let {
                methodsAndClasses.put(id, MethodData(it.fullName, it.className, it.methodName))
            }
        }

        override fun addMethodAction(
            threadId: Int,
            methodId: Long,
            methodAction: TraceAction,
            threadTime: Int,
            globalTime: Int
        ) {
            if (methods[methodId] == null) {
                return
            }
            val methodInfo = methods[methodId]!!
            val thread = threads[threadId]

            val threadTimeBoundsForThread = threadTimeBounds.getOrPut(threadId) { ThreadTimeBoundsImpl() }
            val globalTimeBoundsForThread = globalTimeBounds.getOrPut(threadId) { ThreadTimeBoundsImpl() }

            if (level[threadId] == null) {
                level[threadId] = 0
            }
            if (methodAction != TraceAction.METHOD_ENTER) {
                val parentsStackForThread = parents.getOrDefault(threadId, Stack())
                val stacksForThread = methodsStacksForThread.getOrDefault(threadId, HashMap())

                val stack = stacksForThread[methodId]
                if (stack == null) {
                    log.d("There are no any stack for methodId=${methodId}, thread=$thread, startTime=$globalTime")
                } else {
                    if (parentsStackForThread.isNotEmpty()) {
                        parentsStackForThread.pop()
                    }
                    if (stack.isNotEmpty()) {
                        val data = stack.pop()
                        data.apply {
                            threadEndTimeInMillisecond = threadTime / 1000.0
                            globalEndTimeInMillisecond = globalTime / 1000.0
                        }
                        if (threadTimeBoundsForThread.maxTime < threadTime / 1000.0) {
                            threadTimeBoundsForThread.maxTime = threadTime / 1000.0
                        }
                        if (globalTimeBoundsForThread.maxTime < globalTime / 1000.0) {
                            globalTimeBoundsForThread.maxTime = globalTime / 1000.0
                        }
                        level[threadId] = level[threadId]!! - 1
                    } else {
                        log.w("Action $methodAction but stack is empty for thread=$threadId, startTime=$globalTime\"")
                    }
                }
            }

            if (methodAction == TraceAction.METHOD_ENTER) {
                var parentsStackForThread = parents[threadId]
                if (parentsStackForThread == null) {
                    parentsStackForThread = Stack()
                    parents[threadId] = parentsStackForThread
                }

                var stacksForThread: MutableMap<Long, Stack<ProfileDataImpl>>? = methodsStacksForThread[threadId]
                if (stacksForThread == null) {
                    stacksForThread = HashMap()
                    methodsStacksForThread[threadId] = stacksForThread
                }

                var stack: Stack<ProfileDataImpl>? = stacksForThread[methodId]
                if (stack == null) {
                    stack = Stack()
                    stacksForThread[methodId] = stack
                }
                val parent: ProfileDataImpl? = if (parentsStackForThread.isEmpty()) null else parentsStackForThread.peek()

                val convertedClassName = nameConverter.convertClassName(methodInfo.className)
                val convertedMethodName = nameConverter.convertMethodName(convertedClassName, methodInfo.methodName, methodInfo.signature)
                val duration = ProfileDataImpl(
                    "${convertedClassName}.${convertedMethodName}",
                    level.getOrDefault(threadId, -1),
                    threadStartTimeInMillisecond = threadTime / 1000.0,
                    globalStartTimeInMillisecond = globalTime / 1000.0,
                    parent = parent
                )
                parent?.addChild(duration)

                var traceDataForThread = traceData[threadId]
                if (traceDataForThread == null) {
                    traceDataForThread = ArrayList()
                    traceData[threadId] = traceDataForThread
                }
                traceDataForThread.add(duration)
                stack.push(duration)
                parentsStackForThread.push(duration)
                if (threadTimeBoundsForThread.minTime > threadTime / 1000.0) {
                    threadTimeBoundsForThread.minTime = threadTime / 1000.0
                }
                if (globalTimeBoundsForThread.minTime > threadTime / 1000.0) {
                    globalTimeBoundsForThread.minTime = threadTime / 1000.0
                }

                if (maxLevel < level[threadId]!!) {
                    maxLevel = level[threadId]!!
                }
                level[threadId] = level[threadId]!! + 1
            }
        }

        override fun setStartTimeUs(startTimeUs: Long) {
            this.startTimeUs = startTimeUs
        }
    }
}

