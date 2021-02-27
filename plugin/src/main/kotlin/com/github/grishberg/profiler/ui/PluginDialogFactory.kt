package com.github.grishberg.profiler.ui

import com.github.grishberg.android.adb.AdbWrapper
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.CoroutinesDispatchersImpl
import com.github.grishberg.profiler.common.MainScope
import com.github.grishberg.profiler.common.settings.SettingsFacade
import com.github.grishberg.profiler.common.updates.ReleaseVersion
import com.github.grishberg.profiler.common.updates.UpdatesInfoPanel
import com.github.grishberg.profiler.ui.dialogs.recorder.JavaMethodsRecorderDialogView
import com.github.grishberg.profiler.ui.dialogs.recorder.PluginMethodsRecorderDialog
import com.github.grishberg.profiler.ui.dialogs.recorder.ProjectInfoProvider
import javax.swing.JFrame
import javax.swing.JPanel

class PluginDialogFactory(
    private val adbWrapper: AdbWrapper,
    private val projectInfoProvider: ProjectInfoProvider
) : ViewFactory {
    override val shortTitle: String = "YAMP"
    override val title: String = shortTitle
    override val shouldShowSetAdbMenu = false

    override fun createJavaMethodsRecorderDialog(
        coroutineScope: MainScope,
        coroutinesDispatchers: CoroutinesDispatchersImpl,
        frame: JFrame,
        settings: SettingsFacade,
        log: AppLogger
    ): JavaMethodsRecorderDialogView {
        return PluginMethodsRecorderDialog(
            adbWrapper,
            coroutineScope, coroutinesDispatchers,
            frame, settings, log,
            projectInfoProvider
        )
    }

    override fun createUpdatesInfoPanel(
        parent: JPanel,
        version: ReleaseVersion,
        closeCallback: Runnable,
        linkClickCallback: Runnable
    ): UpdatesInfoPanel {
        throw IllegalStateException("Updates via idea plugins")
    }

    override fun shouldAddToolBar() = true
}
