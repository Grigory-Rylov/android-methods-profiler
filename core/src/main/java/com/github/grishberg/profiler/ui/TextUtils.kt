package com.github.grishberg.profiler.ui

class TextUtils {
    fun shortClassMethodName(s: String): String {
        var dotsCount = 0
        for (i in s.length - 1 downTo 0) {
            if (s[i] == '.') dotsCount++
            if (dotsCount == 2) {
                return s.substring(i + 1)
            }
        }
        return s
    }

    fun shortClassName(s: String): String {
        val lastDotPost = s.lastIndexOf(".")
        var dotsCount = 0
        for (i in s.length - 1 downTo 0) {
            if (s[i] == '.') dotsCount++
            if (dotsCount == 2) {
                return s.substring(i + 1, lastDotPost)
            }
        }
        return s
    }
}