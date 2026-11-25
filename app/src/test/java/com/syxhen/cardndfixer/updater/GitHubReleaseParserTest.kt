package com.syxhen.cardndfixer.updater

import com.syxhen.cardndfixer.data.UpdateChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseParserTest {

    @Test
    fun semanticVersion_parsesStablePrereleaseAndDebugSuffixes() {
        val v1 = SemanticVersion.parseOrNull("v0.1.1")
        val v2 = SemanticVersion.parseOrNull("0.2.0-beta.1")
        val v2Debug = SemanticVersion.parseOrNull("0.2.0-beta.1-debug")
        val v3Debug = SemanticVersion.parseOrNull("0.2.0-debug")

        assertEquals(SemanticVersion(0, 1, 1), v1)
        assertEquals(SemanticVersion(0, 2, 0, listOf("beta", "1")), v2)
        assertEquals(v2, v2Debug)
        assertEquals(SemanticVersion(0, 2, 0), v3Debug)
        assertNull(SemanticVersion.parseOrNull("invalid-tag"))
    }

    @Test
    fun semanticVersion_ordersPrereleasesAndStableCorrectly() {
        val ordered = listOf(
            "0.1.0",
            "0.1.1",
            "0.2.0-alpha.1",
            "0.2.0-beta.1",
            "0.2.0-beta.2",
            "0.2.0-beta.10",
            "0.2.0-rc.1",
            "0.2.0",
            "0.2.1-beta.1",
            "1.0.0",
        ).map { requireNotNull(SemanticVersion.parseOrNull(it)) }

        for (i in 0 until ordered.lastIndex) {
            assertTrue(
                "Expected ${ordered[i]} < ${ordered[i + 1]}",
                ordered[i] < ordered[i + 1],
            )
        }
    }

    @Test
    fun selectCandidate_stableChannelIgnoresPrereleasesAndDrafts() {
        val json = """
            [
              {
                "tag_name": "v0.3.0",
                "name": "Draft 0.3.0",
                "draft": true,
                "prerelease": false,
                "html_url": "https://github.com/Syxhen/cardndfixer/releases/tag/v0.3.0",
                "body": "Draft notes",
                "assets": [
                  {
                    "name": "cardndfixer.apk",
                    "browser_download_url": "https://github.com/Syxhen/cardndfixer/releases/download/v0.3.0/cardndfixer.apk",
                    "size": 2500000
                  }
                ]
              },
              {
                "tag_name": "v0.2.0-beta.1",
                "name": "cardndfixer 0.2.0-beta.1",
                "draft": false,
                "prerelease": true,
                "html_url": "https://github.com/Syxhen/cardndfixer/releases/tag/v0.2.0-beta.1",
                "body": "Beta updater preview",
                "assets": [
                  {
                    "name": "cardndfixer.apk",
                    "browser_download_url": "https://github.com/Syxhen/cardndfixer/releases/download/v0.2.0-beta.1/cardndfixer.apk",
                    "size": 2600000,
                    "digest": "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                  }
                ]
              },
              {
                "tag_name": "v0.1.1",
                "name": "cardndfixer 0.1.1",
                "draft": false,
                "prerelease": false,
                "html_url": "https://github.com/Syxhen/cardndfixer/releases/tag/v0.1.1",
                "body": "Bugfix release\n- Fixed R8 crash",
                "assets": [
                  {
                    "name": "cardndfixer.apk",
                    "browser_download_url": "https://github.com/Syxhen/cardndfixer/releases/download/v0.1.1/cardndfixer.apk",
                    "size": 2521671
                  },
                  {
                    "name": "cardndfixer-debug.apk",
                    "browser_download_url": "https://github.com/Syxhen/cardndfixer/releases/download/v0.1.1/cardndfixer-debug.apk",
                    "size": 35000000
                  }
                ]
              }
            ]
        """.trimIndent()

        val releases = GitHubReleaseParser.parseReleaseList(json)
        assertEquals(3, releases.size)

        val stablePick = GitHubReleaseParser.selectCandidate(
            releases = releases,
            channel = UpdateChannel.STABLE,
            isDebugBuild = false,
        )
        assertNotNull(stablePick)
        assertEquals("v0.1.1", stablePick!!.first.tagName)
        assertFalse(stablePick.first.isPrerelease)
        assertEquals("cardndfixer.apk", stablePick.second.name)

        val betaPick = GitHubReleaseParser.selectCandidate(
            releases = releases,
            channel = UpdateChannel.BETA,
            isDebugBuild = false,
        )
        assertNotNull(betaPick)
        assertEquals("v0.2.0-beta.1", betaPick!!.first.tagName)
        assertTrue(betaPick.first.isPrerelease)
        assertEquals(
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            betaPick.second.sha256,
        )

        val debugStablePick = GitHubReleaseParser.selectCandidate(
            releases = releases,
            channel = UpdateChannel.STABLE,
            isDebugBuild = true,
        )
        assertNotNull(debugStablePick)
        assertEquals("cardndfixer-debug.apk", debugStablePick!!.second.name)
    }

    @Test
    fun selectCandidate_betaChannelPromotesToNewerStableRelease() {
        val json = """
            [
              {
                "tag_name": "v0.2.0-beta.2",
                "draft": false,
                "prerelease": true,
                "html_url": "https://github.com/Syxhen/cardndfixer/releases/tag/v0.2.0-beta.2",
                "assets": [
                  { "name": "cardndfixer.apk", "browser_download_url": "https://github.com/Syxhen/cardndfixer/releases/download/v0.2.0-beta.2/cardndfixer.apk", "size": 100 }
                ]
              },
              {
                "tag_name": "v0.2.0",
                "draft": false,
                "prerelease": false,
                "html_url": "https://github.com/Syxhen/cardndfixer/releases/tag/v0.2.0",
                "assets": [
                  { "name": "cardndfixer.apk", "browser_download_url": "https://github.com/Syxhen/cardndfixer/releases/download/v0.2.0/cardndfixer.apk", "size": 100 }
                ]
              }
            ]
        """.trimIndent()

        val releases = GitHubReleaseParser.parseReleaseList(json)
        val pick = GitHubReleaseParser.selectCandidate(
            releases = releases,
            channel = UpdateChannel.BETA,
            isDebugBuild = false,
        )
        assertNotNull(pick)
        assertEquals("v0.2.0", pick!!.first.tagName)
        assertFalse(pick.first.isPrerelease)
    }

    @Test
    fun extractSha256FromChecksums_matchesExactApkName() {
        val sums = """
            aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa  cardndfixer.apk
            bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb *cardndfixer-debug.apk
        """.trimIndent()

        assertEquals(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            GitHubReleaseParser.extractSha256FromChecksums(sums, "cardndfixer.apk"),
        )
        assertEquals(
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            GitHubReleaseParser.extractSha256FromChecksums(sums, "cardndfixer-debug.apk"),
        )
        assertNull(GitHubReleaseParser.extractSha256FromChecksums(sums, "other.apk"))
    }
}
