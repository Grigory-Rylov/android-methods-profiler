package com.github.grishberg.profiler.ui

import com.github.grishberg.profiler.chart.highlighting.MethodsColorRepository
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.CoroutinesDispatchersImpl
import com.github.grishberg.profiler.common.MainScope
import com.github.grishberg.profiler.common.settings.SettingsFacade
import com.github.grishberg.profiler.common.updates.ReleaseVersion
import com.github.grishberg.profiler.common.updates.UpdatesInfoPanel
import com.github.grishberg.profiler.ui.dialogs.ElementWithColorDialog
import com.github.grishberg.profiler.ui.dialogs.highlighting.HighlightDialog
import com.github.grishberg.profiler.ui.dialogs.recorder.JavaMethodsRecorderDialog
import com.github.grishberg.profiler.ui.dialogs.recorder.JavaMethodsRecorderDialogView
import com.github.grishberg.profiler.ui.dialogs.recorder.NoOpProjectInfo
import com.github.grishberg.profiler.ui.dialogs.recorder.ProjectInfoProvider
import java.awt.Dialog
import javax.swing.JFrame
import javax.swing.JPanel

class StandaloneAppDialogFactory(
    settings: SettingsFacade,
    private val methodsColorRepository: MethodsColorRepository
) : ViewFactory {
    private val projectInfoProvider = ProjectInfoProvider(NoOpProjectInfo, settings)
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
        return JavaMethodsRecorderDialog(
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
        return UpdatesInfoPanel(parent, version, closeCallback, linkClickCallback)
    }

    override fun shouldAddToolBar() = false

    override val shouldShowSetAdbMenu = true

    override fun createHighlightDialog(owner: JFrame): HighlightDialog {
        return HighlightDialog(owner, methodsColorRepository, this)
    }

    override fun createElementWithColorDialog(owner: Dialog, title: String): ElementWithColorDialog {
        return ElementWithColorDialog(owner, title, true)
    }

    override fun createElementWithColorDialog(owner: JFrame, title: String): ElementWithColorDialog {// not used
        return ElementWithColorDialog(owner, title, true)
    }
}
