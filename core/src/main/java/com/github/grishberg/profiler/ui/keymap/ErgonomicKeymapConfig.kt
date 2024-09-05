package com.github.grishberg.profiler.ui.keymap

import java.awt.event.KeyEvent

class ErgonomicKeymapConfig : KeymapConfig {

    override val zoomOutKeyCode: Int = KeyEvent.VK_E
    override val zoomInActionKeyCode: Int = KeyEvent.VK_D
    override val panLeftActionKeyCode: Int = KeyEvent.VK_S
    override val panRightActionKeyCode: Int = KeyEvent.VK_F
    override val upActionKeyCode: Int = KeyEvent.VK_I
    override val downActionKeyCode: Int = KeyEvent.VK_K
    override val focusNextFoundItemActionKeyCode: Int = KeyEvent.VK_R
    override val focusPrevFoundItemActionKeyCode: Int = KeyEvent.VK_W
    override val removeSelectionActionKeyCode: Int = KeyEvent.VK_ESCAPE
    override val theadTimeModeActionKeyCode: Int = KeyEvent.VK_T
    override val globalTimeModeActionKeyCode: Int = KeyEvent.VK_G
    override val toggleBookmarkModeActionKeyCode: Int = KeyEvent.VK_B
    override val goToFindActionKeyCode: Int = KeyEvent.VK_F
    override val copyStackTraceActionKeyCode: Int = KeyEvent.VK_S
    override val openFileDialogActionKeyCode: Int = KeyEvent.VK_O
    override val newTraceActionKeyCode: Int = KeyEvent.VK_N
    override val openFileDialogNewWindowActionKeyCode: Int = KeyEvent.VK_N
    override val newTraceNewWindowActionKeyCode: Int = KeyEvent.VK_M
    override val foundToMarkerKeyCode: Int = KeyEvent.VK_M
    override val showThreadSwitcherKeyCode: Int = KeyEvent.VK_T
    override val switchToMainThreadKeyCode: Int = KeyEvent.VK_0
    override val findAllChildrenKeyCode: Int = KeyEvent.VK_I
    override val addBookmarkActionKeyCode: Int = KeyEvent.VK_M
    override val copySelectedFullClassNameActionKeyCode: Int = KeyEvent.VK_C
    override val removeCurrentBookmarkActionKeyCode: Int = KeyEvent.VK_R
    override val centerSelectedElementActionKeyCode: Int = KeyEvent.VK_C
    override val resetZoomActionKeyCode: Int = KeyEvent.VK_Z
    override val fitSelectedElementActionKeyCode: Int = KeyEvent.VK_X
    override val nextBookmarkActionKeyCode: Int = KeyEvent.VK_R
    override val prevBookmarkActionKeyCode: Int = KeyEvent.VK_W
    override val clearAllBookmarksActionKeyCode: Int = KeyEvent.VK_BACK_SPACE
    override val generateReportsActionKeyCode: Int = KeyEvent.VK_P
    override val changeFontSizeActionTrueKeyCode: Int = KeyEvent.VK_PLUS
    override val changeFontSizeActionFalseKeyCode: Int = KeyEvent.VK_MINUS
    override val copySelectedShortClassNameActionKeyCode: Int = KeyEvent.VK_C
    override val exportTraceWithBookmarksActionKeyCode: Int = KeyEvent.VK_E
    override val copySelectedShortClassNameWithoutMethodActionKeyCode: Int = KeyEvent.VK_C
    override val openRangeDialogKeyCode: Int = KeyEvent.VK_R
}
