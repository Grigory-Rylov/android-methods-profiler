package com.github.grishberg.profiler.chart.threads

import com.github.grishberg.profiler.core.ThreadItem
import com.github.grishberg.profiler.chart.preview.PreviewImageRepository
import com.github.grishberg.profiler.chart.preview.PreviewType
import java.awt.image.BufferedImage
import javax.swing.table.DefaultTableModel

class ThreadListModel(
    private val previewImageRepository: PreviewImageRepository
) : DefaultTableModel() {
    private val columnNames = arrayOf("Thread name", "Preview")
    private val data = mutableListOf<ThreadItem>()

    fun setData(threads: List<ThreadItem>) {
        data.clear()
        data.addAll(threads)
    }

    override fun getRowCount(): Int = if (data == null) 0 else data.size

    override fun getColumnCount(): Int = 2

    override fun getColumnClass(column: Int): Class<*>? {
        return when (column) {
            0 -> String::class.java
            1 -> ImageHolder::class.java
            else -> String::class.java
        }
    }

    override fun getValueAt(row: Int, column: Int): Any {
        val item = data[row]
        return when (column) {
            0 -> item.name
            1 -> getImageByThreadId(item.threadId, row, column)
            else -> ""
        }
    }

    private fun getImageByThreadId(threadId: Int, row: Int, column: Int): ImageHolder {
        val imageHolder = ImageHolder(threadId)
        val image = previewImageRepository.preparePreview(
            threadId,
            PreviewType.THREAD_SWITCHER,
            object : PreviewImageRepository.Callback {
                override fun onImageReady(image: BufferedImage, threadId: Int) {
                    fireTableCellUpdated(row, column)
                }
            })
        if (image != null) {
            imageHolder.setImage(image, threadId)
        }
        return imageHolder
    }

    override fun getColumnName(column: Int): String {
        return columnNames[column]
    }

    fun getThreadInfo(index: Int): ThreadItem {
        return data[index]
    }

    interface ImageLoadedCallback {
        fun onImageReady(image: BufferedImage, threadId: Int)
    }

    class ImageHolder(val id: Int) {
        var callback: ImageLoadedCallback? = null
        var image: BufferedImage? = null

        fun setImage(image: BufferedImage, threadId: Int) {
            this.image = image
            callback?.onImageReady(image, threadId)
        }
    }
}
