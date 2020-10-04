package com.github.grishberg.profiler.chart.stages

import com.github.grishberg.android.profiler.core.ProfileData
import com.github.grishberg.profiler.chart.ProfileRectangle
import com.github.grishberg.profiler.chart.ProfilerPanel
import com.github.grishberg.profiler.chart.RepaintDelegate
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.CoroutinesDispatchers
import com.github.grishberg.profiler.common.toHex
import com.github.grishberg.profiler.plugins.stages.MethodWithIndex
import com.github.grishberg.profiler.plugins.stages.Stage
import com.github.grishberg.profiler.plugins.stages.StagesState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.awt.Color
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
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
    private val stages = mutableListOf<Stage>()
    private val stagesRectangles = mutableListOf<StageRectangle>()
    private var methodsRectangles: List<ProfileRectangle>? = null
    var repaintDelegate: RepaintDelegate? = null
    var height = -1.0
    val stagesList: List<Stage>
        get() = stages
    private var isThreadTimeMode = false

    fun clear() {
        stages.clear()
    }

    fun setStages(stagesList: List<Stage>) {
        stages.clear()
        stages.addAll(stagesList)
        calculateStagesBounds()
    }

    fun stageForMethod(method: ProfileData): Stage? {
        for (stage in stagesRectangles) {
            if (method == stage.methodRectangle.profileData) {
                return stage.stage
            }
        }
        return null
    }

    fun createStage(method: ProfileData, title: String, color: Color) {
        stages.add(Stage(title, listOf(MethodWithIndex(method.name)), color.toHex()))
        calculateStagesBounds()
    }

    fun editStage(targetStage: Stage, newTitle: String, newColor: Color) {
        val index = stages.indexOf(targetStage)
        if (index < 0) {
            return
        }
        stages.removeAt(index)
        stages.add(index, Stage(newTitle, targetStage.methods, newColor.toHex()))
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

            val cx = rect.x + rect.width / 2
            val name = stageRectangle.stage.name
            val labelTextWidth = max(fm.stringWidth(name), ProfilerPanel.MARKER_LABEL_TEXT_MIN_WIDTH)

            // header background
            g.color = stageRectangle.headerColor
            g.fillRect(rect.x, 0, rect.width, ProfilerPanel.TOP_OFFSET)

            g.color = stageRectangle.headerTitleColor
            if (name.isNotEmpty()) {
                g.drawString(name, cx - labelTextWidth / 2, fm.height)
            }
        }
    }

    fun removeStage(stageForMethod: Stage) {
        stages.remove(stageForMethod)
        calculateStagesBounds()
    }
}
