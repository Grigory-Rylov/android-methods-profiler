package com.github.grishberg.profiler.ui.dialogs.recorder

import com.github.grishberg.profiler.common.settings.SettingsFacade

class ProjectInfoProvider(
    private val projectInfo: ProjectInfo,
    private val settingsFacade: SettingsFacade
) {
    fun packageName(): String {
        if (settingsFacade.packageName.isEmpty()) {
            return projectInfo.packageName ?: ""
        }
        return settingsFacade.packageName
    }

    fun activityName(): String {
        if (settingsFacade.activityName.isEmpty()) {
            return projectInfo.activityName ?: ""
        }
        return settingsFacade.activityName
    }

    fun updatePackageName(newPackageName: String) {
        settingsFacade.packageName = newPackageName
    }

    fun updateActivityName(newActivityName: String) {
        settingsFacade.activityName = newActivityName
    }
}
