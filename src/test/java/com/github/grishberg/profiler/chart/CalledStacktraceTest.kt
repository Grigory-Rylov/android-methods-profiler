package com.github.grishberg.profiler.chart

import com.github.grishberg.android.profiler.core.ProfileData
import com.github.grishberg.profiler.analyzer.ProfileDataImpl
import com.github.grishberg.profiler.common.AppLogger
import com.nhaarman.mockitokotlin2.mock
import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.geom.AffineTransform

internal class CalledStacktraceTest {
    val callers = mutableListOf<ProfileData>()
    val callTraceItems = mutableListOf<ProfileData>()
    val renderer = object : SelectionRenderer {
        override var currentThreadId: Int = -1

        override fun draw(
            g: Graphics2D,
            at: AffineTransform,
            fm: FontMetrics,
            threadId: Int,
            selected: ProfileRectangle?,
            minimumSizeInMs: Double,
            screenLeft: Double,
            screenTop: Double,
            screenRight: Double,
            screenBottom: Double
        ) = Unit

        override fun addCallerRectangle(profileData: ProfileData) {
            callers.add(profileData)
        }

        override fun addCallTraceItems(profileData: ProfileData) {
            callTraceItems.add(profileData)
        }

        override fun clear() {
            callers.clear()
            callTraceItems.clear()
        }

        override fun removeRectanglesUntilCurrent(factoryMethod: ProfileData) {
            while (callers.isNotEmpty()) {
                val element = callers.last()
                callers.remove(element)
                if (element == factoryMethod) {
                    return
                }
            }
        }
    }
    val logger = mock<AppLogger>()
    val customPanelView = profileData("panel.CustomPanelView.<init>")
    val createdStubDrawable = profileData("stub.CustomPanelStubDrawable.<init>")
    val createdChildDimensionProvider = profileData("common.DimensionProvider.<init>")
    val caller = profileData("panel.CustomPanelController.<init>")
    val parent = profileData("MainActivity.onCreate")
        .child("di.DaggerCustomPanelComponent.getPanelController").apply {
            child("panel.CustomPanelDataSource.<init>")
            child("dagger.internal.DoubleCheck.get")
                .child("di.CustomPanelModule_ProvideCustomPanelViewFactory.get")
                .child("di.CustomPanelModule_ProvideCustomPanelViewFactory.get").apply {
                    child("common.DimensionProvider_Factory.get").child("common.DimensionProvider_Factory.get")
                        .child("common.DimensionProvider_Factory.newInstance")
                        .addChild(createdChildDimensionProvider)
                    child("di.CustomPanelModule_ProvideCustomPanelViewFactory.provideCustomPanelView").apply {
                        child("di.CustomPanelModule.provideCustomPanelView")
                            .addChild(customPanelView)
                        child("dagger.internal.Preconditions.checkNotNull")
                    }
                }
            child("di.DaggerCustomPanelComponent.getCustomPanelStubDrawable")
                .addChild(createdStubDrawable)
            addChild(caller)
        }

    val dependenciesList = mutableListOf<ProfileData>()
    val underTest = CalledStacktrace(renderer, logger).apply {
        dependenciesFoundAction = object : DependenciesFoundAction {
            override fun onDependenciesFound(dependencies: List<ProfileData>) {
                dependenciesList.addAll(dependencies)
            }
        }
    }

    @Test
    fun `find all children`() {
        val parent = profileData("parent")
        val child1 = profileData("child1")
        val child2 = profileData("child2")

        parent.addChild(child1)
        parent.addChild(child2)

        underTest.findChildren(parent, 0)

        assertEquals(3, dependenciesList.size)
    }

    @Test
    fun `find created dagger dependencies after click on target`() {
        underTest.findDaggerCreationTrace(caller, 0)

        assertEquals(caller, callers[0])
        assertEquals(2, callers.size)
        assertEquals("stub.CustomPanelStubDrawable.<init>", callTraceItems.last().name)
    }

    @Test
    fun `find created dagger dependencies after click on middle created class`() {
        underTest.findDaggerCreationTrace(customPanelView, 0)

        assertEquals(4, callers.size)
        assertEquals(customPanelView, callers[0])
        assertEquals(4, callTraceItems.size)
        assertEquals(createdChildDimensionProvider, callTraceItems.last())
    }

    @Test
    fun `find dagger caller chain`() {
        underTest.findDaggerCallerChain(createdChildDimensionProvider, 0)

        assertEquals(customPanelView, callers.last())
        assertEquals("common.DimensionProvider_Factory.get", callTraceItems.last().name)
    }

    fun ProfileDataImpl.child(childName: String): ProfileDataImpl {
        val child = profileData(childName)
        this.addChild(child)
        return child
    }

    private fun profileData(name: String): ProfileDataImpl = ProfileDataImpl(name, 0, 0.0, 100.0)
}
