package com.github.grishberg.profiler.ui.dialogs.highlighting

import com.github.grishberg.profiler.chart.highlighting.ColorInfo
import com.github.grishberg.profiler.chart.highlighting.MethodsColorRepository

class HighlightListDialogLogic(
    private val methodsColorRepository: MethodsColorRepository
) {
    var view: HighlightDialogView? = null
        set(value) {
            field = value
            value?.let {
                it.setEventListener(HighlightViewEvents())
                initView()
            }
        }

    private fun initView() {
        view?.showList(methodsColorRepository.getColors())
        updateRemoveButtonVisibility()
    }

    private inner class HighlightViewEvents : HighlightDialogView.EventListener {
        override fun onClickedToItem(index: Int) {
            if (index < 0) {
                return
            }
            val selectedItem = methodsColorRepository.getColors()[index]
            val requestedColorInfo = view?.requestColorInfoFromDialog(selectedItem) ?: return
            addOrEditElement(index, requestedColorInfo)
        }

        override fun onClickedAddButton() {
            val requestedColorInfo = view?.requestColorInfoFromDialog() ?: return
            addOrEditElement(methodsColorRepository.getColors().size, requestedColorInfo)
            updateRemoveButtonVisibility()
        }

        override fun onClickedRemoveButton() {
            if (methodsColorRepository.getColors().isEmpty())  {
                return
            }
            val selectedIndex = view?.selectedItem ?: -1
            if (selectedIndex < 0) {
                return
            }
            val newList = mutableListOf<ColorInfo>().apply {
                addAll(methodsColorRepository.getColors())
            }
            newList.removeAt(selectedIndex)

            updateColorList(newList)

            updateRemoveButtonVisibility()
        }
    }

    private fun addOrEditElement(index: Int, color: ColorInfo) {
        val list = mutableListOf<ColorInfo>()
        list.addAll(methodsColorRepository.getColors())
        if (index >= list.size) {
            list.add(color)
        } else {
            list[index] = color
        }

        updateColorList(list)
    }

    private fun updateColorList(newList: MutableList<ColorInfo>) {
        view?.showList(newList)
        methodsColorRepository.updateColors(newList)
    }

    private fun updateRemoveButtonVisibility() {
        if (methodsColorRepository.getColors().isEmpty()) {
            view?.disableRemoveButton()
        } else {
            view?.enableRemoveButton()
        }
    }
}
