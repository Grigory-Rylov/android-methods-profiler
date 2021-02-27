package com.github.grishberg.profiler.androidstudio

import com.android.ddmlib.IDevice
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.run.activity.ActivityLocator
import com.android.tools.idea.run.activity.DefaultActivityLocator
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.ui.dialogs.recorder.ProjectInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.sdk.AndroidSdkUtils
import org.jetbrains.android.util.AndroidUtils

class PluginProjectInfo(
    project: Project,
    private val logger: AppLogger
) : ProjectInfo {
    override val packageName: String?
    override val activityName: String?

    init {
        val facets = AndroidUtils.getApplicationFacets(project)
        packageName = createPackageName(facets)
        val devices = AndroidSdkUtils.getDebugBridge(project)?.devices ?: emptyArray()
        activityName = getDefaultActivityName(facets, devices)
    }

    private fun createPackageName(facets: List<AndroidFacet>): String? {
        if (facets.isNotEmpty()) {
            logger.d("found ${facets.size} facets")
            val facet = facets.firstOrNull()
            if (facet != null) {
                val applicationId = AndroidModuleModel.get(facet)?.applicationId
                logger.d("selected applicationId =$applicationId")
                return applicationId
            }
        }
        return null
    }

    @Throws(ActivityLocator.ActivityLocatorException::class)
    private fun getDefaultActivityName(facets: List<AndroidFacet>, devices: Array<IDevice>): String? {
        val facet = facets.firstOrNull() ?: return null
        val device = devices.firstOrNull() ?: return null
        return ApplicationManager.getApplication()
            .runReadAction(ThrowableComputable<String, ActivityLocator.ActivityLocatorException?> {
                DefaultActivityLocator(
                    facet
                ).getQualifiedActivityName(device)
            })
    }
}
