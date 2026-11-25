package com.syxhen.cardndfixer.updater

import android.content.Context
import com.syxhen.cardndfixer.BuildConfig
import com.syxhen.cardndfixer.data.UpdateChannel
import com.syxhen.cardndfixer.diagnostics.DiagnosticSink
import com.syxhen.cardndfixer.diagnostics.NoOpDiagnosticSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val update: AvailableUpdate) : UpdateCheckResult
    data class UpToDate(val latestTagName: String?) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class UpToDate(val checkedChannel: UpdateChannel, val latestTagName: String?) : UpdateUiState
    data class Available(val update: AvailableUpdate) : UpdateUiState
    data class Downloading(
        val update: AvailableUpdate,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : UpdateUiState {
        val progress: Float?
            get() = if (totalBytes > 0L) {
                (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            } else {
                null
            }
    }

    data class ReadyToInstall(
        val update: AvailableUpdate,
        val apkFile: File,
        val verifiedSha256: Boolean,
    ) : UpdateUiState

    data class Error(val message: String) : UpdateUiState
}

class AppUpdater(
    context: Context,
    private val diagnostics: DiagnosticSink = NoOpDiagnosticSink,
) {
    private val updatesDir: File = File(context.cacheDir, UPDATES_DIR_NAME)

    suspend fun checkForUpdate(
        channel: UpdateChannel,
        currentVersionName: String = BuildConfig.VERSION_NAME,
        isDebugBuild: Boolean = BuildConfig.DEBUG_TOOLS_ENABLED,
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val currentVersion = SemanticVersion.parseOrNull(currentVersionName)
            ?: return@withContext UpdateCheckResult.Error(
                "Could not parse current app version ($currentVersionName).",
            )

        diagnostics.record(
            TAG,
            "Checking GitHub releases (channel=$channel, current=$currentVersion, debug=$isDebugBuild)",
        )

        runCatching {
            val releases = when (channel) {
                UpdateChannel.STABLE -> {
                    val response = fetchTextOrNull(LATEST_RELEASE_URL)
                    if (response == null) {
                        emptyList()
                    } else {
                        listOfNotNull(GitHubReleaseParser.parseSingleRelease(response))
                    }
                }
                UpdateChannel.BETA -> {
                    val response = fetchTextOrNull(ALL_RELEASES_URL)
                    if (response == null) {
                        emptyList()
                    } else {
                        GitHubReleaseParser.parseReleaseList(response)
                    }
                }
            }

            val selected = GitHubReleaseParser.selectCandidate(
                releases = releases,
                channel = channel,
                isDebugBuild = isDebugBuild,
            ) ?: return@runCatching UpdateCheckResult.UpToDate(latestTagName = null)

            val (release, apkAsset) = selected
            if (release.version <= currentVersion) {
                diagnostics.record(
                    TAG,
                    "App is up to date (current=$currentVersion, latest=${release.tagName})",
                )
                return@runCatching UpdateCheckResult.UpToDate(latestTagName = release.tagName)
            }

            val resolvedSha256 = apkAsset.sha256 ?: resolveChecksumFromAsset(release, apkAsset.name)
            val resolvedAsset = apkAsset.copy(sha256 = resolvedSha256)
            val available = AvailableUpdate(
                tagName = release.tagName,
                version = release.version,
                title = release.title,
                releaseNotes = release.releaseNotes,
                isPrerelease = release.isPrerelease,
                htmlUrl = release.htmlUrl,
                apkAsset = resolvedAsset,
            )
            diagnostics.record(
                TAG,
                "Update available: ${available.tagName} (${resolvedAsset.name}, sha256=${resolvedSha256 != null})",
            )
            UpdateCheckResult.UpdateAvailable(available)
        }.getOrElse { error ->
            diagnostics.record(TAG, "Update check failed: ${error.message}")
            UpdateCheckResult.Error(error.message ?: "Could not check GitHub for updates.")
        }
    }

    suspend fun downloadUpdate(
        update: AvailableUpdate,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        validateDownloadUrl(update.apkAsset.downloadUrl)
        updatesDir.mkdirs()

        // Remove stale APK files before downloading a new one.
        updatesDir.listFiles()?.forEach { existing ->
            existing.delete()
        }

        val safeFileName = update.apkAsset.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val partFile = File(updatesDir, "$safeFileName.part")
        val finalFile = File(updatesDir, safeFileName)

        diagnostics.record(TAG, "Downloading ${update.tagName} (${update.apkAsset.name})")
        val connection = openFollowingRedirects(update.apkAsset.downloadUrl)
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IOException("GitHub download returned HTTP $status.")
            }

            val totalBytes = connection.contentLengthLong
                .takeIf { it > 0L }
                ?: update.apkAsset.sizeBytes
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            var lastReported = 0L

            connection.inputStream.buffered().use { input ->
                FileOutputStream(partFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastReported >= PROGRESS_STEP_BYTES || downloaded == totalBytes) {
                            lastReported = downloaded
                            onProgress(downloaded, totalBytes)
                        }
                    }
                    output.flush()
                }
            }

            if (downloaded == 0L || !hasZipMagic(partFile)) {
                partFile.delete()
                throw IOException("Downloaded file is not a valid Android APK.")
            }

            val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
            val expectedSha256 = update.apkAsset.sha256
            if (expectedSha256 != null && !actualSha256.equals(expectedSha256, ignoreCase = true)) {
                partFile.delete()
                diagnostics.record(TAG, "SHA-256 mismatch for ${update.tagName}")
                throw IOException("APK checksum verification failed. Please retry.")
            }

            if (finalFile.exists()) finalFile.delete()
            if (!partFile.renameTo(finalFile)) {
                partFile.copyTo(finalFile, overwrite = true)
                partFile.delete()
            }

            onProgress(downloaded, totalBytes.coerceAtLeast(downloaded))
            diagnostics.record(
                TAG,
                "Downloaded ${update.tagName} ($downloaded bytes, sha256Verified=${expectedSha256 != null})",
            )
            finalFile
        } catch (t: Throwable) {
            partFile.delete()
            throw t
        } finally {
            connection.disconnect()
        }
    }

    private fun resolveChecksumFromAsset(release: GitHubRelease, apkName: String): String? {
        val checksumsAsset = release.assets.firstOrNull {
            it.name == GitHubReleaseParser.CHECKSUMS_ASSET_NAME
        } ?: return null

        return runCatching {
            validateDownloadUrl(checksumsAsset.downloadUrl)
            val connection = openFollowingRedirects(checksumsAsset.downloadUrl)
            try {
                if (connection.responseCode !in 200..299) return@runCatching null
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                GitHubReleaseParser.extractSha256FromChecksums(text, apkName)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private fun fetchTextOrNull(urlString: String): String? {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "cardndfixer/${BuildConfig.VERSION_NAME}")
        }
        return try {
            when (val status = connection.responseCode) {
                in 200..299 -> connection.inputStream.bufferedReader().use { it.readText() }
                404 -> null
                403, 429 -> throw IOException("GitHub API rate limit reached. Try again in a few minutes.")
                else -> throw IOException("GitHub API returned HTTP $status.")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openFollowingRedirects(initialUrl: String): HttpURLConnection {
        var currentUrl = initialUrl
        repeat(MAX_REDIRECTS) {
            validateDownloadUrl(currentUrl)
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = DOWNLOAD_READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "cardndfixer/${BuildConfig.VERSION_NAME}")
            }
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) {
                    throw IOException("Redirect response missing Location header.")
                }
                currentUrl = URI(currentUrl).resolve(location).toString()
            } else {
                return connection
            }
        }
        throw IOException("Too many redirects while downloading APK.")
    }

    private fun validateDownloadUrl(rawUrl: String) {
        val uri = URI(rawUrl)
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            throw IOException("Refusing non-HTTPS update URL.")
        }
        val host = uri.host?.lowercase().orEmpty()
        val allowed = host == "github.com" ||
            host == "api.github.com" ||
            host.endsWith(".githubusercontent.com")
        if (!allowed) {
            throw IOException("Refusing update download from untrusted host: $host")
        }
    }

    private fun hasZipMagic(file: File): Boolean {
        if (file.length() < 4) return false
        val header = ByteArray(4)
        file.inputStream().use { input ->
            if (input.read(header) != 4) return false
        }
        return header[0] == 0x50.toByte() &&
            header[1] == 0x4B.toByte() &&
            header[2] == 0x03.toByte() &&
            header[3] == 0x04.toByte()
    }

    companion object {
        private const val TAG = "AppUpdater"
        private const val UPDATES_DIR_NAME = "updates"
        private const val CONNECT_TIMEOUT_MS = 12_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val DOWNLOAD_READ_TIMEOUT_MS = 30_000
        private const val MAX_REDIRECTS = 6
        private const val PROGRESS_STEP_BYTES = 32 * 1024L

        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/Syxhen/cardndfixer/releases/latest"
        const val ALL_RELEASES_URL =
            "https://api.github.com/repos/Syxhen/cardndfixer/releases?per_page=15"
    }
}
