package com.github.grishberg.profiler.common.updates

interface UpdatesChecker {
    var checkForUpdatesState: Boolean
    fun checkForUpdates(callback: UpdatesFoundAction)

    /**
     * returns true when should add menu item to toolbar.
     */
    fun shouldAddToMenu(): Boolean

    interface UpdatesFoundAction {
        fun onUpdatesFound(version: ReleaseVersion)
    }
}
