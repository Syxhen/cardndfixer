package com.syxhen.cardndfixer.ui

import android.os.Build
import java.net.URLEncoder

object GitHubLinks {
    const val DEVELOPER_NAME = "Syxhen"
    const val DEVELOPER_BIO = "IT-CS student and writing stuffs."
    const val DEVELOPER_PROFILE_URL = "https://github.com/Syxhen"
    const val PULL_REQUESTS_URL = "https://github.com/Syxhen/cardndfixer/pulls"
    private const val NEW_ISSUE_URL = "https://github.com/Syxhen/cardndfixer/issues/new"

    fun buildDeviceBugReportUrl(appVersion: String): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val deviceLabel = when {
            manufacturer.isBlank() -> model.ifBlank { "Unknown" }
            model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }
        return buildBugReportUrl(
            appVersion = appVersion,
            androidRelease = Build.VERSION.RELEASE.orEmpty().ifBlank { "Unknown" },
            apiLevel = Build.VERSION.SDK_INT,
            deviceModel = deviceLabel,
        )
    }

    fun buildBugReportUrl(
        appVersion: String,
        androidRelease: String,
        apiLevel: Int,
        deviceModel: String,
    ): String {
        val cleanVersion = appVersion.trim().removePrefix("v")
        val versionValue = if (cleanVersion.isNotEmpty()) "v$cleanVersion" else "unknown"
        val androidValue = "Android ${androidRelease.trim()} (API $apiLevel)"
        val deviceValue = deviceModel.trim().ifEmpty { "Unknown" }

        return buildString {
            append(NEW_ISSUE_URL)
            append("?template=bug_report.yml")
            append("&labels=bug")
            append("&app-version=").append(encode(versionValue))
            append("&android-version=").append(encode(androidValue))
            append("&device=").append(encode(deviceValue))
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
