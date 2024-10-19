package com.github.grishberg.profiler.plugins.stages.constructors

import com.github.grishberg.profiler.chart.stages.methods.StageRectangle
import com.github.grishberg.profiler.chart.stages.methods.StagesFacade
import com.github.grishberg.profiler.core.ProfileData
import com.github.grishberg.profiler.plugins.stages.Stage
import com.github.grishberg.profiler.plugins.stages.Stages
import com.github.grishberg.profiler.plugins.stages.WrongStage
import java.util.Locale

/**
 * Searches early constructors usage.
 *
 */
class EarlyConstructorsAnalyzer(
    private val stagesFacade: StagesFacade,
    private val allMethods: Map<Int, List<ProfileData>>,
    private val currentThreadId: Int,
    private val currentThreadMethods: List<ProfileData>,
) {

    /**
     * [stage] - stage where classes without usage should be make lazy.
     * [stages] - all stages in project.
     * [allMethods] - all threads methods.
     * [currentThreadId] - current thread id.
     * [currentThreadMethods] - methods of current thread.
     */
    fun analyze(
        stage: Stage,
        stages: Stages,
        targetMethodMask: String,
        parentMethodText: String,
        shouldFilterInnerClasses: Boolean,
        searchInOtherThreads: Boolean,
    ): List<WrongStage> {
        val isDirectParent = false
        val ignoreCase = false
        val methodMask = targetMethodMask.ifEmpty { null }
        val parentMask = parentMethodText.ifEmpty { null }

        val stageBounds = stagesFacade.getStageBounds(stage)

        val result = mutableListOf<WrongStage>()

        stages.init()

        val classes = LinkedHashMap<String, ProfileData>()

        for (method in currentThreadMethods) {
            stages.updateCurrentStage(method)

            if (stage != stages.currentStage) {
                continue
            }

            val className = extractClassName(method)

            if (isCurrentMethodApplied(method, methodMask, shouldFilterInnerClasses)) {
                if (parentMask != null) {
                    val shouldEndsWithTextParent = parentMask.endsWith("()")
                    val parentMaskPrepared = prepareTextToFind(shouldEndsWithTextParent, parentMask, ignoreCase)
                    if (shouldIgnoreByParentCondition(
                            currentMethod = method,
                            ignoreCase = ignoreCase,
                            shouldEndsWithText = shouldEndsWithTextParent,
                            parentMask = parentMaskPrepared,
                            isDirectParent = isDirectParent,
                        )
                    ) {
                        continue
                    }
                }
                classes[className] = method
                continue
            }

            if (classes[className] != null) {
                classes.remove(className)
            }
        }

        if (searchInOtherThreads) {
            allMethods.forEach { (threadId, methodsData) ->
                if (threadId != currentThreadId) {
                    searchClassUsage(
                        stageBounds = stageBounds,
                        classes = classes,
                        threadMethods = methodsData
                    )
                }
            }
        }

        classes.forEach { (_, ctr) ->
            result.add(WrongStage(ctr, stage, correctStage = null))
        }

        return result
    }

    private fun searchClassUsage(
        stageBounds: StageRectangle,
        classes: MutableMap<String, ProfileData>,
        threadMethods: List<ProfileData>
    ) {
        for (method in threadMethods) {
            if (method.globalStartTimeInMillisecond < stageBounds.globalTimeTimeStart || method.globalEndTimeInMillisecond > stageBounds.globalTimeTimeEnd) {
                continue
            }

            val className = extractClassName(method)

            if (classes[className] != null) {
                classes.remove(className)
            }

        }
    }

    private fun isCurrentMethodApplied(
        method: ProfileData,
        methodMask: String?,
        shouldFilterInnerClasses: Boolean
    ): Boolean {
        if (!isConstructor(method, shouldFilterInnerClasses)) {
            return false
        }
        if (methodMask == null) {
            return true
        }
        return compareMethod(method, ignoreCase = false, false, methodMask)
    }

    private fun isConstructor(method: ProfileData, shouldFilterInnerClasses: Boolean): Boolean {
        if (method.name == "java.lang.Object.<init>") {
            return false
        }
        if (shouldFilterInnerClasses && method.name.contains('$')) {
            return false
        }
        return method.name.endsWith(".<init>")
    }

    private fun extractClassName(method: ProfileData): String {
        val lastDotPosition = method.name.lastIndexOf('.')
        if (lastDotPosition < 0) {
            return method.name
        }
        return method.name.substring(0, lastDotPosition)
    }


    //TODO: move to some delegate.

    private fun shouldIgnoreByParentCondition(
        currentMethod: ProfileData,
        ignoreCase: Boolean,
        shouldEndsWithText: Boolean,
        parentMask: String,
        isDirectParent: Boolean
    ): Boolean {
        return !currentMethodHasParent(
            currentMethod = currentMethod,
            ignoreCase = ignoreCase,
            shouldEndsWithText = shouldEndsWithText,
            parentMask = parentMask,
            isDirectParent = isDirectParent,
        )
    }

    private fun currentMethodHasParent(
        currentMethod: ProfileData,
        ignoreCase: Boolean,
        shouldEndsWithText: Boolean,
        parentMask: String,
        isDirectParent: Boolean
    ): Boolean {
        val parent = currentMethod.parent ?: return false
        if (isDirectParent) {
            return compareMethod(parent, ignoreCase, shouldEndsWithText, parentMask)
        } else {
            if (compareMethod(parent, ignoreCase, shouldEndsWithText, parentMask)) {
                return true
            }
            return currentMethodHasParent(parent, ignoreCase, shouldEndsWithText, parentMask, isDirectParent)
        }
    }

    private fun compareMethod(
        currentMethod: ProfileData,
        ignoreCase: Boolean,
        shouldEndsWithText: Boolean,
        targetString: String
    ): Boolean {
        val lowerCasedName: String = if (ignoreCase) currentMethod.name.lowercase(Locale.US) else currentMethod.name
        val isEquals: Boolean =
            if (shouldEndsWithText) lowerCasedName.endsWith(targetString) else lowerCasedName.contains(
                targetString
            )
        return isEquals
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
        val targetString = if (ignoreCase) text.lowercase(Locale.US) else text
        return targetString
    }
}
