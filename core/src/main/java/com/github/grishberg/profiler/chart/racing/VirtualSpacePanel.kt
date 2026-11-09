package com.github.grishberg.profiler.chart.racing

import com.github.grishberg.profiler.chart.CellPaintDelegate
import com.github.grishberg.profiler.chart.ElementColor
import com.github.grishberg.profiler.chart.ProfileRectangle
import com.github.grishberg.profiler.chart.highlighting.MethodsColor
import com.github.grishberg.profiler.core.ProfileData
import java.awt.Color
import java.awt.Cursor
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.event.MouseWheelEvent
import java.awt.geom.Point2D
import java.awt.geom.AffineTransform
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.util.Collections
import java.util.IdentityHashMap
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Панель виртуального пространства: paint, pinch/pan колесом, drag-pan, клавиши,
 * коллбэк onViewChanged.
 *
 * Логика цветов элементов — копия CallTracePanel.calculateColorProfileData на тех
 * же константах: базовый цвет из [MethodsColor] по [ProfileData] (включая
 * Syscall/CppFunction/NoSymbol по extendedData), выбранный — selectionColor,
 * в режиме поиска найденные — found/focusedFound/selectedFound, остальные затемняются.
 *
 * Подписи баров: если задан [labelPainter] (в YAMP — MethodsNameDrawer),
 * названия компактизируются как в основном графике: пакеты сворачиваются в
 * инициалы, текст обрезается по правой видимой границе бара.
 */
class VirtualSpacePanel : JPanel() {

    val state = ViewState()
    var onViewChanged: (() -> Unit)? = null

    /** Цветовая схема (общий мутируемый объект с [TimelineHeader]). */
    var colors: RacingColorScheme = RacingColorScheme.DARK

    /**
     * Рендер подписей баров; YAMP подставляет MethodsNameDrawer (компактизация
     * имён как в CallTracePanel). null — plain-имя при ширине бара > [NAME_MIN_PX].
     */
    var labelPainter: CellPaintDelegate? = null

    /** Источник цветов элементов, как в основном графике. null — цвет по хэшу имени. */
    var methodsColor: MethodsColor? = null
        set(value) {
            field = value
            baseColorCache.clear()
        }

    var selectedRow: CallRow? = null
        set(value) {
            field = value
            repaint()
        }

    /** Режим поиска: найденные — found-цветами, остальные затемнены (как isSearchingInProgress). */
    var isSearchCalled = false
        private set

    /** Клик без drag по бару (или мимо) — для выделения/меню снаружи. */
    var onRowClicked: ((CallRow?, Point) -> Unit)? = null

    /**
     * Цвет элемента в режиме сравнения (methodsColor.getColorForCompare) в обход
     * базовой логики; null — стандартный цвет из [methodsColor].
     */
    var colorOverrideProvider: ((CallRow) -> Color?)? = null

    /** Оверлеи: под барами (полосы закладок) и над барами (стадии, шапки, стектрейсы).
     *  Входные данные — мировые координаты (мс, единицы ROW_H) и transform мир→экран. */
    var overlayUnderRows: ((Graphics2D, AffineTransform) -> Unit)? = null
    var overlayOverRows: ((Graphics2D, AffineTransform) -> Unit)? = null

    /** false — встроенные клавиши панели выключены (клавиатурой ведёт внешний KeyBinder). */
    var useComponentKeyBindings: Boolean = true

    private val foundRows = ArrayList<CallRow>()

    /** Identity-семантика как в YAMP (сравнение ссылок элементов), без value-equals data class. */
    private val foundSet: MutableSet<CallRow> = Collections.newSetFromMap(IdentityHashMap())
    private var focusedFoundIndex = -1
    private val colorBuffer = ElementColor()
    private val baseColorCache = HashMap<ProfileData, Color>()

    private var rows: List<CallRow> = emptyList()
    private val rowsByDepth = HashMap<Int, MutableList<CallRow>>()
    private var dragFrom: Point? = null
    private var pressedPoint: Point? = null

    private val pinchListener = MacPinchBridge.PinchListener { factor -> pinchZoom(factor) }

    init {
        isOpaque = true
        background = colors.background
        isFocusable = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    dragFrom = e.point
                    pressedPoint = e.point
                    cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    val pressed = pressedPoint
                    dragFrom = null
                    pressedPoint = null
                    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    // Клик (без паноражирования) без модификаторов — как onMouseClicked
                    // в основном графике; ctrl/shift-клик обрабатывается снаружи (dagger).
                    val mods = e.modifiersEx
                    val plain = (mods and (InputEvent.CTRL_DOWN_MASK
                            or InputEvent.SHIFT_DOWN_MASK)) == 0
                    if (plain && pressed != null && abs(e.x - pressed.x) <= CLICK_TOLERANCE_PX
                        && abs(e.y - pressed.y) <= CLICK_TOLERANCE_PX
                    ) {
                        val hit = rowUnderScreen(e.point)
                        selectedRow = hit
                        onRowClicked?.invoke(hit, e.point)
                    }
                }
            }
        })
        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                val from = dragFrom ?: return
                state.viewX -= (e.x - from.x) / state.scale
                state.viewY -= (e.y - from.y) / state.scale
                dragFrom = e.point
                state.clamp(width.toDouble(), height.toDouble())
                changed()
            }
        })
        addMouseWheelListener { e -> onWheel(e) }
        // Pinch macOS: JNI-монитор + eawt-канал (secondary), дедупликация в MacPinchBridge.
        MacPinchBridge.attach(this)
        installKeyBindings()
    }

    // Pinch-слушатель живёт ровно пока панель в иерархии: диалогов может быть
    // несколько, висячие слушатели синглтона MacPinchBridge не копятся.
    override fun addNotify() {
        super.addNotify()
        MacPinchBridge.addListener(pinchListener)
        // Первая сборка дайлиба clang'ом — секунды, EDT не держим.
        Thread({ MacPinchBridge.install() }, "racing-pinch-install").apply { isDaemon = true }.start()
    }

    override fun removeNotify() {
        MacPinchBridge.removeListener(pinchListener)
        super.removeNotify()
    }

    // --- публичный API -----------------------------------------------------------
    /**
     * false — зум масштабирует только X: высота строки фиксирована
     * и выведена из [fontHeightPx] (минимум — шрифт влезает в бар).
     */
    var verticalScalingEnabled: Boolean = true
        set(value) {
            field = value
            syncYScale()
            changed()
        }

    /**
     * Кегль подписей баров, динамически. В анизотропном режиме (без вертикального
     * зума) от него зависит и высота строки — [rowHeightPx].
     */
    var fontHeightPx: Int = DEFAULT_FONT_PX
        set(value) {
            field = value.coerceIn(MIN_FONT_PX, MAX_FONT_PX)
            barNameFont = NAME_FONT.deriveFont(field.toFloat())
            syncYScale()
            changed()
        }

    /**
     * Высота строки в px (анизотропный режим): бар — доля (ROW_H−2·BAR_PAD)/ROW_H
     * от строки, берём ceil так, чтобы высота шрифта гарантированно влезала в бар.
     */
    internal fun rowHeightPx(): Int {
        val fmH = getFontMetrics(barNameFont).height
        return ceil(fmH * ROW_H / (ROW_H - 2 * BAR_PAD)).toInt() + 1
    }

    /** Шрифт подписей баров (тесты/отладка). */
    internal var barNameFont = NAME_FONT
        private set

    /**
     * Подсветка найденных методов (аналог CallTracePanel.renderFoundItems):
     * найденные красятся found-цветами, остальные затемняются.
     */
    fun renderFoundRows(found: Collection<CallRow>) {
        foundRows.clear()
        foundSet.clear()
        foundRows.addAll(found)
        foundSet.addAll(found)
        isSearchCalled = true
        focusedFoundIndex = -1
        repaint()
    }

    fun clearFoundRows() {
        foundRows.clear()
        foundSet.clear()
        isSearchCalled = false
        focusedFoundIndex = -1
        repaint()
    }

    /** Индекс сфокусированного найденного (для раскраски); вьюпорт не двигает. */
    fun setFocusedFoundIndex(index: Int) {
        focusedFoundIndex = index
        repaint()
    }

    fun getFocusedFoundIndex(): Int = focusedFoundIndex

    fun getFoundRowCount(): Int = foundRows.size

    fun foundRowAt(index: Int): CallRow? = foundRows.getOrNull(index)

    /** Сфокусированная найденная строка (identity-семантика, как foundItems в YAMP). */
    var focusedFoundRow: CallRow?
        get() = foundRows.getOrNull(focusedFoundIndex)
        set(value) {
            focusedFoundIndex = value?.let { v -> foundRows.indexOfFirst { it === v } } ?: -1
            repaint()
        }

    fun invalidateColorCache() {
        baseColorCache.clear()
    }

    /** Скролл/зум так, чтобы бар был видим и примерно по центру. */
    fun focusOnRow(row: CallRow) {
        val vpW = max(width.toDouble(), 1.0)
        val vpH = max(height.toDouble(), 1.0)
        val needed = (row.durMs + 2.0).coerceAtLeast(1e-6)
        if (vpW / state.scale < needed * 2) {
            state.focusX(row.startMs - needed, row.startMs + needed * 3.0, vpW)
        }
        val yCenter = row.depth * ROW_H + ROW_H / 2
        val minY = (state.worldHeight * state.scaleY - vpH).coerceAtLeast(0.0)
        state.viewY = (yCenter - vpH / 2 / state.scaleY).coerceIn(0.0, minY)
        state.clamp(vpW, vpH)
        changed()
    }

    /**
     * Как CallTracePanel.fitZoom(element, padding, ENABLED): масштаб, при котором
     * строка влезает в ширину минус два паддинга, и центрирование по x (опц. по y).
     */
    fun fitToRow(row: CallRow, paddingPx: Double, vertical: Boolean) {
        val vpW = max(width.toDouble(), 1.0)
        val vpH = max(height.toDouble(), 1.0)
        val dur = max(row.durMs, 1e-6)
        state.scale =
            ((vpW - 2 * paddingPx) / dur).coerceIn(ViewState.MIN_SCALE, ViewState.MAX_SCALE)
        state.viewX = row.startMs + row.durMs / 2 - (vpW / 2) / state.scale
        if (vertical) {
            state.viewY = row.depth * ROW_H + ROW_H / 2 - (vpH / 2) / state.scaleY
        }
        state.clamp(vpW, vpH)
        changed()
    }

    /** Центрировать строку по x (опционально по y) без изменения масштаба — navigateToRectangle-подобно. */
    fun centerOnRow(row: CallRow, vertical: Boolean) {
        val vpW = max(width.toDouble(), 1.0)
        val vpH = max(height.toDouble(), 1.0)
        state.viewX = row.startMs + row.durMs / 2 - (vpW / 2) / state.scale
        if (vertical) {
            state.viewY = row.depth * ROW_H + ROW_H / 2 - (vpH / 2) / state.scaleY
        }
        state.clamp(vpW, vpH)
        changed()
    }

    /** Центрировать мировое время при текущем масштабе (клик по превью, переход к закладке). */
    fun centerOnTime(worldX: Double) {
        val vpW = max(width.toDouble(), 1.0)
        state.viewX = worldX - (vpW / 2) / state.scale
        state.clamp(vpW, max(height.toDouble(), 1.0))
        changed()
    }

    /** Вписать мировой отрезок [fromX..toX] во вьюпорт с боковым паддингом. */
    fun fitToRange(fromX: Double, toX: Double, paddingPx: Double, topVertical: Boolean) {
        if (width <= 0) {
            // Открытие до раскладки окна: дождемся первого реального размера.
            pendingFit = Triple(fromX, toX, topVertical)
            return
        }
        val vpW = max(width.toDouble(), 1.0)
        val span = max(toX - fromX, 1e-6)
        state.scale =
            ((vpW - 2 * paddingPx) / span).coerceIn(ViewState.MIN_SCALE, ViewState.MAX_SCALE)
        state.viewX = fromX + span / 2 - (vpW / 2) / state.scale
        if (topVertical) state.viewY = 0.0
        state.clamp(vpW, max(height.toDouble(), 1.0))
        changed()
    }

    private var pendingFit: Triple<Double, Double, Boolean>? = null

    /** Применить fit, отложенный при нулевом размере (панель открыта до layout окна). */
    fun applyPendingFit() {
        val fit = pendingFit ?: return
        if (width <= 0) return
        pendingFit = null
        fitToRange(fit.first, fit.second, 0.0, fit.third)
    }

    /** Мировые координаты (мс/единицы ROW_H) → экран; для оверлеев. */
    fun dataTransform(): AffineTransform = AffineTransform(
        state.scale, 0.0, 0.0, state.scaleY,
        -state.viewX * state.scale, -state.viewY * state.scaleY,
    )

    private fun syncYScale() {
        state.verticalZoomEnabled = verticalScalingEnabled
        if (!verticalScalingEnabled) state.yScale = rowHeightPx() / ROW_H
    }

    fun setWorldSize(w: Double, h: Double) {
        require(w > 0.0 && h > 0.0 && w.isFinite() && h.isFinite()) { "world size must be finite and > 0, got ${w}x$h" }
        state.worldWidth = w
        state.worldHeight = h
        state.clamp(width.toDouble(), height.toDouble())
        changed()
    }

    fun setData(rows: List<CallRow>) {
        this.rows = rows
        rowsByDepth.clear()
        for (row in rows) {
            rowsByDepth.getOrPut(row.depth) { ArrayList() }.add(row)
        }
        selectedRow = null
        foundRows.clear()
        foundSet.clear()
        isSearchCalled = false
        focusedFoundIndex = -1
        baseColorCache.clear()
        repaint()
    }

    /** Данные текущей загрузки (для hit-testing'а из внешнего кода). */
    fun getData(): List<CallRow> = rows

    /** Полоса под точкой панели или null (бакет глубины — O(строк на глубине)). */
    fun rowUnderScreen(p: Point): CallRow? =
        rowUnderWorld(state.worldXUnder(p.x.toDouble()), state.worldYUnder(p.y.toDouble()))

    /** Полоса под мировыми координатами (мс по X, единицы ROW_H по Y) или null. */
    fun rowUnderWorld(worldX: Double, worldY: Double): CallRow? {
        if (worldY < 0.0 || worldX < 0.0) return null
        val depth = floor(worldY / ROW_H).toInt()
        val bucket = rowsByDepth[depth] ?: return null
        for (row in bucket) {
            if (worldX >= row.startMs && worldX <= max(row.startMs + row.durMs, row.startMs)) {
                return row
            }
        }
        return null
    }

    /** Смена строк без сброса поиска (переключение потока с закэшированными данными). */
    fun updateRows(rows: List<CallRow>) {
        this.rows = rows
        rowsByDepth.clear()
        for (row in rows) {
            rowsByDepth.getOrPut(row.depth) { ArrayList() }.add(row)
        }
        selectedRow = null
        repaint()
    }

    fun zoomAt(screenX: Double, screenY: Double, factor: Double) {
        state.zoomAt(screenX, screenY, factor, width.toDouble(), height.toDouble())
        changed()
    }

    fun panBy(dxPx: Double, dyPx: Double) {
        state.scrollBy(dxPx, dyPx, width.toDouble(), height.toDouble())
        changed()
    }

    fun fitToWindow() {
        state.fit(width.toDouble(), height.toDouble())
        changed()
    }

    /** Вписать отрезок мирового X в вьюпорт (аналог fitZoom основного графика). */
    fun focusOnRange(fromX: Double, toX: Double) {
        if (toX - fromX <= 0.0) return
        state.focusX(fromX, toX, width.toDouble())
        state.viewY = 0.0
        state.clamp(width.toDouble(), height.toDouble())
        changed()
    }

    fun worldUnderScreen(p: Point): Point2D.Double =
        Point2D.Double(state.worldXUnder(p.x.toDouble()), state.worldYUnder(p.y.toDouble()))

    /**
     * Pinch с MacPinchBridge (оба канала глобальные, якоря в событиях нет):
     * зумим под указателем (при pinch он статичен); вне панели — игнор,
     * это чужой жест (другое окно).
     */
    private fun pinchZoom(factor: Double) {
        if (!isShowing) return
        val mouse = MouseInfo.getPointerInfo()?.location ?: return
        val origin = locationOnScreen
        if (!Rectangle(origin, size).contains(mouse)) return
        zoomAt((mouse.x - origin.x).toDouble(), (mouse.y - origin.y).toDouble(), factor)
    }

    // --- жесты ---------------------------------------------------------------------

    /** Приоритет precise-вращения (плавные тачпады), фолбэк — целочисленное. */
    private fun rotationOf(e: MouseWheelEvent): Double {
        val precise = e.preciseWheelRotation
        return if (precise != 0.0) precise else e.wheelRotation.toDouble()
    }

    private fun onWheel(e: MouseWheelEvent) {
        val mods = e.modifiersEx
        val ctrl = (mods and InputEvent.CTRL_DOWN_MASK) != 0
        val shift = (mods and InputEvent.SHIFT_DOWN_MASK) != 0
        if (ctrl && !shift) {
            // Эхо жеста: Oracle JDK и macOS <15 дублируют pinch как ctrl+wheel —
            // зум уже применил MacPinchBridge, второй раз зумить нельзя.
            if (MacPinchBridge.isPinchEcho()) return
            // Pinch: factor = exp(-rot * 0.18), якорь — точка под курсором.
            val factor = Math.exp(-rotationOf(e) * PINCH_EXP)
            state.zoomAt(e.x.toDouble(), e.y.toDouble(), factor, width.toDouble(), height.toDouble())
        } else if (!ctrl) {
            // Pan двумя пальцами. У AWT-колеса нет оси X — горизонтальный жест
            // приезжает как Shift+wheel (канон Swing, так же делает BasicScrollBarUI).
            val rot = rotationOf(e)
            val rotX = if (shift) rot else 0.0
            state.scrollBy(rotX * PAN_PX, rot * PAN_PX, width.toDouble(), height.toDouble())
        }
        changed()
    }

    private fun installKeyBindings() {
        val im = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        for (k in intArrayOf(KeyEvent.VK_PLUS, KeyEvent.VK_EQUALS, KeyEvent.VK_ADD))
            im.put(KeyStroke.getKeyStroke(k, 0), "spZoomIn")
        for (k in intArrayOf(KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT))
            im.put(KeyStroke.getKeyStroke(k, 0), "spZoomOut")
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_0, 0), "spFit")
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "spPanLeft")
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "spPanRight")
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "spPanUp")
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "spPanDown")
        val am = actionMap
        am.put("spZoomIn", action { zoomAt(width / 2.0, height / 2.0, KEY_ZOOM_IN) })
        am.put("spZoomOut", action { zoomAt(width / 2.0, height / 2.0, 1.0 / KEY_ZOOM_IN) })
        am.put("spFit", action { fitToWindow() })
        am.put("spPanLeft", action { panBy(-PAN_PX, 0.0) })
        am.put("spPanRight", action { panBy(PAN_PX, 0.0) })
        am.put("spPanUp", action { panBy(0.0, -PAN_PX) })
        am.put("spPanDown", action { panBy(0.0, PAN_PX) })
    }

    private fun action(f: () -> Unit) = object : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
            if (useComponentKeyBindings) f()
        }
    }

    // --- отрисовка -----------------------------------------------------------------

    override fun paintComponent(g0: Graphics) {
        background = colors.background
        super.paintComponent(g0)
        val g = g0 as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        GridPainter.paint(g, state, width, height, colors)
        paintRowStripes(g)
        val at = dataTransform()
        overlayUnderRows?.invoke(g, at)
        paintRows(g)
        overlayOverRows?.invoke(g, at)
    }

    private fun paintRowStripes(g: Graphics2D) {
        val py = state.scaleY
        val hRow = ROW_H * py
        if (hRow < 2.0) return
        var depth = floor(state.viewY / ROW_H).toInt()
        while (depth * ROW_H < state.viewY + height / py) {
            if (depth % 2 == 1) {
                val sy = (depth * ROW_H - state.viewY) * py
                g.color = colors.stripe
                g.fillRect(0, sy.toInt(), width, hRow.toInt() + 1)
            }
            depth++
        }
    }

    /** Форма баров: false (как в прежнем CallTracePanel) — прямоугольники,
     *  true — скруглённые (arc 0.6·h, 2..12px). Флажок компонента. */
    var roundedBars: Boolean = false

    private fun paintRows(g: Graphics2D) {
        val px = state.scale
        val py = state.scaleY
        val top = state.viewY
        val bottom = state.viewY + height / py
        // Только видимые глубины (бакеты), внутри — горизонтальный culling
        // как в CallTracePanel.isInScreen.
        var depth = max(0, floor(top / ROW_H).toInt())
        while (depth * ROW_H <= bottom) {
            val bucket = rowsByDepth[depth]
            if (bucket != null) {
                val y0 = depth * ROW_H
                val by = ((y0 + BAR_PAD - state.viewY) * py).toFloat()
                val bh = ((ROW_H - 2 * BAR_PAD) * py).toFloat()
                for (row in bucket) {
                    val x = (row.startMs - state.viewX) * px
                    val w = row.durMs * px
                    if (x + w < 0 || x > width) continue
                    // Как в основном графике (minimumSizeInMs): элементы тоньше
                    // пикселя не рисуем — на плотном трейсе это десятки тысяч
                    // заштрихованных форм на кадр, от них лагает.
                    if (w < MIN_BAR_PX) continue
                    val shape = if (roundedBars) {
                        val arc = (bh * 0.6f).coerceIn(2f, 12f)
                        RoundRectangle2D.Float(x.toFloat(), by, w.toFloat(), bh, arc, arc)
                    } else {
                        Rectangle2D.Float(x.toFloat(), by, w.toFloat(), bh)
                    }
                    val fill = elementColorFor(row)
                    g.color = fill.fillColor
                    g.fill(shape)
                    g.color = fill.borderColor
                    g.draw(shape)
                    paintRowName(g, row, x, w, by, bh)
                }
            }
            depth++
        }
    }

    /**
     * Цвет элемента — ровно calculateColorProfileData из CallTracePanel,
     * на тех же константах. Возвращает общий буфер [colorBuffer].
     */
    internal fun elementColorFor(row: CallRow): ElementColor {
        val isSelectedElement = selectedRow != null && row === selectedRow
        if (isSearchCalled) {
            if (foundSet.contains(row)) {
                val isFocusedElement = focusedFoundIndex >= 0
                        && row === foundRows.getOrNull(focusedFoundIndex)
                var color = if (isSelectedElement) selectedFoundColor else foundColor
                if (isFocusedElement && !isSelectedElement) {
                    color = focusedFoundColor
                }
                val borderColor = if (isFocusedElement) selectedBookmarkBorderColor else edgesColor
                colorBuffer.set(color, borderColor)
                return colorBuffer
            } else {
                if (isSelectedElement) {
                    colorBuffer.set(selectionColor, edgesColor)
                    return colorBuffer
                }
                colorBuffer.set(darker(baseColor(row), NOT_FOUND_ITEM_DARKEN_FACTOR), edgesColor)
                return colorBuffer
            }
        }
        if (isSelectedElement) {
            colorBuffer.set(selectionColor, edgesColor)
            return colorBuffer
        }
        colorBuffer.set(baseColor(row), edgesColor)
        return colorBuffer
    }

    /** Базовый цвет метода: MethodsColor по ProfileRectangle (ExtendedData-специфика включена). */
    private fun baseColor(row: CallRow): Color {
        colorOverrideProvider?.invoke(row)?.let { return it }
        val methodsColor = methodsColor ?: return barColor(row.name)
        val data = row.data
        if (data == null) return methodsColor.getColorForMethod(row.name)
        return baseColorCache.getOrPut(data) {
            methodsColor.getColorForMethod(ProfileRectangle(0.0, 0.0, 0.0, 0.0, data))
        }
    }

    private fun darker(color: Color, factor: Double): Color = Color(
        max((color.red * factor).toInt(), 0),
        max((color.green * factor).toInt(), 0),
        max((color.blue * factor).toInt(), 0),
        color.alpha,
    )

    private fun paintRowName(g: Graphics2D, row: CallRow, x: Double, w: Double, by: Float, bh: Float) {
        val painter = labelPainter
        if (painter == null) {
            if (w > NAME_MIN_PX) {
                g.font = barNameFont
                val fm = g.fontMetrics
                if (bh >= fm.height) {
                    g.color = Color.WHITE
                    g.drawString(row.name, (x + 6).toFloat(), by + (bh - fm.descent + fm.ascent) / 2f)
                }
            }
            return
        }
        g.font = barNameFont
        val fm = g.fontMetrics
        if (bh < fm.height) return
        // Как в основном графике YAMP: подпись рисуется на баре от 1px,
        // MethodsNameDrawer сворачивает пакеты в инициалы и обрезает по right.
        if (w < LABEL_MIN_PX) return
        g.color = LABEL_TEXT_COLOR
        val left = max(0.0, x)
        val right = min(width.toDouble(), x + w)
        val baseline = (by + (bh - fm.descent + fm.ascent) / 2f).toInt()
        painter.drawLabel(g, fm, row.name, left, right, baseline)
    }

    private fun barColor(name: String): Color =
        Color.getHSBColor((abs(name.hashCode()) % 360) / 360f, 0.55f, 0.62f)

    private fun changed() {
        onViewChanged?.invoke()
        repaint()
    }

    companion object {
        private const val PINCH_EXP = 0.18
        private const val PAN_PX = 40.0

        /** Высота строки в мировых единицах по Y; связь depth→Y видна внешнему коду. */
        const val ROW_H = 20.0

        private const val BAR_PAD = 0.0
        private const val NAME_MIN_PX = 60.0

        /** Порог ширины бара для подписи через labelPainter — как в CallTracePanel (1px). */
        private const val LABEL_MIN_PX = 1.0
        private val LABEL_TEXT_COLOR = Color.BLACK

        private const val MIN_BAR_PX = 1.0
        private const val CLICK_TOLERANCE_PX = 2

        // Те же константы, что в CallTracePanel (логика цветов идентична).
        private val selectionColor = Color(115, 238, 46)
        private val foundColor = Color(70, 238, 220)
        private val focusedFoundColor = Color(171, 238, 221)
        private val selectedFoundColor = Color(110, 238, 161)
        private val edgesColor = Color(0, 0, 0, 131)
        private val selectedBookmarkBorderColor = Color(246, 255, 241)
        private const val NOT_FOUND_ITEM_DARKEN_FACTOR = 0.5

        private val KEY_ZOOM_IN = Math.exp(3 * PINCH_EXP)
        private val NAME_FONT = Font(Font.SANS_SERIF, Font.PLAIN, 11)
        private const val DEFAULT_FONT_PX = 11
        private const val MIN_FONT_PX = 8
        private const val MAX_FONT_PX = 64
    }
}
