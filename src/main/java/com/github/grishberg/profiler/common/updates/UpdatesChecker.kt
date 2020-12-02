package com.github.grishberg.profiler.common.updates

interface UpdatesChecker {
    fun checkForUpdates(callback: UpdatesFoundAction)

    interface UpdatesFoundAction {
        fun onUpdatesFound(version: ReleaseVersion)
    }
}
