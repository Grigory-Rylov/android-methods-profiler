package com.github.grishberg.profiler.ui.dialogs.info

import com.github.grishberg.android.profiler.core.ProfileData

interface FocusElementDelegate {
    fun selectProfileElement(selectedElement: ProfileData)
}
