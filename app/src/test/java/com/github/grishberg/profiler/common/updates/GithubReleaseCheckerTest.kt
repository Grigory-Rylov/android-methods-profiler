package com.github.grishberg.profiler.common.updates

import com.github.grishberg.profiler.NoOpLogger
import org.junit.Assert.assertNotNull
import org.junit.Ignore
import org.junit.Test

class GithubReleaseCheckerTest {
    private val underTest = GithubReleaseChecker(NoOpLogger)

    @Ignore
    @Test
    fun checkLastRelease() {
        val response = underTest.getLastRelease("android-methods-profiler", "grigory-rylov")
        assertNotNull(response)
    }
}
