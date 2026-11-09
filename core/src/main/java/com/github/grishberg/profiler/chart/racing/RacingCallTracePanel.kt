package com.github.grishberg.profiler.chart.racing

import com.github.grishberg.profiler.chart.CalledStacktrace
import com.github.grishberg.profiler.chart.ChartPaintDelegate
import com.github.grishberg.profiler.chart.ElementsSelectionRenderer
import com.github.grishberg.profiler.chart.Finder
import com.github.grishberg.profiler.chart.FoundNavigationListener
import com.github.grishberg.profiler.chart.Bookmarks
import com.github.grishberg.profiler.chart.BookmarksRectangle
import com.github.grishberg.profiler.chart.CallTracePreviewPanel
import com.github.grishberg.profiler.chart.MethodsNameDrawer
import com.github.grishberg.profiler.chart.OnPreviewClickedAction
import com.github.grishberg.profiler.chart.ProfileDataDimensionDelegate
import com.github.grishberg.profiler.chart.ProfileRectangle
import com.github.grishberg.profiler.chart.ProfilerPanel
import com.github.grishberg.profiler.chart.RepaintDelegate
import com.github.grishberg.profiler.chart.highlighting.MethodsColorImpl
import com.github.grishberg.profiler.chart.preview.PreviewImageRepository
import com.github.grishberg.profiler.chart.preview.PreviewType
import com.github.grishberg.profiler.chart.stages.methods.StagesFacade
import com.github.grishberg.profiler.chart.stages.systrace.SystraceStagesFacade
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.TraceContainer
import com.github.grishberg.profiler.common.settings.SettingsFacade
import com.github.grishberg.profiler.comparator.model.ComparableProfileData
import com.github.grishberg.profiler.core.AnalyzerResult
import com.github.grishberg.profiler.core.ProfileData
import com.github.grishberg.profiler.ui.BookMarkInfo
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * Основной график вызовов на racing-компоненте ([VirtualSpaceView]).
 *
 * Семантика публичного API ([ProfilerPanel]) повторяет прежний CallTracePanel:
 * координаты методов — model-space (x — мс thread/global времени, y — мировые
 * единицы строки ROW_H). View-мировые координаты панели сдвинуты на
 * [ViewState.timeOffset] (thread-time бывает отрицательным), поэтому на границе
 * контракта выполняем перевод raw↔world ([toWorld]/[toRaw]).
 *
 * Цвета элементов, поиск, закладки, сравнение, стадии, dagger-подсветка и
 * превью — те же модели, что у прежнего рендера ([MethodsColorImpl],
 * [Bookmarks], [CalledStacktrace], [PreviewImageRepository]).
 */
class RacingCallTracePanel(
    private val methodsColor: MethodsColorImpl,
    private val foundNavigationListener: FoundNavigationListener<ProfileData>,
    private val settings: SettingsFacade,
    private val logger: AppLogger,
    dependencyAction: com.github.grishberg.profiler.chart.DependenciesFoundAction,
    private val stagesFacade: StagesFacade,
    private val systraceStagesFacade: SystraceStagesFacade,
    private val bookmarks: Bookmarks,
    private val previewImageRepository: PreviewImageRepository,
    private val previewPanel: CallTracePreviewPanel,
    private val palette: com.github.grishberg.profiler.ui.theme.Palette,
) : ProfilerPanel(), ProfileDataDimensionDelegate, ChartPaintDelegate, RepaintDelegate {

    private val colorScheme = RacingColorScheme.forPalette(palette)
    private var constructed = false
    internal val view = VirtualSpaceView()
    private val panel: VirtualSpacePanel get() = view.panel
    private val nameDrawer = MethodsNameDrawer()

    private val renderer = ElementsSelectionRenderer(this, this)
    private val calledStacktrace = CalledStacktrace(renderer, logger).apply {
        dependenciesFoundAction = dependencyAction
    }

    private var mouseEventsListener: MouseEventsListener? = null
    private var rightClickListener: OnRightClickListener? = null

    private var result: AnalyzerResult? = null
    private var isThreadTime = settings.threadTimeMode
    private var currentThreadId = -1

    /** Кэш маппинга потока в строки: ключ "$threadId:$modeFlag". */
    private val threadCache = HashMap<String, RacingViewData>()

    /** Данные текущей загрузки (строки) + мировые прямоугольники для стадий/отчётов. */
    private var currentData: RacingViewData? = null
    private var currentRects: List<ProfileRectangle> = emptyList()

    /** Раскраска режима сравнения: элемент → цвет отличия (аналог rect.setColor в YAMP). */
    private var compareColors: HashMap<ProfileData, Color>? = null

    private var minLeftOffset = 0.0
    private var maxRightOffset = 0.0

    private var foundTotalGlobalDuration = 0.0
    private var foundTotalThreadDuration = 0.0

    private val labelFont = Font(Font.SANS_SERIF, Font.PLAIN, SCALE_FONT_SIZE)
    private val labelFontMetrics: FontMetrics get() = getFontMetrics(labelFont)

    /** Точка press'а для синтеза кликов с модификаторами. */
    private var pressPoint: Point? = null
    private var pressModifiers = 0

    private val shift: Double get() = panel.state.timeOffset
    private fun toWorld(rawMs: Double): Double = rawMs + shift
    private fun toRaw(worldMs: Double): Double = worldMs - shift

    init {
        layout = BorderLayout()
        add(view, BorderLayout.CENTER)

        view.setColors(colorScheme)
        view.verticalScalingEnabled = false
        view.fontHeightPx = settings.fontSize
        panel.labelPainter = nameDrawer
        panel.methodsColor = methodsColor
        // Клавиатурой основного окна ведёт KeyBinder (WHEN_IN_FOCUSED_WINDOW),
        // иначе стрелки/зум срабатывали бы дважды.
        panel.useComponentKeyBindings = false
        panel.colorOverrideProvider = { row ->
            compareColors?.let { map -> row.data?.let { map[it] } }
        }
        panel.overlayUnderRows = { g, at -> drawBookmarkBands(g, at) }
        panel.overlayOverRows = { g, at -> drawOverlays(g, at) }

        stagesFacade.repaintDelegate = this
        stagesFacade.labelPaintDelegate = this
        systraceStagesFacade.repaintDelegate = this
        systraceStagesFacade.labelPaintDelegate = this

        panel.onRowClicked = { _, panelPoint ->
            val world = panel.worldUnderScreen(panelPoint)
            mouseEventsListener?.onMouseClicked(
                toViewCoords(panelPoint), toRaw(world.x), world.y
            )
        }
        panel.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    pressPoint = e.point
                    pressModifiers = e.modifiersEx
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                if (!SwingUtilities.isLeftMouseButton(e)) return
                val pressed = pressPoint
                pressPoint = null
                if (pressed == null) return
                if (abs(e.x - pressed.x) > CLICK_TOLERANCE_PX || abs(e.y - pressed.y) > CLICK_TOLERANCE_PX) {
                    return
                }
                val ctrl = (pressModifiers and MouseEvent.CTRL_DOWN_MASK) != 0
                val shiftMask = (pressModifiers and MouseEvent.SHIFT_DOWN_MASK) != 0
                if (!ctrl && !shiftMask) return // обычный клик уже разобран onRowClicked
                val world = panel.worldUnderScreen(e.point)
                val viewPoint = toViewCoords(e.point)
                if (shiftMask) {
                    mouseEventsListener?.onControlShiftMouseClicked(viewPoint, toRaw(world.x), world.y)
                    findDaggerCaller(e.point)
                } else {
                    mouseEventsListener?.onControlMouseClicked(viewPoint, toRaw(world.x), world.y)
                    findCreatedByDagger(e.point)
                }
            }

            override fun mouseExited(e: MouseEvent) {
                mouseEventsListener?.onMouseExited()
            }
        })
        panel.addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val world = panel.worldUnderScreen(e.point)
                mouseEventsListener?.onMouseMove(
                    toViewCoords(e.point), toRaw(world.x), world.y
                )
            }
        })
        panel.addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent) {
                if (!SwingUtilities.isRightMouseButton(e)) return
                if (checkBookmarkHeaderClicked(e.point)) return
                val world = panel.worldUnderScreen(e.point)
                rightClickListener?.onMethodRightClicked(
                    toViewCoords(e.point), Point2D.Double(toRaw(world.x), world.y)
                )
            }
        })

        previewPanel.previewClickedAction = object : OnPreviewClickedAction {
            override fun onPreviewClicked(offsetInPercent: Double) {
                val result = result ?: return
                val bounds =
                    (if (isThreadTime) result.threadTimeBounds else result.globalTimeBounds)
                        .getOrDefault(currentThreadId, null)
                val offset = (bounds?.maxTime ?: 0.0) * offsetInPercent
                panel.centerOnTime(toWorld(offset))
            }
        }
        constructed = true
    }

    /**
     * Ручная раскладка вьюпорта не входит в каскад Container.doLayout —
     * прокидываем явно (в валидации фрейма это идемпотентно).
     */
    override fun doLayout() {
        super.doLayout()
        if (constructed) view.doLayout()
    }

    // --- жизненный цикл -------------------------------------------------------------

    /** Палитра в YAMP мутируемая (PaletteProxy) — пересобираем схему на месте. */
    override fun onThemeChanged() {
        colorScheme.applyPalette(palette)
        view.setColors(colorScheme)
    }

    override fun openTraceResult(trace: TraceContainer) {
        disableSearching()
        nameDrawer.resetFontSize()
        stagesFacade.onOpenNewTrace(trace.result)

        result = trace.result
        threadCache.clear()
        compareColors = null
        currentThreadId = -1

        switchThread(trace.result.mainThreadId)

        bookmarks.set(trace.bookmarks)
        bookmarks.setup(panel.state.worldHeight, isThreadTime)
        updateMarkersState()

        panel.fitToRange(toWorld(minLeftOffset), toWorld(maxRightOffset), 0.0, topVertical = true)
        removeSelection()
        previewImageRepository.setAnalyzerResult(trace.result)
        updatePreviewImage()
    }

    override fun closeTrace() {
        result = null
        threadCache.clear()
        compareColors = null
        currentData = null
        currentRects = emptyList()
        currentThreadId = -1
        panel.setData(emptyList())
        repaint()
    }

    override fun switchThread(threadId: Int) {
        requestFocus()
        val result = result ?: return
        updateBounds(threadId)
        if (threadId == currentThreadId) {
            return
        }
        currentThreadId = threadId
        updateMarkersState()
        updatePreviewImage()

        val data = cachedData(threadId, isThreadTime)
        compareColors = null
        applyData(data, data.rows)
        updateStages(threadId, currentRects)
    }

    override fun switchTimeMode(isThreadTime: Boolean) {
        if (isThreadTime == this.isThreadTime) return
        this.isThreadTime = isThreadTime
        val result = result ?: return
        updateMarkersState()
        updateBounds(currentThreadId)
        compareColors = null
        applyData(cachedData(currentThreadId, isThreadTime))
        stagesFacade.onThreadModeSwitched(isThreadTime)
        updatePreviewImage()
        repaint()
    }

    private fun cacheKey(threadId: Int, threadTime: Boolean) =
        "$threadId:" + (if (threadTime) 1 else 0)

    private fun cachedData(threadId: Int, threadTime: Boolean): RacingViewData =
        threadCache.getOrPut(cacheKey(threadId, threadTime)) {
            val result = result ?: throw IllegalStateException("trace is not opened")
            RacingViewDataFactory.create(result, threadId, threadTime)
        }

    private fun applyData(data: RacingViewData) = applyData(data, data.rows)

    /** Применить данные к панели; поиск сохраняется (как переключение кэшированного потока в YAMP). */
    private fun applyData(data: RacingViewData, rows: List<CallRow>) {
        currentData = data
        view.state.timeOffset = data.timeOffset
        view.setWorldSize(data.worldWidth, data.worldHeight)
        panel.updateRows(rows)
        currentRects = rows.map {
            ProfileRectangle(it.startMs, it.depth * VirtualSpacePanel.ROW_H, it.durMs,
                VirtualSpacePanel.ROW_H, it.data)
        }
        nameDrawer.resetFontSize()
        repaint()
    }

    private fun updateBounds(threadId: Int) {
        val result = result ?: return
        val bounds =
            (if (isThreadTime) result.threadTimeBounds else result.globalTimeBounds)[threadId]
        minLeftOffset = bounds?.minTime ?: 0.0
        maxRightOffset = bounds?.maxTime ?: 0.0
    }

    private fun updateMarkersState() {
        val it = bookmarks.iterator()
        while (it.hasNext()) {
            it.next()?.switchThread(currentThreadId, isThreadTime)
        }
    }

    private fun updateStages(threadId: Int, rects: List<ProfileRectangle>) {
        val result = result ?: return
        val isMain = threadId == result.mainThreadId
        stagesFacade.onThreadSwitched(rects, isMain, isThreadTime, TOP_OFFSET.toDouble())
        systraceStagesFacade.onThreadSwitched(rects, isMain, isThreadTime, TOP_OFFSET.toDouble())
    }

    // --- ввод -------------------------------------------------------------------------

    override fun setMouseEventListener(listener: MouseEventsListener) {
        mouseEventsListener = listener
    }

    override fun setRightClickListener(listener: OnRightClickListener) {
        rightClickListener = listener
    }

    /** LOD-сетка racing-панели встроенная — тумблер старого Grid оставлен no-op. */
    override fun setGridEnabled(selected: Boolean) = Unit

    private fun toViewCoords(panelPoint: Point): Point =
        SwingUtilities.convertPoint(panel, panelPoint, this)

    private fun checkBookmarkHeaderClicked(panelPoint: Point): Boolean {
        val listener = rightClickListener ?: return false
        val at = panel.dataTransform()
        val fm = labelFontMetrics
        val shift = shift
        val worldH = panel.state.worldHeight
        for (i in bookmarks.size() - 1 downTo 0) {
            val bookmark = bookmarks.bookmarkAt(i)
            val band = Rectangle2D.Double(bookmark.x + shift, 0.0, bookmark.width, worldH)
            val rect = at.createTransformedShape(band).bounds
            val cx = rect.x + rect.width / 2
            val labelTextWidth = max(
                fm.stringWidth(bookmark.name),
                MARKER_LABEL_TEXT_MIN_WIDTH,
            )
            val labelRect = Rectangle(
                cx - labelTextWidth / 2 - LEFT_SYMBOL_OFFSET,
                0,
                labelTextWidth + 2 * LEFT_SYMBOL_OFFSET,
                TOP_OFFSET,
            )
            if (labelRect.contains(panelPoint)) {
                listener.onBookmarkRightClicked(panelPoint.x, panelPoint.y, bookmark)
                return true
            }
        }
        return false
    }

    private fun findCreatedByDagger(panelPoint: Point) {
        val found = panel.rowUnderScreen(panelPoint)?.data ?: return
        calledStacktrace.findDaggerCreationTrace(found, currentThreadId, true)
        repaint()
    }

    private fun findDaggerCaller(panelPoint: Point) {
        val found = panel.rowUnderScreen(panelPoint)?.data ?: return
        calledStacktrace.findDaggerCallerChain(found, currentThreadId)
        repaint()
    }

    // --- выделение -----------------------------------------------------------------------

    override fun findDataByPositionAndSelect(x: Double, y: Double): ProfileData? {
        calledStacktrace.removeElements()
        val row = hitTest(x, y)
        panel.selectedRow = row
        if (row == null) return null
        repaint()
        return row.data
    }

    override fun findDataByPosition(x: Double, y: Double): ProfileData? = hitTest(x, y)?.data

    private fun hitTest(rawX: Double, worldY: Double): CallRow? =
        panel.rowUnderWorld(toWorld(rawX), worldY)

    override fun selectProfileData(selectedElement: ProfileData) {
        val row = panel.getData().firstOrNull { it.data === selectedElement } ?: return
        panel.selectedRow = row
        centerSelectedElement()
    }

    override fun removeSelection() {
        calledStacktrace.removeElements()
        panel.selectedRow = null
        repaint()
    }

    override fun getSelected(): ProfileData? = panel.selectedRow?.data

    override fun highlightSelectedProfileChildren() {
        val selected = getSelected() ?: return
        calledStacktrace.findChildren(selected, currentThreadId, true)
        repaint()
    }

    override fun copySelectedStacktrace(): String? {
        val selected = getSelected() ?: return null
        val sb = StringBuilder(selected.name)
        var parent = selected.parent
        while (parent != null) {
            sb.append("\n")
            sb.append(parent.name)
            parent = parent.parent
        }
        return sb.toString()
    }

    // --- поиск ------------------------------------------------------------------------------

    /**
     * Как CallTracePanel.renderFoundItems: сканирует элементы текущего потока,
     * фокусирует первый найденный, центрирует с паддингом, уведомляет слушателя.
     * Бросает IndexOutOfBoundsException на пустом результате — вызывающий код
     * (Main) гарантирует непустоту.
     */
    override fun renderFoundItems(threadFindResult: Finder.ThreadFindResult) {
        val found = panel.getData().filter { it.data != null && threadFindResult.hasMethod(it.data!!) }
        panel.renderFoundRows(found)

        val first = found[0]
        foundTotalGlobalDuration = threadFindResult.totalGlobalDuration
        foundTotalThreadDuration = threadFindResult.totalThreadDuration
        foundNavigationListener.onSelected(
            found.size, 0, first.data!!,
            foundTotalGlobalDuration, foundTotalThreadDuration,
        )
        panel.focusedFoundRow = first
        panel.fitToRow(first, FIT_PADDING, true)
        requestFocus()
    }

    override fun isSearchingInProgress(): Boolean = panel.isSearchCalled

    override fun disableSearching() {
        if (!panel.isSearchCalled) return
        panel.clearFoundRows()
    }

    private fun focusFoundItem(index: Int) {
        val found = panel.foundRowAt(index) ?: return
        panel.focusedFoundRow = found
        panel.fitToRow(found, FIT_PADDING, true)
        foundNavigationListener.onSelected(
            panel.getFoundRowCount(), index, found.data!!,
            foundTotalGlobalDuration, foundTotalThreadDuration,
        )
    }

    override fun focusPrevFoundItem() {
        if (panel.getFoundRowCount() == 0) return
        val index = panel.getFocusedFoundIndex() - 1
        panel.setFocusedFoundIndex(index)
        if (index < 0) {
            foundNavigationListener.onNavigatedOverFirstItem()
            return
        }
        focusFoundItem(index)
    }

    override fun focusNextFoundItem() {
        if (panel.getFoundRowCount() == 0) return
        val index = panel.getFocusedFoundIndex() + 1
        panel.setFocusedFoundIndex(index)
        if (index >= panel.getFoundRowCount()) {
            foundNavigationListener.onNavigatedOverLastItem()
            return
        }
        focusFoundItem(index)
    }

    override fun resetFoundItemToStart() {
        panel.setFocusedFoundIndex(0)
        focusFoundItem(0)
    }

    override fun resetFoundItemToEnd() {
        val index = panel.getFoundRowCount() - 1
        panel.setFocusedFoundIndex(index)
        focusFoundItem(index)
    }

    // --- закладки ------------------------------------------------------------------------------

    private fun addBookmark(row: CallRow, info: BookMarkInfo) {
        val data = row.data ?: return
        bookmarks.add(
            BookmarksRectangle(
                info.title,
                info.color,
                data.threadStartTimeInMillisecond,
                data.threadEndTimeInMillisecond,
                data.globalStartTimeInMillisecond,
                data.globalEndTimeInMillisecond,
                data.level,
                currentThreadId,
                panel.state.worldHeight,
                isThreadTime,
            )
        )
        previewImageRepository.clear()
        updatePreviewImage()
    }

    override fun addBookmarkAtSelectedElement(bookMarkInfo: BookMarkInfo) {
        val row = panel.selectedRow ?: return
        addBookmark(row, bookMarkInfo)
        repaint()
    }

    override fun addBookmarkAtFocusedFound(bookMarkInfo: BookMarkInfo) {
        if (!panel.isSearchCalled) return
        val row = panel.focusedFoundRow ?: return
        addBookmark(row, bookMarkInfo)
        repaint()
    }

    override fun removeCurrentBookmark() {
        bookmarks.removeCurrentBookmark()
        repaint()
        previewImageRepository.clear()
        updatePreviewImage()
    }

    override fun removeBookmark(selectedBookmark: BookmarksRectangle) {
        bookmarks.remove(selectedBookmark)
        repaint()
        previewImageRepository.clear()
        updatePreviewImage()
    }

    override fun updateBookmark(selectedBookmark: BookmarksRectangle, result: BookMarkInfo) {
        bookmarks.updateBookmark(selectedBookmark, result)
        repaint()
    }

    override fun clearBookmarks() {
        bookmarks.clear()
        repaint()
    }

    override fun onBookmarksStateChanged() {
        repaint()
    }

    private fun focusMarker(bookmark: BookmarksRectangle?) {
        bookmark ?: return
        panel.centerOnTime(toWorld(bookmark.x) + bookmark.width / 2)
    }

    override fun focusPrevMarker() = focusMarker(bookmarks.focusPreviousBookmark())

    override fun focusNextMarker() = focusMarker(bookmarks.focusNextBookmark())

    // --- сравнение ------------------------------------------------------------------------------

    override fun highlightCompare(rootCompareData: ComparableProfileData, threadId: Int) {
        requestFocus()
        val result = result ?: return
        updateBounds(threadId)
        currentThreadId = threadId
        updateMarkersState()
        updatePreviewImage()

        check(rootCompareData.profileData.level == -1) { "Root has level -1" }

        val compareMap = HashMap<ProfileData, Color>()
        val keep = HashSet<ProfileData>()
        collectCompare(rootCompareData, compareMap, keep)
        compareColors = compareMap

        val data = cachedData(threadId, isThreadTime)
        applyData(data, data.rows.filter { keep.contains(it.data) })
        // В YAMP стадии при highlightCompare получают пустую колонку методов.
        updateStages(threadId, emptyList())
    }

    override fun updateCompare(root: ComparableProfileData, threadId: Int) {
        val compareMap = HashMap<ProfileData, Color>()
        collectCompare(root, compareMap, null)
        compareColors = compareMap
        repaint()
    }

    private fun collectCompare(
        node: ComparableProfileData,
        colors: HashMap<ProfileData, Color>,
        keep: HashSet<ProfileData>?,
    ) {
        if (node.profileData.level != -1) {
            colors[node.profileData] = methodsColor.getColorForCompare(node.mark, node.name)
            keep?.add(node.profileData)
        }
        for (child in node.children) {
            collectCompare(child, colors, keep)
        }
    }

    // --- навигация/зум ------------------------------------------------------------------------------

    override fun scrollUp() = panel.panBy(0.0, MOVE_BY_KEYBOARD_OFFSET)
    override fun scrollDown() = panel.panBy(0.0, -MOVE_BY_KEYBOARD_OFFSET)
    override fun scrollLeft() = panel.panBy(-MOVE_BY_KEYBOARD_OFFSET, 0.0)
    override fun scrollRight() = panel.panBy(MOVE_BY_KEYBOARD_OFFSET, 0.0)

    override fun zoomIn() = panel.zoomAt(panel.width / 2.0, panel.height / 2.0, KEYBOARD_ZOOM)
    override fun zoomOut() = panel.zoomAt(panel.width / 2.0, panel.height / 2.0, 1.0 / KEYBOARD_ZOOM)

    private fun selectedOrFocusedFound(): CallRow? {
        if (panel.getFoundRowCount() > 0) {
            return panel.focusedFoundRow
        }
        return panel.selectedRow
    }

    override fun centerSelectedElement() {
        val row = panel.selectedRow ?: return
        panel.centerOnRow(row, vertical = true)
    }

    override fun resetZoom() {
        panel.fitToRange(toWorld(minLeftOffset), toWorld(maxRightOffset), 0.0, topVertical = true)
        repaint()
    }

    override fun fitSelectedElement() {
        val row = selectedOrFocusedFound() ?: return
        panel.fitToRow(row, FIT_PADDING, true)
    }

    override fun scaleScreenToRange(start: Double, end: Double) {
        panel.fitToRange(toWorld(start), toWorld(end), 0.0, topVertical = true)
    }

    // --- данные/отрисовка ------------------------------------------------------------------------------

    override fun updatePreviewImage() {
        val result = result ?: return
        val previewType =
            if (isThreadTime) PreviewType.PREVIEW_THREAD else PreviewType.PREVIEW_GLOBAL
        val cachedImage = previewImageRepository.preparePreview(
            currentThreadId,
            previewType,
            object : PreviewImageRepository.Callback {
                override fun onImageReady(image: java.awt.image.BufferedImage, threadId: Int) {
                    previewPanel.image = image
                }
            }
        )
        if (cachedImage != null) {
            previewPanel.image = cachedImage
        }
    }

    override fun invalidateHighlighting() {
        panel.invalidateColorCache()
        previewImageRepository.clear()
        updatePreviewImage()
        repaint()
    }

    override fun getData(): ProfilerPanelData =
        ProfilerPanelData(currentRects, bookmarks.bookmarks)

    override fun getCurrentThreadMethods(): List<ProfileData> {
        val result = result ?: return emptyList()
        return result.data[currentThreadId] ?: emptyList()
    }

    override fun increaseFontSize() = changeFontSize(1)

    override fun decreaseFontSize() = changeFontSize(-1)

    private fun changeFontSize(delta: Int) {
        nameDrawer.resetFontSize()
        val newFontSize = max(settings.fontSize + delta, MIN_FONT_SIZE)
        settings.fontSize = newFontSize
        panel.fontHeightPx = newFontSize
        repaint()
    }

    // --- делегаты отрисовки ------------------------------------------------------------------------------

    override fun calculateStartXForTime(record: ProfileData): Double =
        toWorld(
            if (isThreadTime) record.threadStartTimeInMillisecond
            else record.globalStartTimeInMillisecond
        )

    override fun calculateEndXForTime(record: ProfileData): Double =
        toWorld(
            if (isThreadTime) record.threadEndTimeInMillisecond
            else record.globalEndTimeInMillisecond
        )

    override fun calculateTopForLevel(level: Int): Double = level * VirtualSpacePanel.ROW_H

    override fun levelHeight(): Double = VirtualSpacePanel.ROW_H

    override fun fontTopOffset(): Int = FONT_TOP_OFFSET

    /** Посимвольная отрисовка подписи с обрезкой по bounds — как CallTracePanel.drawLabel. */
    override fun drawLabel(
        g: Graphics2D,
        fm: FontMetrics,
        name: String,
        horizontalBounds: Rectangle,
        topPosition: Int,
    ) {
        var leftPosition = horizontalBounds.x + LEFT_SYMBOL_OFFSET
        if (leftPosition < 0) {
            leftPosition = 0
        }
        for (i in name.indices) {
            val c = name[i]
            val w = fm.charWidth(c)
            if (leftPosition + w > horizontalBounds.x + horizontalBounds.width) {
                break
            }
            g.drawString(c.toString(), leftPosition, topPosition)
            leftPosition += w
        }
    }

    override fun repaint() {
        super.repaint()
        // Swing дёргает repaint() из конструктора JPanel — до инициализации view.
        if (!constructed) return
        view.panel.repaint()
        view.header.repaint()
    }

    override fun requestFocus() {
        view.panel.requestFocus()
    }

    // --- оверлеи --------------------------------------------------------------------------------------

    /** Полосы закладок поверх строк (как drawBookmarks в YAMP): цветная форма + рамка выбранной. */
    private fun drawBookmarkBands(g: Graphics2D, at: java.awt.geom.AffineTransform) {
        val shift = shift
        val worldH = panel.state.worldHeight
        val iterator = bookmarks.iterator()
        while (iterator.hasNext()) {
            val bookmark = iterator.next() ?: continue
            if (!bookmark.shouldShow) continue
            val band = Rectangle2D.Double(bookmark.x + shift, bookmark.y, bookmark.width, worldH)
            val shape = at.createTransformedShape(band)
            g.color = bookmark.color
            g.fill(shape)
            if (bookmark === bookmarks.currentSelectedBookmark) {
                g.color = SELECTED_BOOKMARK_BORDER_COLOR
                g.draw(shape)
            }
        }
    }

    /** Поверх баров: dagger-подсветка, фон шапки, стадии, шапки закладок — порядок как в YAMP. */
    private fun drawOverlays(g: Graphics2D, at: java.awt.geom.AffineTransform) {
        val state = panel.state
        val vpW = panel.width.toDouble()
        val vpH = panel.height.toDouble()
        val dataLeft = state.viewX
        val dataTop = state.viewY
        val dataRight = state.viewX + vpW / state.scale
        val dataBottom = state.viewY + vpH / state.scaleY
        val minSizeMs = 1.0 / state.scale
        val selectedRow = panel.selectedRow
        val selectedRect = selectedRow?.let {
            ProfileRectangle(it.startMs, it.depth * VirtualSpacePanel.ROW_H, it.durMs,
                VirtualSpacePanel.ROW_H, it.data)
        }
        calledStacktrace.draw(
            g, at, panel.getFontMetrics(panel.barNameFont), currentThreadId,
            selectedRect, minSizeMs, dataLeft, dataTop, dataRight, dataBottom
        )

        // toolbar background (фиксированная полоса сверху) + стадии + шапки закладок.
        g.color = colorScheme.background
        g.fillRect(0, 0, panel.width, TOP_OFFSET)
        val fm = labelFontMetrics
        systraceStagesFacade.drawStages(g, at, fm)
        stagesFacade.drawStages(g, at, fm)

        val shift = shift
        val worldH = panel.state.worldHeight
        val iterator = bookmarks.iterator()
        while (iterator.hasNext()) {
            val bookmark = iterator.next() ?: continue
            if (!bookmark.shouldShow) continue
            val band = Rectangle2D.Double(bookmark.x + shift, 0.0, bookmark.width, worldH)
            val rect = at.createTransformedShape(band).bounds
            val cx = rect.x + rect.width / 2
            val labelTextWidth = max(fm.stringWidth(bookmark.name), MARKER_LABEL_TEXT_MIN_WIDTH)
            g.font = labelFont
            g.color = bookmark.headerColor
            g.fillRect(
                cx - labelTextWidth / 2 - LEFT_SYMBOL_OFFSET,
                0,
                labelTextWidth + 2 * LEFT_SYMBOL_OFFSET,
                TOP_OFFSET,
            )
            g.color = bookmark.headerTitleColor
            if (bookmark.name.isNotEmpty()) {
                g.drawString(bookmark.name, cx - labelTextWidth / 2, fm.height)
            }
        }
    }

    companion object {
        private const val FIT_PADDING = 80.0
        private const val CLICK_TOLERANCE_PX = 2
        private const val SCALE_FONT_SIZE = 13
        private const val MIN_FONT_SIZE = 4
        private const val FONT_TOP_OFFSET = 4
        private const val LEFT_SYMBOL_OFFSET = 4
        private const val MOVE_BY_KEYBOARD_OFFSET = 50.0
        private const val KEYBOARD_ZOOM = 1.5
        private val SELECTED_BOOKMARK_BORDER_COLOR = Color(246, 255, 241)
    }
}
