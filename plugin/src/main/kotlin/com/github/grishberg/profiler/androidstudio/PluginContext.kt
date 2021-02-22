package com.github.grishberg.profiler.androidstudio

import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project

class PluginContext(
    private val project: Project
) : ProjectComponent {
    val adb by lazy { AsAdbWrapper(project, PluginLogger()) }
}

fun Project.context(): PluginContext {
    return this.getComponent(PluginContext::class.java)
}
