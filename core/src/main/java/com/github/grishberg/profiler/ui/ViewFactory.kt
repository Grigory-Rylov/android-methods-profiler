package com.github.grishberg.profiler.ui

import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.CoroutinesDispatchersImpl
import com.github.grishberg.profiler.common.MainScope
import com.github.grishberg.profiler.common.settings.SettingsFacade
import com.github.grishberg.profiler.common.updates.ReleaseVersion
import com.github.grishberg.profiler.common.updates.UpdatesInfoPanel
import com.github.grishberg.profiler.ui.dialogs.ElementWithColorDialogFactory
import com.github.grishberg.profiler.ui.dialogs.highlighting.HighlightDialog
import com.github.grishberg.profiler.ui.dialogs.recorder.JavaMethodsRecorderDialogView
import javax.swing.JFrame
import javax.swing.JPanel

interface ViewFactory: ElementWithColorDialogFactory {
    val title: String
    val shortTitle: String
    val shouldShowSetAdbMenu: Boolean

    fun createJavaMethodsRecorderDialog(
        coroutineScope: MainScope,
        coroutinesDispatchers: CoroutinesDispatchersImpl,
        frame: JFrame,
        settings: SettingsFacade,
        log: AppLogger
    ): JavaMethodsRecorderDialogView

    fun createUpdatesInfoPanel(
        parent: JPanel,
        version: ReleaseVersion,
        closeCallback: Runnable,
        linkClickCallback: Runnable
    ): UpdatesInfoPanel

    fun shouldAddToolBar(): Boolean
    fun createHighlightDialog(frame: JFrame): HighlightDialog
}
