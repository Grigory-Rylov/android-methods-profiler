package com.github.grishberg.profiler.common.updates.github

import com.google.gson.annotations.SerializedName
import java.util.Date

data class ResponseModel(
    @SerializedName(value = "html_url")
    val htmlUrl: String,

    @SerializedName(value = "tag_name")
    val versionName: String,

    val draft: Boolean,
    val prerelease: Boolean,

    @SerializedName(value = "published_at")
    val publishedAt: Date,
    val body: String
)
