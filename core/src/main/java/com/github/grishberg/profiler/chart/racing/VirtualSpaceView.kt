package com.github.grishberg.profiler.chart.racing

import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.geom.Point2D
import javax.swing.JPanel
import javax.swing.JScrollBar

/**
 * Контейнер «таймлайн + панель + условные скроллбары».
 * doLayout ручной: скроллбар показан ровно когда контент по его оси не влезает;
 * скрытый скроллбар полностью отдаёт своё место вьюпорту (без corner-щели).
 */
class VirtualSpaceView : JPanel() {

    val panel = VirtualSpacePanel()
    val header = TimelineHeader(panel.state, panel.colors)
    val vScrollBar = JScrollBar(JScrollBar.VERTICAL)
    val hScrollBar = JScrollBar(JScrollBar.HORIZONTAL)
    val state: ViewState get() = panel.state

    /** Толщину скроллбара меряем один раз, не хардкодим. */
    private val barSize: Int = vScrollBar.preferredSize.width
    private var syncing = false
    private var lastVpW = 0.0
    private var lastVpH = 0.0

    init {
        background = panel.colors.background
        layout = null
        add(panel)
        add(header)
        add(vScrollBar)
        add(hScrollBar)
        panel.onViewChanged = { layoutNow() }
        vScrollBar.model.addChangeListener { barMoved(vertical = true) }
        hScrollBar.model.addChangeListener { barMoved(vertical = false) }
    }

    /**
     * Общая цветовая схема для панели и заголовка; фон контейнера — тот же.
     * Схема мутируемая, поэтому [RacingColorScheme.applyPalette] обновляет
     * всё вью на месте, повторный вызов не нужен.
     */
    fun setColors(colors: RacingColorScheme) {
        panel.colors = colors
        header.colors = colors
        background = colors.background
        repaint()
    }

    // --- публичный API -------------------------------------------------------------

    fun setWorldSize(w: Double, h: Double) {
        panel.setWorldSize(w, h)
        layoutNow()
    }

    fun setData(rows: List<CallRow>) = panel.setData(rows)

    fun getData(): List<CallRow> = panel.getData()

    fun zoomAt(screenX: Double, screenY: Double, factor: Double) = panel.zoomAt(screenX, screenY, factor)

    fun panBy(dxPx: Double, dyPx: Double) = panel.panBy(dxPx, dyPx)

    fun fitToWindow() = panel.fitToWindow()

    fun focusOnRange(fromX: Double, toX: Double) = panel.focusOnRange(fromX, toX)

    /** Прокси к [VirtualSpacePanel.verticalScalingEnabled]. */
    var verticalScalingEnabled: Boolean
        get() = panel.verticalScalingEnabled
        set(value) { panel.verticalScalingEnabled = value }

    /** Прокси к [VirtualSpacePanel.fontHeightPx]. */
    var fontHeightPx: Int
        get() = panel.fontHeightPx
        set(value) { panel.fontHeightPx = value }

    fun worldUnderScreen(p: Point): Point2D.Double = panel.worldUnderScreen(p)

    // --- раскладка -----------------------------------------------------------------

    override fun doLayout() = layoutNow()

    override fun getPreferredSize(): Dimension = Dimension(1000, 640)

    private fun layoutNow() {
        var vpW = width.toDouble()
        var vpH = (height - TimelineHeader.HEIGHT).toDouble()
        var needV = false
        var needH = false
        // Двух проходок достаточно: видимость оси зависит только от размеров вьюпорта,
        // а те — от видимости перпендикулярной оси (оси независимы).
        repeat(2) {
            needV = state.needsVScroll(vpH)
            needH = state.needsHScroll(vpW)
            vpW = (width - if (needV) barSize else 0).toDouble()
            vpH = (height - TimelineHeader.HEIGHT - if (needH) barSize else 0).toDouble()
        }
        lastVpW = vpW
        lastVpH = vpH
        // Уменьшение окна может выбросить view за мир — рекламим прямо на раскладке.
        state.clamp(vpW, vpH)
        vScrollBar.isVisible = needV
        hScrollBar.isVisible = needH
        val vw = vpW.toInt()
        val vh = vpH.coerceAtLeast(1.0).toInt()
        header.bounds = Rectangle(0, 0, vw, TimelineHeader.HEIGHT)
        panel.bounds = Rectangle(0, TimelineHeader.HEIGHT, vw, vh)
        if (needV) {
            vScrollBar.bounds = Rectangle(
                width - barSize, TimelineHeader.HEIGHT, barSize,
                (height - TimelineHeader.HEIGHT - if (needH) barSize else 0).coerceAtLeast(1),
            )
        }
        if (needH) {
            hScrollBar.bounds = Rectangle(
                0, height - barSize,
                (width - if (needV) barSize else 0).coerceAtLeast(1), barSize,
            )
        }
        panel.applyPendingFit()
        syncScrollBars(vpW, vpH)
        header.repaint()
    }

    private fun syncScrollBars(vpW: Double, vpH: Double) {
        if (syncing) return
        syncing = true
        try {
            syncBar(vScrollBar, state.worldHeight, state.viewY, vpH, state.scaleY)
            syncBar(hScrollBar, state.worldWidth, state.viewX, vpW, state.scale)
        } finally {
            syncing = false
        }
    }

    /** Ядро синхронизации БЕЗ guard: вызывающий сам держит [syncing] (barMoved обязан
     *  пересинхронизировать бар внутри guard'а — с guard'ом здесь это был бы no-op).
     *  [px] — масштаб своей оси (X: scale, Y: scaleY — анизотропия). */
    private fun syncBar(bar: JScrollBar, worldSize: Double, viewPos: Double, viewport: Double, px: Double) {
        val worldPx = worldSize * px
        val m = model(worldPx, viewPos * px, viewport * px, saturate = worldPx > SATURATION_LIMIT)
        bar.setValues(m[0], m[1], m[2], m[3])
    }

    /** Мировая координата под значением бара; учитывает насыщение модели. */
    private fun barWorldPos(bar: JScrollBar, worldSize: Double, viewport: Double, px: Double): Double {
        val worldPx = worldSize * px
        val vpPx = viewport * px
        return if (worldPx > SATURATION_LIMIT) {
            val m = model(worldPx, 0.0, vpPx, saturate = true)
            bar.value.toDouble() / (m[3] - m[1]).coerceAtLeast(1) * (worldPx - vpPx) / px
        } else {
            bar.value / px
        }
    }

    private fun barMoved(vertical: Boolean) {
        if (syncing) return
        syncing = true
        try {
            val bar = if (vertical) vScrollBar else hScrollBar
            val worldSize = if (vertical) state.worldHeight else state.worldWidth
            val vp = if (vertical) lastVpH else lastVpW
            val px = if (vertical) state.scaleY else state.scale
            val worldPos = barWorldPos(bar, worldSize, vp, px)
            if (vertical) state.viewY = worldPos else state.viewX = worldPos
            state.clamp(lastVpW, lastVpH)
            // После клампа пересинхронизируем этот бар: desync бара↔view ≤ 1px уходит.
            syncBar(bar, worldSize, if (vertical) state.viewY else state.viewX, vp, px)
            panel.repaint()
            panel.onStateChanged?.invoke()
            header.repaint()
        } finally {
            syncing = false
        }
    }

    companion object {
        private val SATURATION_LIMIT = (Int.MAX_VALUE - 1).toLong()
        /**
         * Параметры модели скроллбара `[value, extent, min, max]`. В px-семантике, пока
         * worldPx помещается в Int; при насыщении (worldPx > Int.MAX-1) — пропорциональное
         * отображение скроллироемого диапазона [0, worldPx-extentPx] → [0, max-extent]:
         * max = Int.MAX-1, extent = round(extentPx/worldPx·max),
         * value = round(viewPx/(worldPx-extentPx)·(max-extent)); обратный маппинг —
         * viewPx = value/(max-extent)·(worldPx-extentPx), полный скролл точно = world-viewport.
         */
        fun model(worldPx: Double, viewPx: Double, extentPx: Double, saturate: Boolean): IntArray {
            val max = if (worldPx > SATURATION_LIMIT) Int.MAX_VALUE - 1
            else Math.round(worldPx).coerceIn(1L, SATURATION_LIMIT).toInt()
            val extent = if (saturate) {
                Math.round(extentPx / worldPx * max).coerceIn(1L, max.toLong() - 1).toInt()
            } else {
                Math.round(extentPx).coerceIn(1L, max.toLong()).toInt()
            }
            val valuePx = if (saturate) {
                viewPx / (worldPx - extentPx).coerceAtLeast(1.0) * (max - extent)
            } else {
                viewPx
            }
            val value = Math.round(valuePx).coerceIn(0L, (max - extent).coerceAtLeast(0).toLong()).toInt()
            return intArrayOf(value, extent, 0, max)
        }
    }
}
