package com.github.grishberg.profiler.chart

import com.github.grishberg.profiler.core.ProfileData
import com.github.grishberg.profiler.common.AppLogger
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.geom.AffineTransform

interface DependenciesFoundAction {
    /**
     * Is called when found any constructors that resolved for creating selected object.
     */
    fun onDependenciesFound(dependencies: List<ProfileData>)
}

/**
 * Collection of call stacktrace from called to caller.
 */
class CalledStacktrace(
    private val renderer: SelectionRenderer,
    private val logger: AppLogger
) {
    private var lastDaggerFactory: ProfileData? = null
    private var lastSelectedElement: ProfileData? = null
    private val foundProfileData = mutableListOf<ProfileData>()
    private val shouldSkipBaseLangObjects = false
    private val findChildrenStrategy = FindChildrenStrategy()
    private val findDaggerStackTraceStrategy = FindDaggerStackTraceStrategy()
    private val findDaggerCallerMethodStrategy = FindDaggerCallerMethodStrategy()
    private var addChildrenStrategy: ApplicableChildrenStrategy = findChildrenStrategy

    var dependenciesFoundAction: DependenciesFoundAction? = null

    fun findChildren(
        profileData: ProfileData,
        threadId: Int,
        shouldShowDialog: Boolean = true
    ) {
        addChildrenStrategy = findChildrenStrategy
        clearAllData()
        lastSelectedElement = profileData
        renderer.setCurrentThreadId(threadId)

        if (foundProfileData.isNotEmpty()) {
            dependenciesFoundAction?.onDependenciesFound(foundProfileData)
        }
        renderer.addCallerRectangle(profileData)
        foundProfileData.add(profileData)
        //TODO: use coroutine
        for (child in profileData.children) {
            findNextChild(child)
        }
        if (shouldShowDialog && foundProfileData.isNotEmpty()) {
            dependenciesFoundAction?.onDependenciesFound(foundProfileData)
        }
    }

    /**
     * Find classes created while [found] element created by dagger.
     */
    fun findDaggerCreationTrace(
        found: ProfileData,
        threadId: Int,
        shouldShowDialog: Boolean = true
    ) {
        addChildrenStrategy = findDaggerStackTraceStrategy
        clearAllData()
        if (!found.name.endsWith(".<init>")) {
            return
        }
        renderer.setCurrentThreadId(threadId)
        lastSelectedElement = found

        //TODO: use coroutine
        findDaggerFactory(found)
        if (shouldShowDialog && foundProfileData.isNotEmpty()) {
            dependenciesFoundAction?.onDependenciesFound(foundProfileData)
        }
    }

    private fun findDaggerFactory(calledChild: ProfileData) {
        renderer.addCallerRectangle(calledChild)

        val parent = calledChild.parent ?: return
        if (!isDaggerMethod(parent)) {
            // this is not created by Dagger
            clearAllData()
            return
        }
        val currentChildPosition = parent.children.indexOf(calledChild)
        if (isDaggerFactoryMethod(parent) && currentChildPosition > 0) {
            renderer.addCallerRectangle(parent)
            // found factory, now find all dependencies
            for (i in 0 until currentChildPosition) {
                findNextChild(parent.children[i])
            }
            return
        }
        findDaggerFactory(parent)
    }

    private fun isDaggerFactoryMethod(parent: ProfileData) = parent.name.endsWith("Factory.get") ||
            parent.name.contains(".Dagger")

    /**
     * Find call trace from [found] until caller.
     */
    fun findDaggerCallerChain(
        found: ProfileData,
        threadId: Int
    ) {
        addChildrenStrategy = findDaggerCallerMethodStrategy
        clearAllData()
        renderer.setCurrentThreadId(threadId)
        lastSelectedElement = found
        lastDaggerFactory = null

        //TODO: use coroutine
        renderer.addCallTraceItems(found)
        findDaggerCallTrace(found.parent, found)
    }

    private fun findDaggerCallTrace(currentParent: ProfileData?, child: ProfileData) {
        if (currentParent == null) return
        if (!isDaggerMethod(currentParent)) {
            // this is not created by Dagger
            clearAllData()
            return
        }

        val parent = currentParent.parent
        if (parent == null) {

            clearAllData()
            return
        }

        val currentChildIndex = currentParent.children.indexOf(child)
        if (isDaggerFactoryMethod(currentParent) && (currentChildIndex < currentParent.children.size - 1)) {
            renderer.addCallerRectangle(currentParent)
            // this is root factory
            if (currentParent.children.size == 1) {
                // someone called factory directly to create instance. mark this factory and exit.
                return
            }

            val callerProfileData = currentParent.children.last()
            findNextChildUntilConstructor(callerProfileData)
            return
        }
        renderer.addCallTraceItems(currentParent)
        findDaggerCallTrace(parent, currentParent)
    }

    private fun findNextChildUntilConstructor(parent: ProfileData) {
        renderer.addCallerRectangle(parent)
        if (parent.name.endsWith(".<init>")) {
            return
        }
        for (child in parent.children) {
            if (isDaggerMethod(child) || child.name.endsWith(".<init>")) {
                findNextChildUntilConstructor(child)
            }
        }
    }

    private fun isDaggerMethod(parent: ProfileData) = parent.name.endsWith("Factory.get") ||
            parent.name.endsWith("Factory.newInstance") || parent.name.contains(".Dagger") ||
            parent.name.contains(".provide") ||
            parent.name == "dagger.internal.DoubleCheck.get"

    private fun findNextChild(parent: ProfileData) {
        foundProfileData.add(parent)

        renderer.addCallTraceItems(parent)
        for (child in parent.children) {
            findNextChild(child)
        }
    }

    fun removeElements() {
        lastSelectedElement = null
        renderer.setCurrentThreadId(-1)
        clearAllData()
    }

    private fun clearAllData() {
        renderer.clear()
        foundProfileData.clear()
    }

    fun invalidate() {
        addChildrenStrategy.invalidate()
    }

    fun draw(
        g: Graphics2D,
        at: AffineTransform,
        fm: FontMetrics,
        currentThreadId: Int,
        selected: ProfileRectangle?,
        minimumSizeInMs: Double,
        screenLeft: Double,
        screenTop: Double,
        screenRight: Double,
        screenBottom: Double
    ) {
        renderer.draw(
            g, at, fm, currentThreadId, selected, minimumSizeInMs,
            screenLeft, screenTop, screenRight, screenBottom
        )
    }

    private inner class FindDaggerCallerMethodStrategy : ApplicableChildrenStrategy {
        override fun shouldAddProfileData(parent: ProfileData): Boolean {
            return parent.name.endsWith(".<init>")
        }

        override fun invalidate() {
            lastSelectedElement?.let {
                findDaggerCallerChain(it, renderer.currentThreadId)
            }
        }
    }

    private inner class FindDaggerStackTraceStrategy : ApplicableChildrenStrategy {
        override fun shouldAddProfileData(parent: ProfileData): Boolean {
            return parent.name.endsWith(".<init>") && (
                    !shouldSkipBaseLangObjects || (!parent.name.startsWith("java.lang.") &&
                            !parent.name.startsWith("java.util.") &&
                            !parent.name.startsWith("sun.misc.") &&
                            !parent.name.startsWith("libcore.util.")))
        }

        override fun invalidate() {
            lastSelectedElement?.let {
                findDaggerCreationTrace(it, renderer.currentThreadId, shouldShowDialog = false)
            }
        }
    }

    private inner class FindChildrenStrategy : ApplicableChildrenStrategy {
        override fun shouldAddProfileData(parent: ProfileData) = true
        override fun invalidate() {
            lastSelectedElement?.let {
                findChildren(it, renderer.currentThreadId, shouldShowDialog = false)
            }
        }
    }

    private interface ApplicableChildrenStrategy {
        fun shouldAddProfileData(parent: ProfileData): Boolean
        fun invalidate()
    }
}
