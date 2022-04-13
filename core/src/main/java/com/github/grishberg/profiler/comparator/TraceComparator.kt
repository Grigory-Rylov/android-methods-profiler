package com.github.grishberg.profiler.comparator

import com.github.grishberg.profiler.analyzer.ProfileDataImpl
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.TraceContainer

class TraceComparator(
    private val log: AppLogger
) {

    /**
     * Алгоритм:
     * 1. Из распаршенных трейсов получаю дерево, удобное для сравнения
     * 2. Сравниваю поддеревья с корнем в несистемном методе (двигаюсь по дереву сверху внизу, слево направо)
     */
    fun compare(traceReference: TraceContainer, traceTested: TraceContainer): Pair<ComparableProfileData, ComparableProfileData> {
        val referenceResult = traceReference.result
        val testedResult = traceTested.result
        val root = ProfileDataImpl("INIT", level = -1, 0.0, 0.0)
        val refRootNode = ComparableProfileData(CompareID(root.name, null), null, root)
        val testRootNode = ComparableProfileData(CompareID(root.name, null), null, root.copy())
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

        referenceProfileDataList.sortedWith(compareBy({ it.profileData.level }, { it.profileData.threadStartTimeInMillisecond }))
        testedProfileDataList.sortedWith(compareBy({ it.profileData.level }, { it.profileData.threadStartTimeInMillisecond }))

        val reference = mutableMapOf<CompareID, MutableList<ComparableProfileData>>()
        val tested = mutableMapOf<CompareID, MutableList<ComparableProfileData>>()

        for (profileData in referenceProfileDataList) {
            reference.compute(profileData.id) { _, list ->
                (list ?: mutableListOf()).apply { add(profileData) }
            }
        }
        for (profileData in testedProfileDataList) {
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
                    check(refNode.mark.isVisited() && testedNode.mark.isVisited()) {
                        "${refNode.id.name}, ${testedNode.id.name} mark: ${refNode.mark}, mark: ${testedNode.mark}"
                    }
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

        return refRootNode to testRootNode
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
                if ((newRefNode.mark == MarkType.NONE || newRefNode.mark == MarkType.SUSPICIOUS) && (newTestNode.mark == MarkType.NONE || newTestNode.mark == MarkType.SUSPICIOUS)) {
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
     * 3. если 1 или 2 пометили слой как suspicious
     */
    private fun compareChildren(
        refChildren: List<ComparableProfileData>,
        testedChildren: List<ComparableProfileData>
    ): Pair<Map<String, MutableList<Int>>, Map<String, MutableList<Int>>> {
        val reference = mutableMapOf<String, MutableList<Int>>()
        val tested = mutableMapOf<String, MutableList<Int>>()

        for ((index, child) in refChildren.withIndex()) {
            reference.compute(child.name) { _, ids ->
                ids?.apply { add(index) } ?: mutableListOf(index)
            }
        }
        for ((index, child) in testedChildren.withIndex()) {
            tested.compute(child.name) { _, ids ->
                ids?.apply { add(index) } ?: mutableListOf(index)
            }
        }

        var isSuspiciousLayer = false
        for ((methodName, ids) in reference) {
            val testedMethodIds = tested[methodName]
            if (testedMethodIds == null || ids.size > testedMethodIds.size) {
                for (index in ids.subList(testedMethodIds?.size ?: 0, ids.size)) {
                    refChildren[index].mark = MarkType.OLD
                }
                isSuspiciousLayer = true
            } else if (ids.size < testedMethodIds.size) {
                for (index in testedMethodIds.subList(ids.size, testedMethodIds.size)) {
                    testedChildren[index].mark = MarkType.NEW
                }
                isSuspiciousLayer = true
            }
        }
        for ((testedMethodName, ids) in tested) {
            val method = reference[testedMethodName]
            if (method == null) {
                ids.forEach { index ->
                    testedChildren[index].mark = MarkType.NEW
                }
                isSuspiciousLayer = true
            }
        }
        if (isSuspiciousLayer) {
            refChildren.forEach { it.mark = MarkType.SUSPICIOUS }
            testedChildren.forEach { it.mark = MarkType.SUSPICIOUS }
        }

        return reference to tested
    }

    private fun isSystemMethod(name: String): Boolean {
        return name.startsWith("android.") || name.startsWith("com.android.") || name.startsWith("java.")
    }
}
