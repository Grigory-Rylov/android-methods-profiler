package com.github.grishberg.profiler.ui

import com.github.grishberg.android.adb.AdbWrapper
import com.github.grishberg.profiler.chart.highlighting.MethodsColorRepository
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.CoroutinesDispatchersImpl
import com.github.grishberg.profiler.common.MainScope
import com.github.grishberg.profiler.common.settings.SettingsFacade
import com.github.grishberg.profiler.common.updates.ReleaseVersion
import com.github.grishberg.profiler.common.updates.UpdatesInfoPanel
import com.github.grishberg.profiler.ui.dialogs.ElementWithColorDialog
import com.github.grishberg.profiler.ui.dialogs.ElementWithColorDialogFactory
import com.github.grishberg.profiler.ui.dialogs.highlighting.HighlightDialog
import com.github.grishberg.profiler.ui.dialogs.recorder.JavaMethodsRecorderDialogView
import com.github.grishberg.profiler.ui.dialogs.recorder.PluginMethodsRecorderDialog
import com.github.grishberg.profiler.ui.dialogs.recorder.ProjectInfoProvider
import java.awt.Dialog
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel

class PluginDialogFactory(
    private val adbWrapper: AdbWrapper,
    private val projectInfoProvider: ProjectInfoProvider,
    private val methodsColorRepository: MethodsColorRepository
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
            projectInfoProvider,
            false,
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


    override fun createHighlightDialog(owner: JFrame): HighlightDialog {
        return HighlightDialog(owner, methodsColorRepository, this, false)
    }

    override fun createElementWithColorDialog(owner: Dialog, title: String): ElementWithColorDialog {
        return ElementWithColorDialog(owner, title, false)
    }

    override fun createElementWithColorDialog(owner: JFrame, title: String): ElementWithColorDialog {
        return ElementWithColorDialog(owner, title, false)
    }
}
