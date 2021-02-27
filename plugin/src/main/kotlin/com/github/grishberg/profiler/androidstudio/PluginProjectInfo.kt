package com.github.grishberg.profiler.androidstudio

import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.ui.dialogs.recorder.ProjectInfo
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet
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
        activityName = null
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
}
