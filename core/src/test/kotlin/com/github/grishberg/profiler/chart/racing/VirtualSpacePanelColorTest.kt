package com.github.grishberg.profiler.chart.racing

import com.github.grishberg.profiler.chart.highlighting.ColorInfo
import com.github.grishberg.profiler.chart.highlighting.MethodsColorImpl
import com.github.grishberg.profiler.chart.highlighting.MethodsColorRepository
import com.github.grishberg.profiler.core.ExtendedData
import com.github.grishberg.profiler.core.ProfileData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.awt.Color

/**
 * Логика цветов racing-панели обязана совпадать с CallTracePanel.calculateColorProfileData:
 * базовый цвет (включая ExtendedData-специфику), выбор, режим поиска (found/focused/selected),
 * затемнение ненайденных, цвет границ.
 */
class VirtualSpacePanelColorTest {

    private class FakeProfileData(
        override val name: String,
        override val extendedData: ExtendedData? = null,
    ) : ProfileData {
        override val level = 0
        override val threadStartTimeInMillisecond = 0.0
        override val globalStartTimeInMillisecond = 0.0
        override val threadEndTimeInMillisecond = 1.0
        override val globalEndTimeInMillisecond = 1.0
        override val threadSelfTime = 1.0
        override val globalSelfTime = 1.0
        override val parent: ProfileData? = null
        override val children: List<ProfileData> = emptyList()
    }

    private fun row(name: String, data: ProfileData? = null) =
        CallRow(depth = 0, name = name, startMs = 0.0, durMs = 10.0, data = data)

    private val customColor = Color(-14025114)

    private val methodsColor = MethodsColorImpl(object : MethodsColorRepository {
        override fun getColors(): List<ColorInfo> = listOf(ColorInfo("com.custom.", customColor))
        override fun updateColors(colors: List<ColorInfo>) {}
    })

    private fun panel(): VirtualSpacePanel {
        val panel = VirtualSpacePanel()
        panel.methodsColor = methodsColor
        return panel
    }

    @Test
    fun baseColorUsesMethodsColorWithCustomHighlightRule() {
        val panel = panel()
        val color = panel.elementColorFor(row("com.custom.Class.method"))
        assertEquals(customColor, color.fillColor)
        assertEquals(Color(0, 0, 0, 131), color.borderColor)
    }

    @Test
    fun extendedDataColorsMatchMainRenderer() {
        val panel = panel()
        val syscall = row("ioctl", FakeProfileData("ioctl",
            ExtendedData.SyscallData(tag = null, id = "ioctl", fullName = "ioctl")))
        val cpp = row("libart.so fn", FakeProfileData("libart.so fn", ExtendedData.CppFunctionData(
            tag = null, id = "1", fullName = "libart.so fn", classOrNamespace = "art",
            parameters = emptyList(), isUserCode = false, fileName = "runtime.cc", vAddress = 0L)))
        val noSymbol = row("?", FakeProfileData("?",
            ExtendedData.NoSymbolData(tag = null, id = "2", fullName = "?", isKernel = false)))
        val plain = row("java.lang.Object.toString")

        val syscallColor = panel.elementColorFor(syscall).fillColor
        val cppColor = panel.elementColorFor(cpp).fillColor
        val noSymbolColor = panel.elementColorFor(noSymbol).fillColor
        val plainColor = panel.elementColorFor(plain).fillColor

        // Разные категории -> разные цвета, как в MethodsColorImpl.getColorForMethod(ProfileRectangle).
        assertNotEquals("syscall must have own color", plainColor, syscallColor)
        assertNotEquals("cpp function must have own color", plainColor, cppColor)
        assertNotEquals("no-symbol must have own color", plainColor, noSymbolColor)
        assertNotEquals("categories must differ from each other", syscallColor, cppColor)
    }

    @Test
    fun selectedElementUsesSelectionColor() {
        val panel = panel()
        val selected = row("a.b.C.d")
        panel.selectedRow = selected
        val color = panel.elementColorFor(selected)
        assertEquals(Color(115, 238, 46), color.fillColor)
        assertEquals(Color(0, 0, 0, 131), color.borderColor)
        // другой элемент — базовый цвет
        val other = panel.elementColorFor(row("e.f.G.h"))
        assertNotEquals(Color(115, 238, 46), other.fillColor)
    }

    @Test
    fun searchModeFoundFocusedAndDarkening() {
        val panel = panel()
        val found = row("found.One")
        val other = row("missed.Two")
        val otherBase = panel.elementColorFor(other).fillColor // до включения поиска
        panel.renderFoundRows(listOf(found))

        val foundColor = panel.elementColorFor(found)
        assertEquals(Color(70, 238, 220), foundColor.fillColor)
        assertEquals(Color(0, 0, 0, 131), foundColor.borderColor)

        // value-equals копия найденной строки — НЕ found (identity, как ссылки в YAMP)
        val foundColorOfCopy = panel.elementColorFor(row("found.One"))
        assertNotEquals(Color(70, 238, 220), foundColorOfCopy.fillColor)

        val missed = panel.elementColorFor(other).fillColor
        assertNotEquals(otherBase, missed)
        assertEquals(Color(otherBase.red / 2, otherBase.green / 2, otherBase.blue / 2), missed)

        // выбранный в режиме поиска остаётся selectionColor
        val selected = row("missed.Three")
        panel.selectedRow = selected
        assertEquals(Color(115, 238, 46), panel.elementColorFor(selected).fillColor)

        // фокус на найденном
        panel.renderFoundRows(listOf(found, row("found.Four")))
        panel.setFocusedFoundIndex(0)
        val focused = panel.elementColorFor(found)
        assertEquals(Color(171, 238, 221), focused.fillColor)
        assertEquals(Color(246, 255, 241), focused.borderColor)

        // найденный + выбранный
        panel.selectedRow = found
        assertEquals(Color(110, 238, 161), panel.elementColorFor(found).fillColor)
    }

    @Test
    fun clearFoundRowsRestoresBaseColors() {
        val panel = panel()
        val r = row("com.custom.X.y")
        val base = panel.elementColorFor(r).fillColor
        panel.renderFoundRows(listOf(row("other")))
        val dimmed = panel.elementColorFor(r).fillColor
        assertNotEquals(base, dimmed)
        panel.clearFoundRows()
        assertEquals(base, panel.elementColorFor(r).fillColor)
    }
}
