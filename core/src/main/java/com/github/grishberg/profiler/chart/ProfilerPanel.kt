package com.github.grishberg.profiler.chart

import com.github.grishberg.profiler.common.TraceContainer
import com.github.grishberg.profiler.comparator.model.ComparableProfileData
import com.github.grishberg.profiler.core.ProfileData
import com.github.grishberg.profiler.ui.BookMarkInfo
import java.awt.Point
import java.awt.geom.Point2D
import javax.swing.JPanel

/**
 * Контракт основного графика вызовов для [com.github.grishberg.profiler.ui.Main],
 * [com.github.grishberg.profiler.ui.keymap.KeyBinder], попап-меню и компаратора.
 *
 * Реализация — [com.github.grishberg.profiler.chart.racing.RacingCallTracePanel]
 * (racing-рендер: виртуальное пространство с pinch/pan, LOD-сеткой, таймлайном).
 *
 * Система координат методов («transformed»/model coords): x — время в мс
 * (thread или global в зависимости от режима), y — мировая координата строки.
 * Методы [findDataByPosition], [findDataByPositionAndSelect] и колбэки
 * [MouseEventsListener] работают в этих координатах.
 */
abstract class ProfilerPanel : JPanel() {

    /** Синтезированные клики/движения основного графика (координаты уже model-space). */
    interface MouseEventsListener {
        fun onMouseClicked(point: Point?, x: Double, y: Double)
        fun onMouseMove(point: Point?, x: Double, y: Double)
        fun onMouseExited()
        fun onControlMouseClicked(point: Point?, x: Double, y: Double)
        fun onControlShiftMouseClicked(point: Point?, x: Double, y: Double)
    }

    interface OnRightClickListener {
        /** Правый клик по шапке закладки. */
        fun onBookmarkRightClicked(x: Int, y: Int, bookmark: BookmarksRectangle)

        /** Правый клик по методу; transformed — model-координаты точки. */
        fun onMethodRightClicked(clickedPoint: Point, transformed: Point2D.Double)
    }

    /** Снимок данных текущей вкладки для генераторов отчётов. */
    class ProfilerPanelData(
        @JvmField val profileData: List<ProfileRectangle>,
        @JvmField val markersData: List<BookmarksRectangle>,
    )

    // --- жизненный цикл трейса -------------------------------------------------
    abstract fun openTraceResult(trace: TraceContainer)
    abstract fun closeTrace()

    /** Смена темы приложения: панель может пересобрать палитру-зависимые цвета. */
    open fun onThemeChanged() {}
    abstract fun switchThread(threadId: Int)
    abstract fun switchTimeMode(isThreadTime: Boolean)

    // --- ввод --------------------------------------------------------------------
    abstract fun setMouseEventListener(listener: MouseEventsListener)
    abstract fun setRightClickListener(listener: OnRightClickListener)
    abstract fun setGridEnabled(selected: Boolean)

    // --- выделение ----------------------------------------------------------------
    abstract fun findDataByPositionAndSelect(x: Double, y: Double): ProfileData?
    abstract fun findDataByPosition(x: Double, y: Double): ProfileData?
    abstract fun selectProfileData(selectedElement: ProfileData)
    abstract fun removeSelection()
    abstract fun getSelected(): ProfileData?
    abstract fun highlightSelectedProfileChildren()
    abstract fun copySelectedStacktrace(): String?

    // --- поиск ---------------------------------------------------------------------
    abstract fun renderFoundItems(threadFindResult: Finder.ThreadFindResult)
    abstract fun isSearchingInProgress(): Boolean
    abstract fun disableSearching()
    abstract fun focusPrevFoundItem()
    abstract fun focusNextFoundItem()
    abstract fun resetFoundItemToStart()
    abstract fun resetFoundItemToEnd()

    // --- закладки --------------------------------------------------------------------
    abstract fun focusPrevMarker()
    abstract fun focusNextMarker()
    abstract fun addBookmarkAtSelectedElement(bookMarkInfo: BookMarkInfo)
    abstract fun addBookmarkAtFocusedFound(bookMarkInfo: BookMarkInfo)
    abstract fun removeCurrentBookmark()
    abstract fun removeBookmark(selectedBookmark: BookmarksRectangle)
    abstract fun updateBookmark(selectedBookmark: BookmarksRectangle, result: BookMarkInfo)
    abstract fun clearBookmarks()
    abstract fun onBookmarksStateChanged()

    // --- сравнение ---------------------------------------------------------------------
    abstract fun highlightCompare(rootCompareData: ComparableProfileData, threadId: Int)
    abstract fun updateCompare(root: ComparableProfileData, threadId: Int)

    // --- навигация/зум -------------------------------------------------------------------
    abstract fun scrollUp()
    abstract fun scrollDown()
    abstract fun scrollLeft()
    abstract fun scrollRight()
    abstract fun zoomIn()
    abstract fun zoomOut()
    abstract fun centerSelectedElement()
    abstract fun resetZoom()
    abstract fun fitSelectedElement()
    abstract fun scaleScreenToRange(start: Double, end: Double)

    // --- данные/отрисовка ------------------------------------------------------------------
    abstract fun updatePreviewImage()
    abstract fun invalidateHighlighting()
    abstract fun getData(): ProfilerPanelData
    abstract fun getCurrentThreadMethods(): List<ProfileData>
    abstract fun increaseFontSize()
    abstract fun decreaseFontSize()

    companion object {
        /** Высота шапки закладок/стадий поверх графика, px. */
        const val TOP_OFFSET = 20

        /** Минимальная ширина подписи маркера, px. */
        const val MARKER_LABEL_TEXT_MIN_WIDTH = 20
    }
}
