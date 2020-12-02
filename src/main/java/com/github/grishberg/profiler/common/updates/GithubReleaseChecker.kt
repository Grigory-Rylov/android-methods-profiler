package com.github.grishberg.profiler.common.updates

import com.github.grishberg.profiler.common.AppLogger
import com.github.grishberg.profiler.common.updates.github.ResponseModel
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.lang.reflect.Type
import java.net.URL

private const val TAG = "GithubReleaseChecker"

class GithubReleaseChecker(
    private val logger: AppLogger
) : RemoteReleaseChecker {
    private var gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ssz")
        .create()

    override fun getLastRelease(repo: String, owner: String): ReleaseVersion? {
        val json = readUrl("https://api.github.com/repos/$owner/$repo/releases/latest") ?: return null

        val responseType: Type = object : TypeToken<ResponseModel>() {}.type

        try {
            val loadedRelease: ResponseModel = gson.fromJson(json, responseType)
            return ReleaseVersion(
                loadedRelease.versionName,
                loadedRelease.htmlUrl,
                loadedRelease.body
            )
        } catch (e: FileNotFoundException) {
            logger.d("$TAG: there is no bookmarks file.")
        } catch (e: Exception) {
            logger.e("$TAG: Cant load bookmarks", e)
        }
        return null
    }

    @Throws(java.lang.Exception::class)
    private fun readUrl(urlString: String): String? {
        var reader: BufferedReader? = null
        return try {
            val url = URL(urlString)
            reader = BufferedReader(InputStreamReader(url.openStream()))
            val buffer = StringBuffer()
            var read: Int
            val chars = CharArray(1024)
            while (reader.read(chars).also { read = it } != -1) buffer.append(chars, 0, read)
            buffer.toString()
        } finally {
            reader?.close()
        }
    }
}
