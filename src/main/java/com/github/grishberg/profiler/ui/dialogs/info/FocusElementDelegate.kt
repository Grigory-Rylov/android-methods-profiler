package com.github.grishberg.profiler.ui.dialogs.info

import com.github.grishberg.profiler.analyzer.ProfileData

interface FocusElementDelegate {
    fun selectProfileElement(selectedElement: ProfileData)
}
