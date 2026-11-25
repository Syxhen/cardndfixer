package com.syxhen.cardndfixer.updater

import com.syxhen.cardndfixer.data.UpdateChannel

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String? = null,
)

data class GitHubRelease(
    val tagName: String,
    val version: SemanticVersion,
    val title: String,
    val releaseNotes: String,
    val isPrerelease: Boolean,
    val isDraft: Boolean,
    val htmlUrl: String,
    val assets: List<ReleaseAsset>,
)

data class AvailableUpdate(
    val tagName: String,
    val version: SemanticVersion,
    val title: String,
    val releaseNotes: String,
    val isPrerelease: Boolean,
    val htmlUrl: String,
    val apkAsset: ReleaseAsset,
)

object GitHubReleaseParser {

    const val RELEASE_APK_NAME = "cardndfixer.apk"
    const val DEBUG_APK_NAME = "cardndfixer-debug.apk"
    const val CHECKSUMS_ASSET_NAME = "SHA256SUMS.txt"

    private val SHA256_HEX_REGEX = Regex("^[0-9a-fA-F]{64}$")

    fun parseSingleRelease(json: String): GitHubRelease? {
        val obj = SimpleJson.parse(json) as? Map<*, *> ?: return null
        return parseReleaseObject(obj)
    }

    fun parseReleaseList(json: String): List<GitHubRelease> {
        val list = SimpleJson.parse(json) as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            (item as? Map<*, *>)?.let(::parseReleaseObject)
        }
    }

    fun selectCandidate(
        releases: List<GitHubRelease>,
        channel: UpdateChannel,
        isDebugBuild: Boolean,
    ): Pair<GitHubRelease, ReleaseAsset>? {
        val desiredApkName = if (isDebugBuild) DEBUG_APK_NAME else RELEASE_APK_NAME
        return releases
            .asSequence()
            .filter { !it.isDraft }
            .filter { channel == UpdateChannel.BETA || !it.isPrerelease }
            .sortedByDescending { it.version }
            .mapNotNull { release ->
                val apk = release.assets.firstOrNull { it.name == desiredApkName }
                    ?: return@mapNotNull null
                release to apk
            }
            .firstOrNull()
    }

    fun extractSha256FromChecksums(checksumsText: String, targetFileName: String): String? {
        checksumsText.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            val parts = trimmed.split(Regex("\\s+"), limit = 2)
            if (parts.size == 2) {
                val hash = parts[0].lowercase()
                val fileName = parts[1].removePrefix("*").substringAfterLast('/')
                if (fileName == targetFileName && SHA256_HEX_REGEX.matches(hash)) {
                    return hash
                }
            }
        }
        return null
    }

    private fun parseReleaseObject(obj: Map<*, *>): GitHubRelease? {
        val tagName = obj["tag_name"] as? String ?: return null
        val version = SemanticVersion.parseOrNull(tagName) ?: return null
        val htmlUrl = obj["html_url"] as? String ?: return null
        val isDraft = obj["draft"] as? Boolean ?: false
        val isPrerelease = (obj["prerelease"] as? Boolean ?: false) || version.isPrerelease
        val title = (obj["name"] as? String)?.takeIf { it.isNotBlank() } ?: tagName
        val notes = (obj["body"] as? String).orEmpty().trim()
        val rawAssets = obj["assets"] as? List<*> ?: emptyList<Any?>()
        val assets = rawAssets.mapNotNull { item ->
            val assetObj = item as? Map<*, *> ?: return@mapNotNull null
            val name = assetObj["name"] as? String ?: return@mapNotNull null
            val url = assetObj["browser_download_url"] as? String ?: return@mapNotNull null
            val size = (assetObj["size"] as? Number)?.toLong() ?: 0L
            val rawDigest = assetObj["digest"] as? String
            val sha256 = rawDigest
                ?.removePrefix("sha256:")
                ?.lowercase()
                ?.takeIf { SHA256_HEX_REGEX.matches(it) }
            ReleaseAsset(
                name = name,
                downloadUrl = url,
                sizeBytes = size,
                sha256 = sha256,
            )
        }
        return GitHubRelease(
            tagName = tagName,
            version = version,
            title = title,
            releaseNotes = notes,
            isPrerelease = isPrerelease,
            isDraft = isDraft,
            htmlUrl = htmlUrl,
            assets = assets,
        )
    }
}
