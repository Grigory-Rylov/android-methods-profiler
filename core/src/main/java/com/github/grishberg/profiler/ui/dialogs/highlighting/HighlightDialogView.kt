package com.github.grishberg.profiler.ui.dialogs.highlighting

import com.github.grishberg.profiler.chart.highlighting.ColorInfo

interface HighlightDialogView {
    val selectedItem: Int
    fun showList(colors: List<ColorInfo>)
    fun setEventListener(listener: EventListener)
    fun enableRemoveButton()
    fun disableRemoveButton()
    fun requestColorInfoFromDialog(selectedItem: ColorInfo? = null): ColorInfo?

    interface EventListener {
        fun onClickedAddButton()
        fun onClickedRemoveButton()
        fun onClickedToItem(index: Int)
        fun onClickedUppButton()
        fun onClickedDownButton()
    }
}
