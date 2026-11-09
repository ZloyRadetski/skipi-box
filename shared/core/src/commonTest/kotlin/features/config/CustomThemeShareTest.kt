// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomThemeShareTest {
    private val theme = CustomThemeShare(
        name = "Night harbour",
        dark = true,
        accent = 0xFF3B82F6,
        background = 0xFF081427,
        surface = 0xFF102443,
        surfaceVariant = 0xFF18345E,
        text = 0xFFE7F1FF,
        textSecondary = 0xFFB2C8EA,
    )

    @Test
    fun aThemeSurvivesTheRoundTripThroughItsCode() {
        assertEquals(theme, parseCustomThemeCodeOrNull(theme.toCode()))
    }

    @Test
    fun theCodeIsShortAndSafeInsideALink() {
        val code = theme.toCode()

        assertTrue(code.length <= 60, "code was ${code.length} characters: $code")
        assertTrue(code.all { it.isLetterOrDigit() || it == '-' || it == '_' }, code)
    }

    @Test
    fun aLightThemeKeepsItsBase() {
        val light = theme.copy(dark = false)

        assertFalse(parseCustomThemeCodeOrNull(light.toCode())!!.dark)
    }

    @Test
    fun transparencyIsDroppedBecauseAThemeIsAlwaysOpaque() {
        val seeThrough = theme.copy(accent = 0x803B82F6)

        assertEquals(0xFF3B82F6, parseCustomThemeCodeOrNull(seeThrough.toCode())!!.accent)
    }

    @Test
    fun theNameIsTrimmedCutToTwentyFourCharactersAndLosesControlCharacters() {
        val messy = theme.copy(name = "  A\nvery long name that keeps going and going  ")

        val name = parseCustomThemeCodeOrNull(messy.toCode())!!.name

        assertEquals("Avery long name that kee", name)
        assertEquals(24, name.length)
    }

    @Test
    fun aCutNeverSplitsASurrogatePair() {
        val emoji = "🌙"
        val name = "a".repeat(23) + emoji

        val decoded = parseCustomThemeCodeOrNull(theme.copy(name = name).toCode())!!.name

        assertEquals("a".repeat(23), decoded)
    }

    @Test
    fun anEmptyNameIsAllowedAndKeptEmpty() {
        assertEquals("", parseCustomThemeCodeOrNull(theme.copy(name = "").toCode())!!.name)
    }

    @Test
    fun nonLatinNamesSurvive() {
        val russian = theme.copy(name = "Ночная гавань")

        assertEquals("Ночная гавань", parseCustomThemeCodeOrNull(russian.toCode())!!.name)
    }

    @Test
    fun aCodeSurroundedByWhitespaceStillParses() {
        assertEquals(theme, parseCustomThemeCodeOrNull("  \n${theme.toCode()}\t "))
    }

    @Test
    fun junkIsRejectedWithoutThrowing() {
        listOf(
            "",
            "   ",
            "!!!!",
            "AAAA",
            theme.toCode().dropLast(5),
            theme.toCode().take(10),
            "A".repeat(500),
        ).forEach { assertNull(parseCustomThemeCodeOrNull(it), "should reject: $it") }
    }

    @Test
    fun aFutureVersionIsRejectedInsteadOfBeingMisread() {
        val bytes = decodeForTest(theme.toCode())
        bytes[0] = 99
        assertNull(parseCustomThemeCodeOrNull(encodeForTest(bytes)))
    }

    @Test
    fun unknownFlagBitsAreIgnoredSoLaterVersionsOfTheSameShapeStillOpen() {
        val bytes = decodeForTest(theme.toCode())
        bytes[1] = (bytes[1].toInt() or 0x40).toByte()

        assertEquals(theme, parseCustomThemeCodeOrNull(encodeForTest(bytes)))
    }

    @Test
    fun aNameThatIsNotValidUtf8IsRejected() {
        val bytes = decodeForTest(theme.copy(name = "ab").toCode())
        bytes[bytes.size - 1] = 0xFF.toByte()
        bytes[bytes.size - 2] = 0xFE.toByte()

        assertNull(parseCustomThemeCodeOrNull(encodeForTest(bytes)))
    }

    @Test
    fun theLinkWrapsTheCodeAndAcceptsTheLinkBack() {
        val link = theme.toLink()

        assertTrue(link.startsWith("skipi://theme/"), link)
        assertEquals(theme, parseCustomThemeCodeOrNull(link))
    }

    @Test
    fun aLinkWithAQueryOrFragmentStillYieldsTheTheme() {
        assertEquals(theme, parseCustomThemeCodeOrNull("${theme.toLink()}?utm=x#top"))
    }

    @Test
    fun contrastRatioFollowsTheWcagDefinition() {
        assertEquals(21.0, contrastRatio(0xFF000000, 0xFFFFFFFF), 0.01)
        assertEquals(1.0, contrastRatio(0xFF336699, 0xFF336699), 0.001)
        assertEquals(contrastRatio(0xFF123456, 0xFFABCDEF), contrastRatio(0xFFABCDEF, 0xFF123456), 0.0001)
    }

    @Test
    fun aThemeWithUnreadableTextIsReportedWithWhichPairIsWeak() {
        val bad = theme.copy(text = theme.background)

        val problems = bad.readabilityProblems()

        assertTrue(CustomThemePair.TextOnBackground in problems)
        assertTrue(CustomThemePair.TextOnSurface in problems)
    }

    @Test
    fun aReadableThemeHasNoProblems() {
        assertEquals(emptyList(), theme.readabilityProblems())
    }

    private fun decodeForTest(code: String): ByteArray =
        kotlin.io.encoding.Base64.UrlSafe.withPadding(kotlin.io.encoding.Base64.PaddingOption.ABSENT).decode(code)

    private fun encodeForTest(bytes: ByteArray): String =
        kotlin.io.encoding.Base64.UrlSafe.withPadding(kotlin.io.encoding.Base64.PaddingOption.ABSENT).encode(bytes)
}
