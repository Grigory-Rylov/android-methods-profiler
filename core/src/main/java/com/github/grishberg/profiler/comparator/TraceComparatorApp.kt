package com.github.grishberg.profiler.comparator

import com.github.grishberg.android.profiler.core.ProfileData
import com.github.grishberg.profiler.chart.highlighting.MethodsColorRepository
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.TraceContainer
import com.github.grishberg.profiler.common.UrlOpener
import com.github.grishberg.profiler.common.settings.SettingsFacade
import com.github.grishberg.profiler.common.updates.UpdatesChecker
import com.github.grishberg.profiler.ui.AppIconDelegate
import com.github.grishberg.profiler.ui.FramesManager
import com.github.grishberg.profiler.ui.Main
import com.github.grishberg.profiler.ui.Main.StartMode
import com.github.grishberg.profiler.ui.ViewFactory
import com.github.grishberg.profiler.ui.theme.ThemeController
import java.io.File
import kotlin.system.exitProcess

interface ComparatorUIListener {
    fun onCompareMenuItemClick(profileData: ProfileData)

    fun onFrameSelected(frame: ProfileData)

    fun onWindowClosed()
}

class TraceComparatorApp(
    private val settings: SettingsFacade,
    private val logger: AppLogger,
    private val framesManager: FramesManager,
    private val updatesChecker: UpdatesChecker,
    private val themeController: ThemeController,
    private val viewFactory: ViewFactory,
    private val urlOpener: UrlOpener,
    private val appIconDelegate: AppIconDelegate,
    private val methodsColorRepository: MethodsColorRepository,
    private val appFilesDir: String,
) {
    private val traceComparator = TraceComparator(logger)
    private var referenceWindow: Main? = null
    private var testedWindow: Main? = null
    private var selectedReferenceFrame: ProfileData? = null
    private var selectedTestedFrame: ProfileData? = null

    fun createFrames(reference: String?, tested: String?) {
        referenceWindow = createWindow(StartMode.DEFAULT, ReferenceComparatorUIListener())
        if (reference != null && tested != null) {
            testedWindow = createWindow(StartMode.DEFAULT, TestedComparatorUIListener())
            val analyzerResults = mutableListOf<TraceContainer>()
            referenceWindow?.openCompareTraceFile(File(reference)) { traceContainer ->
                analyzerResults.add(0, traceContainer)
                if (analyzerResults.size == 2) {
                    onParseTracesFinished(analyzerResults)
                }
            }
            testedWindow?.openCompareTraceFile(File(tested)) { traceContainer ->
                analyzerResults.add(traceContainer)
                if (analyzerResults.size == 2) {
                    onParseTracesFinished(analyzerResults)
                }
            }
        } else if (reference != null) {
            referenceWindow?.openTraceFile(File(reference))
        }
    }

    fun compare(reference: ProfileData, tested: ProfileData) {
        referenceWindow?.selectProfileData(reference)
        referenceWindow?.fitSelectedElement()
        testedWindow?.selectProfileData(tested)
        testedWindow?.fitSelectedElement()
        val (refCompareRes, testCompareRes) = traceComparator.compare(reference, tested)
        referenceWindow?.updateCompareResult(refCompareRes)
        testedWindow?.updateCompareResult(testCompareRes)
    }

    private fun onParseTracesFinished(analyzerResults: List<TraceContainer>) {
        val compareResult = traceComparator.compare(analyzerResults[0], analyzerResults[1])
        referenceWindow?.highlightCompareResult(compareResult.first)
        testedWindow?.highlightCompareResult(compareResult.second)
    }

    private fun createWindow(
        startMode: StartMode,
        comparatorUIListener: ComparatorUIListener
    ): Main {
        return Main(
            startMode,
            settings,
            logger,
            framesManager,
            themeController,
            updatesChecker,
            viewFactory,
            urlOpener,
            appIconDelegate,
            methodsColorRepository,
            appFilesDir,
            comparatorUIListener
        )
    }

    private fun findAndCompare(node: ProfileData, trace: TraceContainer, findMode: FindMode) {
        val threadId = trace.result.mainThreadId
        val traceToFindIn = trace.result.data[threadId] ?: return
        val foundNodes = TraceProfileDataFinder(traceToFindIn).findToCompare(node)

        if (foundNodes.isEmpty()) {
            logger.d("sorry, ${node.name} not found on tested trace :(")
        } else if (foundNodes.size > 1) {
            logger.d(
                "found ${foundNodes.size} same calls on tested trace," +
                        " select one you want to compare with manually"
            )
        } else {
            val reference = if (findMode == FindMode.FIND_TESTED) node else foundNodes.first()
            val tested = if (findMode == FindMode.FIND_REFERENCE) node else foundNodes.first()

            compare(reference, tested)
        }
    }

    private enum class FindMode {
        FIND_REFERENCE,
        FIND_TESTED
    }

    private inner class ReferenceComparatorUIListener : ComparatorUIListener {

        override fun onCompareMenuItemClick(profileData: ProfileData) {
            selectedTestedFrame?.let { tested ->
                compare(profileData, tested)
                return
            }

            if (testedWindow == null) {
                testedWindow = createWindow(StartMode.OPEN_TRACE_FILE, TestedComparatorUIListener())
            }

            assert(testedWindow != null) {
                "Window should be created on main thread"
            }

            var traceContainer = testedWindow?.resultContainer

            if (traceContainer == null) {
                testedWindow?.showOpenFileChooser()
                traceContainer = testedWindow?.resultContainer
            }

            if (traceContainer != null) {
                findAndCompare(profileData, traceContainer, FindMode.FIND_TESTED)
            }
        }

        override fun onFrameSelected(frame: ProfileData) {
            selectedReferenceFrame = frame
        }

        override fun onWindowClosed() {
            selectedReferenceFrame = null
            referenceWindow = null
            exitProcess(0)
        }
    }

    private inner class TestedComparatorUIListener : ComparatorUIListener {

        override fun onCompareMenuItemClick(profileData: ProfileData) {
            val reference = selectedReferenceFrame
            if (reference != null) {
                compare(reference, profileData)
                return
            }

            assert(referenceWindow != null) {
                "Tested window cannot be open when reference closed"
            }

            var traceContainer = referenceWindow?.resultContainer

            if (traceContainer == null) {
                referenceWindow?.showOpenFileChooser()
                traceContainer = referenceWindow?.resultContainer
            }

            if (traceContainer != null) {
                findAndCompare(profileData, traceContainer, FindMode.FIND_REFERENCE)
            }
        }

        override fun onFrameSelected(frame: ProfileData) {
            selectedTestedFrame = frame
        }

        override fun onWindowClosed() {
            selectedTestedFrame = null
            testedWindow = null
        }
    }
}
