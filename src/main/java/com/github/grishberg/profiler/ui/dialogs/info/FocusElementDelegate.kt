package com.github.grishberg.profiler.ui.dialogs.info

import com.github.grishberg.profiler.analyzer.ProfileDataImpl

interface FocusElementDelegate {
    fun selectProfileElement(selectedElement: ProfileDataImpl)
}
