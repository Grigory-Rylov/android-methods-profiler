package com.github.grishberg.profiler.common.updates

interface RemoteReleaseChecker {
    fun getLastRelease(repo: String, owner: String): ReleaseVersion?
}
