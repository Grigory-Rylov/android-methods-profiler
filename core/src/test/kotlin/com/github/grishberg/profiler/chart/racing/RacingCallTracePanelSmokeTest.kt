package com.github.grishberg.profiler.chart.racing

import com.github.grishberg.profiler.analyzer.TraceAnalyzer
import com.github.grishberg.profiler.chart.Bookmarks
import com.github.grishberg.profiler.chart.CallTracePreviewPanel
import com.github.grishberg.profiler.chart.DependenciesFoundAction
import com.github.grishberg.profiler.chart.Finder
import com.github.grishberg.profiler.chart.FoundNavigationListener
import com.github.grishberg.profiler.chart.highlighting.ColorInfo
import com.github.grishberg.profiler.chart.highlighting.MethodsColorImpl
import com.github.grishberg.profiler.chart.highlighting.MethodsColorRepository
import com.github.grishberg.profiler.chart.preview.PreviewImageFactory
import com.github.grishberg.profiler.chart.preview.PreviewImageRepository
import com.github.grishberg.profiler.chart.preview.PreviewType
import com.github.grishberg.profiler.chart.stages.methods.StagesFacade
import com.github.grishberg.profiler.chart.stages.systrace.SystraceStagesFacade
import com.github.grishberg.profiler.chart.theme.DarkPalette
import com.github.grishberg.profiler.common.CoroutinesDispatchersImpl
import com.github.grishberg.profiler.common.TestLogger
import com.github.grishberg.profiler.common.TraceContainer
import com.github.grishberg.profiler.common.settings.SettingsFacade
import com.github.grishberg.profiler.comparator.model.ComparableProfileData
import com.github.grishberg.profiler.comparator.model.CompareID
import com.github.grishberg.profiler.comparator.model.MarkType
import com.github.grishberg.profiler.comparator.model.toComparable
import com.github.grishberg.profiler.core.AnalyzerResult
import com.github.grishberg.profiler.core.ExtendedData
import com.github.grishberg.profiler.core.ProfileData
import com.github.grishberg.profiler.ui.BookMarkInfo
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import junit.framework.TestCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Test

/**
 * Смоук основного racing-графика: реальный simpleperf.trace через
 * [RacingCallTracePanel] — загрузка, выделение, поиск, закладки, сравнение,
 * зум, режимы времени, офскрин-отрисовка.
 */
internal class RacingCallTracePanelSmokeTest {

    private val logger = TestLogger()

    private class FakeSettings : SettingsFacade {
        override fun filesDir() = System.getProperty("java.io.tmpdir")
        override var timeoutBeforeRecording: Long = 0
        override var showBookmarks = true
        override var threadTimeMode = false
        override var theme = "default"
        override var waitForResultTimeout = 0
        override var deviceSerialNumber = ""
        override var deviceAddress = ""
        override var samplingRecordModeEnabled = false
        override var systraceStagePrefix = "systrace"
        override var fileNamePrefixes: List<String> = emptyList()
        override var bufferSize = 0
        override var sampling = 0
        override var activityNames: List<String> = emptyList()
        override var packageNames: List<String> = emptyList()
        override var androidHome = ""
        override var recentFiles: List<String> = emptyList()
        override var traceFileDialogDir: String? = null
        override var reportsFileDialogDir: String? = null
        override var stagesFileDialogDir: String? = null
        override var mappingFileDir: String? = null
        override var fontName = "SansSerif"
        override var fontSize = 13
        override var shouldHideMethodsWithUnknownStages = false
        override var hierarchicalStagesMode = false
        override var isCheckForUpdatesEnabled = false
        override var debugPort = 0
        override var shouldShowToolbar = true
        override var caseSensitive = false
        override var isErgonomicKeymapEnabled = false
    }

    private class NavRecord : FoundNavigationListener<ProfileData> {
        var selectedCount = -1
        var selectedIndex = -1
        var overLast = 0
        var overFirst = 0
        override fun onSelected(
            count: Int, selectedIndex: Int, selectedElement: ProfileData,
            totalGlobalDuration: Double, totalThreadDuration: Double,
        ) {
            selectedCount = count
            this.selectedIndex = selectedIndex
        }

        override fun onNavigatedOverLastItem() { overLast++ }
        override fun onNavigatedOverFirstItem() { overFirst++ }
    }

    private fun <T> onEdt(body: () -> T): T {
        val result = AtomicReference<T>()
        val error = AtomicReference<Throwable>()
        val latch = CountDownLatch(1)
        javax.swing.SwingUtilities.invokeLater {
            try {
                result.set(body())
            } catch (t: Throwable) {
                error.set(t)
            } finally {
                latch.countDown()
            }
        }
        latch.await()
        error.get()?.let { throw it }
        return result.get()
    }

    private fun parseTrace(): TraceContainer {
        val path = javaClass.classLoader.getResource("simpleperf.trace")!!.file
        val result = TraceAnalyzer(logger).analyze(File(path))
        return TraceContainer(File(path), result, Bookmarks(FakeSettings(), logger))
    }

    private fun createPanel(nav: NavRecord, previewPanel: CallTracePreviewPanel): RacingCallTracePanel {
        val settings = FakeSettings()
        val methodsColor = MethodsColorImpl(object : MethodsColorRepository {
            override fun getColors(): List<ColorInfo> = emptyList()
            override fun updateColors(colors: List<ColorInfo>) {}
        })
        val repository = PreviewImageRepository(
            object : PreviewImageFactory {
                override fun createPreview(
                    width: Int, height: Int,
                    result: AnalyzerResult,
                    threadId: Int, previewType: PreviewType,
                ): BufferedImage = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
            },
            settings, logger,
            CoroutineScope(Dispatchers.Unconfined), CoroutinesDispatchersImpl(),
        )
        val chart = RacingCallTracePanel(
            methodsColor, nav, settings, logger,
            object : DependenciesFoundAction {
                override fun onDependenciesFound(dependencies: List<ProfileData>) {}
            },
            StagesFacade(CoroutineScope(Dispatchers.Unconfined), CoroutinesDispatchersImpl(), logger),
            SystraceStagesFacade(logger),
            Bookmarks(settings, logger),
            repository,
            previewPanel,
            DarkPalette(),
        )
        // Размер не задан: открываем, как в проде, до раскладки окна.
        return chart
    }

    private class SyntheticProfileData(
        override val name: String,
        override val level: Int,
        start: Double,
        end: Double,
    ) : ProfileData {
        override val globalStartTimeInMillisecond = start
        override val globalEndTimeInMillisecond = end
        override val threadStartTimeInMillisecond = start
        override val threadEndTimeInMillisecond = end
        override val threadSelfTime = end - start
        override val globalSelfTime = end - start
        override val parent: ProfileData? = null
        override val children: List<ProfileData> = emptyList()
        override val extendedData: ExtendedData? = null
    }

    @Test
    fun smokeMainChart() {
        onEdt {
            val nav = NavRecord()
            val previewPanel = CallTracePreviewPanel(logger)
            val chart = createPanel(nav, previewPanel)
            val trace = parseTrace()

            chart.openTraceResult(trace)
            chart.setSize(1200, 800)
            chart.doLayout()

            val state = chart.view.state
            TestCase.assertTrue("world width must be positive", state.worldWidth > 1.0)
            TestCase.assertTrue(
                "pending fit must apply on first real viewport size",
                state.scale > ViewState.MIN_SCALE + 1e-9,
            )
            TestCase.assertTrue(
                "rects must be built",
                chart.getData().profileData.isNotEmpty(),
            )
            TestCase.assertEquals(
                trace.result.data[trace.result.mainThreadId]!!.size,
                chart.getCurrentThreadMethods().size,
            )

            // --- выделение: клик в центр широкого бара ----------------------------
            val data = trace.result.data[trace.result.mainThreadId]!!
            val target = data.first {
                it.level > 0 && it.globalEndTimeInMillisecond - it.globalStartTimeInMillisecond > 10.0
            }
            val rawX = (target.globalStartTimeInMillisecond + target.globalEndTimeInMillisecond) / 2
            val y = target.level * VirtualSpacePanel.ROW_H + VirtualSpacePanel.ROW_H / 2.0
            val selected = chart.findDataByPositionAndSelect(rawX, y)
            TestCase.assertEquals(target, selected)
            TestCase.assertEquals(target, chart.getSelected())
            TestCase.assertNotNull(chart.copySelectedStacktrace())
            chart.centerSelectedElement()
            chart.fitSelectedElement()
            chart.removeSelection()
            TestCase.assertNull(chart.getSelected())

            // --- закладки ------------------------------------------------------------
            chart.selectProfileData(target)
            chart.addBookmarkAtSelectedElement(BookMarkInfo("smoke", Color.RED))
            TestCase.assertEquals(1, chart.getData().markersData.size)
            val bookmark = chart.getData().markersData[0]
            chart.updateBookmark(bookmark, BookMarkInfo("renamed", Color.RED))
            TestCase.assertEquals("renamed", bookmark.name)
            chart.focusNextMarker()
            chart.removeBookmark(bookmark)
            TestCase.assertEquals(0, chart.getData().markersData.size)

            // --- поиск -----------------------------------------------------------------
            val found = data.filter { it.name == data[100].name }.toSet()
            TestCase.assertTrue(found.isNotEmpty())
            val threadItem = trace.result.threads.first { it.threadId == trace.result.mainThreadId }
            chart.renderFoundItems(
                Finder.ThreadFindResult(found, threadItem.threadId, threadItem, 10.0, 12.0)
            )
            TestCase.assertTrue(chart.isSearchingInProgress())
            TestCase.assertEquals(found.size, nav.selectedCount)
            TestCase.assertEquals(0, nav.selectedIndex)
            chart.focusNextFoundItem()
            TestCase.assertEquals(1, nav.selectedIndex)
            repeat(found.size + 1) { chart.focusNextFoundItem() }
            TestCase.assertTrue("wrap past last must notify", nav.overLast > 0)
            chart.resetFoundItemToStart()
            TestCase.assertEquals(0, nav.selectedIndex)
            chart.disableSearching()
            TestCase.assertTrue(!chart.isSearchingInProgress())

            // --- сравнение ---------------------------------------------------------------
            val root = ComparableProfileData(
                CompareID("root", null),
                SyntheticProfileData("root", -1, 0.0, 1.0),
            )
            root.mark = MarkType.COMPARED
            val child = target.toComparable(parent = root)
            child.mark = MarkType.CHANGE_ORDER
            root.addChild(child)
            val rowsBefore = chart.getData().profileData.size
            chart.highlightCompare(root, trace.result.mainThreadId)
            val rowsAfter = chart.getData().profileData.size
            TestCase.assertTrue(
                "compare mode keeps only the compared tree",
                rowsAfter in 2 until rowsBefore,
            )
            chart.updateCompare(root, trace.result.mainThreadId)
            chart.switchThread(trace.result.mainThreadId + 1)
            chart.switchThread(trace.result.mainThreadId)
            TestCase.assertEquals(
                trace.result.data[trace.result.mainThreadId]!!.size,
                chart.getData().profileData.size,
            )

            // --- зум/скролл ---------------------------------------------------------------
            chart.scaleScreenToRange(20.0, 80.0)
            val scaleWide = chart.view.state.scale
            chart.scaleScreenToRange(20.0, 60.0)
            TestCase.assertTrue(chart.view.state.scale > scaleWide)
            chart.resetZoom()
            chart.zoomIn()
            chart.zoomOut()
            chart.scrollUp()
            chart.scrollDown()
            chart.scrollLeft()
            chart.scrollRight()

            // --- диапазон окна на превью ----------------------------------------------------
            val globalBounds = trace.result.globalTimeBounds[trace.result.mainThreadId]!!
            chart.scaleScreenToRange(globalBounds.minTime + 10.0, globalBounds.minTime + 500.0)
            val windowRange = previewPanel.windowRange
            TestCase.assertNotNull("window range must be set after zoom", windowRange)
            TestCase.assertTrue(
                "window must span a part of the preview",
                windowRange!!.startFraction < windowRange.endFraction,
            )
            TestCase.assertTrue(windowRange.label.isNotEmpty())

            // --- режимы времени -------------------------------------------------------------
            chart.switchTimeMode(true)
            chart.switchTimeMode(false)

            // --- отрисовка офскрин -----------------------------------------------------------
            TestCase.assertTrue(
                "viewport must be laid out",
                chart.view.panel.width > 1000 && chart.view.panel.height > 500,
            )
            val image = BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB)
            chart.paint(image.createGraphics())
            TestCase.assertTrue(
                "panel must paint content",
                nonBackgroundPixels(image) > 100,
            )

            chart.switchTimeMode(false)
            chart.scaleScreenToRange(
                target.globalStartTimeInMillisecond,
                target.globalEndTimeInMillisecond,
            )
            val selectedAgain = chart.findDataByPositionAndSelect(rawX, y)
            TestCase.assertEquals(target, selectedAgain)
            val image2 = BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB)
            chart.paint(image2.createGraphics())
            TestCase.assertTrue(
                "selection color must be painted",
                containsColor(image2, Color(115, 238, 46)),
            )
            Unit
        }
    }

    private fun nonBackgroundPixels(image: BufferedImage): Int {
        var count = 0
        val bg = Color(0x26272E)
        for (x in 0 until image.width step 3) {
            for (y in 0 until image.height step 3) {
                if (Color(image.getRGB(x, y)) != bg) count++
            }
        }
        return count
    }

    private fun containsColor(image: BufferedImage, color: Color): Boolean {
        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                if (Color(image.getRGB(x, y)) == color) return true
            }
        }
        return false
    }
}
