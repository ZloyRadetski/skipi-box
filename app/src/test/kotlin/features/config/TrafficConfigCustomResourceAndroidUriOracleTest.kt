// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Android framework oracle for the URI component codec used by profile custom-resource rows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class TrafficConfigCustomResourceAndroidUriOracleTest {
    @Test
    fun androidUriEncodingUsesItsDefaultSafeSetAndUtf8PercentEscapes() {
        assertEquals(
            "AZaz09_-!.~'()*%20%2B%2C%2F%25%D0%9F",
            Uri.encode("AZaz09_-!.~'()* +,/%П"),
        )
        assertEquals(
            "AZaz09_-!.~'()* +,/%П",
            Uri.decode("AZaz09_-!.~'()*%20%2B%2C%2F%25%D0%9F"),
        )
    }

    @Test
    fun androidUriDecodeKeepsPlusAndMatchesMalformedEscapeReplacement() {
        assertEquals("П+file+name", Uri.decode("%D0%9F+file%2Bname"))
        assertEquals("\uFFFD", Uri.decode("%"))
        assertEquals("\uFFFD", Uri.decode("%A"))
        assertEquals("\uFFFD\u0000G", Uri.decode("%GG"))
        assertEquals("\uFFFD(", Uri.decode("%C3%28"))
    }

    @Test
    fun profileRowsKeepAndroidIdNameUrlAndFirstDuplicateRules() {
        val raw = buildString {
            appendLine("[SKIPI]")
            appendLine(resourceRow(0, "Zero", "https://geo.example/zero.dat"))
            appendLine(resourceRow(-1, "Negative", "https://geo.example/negative.dat"))
            appendLine(resourceRow(2, "", "https://geo.example/blank-name.dat"))
            appendLine(resourceRow(3, "Blank URL", ""))
            appendLine(resourceRow(6, "  First name  ", "  https://geo.example/first.dat  "))
            appendLine(resourceRow(6, "Second name", "https://geo.example/second.dat"))
        }

        val restored = TrafficConfigState(
            id = 1,
            name = "Profile",
            rawConfig = raw,
        ).withSkipiSettingsReadFromRawConfig()

        assertEquals(
            listOf(TrafficConfigCustomResourceFile(6, "First name", "https://geo.example/first.dat")),
            restored.resourceSettings.customFiles,
        )
        assertEquals(7, restored.resourceSettings.nextCustomFileId)
    }

    private fun resourceRow(id: Int, name: String, url: String): String =
        "resource-custom-file = $id,${Uri.encode(name)},${Uri.encode(url)}"
}
