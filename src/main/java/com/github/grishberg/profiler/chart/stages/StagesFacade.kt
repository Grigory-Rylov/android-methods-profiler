package com.github.grishberg.profiler.chart.stages

import com.github.grishberg.android.profiler.core.ProfileData
import com.github.grishberg.profiler.chart.ChartPaintDelegate
import com.github.grishberg.profiler.chart.ProfileRectangle
import com.github.grishberg.profiler.chart.ProfilerPanel
import com.github.grishberg.profiler.chart.RepaintDelegate
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.CoroutinesDispatchers
import com.github.grishberg.profiler.common.toHex
import com.github.grishberg.profiler.plugins.stages.methods.MethodWithIndex
import com.github.grishberg.profiler.plugins.stages.methods.StageRelatedToMethods
import com.github.grishberg.profiler.plugins.stages.methods.StagesRelatedToMethods
import com.github.grishberg.profiler.plugins.stages.methods.StagesState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Color
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import javax.swing.JMenuItem
import kotlin.math.max

private const val TAG = "StagesFacade"

/**
 * Holds stages and draws in call trace panel.
 */
class StagesFacade(
    private val coroutineScope: CoroutineScope,
    private val dispatchers: CoroutinesDispatchers,
    private val log: AppLogger
) {
    val clearStagesMenuItem = JMenuItem("Clear stages").apply {
        isEnabled = false
        addActionListener {
            clearStages()
        }
    }
    private val stages = mutableListOf<StageRelatedToMethods>()
    private val stagesRectangles = mutableListOf<StageRectangle>()
    private var methodsRectangles: List<ProfileRectangle>? = null
    var repaintDelegate: RepaintDelegate? = null
    var labelPaintDelegate: ChartPaintDelegate? = null
    var height = -1.0

    // For storing stages into file
    val stagesList: List<StageRelatedToMethods>
        get() = stages

    // For analyze another opened trace file.
    var storedStages: StagesRelatedToMethods? = null
        private set
    private var isThreadTimeMode = false
    private var isStagesManuallyCreated = false

    /**
     * Is called when user opened new trace.
     */
    fun onOpenNewTrace() {
        if (stagesList.isEmpty()) {
            return
        }
        if (!isStagesManuallyCreated) {
            return
        }
        // create Stages object
        methodsRectangles?.let {
            coroutineScope.launch {
                val result = withContext(coroutineScope.coroutineContext + dispatchers.worker) {
                    StagesRelatedToMethods.createFromStagesListAndMethods(MethodsListIterator(it), stagesList, log)
                }
                storedStages = result
            }
        }
        isStagesManuallyCreated = false
    }

    private fun clearStages() {
        stages.clear()
        storedStages = null
        stagesRectangles.clear()
        repaintDelegate?.repaint()
        clearStagesMenuItem.isEnabled = false
    }

    fun setStages(stagesList: List<StageRelatedToMethods>) {
        clearStagesMenuItem.isEnabled = true
        stages.clear()
        stages.addAll(stagesList)
        calculateStagesBounds()
    }

    fun stageForMethod(method: ProfileData): StageRelatedToMethods? {
        for (stage in stagesRectangles) {
            if (method == stage.methodRectangle.profileData) {
                return stage.stage
            }
        }
        return null
    }

    fun createStage(method: ProfileData, title: String, color: Color) {
        clearStagesMenuItem.isEnabled = true
        isStagesManuallyCreated = true
        stages.add(StageRelatedToMethods(title, listOf(MethodWithIndex(method.name)), color.toHex()))
        calculateStagesBounds()
    }

    fun editStage(targetStage: StageRelatedToMethods, newTitle: String, newColor: Color) {
        isStagesManuallyCreated = true
        val index = stages.indexOf(targetStage)
        if (index < 0) {
            return
        }
        stages.removeAt(index)
        stages.add(index, StageRelatedToMethods(newTitle, targetStage.methods, newColor.toHex()))
    }

    /**
     * Should be called after new trace was opened.
     */
    fun onThreadSwitched(
        rectangles: List<ProfileRectangle>,
        isMainThread: Boolean,
        threadTime: Boolean,
        toolbarHeight: Double
    ) {
        isThreadTimeMode = threadTime
        if (isMainThread) {
            methodsRectangles = rectangles
            height = toolbarHeight
            calculateStagesBounds()
            return
        }
        methodsRectangles = null
    }

    fun onThreadModeSwitched(threadTime: Boolean) {
        isThreadTimeMode = threadTime
        for (rect in stagesRectangles) {
            rect.switchThreadTimeMode(isThreadTimeMode)
        }
        if (stagesRectangles.isNotEmpty()) {
            repaintDelegate?.repaint()
        }
    }

    private fun calculateStagesBounds() {
        methodsRectangles?.let { methods ->
            coroutineScope.launch {

                val result = coroutineScope.async(dispatchers.worker) {
                    val rectangles = mutableListOf<StageRectangle>()
                    val stagesState = StagesState(stages)
                    for (method in methods) {
                        if (stagesState.updateCurrentStage(method.profileData.name)) {
                            val currentStage = stagesState.currentStage ?: continue
                            rectangles.add(
                                StageRectangle.fromMethodRectangle(
                                    currentStage,
                                    method,
                                    height,
                                    isThreadTimeMode
                                )
                            )
                            log.d("$TAG: added ${currentStage.name} stage for method ${method.profileData.name}")
                            if (stagesState.isLastStage()) {
                                break
                            }
                        }
                    }
                    return@async rectangles
                }.await()
                stagesRectangles.clear()
                stagesRectangles.addAll(result)

                repaintDelegate?.repaint()
            }
        }
    }

    fun drawStages(g: Graphics2D, at: AffineTransform, fm: FontMetrics) {
        for (stageRectangle in stagesRectangles) {
            val transformedShape = at.createTransformedShape(stageRectangle)
            val rect = transformedShape.bounds
            val name = stageRectangle.stage.name

            val cx = rect.x + rect.width / 2
            val labelTextWidth = max(fm.stringWidth(name), ProfilerPanel.MARKER_LABEL_TEXT_MIN_WIDTH)

            // header background
            g.color = stageRectangle.headerColor
            g.fillRect(rect.x, 0, rect.width, ProfilerPanel.TOP_OFFSET)

            g.color = stageRectangle.headerTitleColor
            if (name.isNotEmpty()) {
                if (labelTextWidth <= rect.width) {
                    g.drawString(name, cx - labelTextWidth / 2, fm.height)
                } else {
                    labelPaintDelegate?.drawLabel(g, fm, name, rect, fm.height)
                }
            }
        }
    }

    fun removeStage(stageForMethod: StageRelatedToMethods) {
        stages.remove(stageForMethod)
        calculateStagesBounds()
    }

    class MethodsListIterator(
        methodsList: List<ProfileRectangle>
    ) : Iterator<ProfileData> {
        private val innerIterator = methodsList.iterator()

        override fun hasNext(): Boolean = innerIterator.hasNext()

        override fun next(): ProfileData = innerIterator.next().profileData
    }

}
