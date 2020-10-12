package com.github.grishberg.profiler.plugins.stages

import com.github.grishberg.android.profiler.core.ProfileData

private const val REGEX_PATTERN = "([a-z]+\\d+)"

class MethodsAvailabilityImpl : MethodsAvailability {

    private val regex = Regex(REGEX_PATTERN)

    override fun isMethodAvailable(method: ProfileData): Boolean {
        if (isExcludedPackagePrefix(method)) return false

        if (method.name.contains("$")) {
            if (isMethodWithLambdaInName(method.name.toLowerCase())) {
                return false
            }
        }
        /*
        TODO: check packages
        if (packages.isEmpty()) {
            return true
        }

        for (pkg in packages) {
            if (method.name.startsWith(pkg)) {
                return true
            }
        }
         */
        return true
    }

    private fun isMethodWithLambdaInName(name: String): Boolean {
        val pos = name.lastIndexOf('$')
        if (pos < 0) {
            return false
        }
        return regex.containsMatchIn(name.substring(pos + 1))
    }

    private fun isExcludedPackagePrefix(method: ProfileData): Boolean {
        for (pkg in IgnoredMethods.exceptions) {
            if (method.name.startsWith(pkg)) {
                return true
            }
        }
        return false
    }
}
