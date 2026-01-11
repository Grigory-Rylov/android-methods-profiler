package com.github.grishberg.profiler.ui.dialogs.recorder

import com.github.grishberg.profiler.common.settings.SettingsFacade

class ProjectInfoProvider(
    private val projectInfo: ProjectInfo, private val settingsFacade: SettingsFacade
) {

    fun packageNames(): List<String> {
        return getValueOrDefault(
            settingsFacade.packageNames,
            projectInfo.packageName
        )
    }

    fun activityNames(): List<String> {
        return getValueOrDefault(
            settingsFacade.activityNames,
            projectInfo.activityName
        )
    }

    private fun <T> getValueOrDefault(
        settingsValue: List<T>,
        defaultValue: T?
    ): List<T> {
        return settingsValue.ifEmpty {
            listOfNotNull(defaultValue)
        }
    }

    fun updatePackageName(newPackageName: String) {
        updateHistory(
            newValue = newPackageName,
            getCurrentList = { packageNames() },
            updateSettings = { settingsFacade.packageNames = it }
        )
    }

    fun updateActivityName(newActivityName: String) {
        updateHistory(
            newValue = newActivityName,
            getCurrentList = { activityNames() },
            updateSettings = { settingsFacade.activityNames = it }
        )
    }

    companion object {

        private const val MAX_ITEMS_COUNT = 50

        fun updateHistory(
            newValue: String,
            getCurrentList: () -> List<String>,
            updateSettings: (List<String>) -> Unit
        ) {
            if (newValue.isBlank()) return

            val currentList = getCurrentList().toMutableList()

            // Удаляем дубликат, если он существует
            currentList.remove(newValue)

            // Добавляем новое значение в начало
            currentList.add(0, newValue)

            // Ограничиваем размер списка
            val trimmedList = if (currentList.size > MAX_ITEMS_COUNT) {
                currentList.take(MAX_ITEMS_COUNT)
            } else {
                currentList
            }

            updateSettings(trimmedList)
        }

    }
}
