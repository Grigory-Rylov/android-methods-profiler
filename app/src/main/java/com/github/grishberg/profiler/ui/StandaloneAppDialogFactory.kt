package com.github.grishberg.profiler.ui

import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.CoroutinesDispatchersImpl
import com.github.grishberg.profiler.common.MainScope
import com.github.grishberg.profiler.common.settings.SettingsFacade
import com.github.grishberg.profiler.common.updates.ReleaseVersion
import com.github.grishberg.profiler.common.updates.UpdatesInfoPanel
import com.github.grishberg.profiler.ui.dialogs.recorder.JavaMethodsRecorderDialog
import com.github.grishberg.profiler.ui.dialogs.recorder.JavaMethodsRecorderDialogView
import javax.swing.JFrame
import javax.swing.JPanel

class StandaloneAppDialogFactory : ViewFactory {
    override val shortTitle: String
        get() = "YAMP"
    override val title: String
        get() = shortTitle + " v" + javaClass.getPackage().implementationVersion

    override fun createJavaMethodsRecorderDialog(
        coroutineScope: MainScope,
        coroutinesDispatchers: CoroutinesDispatchersImpl,
        frame: JFrame,
        settings: SettingsFacade,
        log: AppLogger
    ): JavaMethodsRecorderDialogView {
        return JavaMethodsRecorderDialog(coroutineScope, coroutinesDispatchers, frame, settings, log)
    }

    override fun createUpdatesInfoPanel(
        parent: JPanel,
        version: ReleaseVersion,
        closeCallback: Runnable,
        linkClickCallback: Runnable
    ): UpdatesInfoPanel {
        return UpdatesInfoPanel(parent, version, closeCallback, linkClickCallback)
    }

    override fun shouldAddToolBar() = false
}
