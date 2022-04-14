package com.github.grishberg.profiler.ui.dialogs.info

import com.github.grishberg.profiler.core.ProfileData

interface FocusElementDelegate {
    fun selectProfileElement(selectedElement: ProfileData)
    fun focusProfileElement(selectedElement: ProfileData)
}
