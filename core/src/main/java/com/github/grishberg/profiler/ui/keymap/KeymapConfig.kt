package com.github.grishberg.profiler.ui.keymap

interface KeymapConfig {

    val zoomOutKeyCode: Int
    val zoomInActionKeyCode: Int
    val panLeftActionKeyCode: Int
    val panRightActionKeyCode: Int
    val upActionKeyCode: Int
    val downActionKeyCode: Int
    val focusNextFoundItemActionKeyCode: Int
    val focusPrevFoundItemActionKeyCode: Int
    val removeSelectionActionKeyCode: Int
    val theadTimeModeActionKeyCode: Int
    val globalTimeModeActionKeyCode: Int
    val toggleBookmarkModeActionKeyCode: Int
    val goToFindActionKeyCode: Int
    val copyStackTraceActionKeyCode: Int
    val openFileDialogActionKeyCode: Int
    val newTraceActionKeyCode: Int
    val openFileDialogNewWindowActionKeyCode: Int
    val newTraceNewWindowActionKeyCode: Int
    val foundToMarkerKeyCode: Int
    val showThreadSwitcherKeyCode: Int
    val switchToMainThreadKeyCode: Int
    val findAllChildrenKeyCode: Int
    val addBookmarkActionKeyCode: Int
    val copySelectedFullClassNameActionKeyCode: Int
    val removeCurrentBookmarkActionKeyCode: Int
    val centerSelectedElementActionKeyCode: Int
    val resetZoomActionKeyCode: Int
    val fitSelectedElementActionKeyCode: Int
    val nextBookmarkActionKeyCode: Int
    val prevBookmarkActionKeyCode: Int
    val clearAllBookmarksActionKeyCode: Int
    val generateReportsActionKeyCode: Int
    val changeFontSizeActionTrueKeyCode: Int
    val changeFontSizeActionFalseKeyCode: Int
    val copySelectedShortClassNameActionKeyCode: Int
    val exportTraceWithBookmarksActionKeyCode: Int
    val copySelectedShortClassNameWithoutMethodActionKeyCode: Int
    val openRangeDialogKeyCode: Int
    val deleteCurrentFile: Int
}
