package com.github.grishberg.profiler.comparator

import com.github.grishberg.android.profiler.core.ProfileData
import com.github.grishberg.profiler.analyzer.ProfileDataImpl
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.TraceContainer
import com.github.grishberg.profiler.comparator.model.ComparableProfileData
import com.github.grishberg.profiler.comparator.model.CompareID
import com.github.grishberg.profiler.comparator.model.MarkType
import com.github.grishberg.profiler.comparator.model.toComparable
import kotlin.math.max

class TraceComparator(
    private val log: AppLogger
) {

    /**
     * Алгоритм:
     * 1. Из распаршенных трейсов получаю дерево, удобное для сравнения
     * 2. Сравниваю поддеревья с корнем в несистемном методе (двигаюсь по дереву сверху внизу, слево направо)
     */
    fun compare(
        traceReference: TraceContainer,
        traceTested: TraceContainer
    ): Pair<ComparableProfileData, ComparableProfileData> {
        val referenceResult = traceReference.result
        val testedResult = traceTested.result
        val root = ProfileDataImpl("INIT", level = -1, 0.0, 0.0)
        val refRootNode = ComparableProfileData(CompareID(root.name, null), root)
        val testRootNode = ComparableProfileData(CompareID(root.name, null), root.copy())
        val referenceProfileDataList = mutableListOf<ComparableProfileData>()
        val testedProfileDataList = mutableListOf<ComparableProfileData>()

        referenceResult.data[referenceResult.mainThreadId]?.forEach {
            if (it.level == 0) {
                val child = it.toComparable(refRootNode, referenceProfileDataList)
                refRootNode.addChild(child)
            }
        }
        testedResult.data[testedResult.mainThreadId]?.forEach {
            if (it.level == 0) {
                val child = it.toComparable(testRootNode, testedProfileDataList)
                testRootNode.addChild(child)
            }
        }

        referenceProfileDataList.sortedForCompare()
        testedProfileDataList.sortedForCompare()

        compare(referenceProfileDataList, testedProfileDataList)

        return refRootNode to testRootNode
    }

    fun compare(
        referenceNode: ProfileData,
        testedNode: ProfileData
    ) : Pair<ComparableProfileData, ComparableProfileData> {
        val refProfileData = mutableListOf<ComparableProfileData>()
        val testProfileData = mutableListOf<ComparableProfileData>()
        val refRootNode = referenceNode.toComparable(fillInto = refProfileData)
        val testRootNode = testedNode.toComparable(fillInto = testProfileData)

        refProfileData.sortedForCompare()
        testProfileData.sortedForCompare()

        compare(refProfileData, testProfileData)

        return refRootNode to testRootNode
    }

    private fun compare(
        referenceProfileData: List<ComparableProfileData>,
        testedProfileData: List<ComparableProfileData>
    ) {
        val reference = mutableMapOf<CompareID, MutableList<ComparableProfileData>>()
        val tested = mutableMapOf<CompareID, MutableList<ComparableProfileData>>()

        for (profileData in referenceProfileData) {
            reference.compute(profileData.id) { _, list ->
                (list ?: mutableListOf()).apply { add(profileData) }
            }
        }
        for (profileData in testedProfileData) {
            tested.compute(profileData.id) { _, list ->
                (list ?: mutableListOf()).apply { add(profileData) }
            }
        }

        for ((id, testedProfiles) in tested) {
            val referenceProfiles = reference[id]
            if (isSystemMethod(id.name)) {
                testedProfiles.forEach { it.mark = MarkType.COMPARED }
                referenceProfiles?.forEach { it.mark = MarkType.COMPARED }
                continue
            }
            if (referenceProfiles == null) {
                testedProfiles.forEach { it.mark = MarkType.NEW }
                continue
            }

            for ((refNode, testedNode) in referenceProfiles.zip(testedProfiles)) {
                if (refNode.mark.isVisited() || testedNode.mark.isVisited()) {
                    check(referenceProfiles.all { it.mark.isVisited() }) {
                        "${refNode.id.name}, ${testedNode.id.name} mark: ${refNode.mark}, mark: ${testedNode.mark}"
                    }
                    check(testedProfiles.all { it.mark.isVisited() }) {
                        "${refNode.id.name}, ${testedNode.id.name} mark: ${refNode.mark}, mark: ${testedNode.mark}"
                    }
                    break
                }
                compareTrees(refNode, testedNode)
            }

            if (referenceProfiles.size < testedProfiles.size) {
                for (profileData in testedProfiles.subList(referenceProfiles.size, testedProfiles.size)) {
                    profileData.mark = MarkType.NEW
                }
            } else if (referenceProfiles.size > testedProfiles.size) {
                for (profileData in referenceProfiles.subList(testedProfiles.size, referenceProfiles.size)) {
                    profileData.mark = MarkType.OLD
                }
            }
        }
    }

    private fun compareTrees(referenceNode: ComparableProfileData, testedNode: ComparableProfileData) {
        referenceNode.mark = MarkType.COMPARED
        testedNode.mark = MarkType.COMPARED
        val (ref, test) = compareChildren(referenceNode.children, testedNode.children)

        for ((name, ids) in ref) {
            val testIds = test[name] ?: continue  // Should be handled
            for ((refId, testId) in ids.zip(testIds)) {
                val newRefNode = referenceNode.children[refId]
                val newTestNode = testedNode.children[testId]
                if (newRefNode.mark.isComparable() && newTestNode.mark.isComparable()) {
                    compareTrees(newRefNode, newTestNode)
                }
            }
        }
    }

    /**
     * Сравниваю детей одинаковых вершин
     * (Вершины считаю одинаковыми, если
     *    1. Одинаковый путь до корня
     *    2. Такое же имя метода
     *    3. Сохранил порядок (ни один вызов "до" не стал вызовом "после")
     *    4. Родитель не "новая"/"старая" вершина, сохранил порядок
     * )
     * 1. есть новые вызовы? Если да, такие вершины помечу "новыми"
     * 2. пропали ли старые? Если да, такие вершины помечу "старыми"
     */
    private fun compareChildren(
        refChildren: List<ComparableProfileData>,
        testedChildren: List<ComparableProfileData>
    ): Pair<Map<String, List<Int>>, Map<String, List<Int>>> {
        val reference = refChildren.buildMethodToIndices()
        val tested = testedChildren.buildMethodToIndices()

        for ((methodName, ids) in reference) {
            val testedMethodIds = tested[methodName]
            if (testedMethodIds == null || ids.size > testedMethodIds.size) {
                for (index in ids.subList(testedMethodIds?.size ?: 0, ids.size)) {
                    refChildren[index].mark = MarkType.OLD
                }
            } else if (ids.size < testedMethodIds.size) {
                for (index in testedMethodIds.subList(ids.size, testedMethodIds.size)) {
                    testedChildren[index].mark = MarkType.NEW
                }
            }
        }

        for ((testedMethodName, ids) in tested) {
            val method = reference[testedMethodName]
            if (method == null) {
                ids.forEach { index ->
                    testedChildren[index].mark = MarkType.NEW
                }
            }
        }

        checkOrder(
            reference = refChildren.filter { it.mark.isOverridable() },
            tested = testedChildren.filter { it.mark.isOverridable() }
        )

        return reference to tested
    }

    private fun checkOrder(
        reference: List<ComparableProfileData>,
        tested: List<ComparableProfileData>
    ) {
        check(reference.size == tested.size) {
            "Order checks on lists with same size, same entries"
        }
        val refMap = reference.buildMethodToIndices()
        val testMap = tested.buildMethodToIndices()
        val refIndexToTestIndex = MutableList(reference.size) { -1 }

        for ((method, refIds) in refMap) {
            val testIds = testMap[method]!!
            check(refIds.size == testIds.size) {
                "Order checks on lists with same size, same entries"
            }
            for ((refIndex, testIndex) in refIds.zip(testIds)) {
                refIndexToTestIndex[refIndex] = testIndex
            }
        }

        var curMaxTestIndex = -1
        for (refIndex in reference.indices) {
            val testIndex = refIndexToTestIndex[refIndex]
            if (testIndex < curMaxTestIndex) {
                reference[refIndex].mark = MarkType.CHANGE_ORDER
                tested[testIndex].mark = MarkType.CHANGE_ORDER
            }
            curMaxTestIndex = max(curMaxTestIndex, testIndex)
        }
    }

    private fun List<ComparableProfileData>.buildMethodToIndices(): Map<String, List<Int>> {
        val result = mutableMapOf<String, MutableList<Int>>()
        for ((index, data) in this.withIndex()) {
            result.compute(data.name) { _, ids ->
                ids?.apply { add(index) } ?: mutableListOf(index)
            }
        }
        return result
    }

    private fun isSystemMethod(name: String): Boolean {
        return name.startsWith("android.os") || name.startsWith("android.app") ||
                name.startsWith("com.android.internal.os") || name == "java.lang.reflect.Method.invoke"
    }
}
