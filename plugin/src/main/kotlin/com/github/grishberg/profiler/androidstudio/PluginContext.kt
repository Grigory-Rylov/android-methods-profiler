package com.github.grishberg.profiler.androidstudio

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

@Service(Service.Level.PROJECT)
class PluginContext(
    private val project: Project
) : ProjectManagerListener {
    val adb by lazy { AsAdbWrapper(project, PluginLogger()) }
}

fun Project.context(): PluginContext {
    return this.getService(PluginContext::class.java)
}
