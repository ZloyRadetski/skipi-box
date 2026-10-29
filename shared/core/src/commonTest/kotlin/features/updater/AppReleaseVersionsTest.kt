// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class AppReleaseVersionsTest {

    @Test
    fun extractVersionCodeFromBody_parses_release_notes() {
        assertEquals(
            180,
            AppReleaseVersions.extractVersionCodeFromBody("## SKIPI v0.3.5\n\n- Version name: 0.3.5\n- Version code: 180"),
        )
        assertEquals(181, AppReleaseVersions.extractVersionCodeFromBody("version code = 181"))
        assertNull(AppReleaseVersions.extractVersionCodeFromBody("SKIPI-universal.apk"))
        assertNull(AppReleaseVersions.extractVersionCodeFromBody(""))
    }

    @Test
    fun isVersionNewer_detects_older_test_build_against_current_release() {
        // Real scenario: local build reports 0.3.2/180, published release is
        // v0.3.5 with the same commit count baked into its notes.
        assertTrue(
            AppReleaseVersions.isVersionNewer(
                currentVersionName = "0.3.2",
                currentVersionCode = 180,
                remoteVersionName = "0.3.5",
                remoteVersionCode = 180,
            ),
        )
    }

    @Test
    fun isVersionNewer_handles_semantic_versions() {
        assertTrue(
            AppReleaseVersions.isVersionNewer(
                currentVersionName = "1.0.0",
                currentVersionCode = 1,
                remoteVersionName = "1.0.1",
                remoteVersionCode = null,
            ),
        )

        assertTrue(
            AppReleaseVersions.isVersionNewer(
                currentVersionName = "1.0.0",
                currentVersionCode = 1,
                remoteVersionName = "1.1.0",
                remoteVersionCode = null,
            ),
        )

        assertTrue(
            AppReleaseVersions.isVersionNewer(
                currentVersionName = "1.0.0",
                currentVersionCode = 1,
                remoteVersionName = "2.0.0",
                remoteVersionCode = null,
            ),
        )

        assertFalse(
            AppReleaseVersions.isVersionNewer(
                currentVersionName = "1.0.1",
                currentVersionCode = 2,
                remoteVersionName = "1.0.0",
                remoteVersionCode = null,
            ),
        )

        assertFalse(
            AppReleaseVersions.isVersionNewer(
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
            AppReleaseVersions.isVersionNewer(
                currentVersionName = "v1.0.0",
                currentVersionCode = 1,
                remoteVersionName = "v1.0.5",
                remoteVersionCode = null,
            ),
        )

        assertFalse(
            AppReleaseVersions.isVersionNewer(
                currentVersionName = "v1.0.5",
                currentVersionCode = 5,
                remoteVersionName = "v1.0.5",
                remoteVersionCode = null,
            ),
        )

        assertFalse(
            AppReleaseVersions.isVersionNewer(
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
            AppReleaseVersions.isVersionNewer(
                currentVersionName = "1.0.0",
                currentVersionCode = 10,
                remoteVersionName = "1.0.0",
                remoteVersionCode = 11,
            ),
        )

        assertFalse(
            AppReleaseVersions.isVersionNewer(
                currentVersionName = "1.0.0",
                currentVersionCode = 10,
                remoteVersionName = "1.0.0",
                remoteVersionCode = 10,
            ),
        )

        assertFalse(
            AppReleaseVersions.isVersionNewer(
                currentVersionName = "1.0.0",
                currentVersionCode = 10,
                remoteVersionName = "1.0.0",
                remoteVersionCode = 9,
            ),
        )
    }
}
