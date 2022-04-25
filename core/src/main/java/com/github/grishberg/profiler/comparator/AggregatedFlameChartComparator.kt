package com.github.grishberg.profiler.comparator

import com.github.grishberg.profiler.comparator.model.AggregatedFlameProfileData
import com.github.grishberg.profiler.comparator.model.FlameMarkType

class AggregatedFlameChartComparator {

    /**
     * Should be called from worker thread.
     */
    fun compare(referenceNode: AggregatedFlameProfileData, testedNode: AggregatedFlameProfileData) {
        val (ref, test) = compareChildren(referenceNode.children, testedNode.children)

        for ((name, refData) in ref) {
            val testedData = test[name] ?: continue  // Should be handled
            compare(refData, testedData)
        }
    }

    private fun compareChildren(
        refChildren: List<AggregatedFlameProfileData>,
        testedChildren: List<AggregatedFlameProfileData>
    ): Pair<Map<String, AggregatedFlameProfileData>, Map<String, AggregatedFlameProfileData>> {
        val reference = refChildren.associateBy { it.name }
        val tested = testedChildren.associateBy { it.name }

        for ((methodName, refData) in reference) {
            val testedData = tested[methodName]
            if (testedData == null) {
                refData.mark = FlameMarkType.OLD_OLD
            } else if (refData.mean - testedData.mean > 1) {
                refData.mark = FlameMarkType.MAYBE_OLD
            } else if (testedData.mean - refData.mean > 1) {
                testedData.mark = FlameMarkType.MAYBE_NEW
            }
        }
        for ((testedMethodName, testedData) in tested) {
            val refData = reference[testedMethodName]
            if (refData == null) {
                testedData.mark = FlameMarkType.NEW_NEW
            }
        }

        return reference to tested
    }
}
