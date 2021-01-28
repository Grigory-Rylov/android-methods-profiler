package com.github.grishberg.profiler.plugins.stages.methods

import com.github.grishberg.android.profiler.core.ProfileData
import com.github.grishberg.profiler.NoOpLogger
import com.github.grishberg.profiler.analyzer.ProfileDataImpl
import com.github.grishberg.profiler.plugins.stages.MethodsAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class StagesTest {
    private val methodsAvailability = object : MethodsAvailability {
        override fun isMethodAvailable(method: ProfileData): Boolean = true
    }
    val logger = NoOpLogger
    val initStage = StageRelatedToMethods("Stage1", listOf())
    val stage2 =
        StageRelatedToMethods("Stage2", listOf(MethodWithIndex("a1"), MethodWithIndex("a2"), MethodWithIndex("a3")))
    val stage3 = StageRelatedToMethods("Stage3", listOf(MethodWithIndex("b1")))
    val stage4 = StageRelatedToMethods("Stage4", listOf(MethodWithIndex("c1"), MethodWithIndex("c2")))

    @Test
    fun `stages test`() {
        val underTest = StagesRelatedToMethods(StagesState(listOf(initStage, stage2, stage3)), emptyMap(), methodsAvailability, logger)

        val methods = listOf(
            method("a1"), method("a2"), method("a3"),
            method("b1"), method("c1")
        )

        whenMethodsAreCalled(methods, underTest)

        assertEquals(stage3, underTest.currentStage)
    }

    @Test
    fun `current stage must be stage3`() {
        val underTest =
            StagesRelatedToMethods(StagesState(listOf(initStage, stage2, stage3, stage4)), emptyMap(), methodsAvailability, logger)

        val methods = listOf(
            method("a1"), method("a2"), method("a3"),
            method("b1"), method("c1")
        )

        whenMethodsAreCalled(methods, underTest)

        assertEquals(stage3, underTest.currentStage)
    }

    @Test
    fun `current stage must be stage4`() {
        val underTest =
            StagesRelatedToMethods(StagesState(listOf(initStage, stage2, stage3, stage4)), emptyMap(), methodsAvailability, logger)

        val methods = listOf(
            method("a1"), method("a2"), method("a3"),
            method("b1"), method("c1"), method("c2")
        )

        whenMethodsAreCalled(methods, underTest)

        assertEquals(stage4, underTest.currentStage)
    }

    @Test
    fun `current stage must be initStage`() {
        val underTest = StagesRelatedToMethods(StagesState(listOf(initStage)), emptyMap(), methodsAvailability, logger)

        val methods = listOf(
            method("a1"), method("a2"), method("a3"),
            method("b1"), method("c1"), method("c2")
        )

        whenMethodsAreCalled(methods, underTest)

        assertEquals(initStage, underTest.currentStage)
    }

    @Test
    fun `stage with second method call`() {
        val stage2 = StageRelatedToMethods("Stage2", listOf(MethodWithIndex("a1", 1)))
        val underTest = StagesRelatedToMethods(StagesState(listOf(initStage, stage2)), emptyMap(), methodsAvailability, logger)

        val methods = listOf(
            method("a1"), method("c1"), method("a1"),
            method("c2")
        )

        whenMethodsAreCalled(methods, underTest)

        assertEquals(stage2, underTest.currentStage)
    }

    @Test
    fun `initialStage with second method call not given`() {
        val stage2 = StageRelatedToMethods("Stage2", listOf(MethodWithIndex("a1", 1)))
        val underTest = StagesRelatedToMethods(StagesState(listOf(initStage, stage2)), emptyMap(), methodsAvailability, logger)

        val methods = listOf(
            method("a1"), method("c1"), method("c2")
        )

        whenMethodsAreCalled(methods, underTest)

        assertEquals(initStage, underTest.currentStage)
    }


    private fun whenMethodsAreCalled(
        methods: List<ProfileData>,
        underTest: StagesRelatedToMethods
    ) {
        for (currentMethod in methods) {
            underTest.updateCurrentStage(currentMethod)
        }
    }

    private fun method(methodName: String): ProfileData {
        return ProfileDataImpl(methodName, 0, 0.0, 0.0)
    }
}
