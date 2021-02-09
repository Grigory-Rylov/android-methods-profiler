package com.github.grishberg.profiler.common.updates

import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.CoroutinesDispatchers
import com.github.grishberg.profiler.common.settings.SettingsFacade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

const val SETTINGS_CHECK_FOR_UPDATES = "Main.checkForUpdates"

class StandaloneAppUpdatesChecker(
    private val settings: SettingsFacade,
    private val coroutineScope: CoroutineScope,
    private val dispatchers: CoroutinesDispatchers,
    private val logger: AppLogger
) : UpdatesChecker {

    override var checkForUpdatesState: Boolean
        get() = settings.isCheckForUpdatesEnabled
        set(value) {
            settings.isCheckForUpdatesEnabled = value
        }

    private var alreadyChecked = true
    private val githubReleaseChecker = GithubReleaseChecker(logger)
    private val currentVersion = javaClass.getPackage().implementationVersion ?: "00.00.00.00"
    private val versionParser = VersionParser(currentVersion)

    override fun checkForUpdates(callback: UpdatesChecker.UpdatesFoundAction) {
        if (!alreadyChecked || !checkForUpdatesState) {
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

    override fun shouldAddToMenu(): Boolean = true
}
