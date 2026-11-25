package com.syxhen.cardndfixer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubLinksTest {

    @Test
    fun `buildBugReportUrl pre-fills template and encodes device metadata`() {
        val url = GitHubLinks.buildBugReportUrl(
            appVersion = "0.3.0-beta.1",
            androidRelease = "16",
            apiLevel = 36,
            deviceModel = "Pixel 9 Pro",
        )

        assertTrue(url.startsWith("https://github.com/Syxhen/cardndfixer/issues/new?"))
        assertTrue(url.contains("template=bug_report.yml"))
        assertTrue(url.contains("labels=bug"))
        assertTrue(url.contains("app-version=v0.3.0-beta.1"))
        assertTrue(url.contains("android-version=Android+16+%28API+36%29"))
        assertTrue(url.contains("device=Pixel+9+Pro"))
    }

    @Test
    fun `buildBugReportUrl avoids duplicating leading v and handles blank device`() {
        val url = GitHubLinks.buildBugReportUrl(
            appVersion = "v0.3.0",
            androidRelease = "15",
            apiLevel = 35,
            deviceModel = "   ",
        )

        assertTrue(url.contains("app-version=v0.3.0"))
        assertTrue(!url.contains("vv0.3.0"))
        assertTrue(url.contains("device=Unknown"))
    }

    @Test
    fun `static developer and repository links point to GitHub over HTTPS`() {
        assertEquals("https://github.com/Syxhen", GitHubLinks.DEVELOPER_PROFILE_URL)
        assertEquals("https://github.com/Syxhen/cardndfixer/pulls", GitHubLinks.PULL_REQUESTS_URL)
    }
}
