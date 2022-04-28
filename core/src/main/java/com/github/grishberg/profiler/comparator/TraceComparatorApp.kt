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
import com.github.grishberg.profiler.ui.ViewFactory
import com.github.grishberg.profiler.ui.theme.ThemeController
import java.io.File

interface ComparatorUIListener {
    fun onCompareMenuItemClick(profileData: ProfileData)

    fun onFrameSelected(frame: ProfileData)
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
        referenceWindow = createWindow(ReferenceComparatorUIListener())
        if (reference != null && tested != null) {
            testedWindow = createWindow(TestedComparatorUIListener())
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
        referenceWindow?.fitSelectedElement()
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

    private fun createWindow(comparatorUIListener: ComparatorUIListener): Main {
        return Main(
            Main.StartMode.DEFAULT,
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

    private inner class ReferenceComparatorUIListener : ComparatorUIListener {

        override fun onCompareMenuItemClick(profileData: ProfileData) {
            val tested = selectedTestedFrame
            if (tested != null) {
                compare(profileData, tested)
                return
            }

            // TODO: try to find tested automatically
        }

        override fun onFrameSelected(frame: ProfileData) {
            selectedReferenceFrame = frame
        }
    }

    private inner class TestedComparatorUIListener : ComparatorUIListener {

        override fun onCompareMenuItemClick(profileData: ProfileData) {
            val reference = selectedReferenceFrame
            if (reference != null) {
                compare(reference, profileData)
                return
            }

            // TODO: try to find tested automatically
        }

        override fun onFrameSelected(frame: ProfileData) {
            selectedTestedFrame = frame
        }
    }
}
