package com.github.grishberg.profiler.common.updates

class VersionParser(currentVersionName: String) {
    private val regex = Regex("(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+)")
    private val currentVersion = versionToCode(currentVersionName)

    fun shouldUpdate(versionFromRepositoryName: String): Boolean {
        val versionFromRepository = versionToCode(versionFromRepositoryName)
        return versionFromRepository > currentVersion
    }

    private fun versionToCode(version: String): Long {
        val match = regex.find(version) ?: return 0L
        val (yy, mm, dd, bb) = match.destructured
        return yy.toLong() * 1000000L + mm.toLong() * 10000L + dd.toLong() * 100 + bb.toLong()
    }
}
