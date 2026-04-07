package com.github.grishberg.profiler.androidstudio

import com.android.tools.idea.model.AndroidModel
import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.ui.dialogs.recorder.ProjectInfo
import com.intellij.facet.FacetManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.jetbrains.android.facet.AndroidFacet
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList

class PluginProjectInfo(
    project: Project, private val logger: AppLogger
) : ProjectInfo {

    override val packageName: String?
    override val activityName: String?

    init {
        val facets = try {
            getAndroidFacets(project)
        } catch (e: Exception) {
            logger.e("getAndroidFacets error", e)
            emptyList()
        }
        packageName = createPackageName(facets)
        activityName = try {
            getDefaultActivityName(facets)
        } catch (e: Exception) {
            logger.e("PluginProjectInfo: Error while fetching getDefaultActivityName", e)
            null
        }
    }

    fun getAndroidFacets(project: Project): List<AndroidFacet> {
        val modules = ModuleManager.getInstance(project).modules
        return modules.flatMap { module ->
            FacetManager.getInstance(module).allFacets.filterIsInstance<AndroidFacet>()
        }
    }

    private fun createPackageName(facets: List<AndroidFacet>): String? {
        if (facets.isNotEmpty()) {
            logger.d("found ${facets.size} facets")
            val facet = facets.firstOrNull()
            if (facet != null) {
                try {
                    val applicationId = AndroidModel.get(facet)?.applicationId
                    logger.d("selected applicationId =$applicationId")
                    return applicationId
                } catch (e: Throwable) {
                    logger.e("Error while trying extract module name from facet", e)
                }
            }
        }
        return null
    }

    @Throws(Exception::class)
    private fun getDefaultActivityName(facets: List<AndroidFacet>): String? {
        val facet = facets.firstOrNull() ?: return null
        return ApplicationManager.getApplication().runReadAction<String?> {
            try {
                val manifestFile = findManifestFileReflection(facet)
                if (manifestFile == null || !manifestFile.exists()) {
                    logger.d("Manifest file not found")
                    return@runReadAction null
                }
                val factory = DocumentBuilderFactory.newInstance()
                val builder = factory.newDocumentBuilder()
                val document: Document = builder.parse(manifestFile)
                val activities: NodeList = document.getElementsByTagName("activity")
                for (i in 0 until activities.length) {
                    val activity = activities.item(i) as Element
                    val intentFilters = activity.getElementsByTagName("intent-filter")
                    for (j in 0 until intentFilters.length) {
                        val intentFilter = intentFilters.item(j) as Element
                        val actions = intentFilter.getElementsByTagName("action")
                        val categories = intentFilter.getElementsByTagName("category")
                        var hasMainAction = false
                        var hasLauncherCategory = false
                        for (k in 0 until actions.length) {
                            val actionNode = actions.item(k) as Element
                            val actionName = actionNode.getAttribute("android:name")
                            if (actionName == "android.intent.action.MAIN") {
                                hasMainAction = true
                            }
                        }
                        for (k in 0 until categories.length) {
                            val categoryNode = categories.item(k) as Element
                            val categoryName = categoryNode.getAttribute("android:name")
                            if (categoryName == "android.intent.category.LAUNCHER") {
                                hasLauncherCategory = true
                            }
                        }
                        if (hasMainAction && hasLauncherCategory) {
                            val activityName = activity.getAttribute("android:name")
                            if (activityName.isNotEmpty()) {
                                logger.d("Found default activity: $activityName")
                                return@runReadAction activityName
                            }
                        }
                    }
                }
                logger.d("No default activity found in manifest")
                null
            } catch (e: Throwable) {
                logger.e("Error while parsing manifest", e)
                null
            }
        }
    }

    private fun findManifestFileReflection(facet: AndroidFacet): File? {
        try {
            val sourceProviderClass = Class.forName("org.jetbrains.android.facet.AndroidFacet\$SourceProvider")
            val sourceProviderGetter = sourceProviderClass.getMethod("getSourceProvider")
            val sourceProvider = sourceProviderGetter.invoke(facet)
            if (sourceProvider != null) {
                val allSourceFilesMethod = sourceProvider.javaClass.getMethod("getAllSourceFiles")

                @Suppress("UNCHECKED_CAST") val allSourceFiles =
                    allSourceFilesMethod.invoke(sourceProvider) as List<File>
                for (file in allSourceFiles) {
                    if (file.name == "AndroidManifest.xml") {
                        logger.d("Found manifest via sourceProvider reflection")
                        return file
                    }
                }
            }
        } catch (e: Throwable) {
            logger.d("sourceProvider reflection failed: ${e.message}")
        }

        try {
            val manifestClass = Class.forName("org.jetbrains.android.dom.manifest.Manifest")
            val manifestGetter = AndroidFacet::class.java.getMethod("getManifest")
            val manifest = manifestGetter.invoke(facet)
            if (manifest != null) {
                val xmlFileGetter = manifestClass.getMethod("getXmlFile")
                val xmlFile = xmlFileGetter.invoke(manifest) as? File
                if (xmlFile != null) {
                    logger.d("Found manifest via manifest.xmlFile reflection")
                    return xmlFile
                }
            }
        } catch (e: Throwable) {
            logger.d("manifest.xmlFile reflection failed: ${e.message}")
        }

        return null
    }
}
