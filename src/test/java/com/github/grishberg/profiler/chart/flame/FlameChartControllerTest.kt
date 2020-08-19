package com.github.grishberg.profiler.chart.flame

import com.github.grishberg.profiler.analyzer.ProfileData
import com.github.grishberg.profiler.chart.highlighting.MethodsColor
import com.github.grishberg.profiler.common.CoroutinesDispatchers
import com.github.grishberg.profiler.common.settings.SettingsRepository
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import junit.framework.Assert.assertEquals
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.awt.Color
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D

internal class FlameChartControllerTest {
    private val root = profileData("A", 0.0, 100.0)
        .apply {
            child("B", 0.0, 20.0)
                .child("C", 10.0, 20.0)
            child("D", 20.0, 100.0).apply {
                child("B", 20.0, 30.0)
                    .child("C", 22.0, 30.0)
                child("C", 30.0, 40.0)
                child("B", 50.0, 60.0)
                    .child("C", 52.0, 60.0)
                child("B", 60.0, 80.0)
            }
        }

    private val drawnRectangles = mutableListOf<FlameRectangle>()
    private var top = -1.0
    private val viewStub = object : View {
        override var levelHeight = 10.0
        override var bounds: Rectangle2D.Double = Rectangle2D.Double()
        override var fontName: String = ""

        override fun redraw() = Unit

        override fun drawRect(
            g: Graphics2D,
            fm: FontMetrics,
            rect: FlameRectangle,
            topOffset: Double,
            borderColor: Color,
            fillColor: Color
        ) {
            drawnRectangles.add(rect)
            top = topOffset
        }

        override fun showDialog() = Unit
        override fun hideDialog() = Unit
        override fun fitZoom(rect: Rectangle2D.Double) = Unit
    }

    private val methodsColor = object : MethodsColor {
        override fun getColorForMethod(profile: ProfileData) = Color.YELLOW
        override fun getColorForMethod(name: String) = Color.YELLOW
    }

    private val settings = mock<SettingsRepository> {
        on { getStringValueOrDefault(any(), any()) } doReturn "Arial"
        on { getIntValueOrDefault(any(), any()) } doReturn 17
    }
    private val testDispatcher = TestCoroutineDispatcher()
    val coroutineScope = GlobalScope
    val dispatchers = object : CoroutinesDispatchers {
        override val worker: CoroutineDispatcher = testDispatcher
        override val ui: CoroutineDispatcher = testDispatcher
    }
    val underTest = FlameChartController(methodsColor, settings, mock(), coroutineScope, dispatchers).apply {
        view = viewStub
    }

    @Before
    fun setup() {
        // Sets the given [dispatcher] as an underlying dispatcher of [Dispatchers.Main].
        // All consecutive usages of [Dispatchers.Main] will use given [dispatcher] under the hood.
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        // Resets state of the [Dispatchers.Main] to the original main dispatcher.
        // For example, in Android Main thread dispatcher will be set as [Dispatchers.Main].
        Dispatchers.resetMain()

        // Clean up the TestCoroutineDispatcher to make sure no other work is running.
        testDispatcher.cleanupTestCoroutines()
    }

    @Test
    fun createSampleFlame() = runBlocking {
        underTest.showFlameChart(listOf(root))

        underTest.onDraw(mock(), mock(), 1.0, 0.0, 0.0, 1000.0, 1000.0)

        assertEquals(7, drawnRectangles.size)

        val first = drawnRectangles.first()
        assertEquals("A", first.name)
        assertEquals(0.0, first.x)
        assertEquals(100.0, first.width)
        assertEquals(60.0, first.y)

        val d = drawnRectangles[1]
        assertEquals("D", d.name)
        assertEquals(0.0, d.x)
        assertEquals(80.0, d.width)
        assertEquals(40.0, d.y)

        val b123 = drawnRectangles[2]
        assertEquals("B", b123.name)
        assertEquals(0.0, b123.x)
        assertEquals(40.0, b123.width)
        assertEquals(20.0, b123.y)

        val c13 = drawnRectangles[3]
        assertEquals("C", c13.name)
        assertEquals(0.0, c13.x)
        assertEquals(16.0, c13.width)
        assertEquals(0.0, c13.y)

        val c2 = drawnRectangles[4]
        assertEquals("C", c2.name)
        assertEquals(40.0, c2.x)
        assertEquals(10.0, c2.width)

        val b = drawnRectangles[5]
        assertEquals("B", b.name)
        assertEquals(80.0, b.x)
        assertEquals(20.0, b.width)

        val c3 = drawnRectangles[6]
        assertEquals("C", c3.name)
        assertEquals(80.0, c3.x)
        assertEquals(10.0, c3.width)

        assertEquals(0.0, top)
    }

    @Test
    fun checkFitBounds() = runBlocking {
        underTest.showFlameChart(listOf(root))

        assertEquals(Rectangle2D.Double(0.0, 0.0, 100.0, 60.0), viewStub.bounds)
    }

    private fun ProfileData.child(childName: String, start: Double, end: Double): ProfileData {
        val child = profileData(childName, start, end, this.level + 1)
        this.addChild(child)
        return child
    }

    private fun profileData(name: String, start: Double, end: Double, level: Int = 0): ProfileData =
        ProfileData(
            name = name, level = level,
            threadStartTimeInMillisecond = start,
            globalStartTimeInMillisecond = start,
            threadEndTimeInMillisecond = end,
            globalEndTimeInMillisecond = end
        )
}
