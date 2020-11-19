package com.github.grishberg.profiler.chart

import java.awt.FontMetrics
import java.awt.Graphics2D

class MethodsNameDrawer(
    private val leftSymbolOffset: Int = 4
) : CellPaintDelegate {
    private val methodsCollapsedWidth = mutableMapOf<String, MethodNameDelegate>()
    private var dotWidth = 0
    private val nameBuffer = NameBuffer()

    fun resetFontSize() {
        methodsCollapsedWidth.clear()
    }

    override fun drawLabel(
        g: Graphics2D,
        fm: FontMetrics,
        name: String,
        left: Double, right: Double, top: Int,
    ) {
        if (dotWidth == 0) {
            dotWidth = fm.charWidth('.')
        }
        val currentPackageInfo = methodsCollapsedWidth.getOrPut(name) {
            MethodNameDelegate(dotWidth, name, fm, leftSymbolOffset, nameBuffer)
        }
        currentPackageInfo.drawPackage(g, left.toInt(), right.toInt(), top)
    }

    class MethodNameDelegate(
        private val dotWidth: Int,
        private val pkg: String,
        private val fm: FontMetrics,
        private val leftSymbolOffset: Int,
        private val buffer: NameBuffer
    ) {
        private val fullWidth = fm.stringWidth(pkg)
        private val words = pkg.split('.')
        private val wordsWidth = IntArray(words.size) {
            fm.stringWidth(words[it])
        }
        private val collapsedLeft = IntArray(words.size)
        private val shortestPkg: String
        private val shortestPkgWidth: Int

        init {
            buffer.reset()
            var collapsedWidth = 0
            for (index in 0 until words.size - 1) {
                val firstChar = words[index][0]
                buffer.addChar(firstChar)
                buffer.addChar('.')
                val width = fm.charWidth(firstChar)
                collapsedLeft[index] = collapsedWidth + width
                collapsedWidth += width + dotWidth
            }
            collapsedLeft[words.size - 1] = collapsedWidth
            buffer.append(words.last())
            shortestPkg = buffer.toString()
            shortestPkgWidth = fm.stringWidth(shortestPkg)
        }

        fun drawPackage(
            g: Graphics2D,
            left: Int, right: Int, top: Int
        ) {
            val visibleWidth = right - left - leftSymbolOffset
            var leftPosition: Int = left + leftSymbolOffset
            if (leftPosition < 0) {
                leftPosition = 0
            }
            // full package
            if (visibleWidth >= fullWidth) {
                g.drawString(pkg, leftPosition, top)
                return
            }

            // short package
            if (visibleWidth < shortestPkgWidth) {
                val method = words.last()
                for (c in method) {
                    val w = fm.charWidth(c)
                    if (leftPosition + w > right) {
                        break
                    }
                    g.drawString(c.toString(), leftPosition, top)
                    leftPosition += w
                }
                return
            }

            // collapsed package
            buffer.reset(-1)
            var currentWidth = 0
            var shouldCollapse = false
            for (index in words.size - 1 downTo 0) {
                val collapsedWidthLeft = collapsedLeft[index]
                if (!shouldCollapse && currentWidth + wordsWidth[index] + collapsedWidthLeft > visibleWidth) {
                    shouldCollapse = true
                }
                if (shouldCollapse) {
                    buffer.addFromEnd(words[index][0])
                    if (index > 0) {
                        buffer.addFromEnd('.')
                    }
                } else {
                    buffer.addFromEnd(words[index])
                    currentWidth += wordsWidth[index]
                    if (index > 0) {
                        buffer.addFromEnd('.')
                        currentWidth += dotWidth
                    }
                }
            }
            g.drawChars(buffer.buf, buffer.startIndex, buffer.size, leftPosition, top)
        }
    }

    class NameBuffer {
        val buf: CharArray = CharArray(1024) { ' ' }
        var size: Int = 0
            private set
        var startIndex = 0

        fun reset() {
            startIndex = 0
            size = 0
        }

        fun reset(offsetFromEnd: Int) {
            reset()
            startIndex = buf.size + offsetFromEnd + 1
        }

        fun addChar(char: Char) {
            buf[size++] = char
        }

        fun addFromEnd(word: String) {
            for (i in word.length - 1 downTo 0) {
                buf[--startIndex] = word[i]
                size++
            }
        }

        fun addFromEnd(c: Char) {
            buf[--startIndex] = c
            size++
        }

        fun get(index: Int): Char {
            return buf[index]
        }

        fun append(last: String) {
            for (c in last) {
                addChar(c)
            }
        }

        override fun toString(): String {
            return String(buf, startIndex, size)
        }
    }
}
