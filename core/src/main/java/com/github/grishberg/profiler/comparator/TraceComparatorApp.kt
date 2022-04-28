package com.github.grishberg.profiler.comparator

import com.github.grishberg.android.profiler.core.ProfileData
import com.github.grishberg.profiler.chart.ProfileRectangle
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.TraceContainer
import com.github.grishberg.profiler.ui.FramesManager
import com.github.grishberg.profiler.ui.Main
import java.io.File

interface ComparatorUIListener {
    fun init()
}

class TraceComparatorApp(
    private val framesManager: FramesManager,
    private val logger: AppLogger,
) {
    private val traceComparator = TraceComparator(logger)
    private var referenceWindow: Main? = null
    private var testedWindow: Main? = null
    private var selectedTestedFrame: ProfileData? = null

    fun createFrames(reference: String?, tested: String?) {
        // TODO: set UI listeners for windows
        referenceWindow = framesManager.createMainFrame(Main.StartMode.DEFAULT)
        if (reference != null && tested != null) {
            testedWindow = framesManager.createMainFrame(Main.StartMode.DEFAULT)
            val analyzerResults = mutableListOf<TraceContainer>()
            referenceWindow?.openCompareTraceFile(File(reference)) { traceContainer ->
                analyzerResults.add(0, traceContainer)
                if (analyzerResults.size == 2) {
                    onCompareFinished(analyzerResults)
                }
            }
            testedWindow?.openCompareTraceFile(File(tested)) { traceContainer ->
                analyzerResults.add(traceContainer)
                if (analyzerResults.size == 2) {
                    onCompareFinished(analyzerResults)
                }
            }
        } else if (reference != null) {
            referenceWindow?.openTraceFile(File(reference))
        }
    }

    fun compare(reference: ProfileData, tested: ProfileData) {
        val (refCompareRes, testCompareRes) = traceComparator.compare(reference, tested)
        referenceWindow?.updateCompareResult(refCompareRes)
        testedWindow?.updateCompareResult(testCompareRes)
    }

    private fun onCompareFinished(analyzerResults: List<TraceContainer>) {
        val compareResult = traceComparator.compare(analyzerResults[0], analyzerResults[1])
        referenceWindow?.highlightCompareResult(compareResult.first)
        testedWindow?.highlightCompareResult(compareResult.second)
    }

    private inner class ReferenceComparatorUIListener : ComparatorUIListener {
        override fun init() {
            // bind to dialog view
        }

        fun onCompareClick(reference: ProfileData) {
            val tested = selectedTestedFrame
            if (tested != null) {
                compare(reference, tested)
                return
            }

            // TODO: try to find tested automatically
        }
    }

    private inner class TestedComparatorUIListener : ComparatorUIListener {
        override fun init() {
            // bind to select frame action
        }

        fun onFrameSelected(frame: ProfileRectangle) {
            selectedTestedFrame = frame.profileData
        }
    }
}
