package com.github.grishberg.profiler.common.updates

import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.CoroutinesDispatchers
import com.github.grishberg.profiler.common.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

private const val SETTINGS_CHECK_FOR_UPDATES = "Main.checkForUpdates"

class StandaloneAppUpdatesChecker(
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
    private val dispatchers: CoroutinesDispatchers,
    private val logger: AppLogger
) : UpdatesChecker {
    private var shouldCheck = true
    private val githubReleaseChecker = GithubReleaseChecker(logger)
    private val currentVersion = javaClass.getPackage().implementationVersion ?: "99.99.99.99"
    private val versionParser = VersionParser(currentVersion)

    override fun checkForUpdates(callback: UpdatesChecker.UpdatesFoundAction) {
        if (!shouldCheck || !settingsRepository.getBoolValueOrDefault(SETTINGS_CHECK_FOR_UPDATES, true)) {
            return
        }

        coroutineScope.launch {
            val release = coroutineScope.async(dispatchers.worker) {
                githubReleaseChecker.getLastRelease("android-methods-profiler", "grigory-rylov")
            }.await() ?: return@launch

            if (versionParser.shouldUpdate(release.versionName)) {
                callback.onUpdatesFound(release)
            }
        }
    }
}
