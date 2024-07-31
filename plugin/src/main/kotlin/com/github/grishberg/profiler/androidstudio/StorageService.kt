package com.github.grishberg.profiler.androidstudio

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@State(
    name = "PluginSettingsState", storages = [Storage("yamp-settings.xml")]
)
class StorageService : PersistentStateComponent<PluginState> {
    private var storage = PluginState()

    override fun getState(): PluginState = storage

    override fun loadState(newStorage: PluginState) {
        storage = newStorage
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): PersistentStateComponent<PluginState> {
            return project.getService(StorageService::class.java)
        }
    }
}
