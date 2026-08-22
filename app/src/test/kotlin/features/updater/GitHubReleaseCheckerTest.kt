// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseCheckerTest {

    @Test
    fun isVersionNewer_handles_semantic_versions() {
        assertTrue(
            GitHubReleaseChecker.isVersionNewer(
                currentVersionName = "1.0.0",
                currentVersionCode = 1,
                remoteVersionName = "1.0.1",
                remoteVersionCode = null,
            ),
        )

        assertTrue(
            GitHubReleaseChecker.isVersionNewer(
                currentVersionName = "1.0.0",
                currentVersionCode = 1,
                remoteVersionName = "1.1.0",
                remoteVersionCode = null,
            ),
        )

        assertTrue(
            GitHubReleaseChecker.isVersionNewer(
                currentVersionName = "1.0.0",
                currentVersionCode = 1,
                remoteVersionName = "2.0.0",
                remoteVersionCode = null,
            ),
        )

        assertFalse(
            GitHubReleaseChecker.isVersionNewer(
                currentVersionName = "1.0.1",
                currentVersionCode = 2,
                remoteVersionName = "1.0.0",
                remoteVersionCode = null,
            ),
        )

        assertFalse(
            GitHubReleaseChecker.isVersionNewer(
                currentVersionName = "1.0.1",
                currentVersionCode = 2,
                remoteVersionName = "1.0.1",
                remoteVersionCode = null,
            ),
        )
    }

    @Test
    fun isVersionNewer_handles_v_prefix() {
        assertTrue(
            GitHubReleaseChecker.isVersionNewer(
                currentVersionName = "v1.0.0",
                currentVersionCode = 1,
                remoteVersionName = "v1.0.5",
                remoteVersionCode = null,
            ),
        )

        assertFalse(
            GitHubReleaseChecker.isVersionNewer(
                currentVersionName = "v1.0.5",
                currentVersionCode = 5,
                remoteVersionName = "v1.0.5",
                remoteVersionCode = null,
            ),
        )

        assertFalse(
            GitHubReleaseChecker.isVersionNewer(
                currentVersionName = "v1.0.5",
                currentVersionCode = 5,
                remoteVersionName = "v1.0.2",
                remoteVersionCode = null,
            ),
        )
    }

    @Test
    fun isVersionNewer_handles_version_code_fallback() {
        assertTrue(
            GitHubReleaseChecker.isVersionNewer(
                currentVersionName = "1.0.0",
                currentVersionCode = 10,
                remoteVersionName = "1.0.0",
                remoteVersionCode = 11,
            ),
        )

        assertFalse(
            GitHubReleaseChecker.isVersionNewer(
                currentVersionName = "1.0.0",
                currentVersionCode = 10,
                remoteVersionName = "1.0.0",
                remoteVersionCode = 10,
            ),
        )

        assertFalse(
            GitHubReleaseChecker.isVersionNewer(
                currentVersionName = "1.0.0",
                currentVersionCode = 10,
                remoteVersionName = "1.0.0",
                remoteVersionCode = 9,
            ),
        )
    }
}
