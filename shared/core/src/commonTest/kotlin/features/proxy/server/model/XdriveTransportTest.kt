// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class XdriveTransportTest {

    private fun googleDrive() = V2RayParameters(
        type = "xdrive",
        security = "tls",
        sni = "www.googleapis.com",
        xdService = "google-drive",
        xdFolder = "skipi",
        xdSecrets = "client-id, client-secret, refresh-token",
    )

    // A real client string from `xray vlessenc`; the core refuses xdrive with encryption "none".
    private val vlessEncryption = "mlkem768x25519plus.native.0rtt.GnDJ0ePW6KrjHz1G78pJeOX5jl8BPzLdv2Hi_t4RmUo"

    private fun vless(parms: V2RayParameters, encryption: String = vlessEncryption, flow: String = "") = VLESS(
        remarks = "Drive",
        id = "11111111-2222-4333-8444-555555555555",
        server = "drive.example.com",
        port = "443",
        encryption = encryption,
        flow = flow,
        parms = parms,
    )

    private fun xdriveSettings(parms: V2RayParameters): JsonObject =
        vless(parms).toXrayOutbound("proxy").streamSettings!!["xdriveSettings"]!!.jsonObject

    private fun issues(parms: V2RayParameters) = vless(parms).validateFull()

    private val s3Extra = """{"s3":{"endpoint":"https://s3.example.com","region":"us-east-1","bucket":"my-bucket"}}"""

    private fun s3(extra: String = s3Extra, folder: String = "skipi", secrets: String = "access-key, secret-key") =
        googleDrive().copy(xdService = "s3", xdFolder = folder, xdSecrets = secrets, extra = extra)

    // --- transport type ---

    @Test
    fun xdriveIsACanonicalTransportAndAnOption() {
        assertEquals(V2RayTransportXdrive, "xdrive".toCanonicalV2RayTransportType())
        assertEquals(V2RayTransportXdrive, "XDRIVE".toCanonicalV2RayTransportType())
        assertEquals("xdrive", V2RayTransportXdrive.toV2RayTransportUrlType())
        assertTrue(V2RayTransportOptions.any { it.value == V2RayTransportXdrive })
    }

    @Test
    fun theTransportIsShownAsXdrive() {
        assertEquals("XDRIVE", googleDrive().copy(security = "none").getTransportDisplay())
    }

    // --- Xray stream settings ---

    @Test
    fun streamSettingsUseTheXdriveNetworkWithTheServiceFolderAndSecrets() {
        val stream = vless(googleDrive()).toXrayOutbound("proxy").streamSettings!!
        val settings = stream["xdriveSettings"]!!.jsonObject

        assertEquals("xdrive", stream["network"]?.jsonPrimitive?.content)
        assertEquals("tls", stream["security"]?.jsonPrimitive?.content)
        assertEquals("google-drive", settings["service"]?.jsonPrimitive?.content)
        assertEquals("skipi", settings["remoteFolder"]?.jsonPrimitive?.content)
        assertEquals(
            listOf("client-id", "client-secret", "refresh-token"),
            settings["secrets"]?.jsonArray?.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun blankFolderAndSecretsAreNotWritten() {
        val settings = xdriveSettings(googleDrive().copy(xdFolder = "", xdSecrets = ""))

        assertNull(settings["remoteFolder"])
        assertNull(settings["secrets"])
    }

    @Test
    fun extraIsMergedFlatBecauseTheCoreReadsTheTuningFieldsFromXdriveSettingsItself() {
        val settings = xdriveSettings(
            googleDrive().copy(extra = """{"segmentBytes":1048576,"xmux":{"maxConnections":"2"}}"""),
        )

        assertEquals(1048576, settings["segmentBytes"]?.jsonPrimitive?.int)
        assertEquals("2", settings["xmux"]?.jsonObject?.get("maxConnections")?.jsonPrimitive?.content)
        assertNull(settings["extra"])
    }

    @Test
    fun theStructuredFieldsWinOverTheSameKeysInExtra() {
        val settings = xdriveSettings(googleDrive().copy(extra = """{"service":"s3","remoteFolder":"other"}"""))

        assertEquals("google-drive", settings["service"]?.jsonPrimitive?.content)
        assertEquals("skipi", settings["remoteFolder"]?.jsonPrimitive?.content)
    }

    @Test
    fun anS3ServiceKeepsItsS3ObjectFromExtra() {
        val settings = xdriveSettings(
            V2RayParameters(
                type = "xdrive",
                security = "tls",
                xdService = "s3",
                extra = """{"s3":{"endpoint":"https://s3.example.com","region":"us-east-1","bucket":"b","pathStyle":false}}""",
            ),
        )

        val s3 = settings["s3"]!!.jsonObject
        assertEquals("https://s3.example.com", s3["endpoint"]?.jsonPrimitive?.content)
        assertEquals("b", s3["bucket"]?.jsonPrimitive?.content)
    }

    @Test
    fun otherTransportsDoNotGetXdriveSettings() {
        val stream = vless(googleDrive().copy(type = "raw")).toXrayOutbound("proxy").streamSettings!!

        assertNull(stream["xdriveSettings"])
    }

    // --- links and persistence ---

    @Test
    fun parsesAnXdriveLink() {
        val server = assertIs<VLESS>(
            ProxyServer.parse(
                "vless://11111111-2222-4333-8444-555555555555@drive.example.com:443?type=xdrive&security=tls" +
                    "&sni=www.googleapis.com&service=google-drive&folder=skipi&secrets=a%2Cb%2Cc" +
                    "&extra=%7B%22segmentBytes%22%3A1048576%7D#Drive",
            ),
        )

        assertEquals("xdrive", server.parms.type)
        assertEquals("google-drive", server.parms.xdService)
        assertEquals("skipi", server.parms.xdFolder)
        assertEquals("a,b,c", server.parms.xdSecrets)
        assertEquals("""{"segmentBytes":1048576}""", server.parms.extra)
    }

    @Test
    fun linkRoundTripKeepsTheXdriveFields() {
        val original = vless(googleDrive().copy(xdSecrets = "a,b,c", extra = """{"segmentBytes":1048576}"""))

        val restored = assertIs<VLESS>(ProxyServer.parse(original.getUrl()))

        assertEquals("xdrive", restored.parms.type)
        assertEquals(original.parms.xdService, restored.parms.xdService)
        assertEquals(original.parms.xdFolder, restored.parms.xdFolder)
        assertEquals(original.parms.xdSecrets, restored.parms.xdSecrets)
        assertEquals(original.parms.extra, restored.parms.extra)
    }

    @Test
    fun jsonPersistenceKeepsTheXdriveFields() {
        val original = vless(googleDrive())

        val restored = ProxyServer.json.decodeFromString<VLESS>(ProxyServer.json.encodeToString(original))

        assertEquals(original, restored)
    }

    @Test
    fun oldStoredServersWithoutXdriveFieldsStillLoad() {
        val old = """{"remarks":"x","id":"u","server":"s","port":"443","parms":{"type":"raw","security":"none"}}"""

        val restored = ProxyServer.json.decodeFromString<VLESS>(old)

        assertNull(restored.parms.xdService)
        assertNull(restored.parms.xdFolder)
        assertNull(restored.parms.xdSecrets)
    }

    @Test
    fun fingerprintSeparatesServersThatOnlyDifferInTheirDriveAccount() {
        val base = vless(googleDrive()).connectionFingerprint()

        assertFalse(base == vless(googleDrive().copy(xdFolder = "other")).connectionFingerprint())
        assertFalse(base == vless(googleDrive().copy(xdSecrets = "a,b,c")).connectionFingerprint())
        assertFalse(base == vless(googleDrive().copy(xdService = "local")).connectionFingerprint())
    }

    // --- validation ---

    @Test
    fun aGoogleDriveServerWithThreeSecretsIsValid() {
        val found = issues(googleDrive())
        assertTrue(found.isEmpty(), "unexpected issues: $found")
    }

    @Test
    fun googleDriveNeedsExactlyThreeSecrets() {
        val two = issues(googleDrive().copy(xdSecrets = "a,b"))
        val four = issues(googleDrive().copy(xdSecrets = "a,b,c,d"))

        assertTrue(two.any { it.error == ProxyServerValidationError.ValueOutOfRange })
        assertTrue(four.any { it.error == ProxyServerValidationError.ValueOutOfRange })
    }

    @Test
    fun theServiceIsRequiredAndMustBeOneTheCoreKnows() {
        val missing = issues(googleDrive().copy(xdService = ""))
        val unknown = issues(googleDrive().copy(xdService = "dropbox"))

        assertTrue(missing.any { it.error == ProxyServerValidationError.RequiredField })
        assertTrue(unknown.any { it.error == ProxyServerValidationError.UnsupportedValue })
    }

    @Test
    fun localServiceNeedsNoSecrets() {
        assertTrue(issues(googleDrive().copy(xdService = "local", xdSecrets = "")).isEmpty())
    }

    @Test
    fun templateServiceNeedsATemplateObjectInExtra() {
        val without = issues(googleDrive().copy(xdService = "template", xdSecrets = ""))
        val with = issues(googleDrive().copy(xdService = "template", xdSecrets = "", extra = """{"template":{"a":1}}"""))

        assertTrue(without.any { it.error == ProxyServerValidationError.RequiredField })
        assertTrue(with.isEmpty())
    }

    @Test
    fun s3ServiceNeedsAnS3ObjectInExtra() {
        val without = issues(s3(extra = ""))
        val with = issues(s3())

        assertTrue(without.any { it.error == ProxyServerValidationError.RequiredField })
        assertTrue(with.isEmpty(), "unexpected issues: $with")
    }

    // --- what the core demands of the VLESS server that carries xdrive ---

    @Test
    fun xdriveIsOnlyForVless() {
        val parms = googleDrive()
        val id = "11111111-2222-4333-8444-555555555555"
        val vmess = VMess(remarks = "x", id = id, server = "s.example.com", port = "443", parms = parms)
        val trojan = Trojan(remarks = "x", password = "p", server = "s.example.com", port = "443", parms = parms)

        assertTrue(vmess.validateFull().any { it.error == ProxyServerValidationError.UnsupportedValue })
        assertTrue(trojan.validateFull().any { it.error == ProxyServerValidationError.UnsupportedValue })
        assertTrue(vless(parms).validateFull().isEmpty())
    }

    @Test
    fun vlessNeedsRealEncryptionOnXdrive() {
        val none = vless(googleDrive(), encryption = "none").validateFull()
        val blank = vless(googleDrive(), encryption = "").validateFull()

        assertTrue(none.any { it.error == ProxyServerValidationError.RequiredField })
        assertTrue(blank.any { it.error == ProxyServerValidationError.RequiredField })
    }

    @Test
    fun vlessEncryptionIsStillOptionalOnOtherTransports() {
        val raw = googleDrive().copy(type = "raw", security = "none")

        assertTrue(vless(raw, encryption = "none").validateFull().isEmpty())
    }

    @Test
    fun flowIsNotSupportedOnXdrive() {
        val issues = vless(googleDrive(), flow = "xtls-rprx-vision").validateFull()

        assertTrue(issues.any { it.error == ProxyServerValidationError.UnsupportedValue })
    }

    @Test
    fun xdriveOutboundAlwaysCarriesAnEnabledMux() {
        val mux = vless(googleDrive()).toXrayOutbound("proxy").mux!!

        assertEquals("true", mux["enabled"]?.jsonPrimitive?.content)
    }

    @Test
    fun otherTransportsGetNoMuxFromTheModel() {
        assertNull(vless(googleDrive().copy(type = "raw", security = "none")).toXrayOutbound("proxy").mux)
    }

    // --- S3 rules of the core (endpoint scheme, credentials, bucket; region and addressing stay with the core) ---

    @Test
    fun s3NeedsARelativeFolderWithoutDotComponents() {
        listOf("", "/abs", "a//b", "a/./b", "a/../b", "..", ".").forEach { folder ->
            val found = issues(s3(folder = folder))
            assertTrue(
                found.any {
                    it.error == ProxyServerValidationError.UnsupportedValue ||
                        it.error == ProxyServerValidationError.RequiredField
                },
                "folder '$folder' must be rejected",
            )
        }
        assertTrue(issues(s3(folder = "skipi/sessions/")).isEmpty())
    }

    @Test
    fun s3NeedsTwoOrThreeSecretsWithAKeyIdAndASecret() {
        assertTrue(issues(s3(secrets = "only-one")).any { it.error == ProxyServerValidationError.ValueOutOfRange })
        assertTrue(issues(s3(secrets = "a,b,c,d")).any { it.error == ProxyServerValidationError.ValueOutOfRange })
        assertTrue(issues(s3(secrets = "access, secret, session")).isEmpty())
        assertTrue(issues(s3(secrets = "bad/key, secret")).any { it.error == ProxyServerValidationError.UnsupportedValue })
    }

    @Test
    fun s3NeedsAnHttpEndpointWithoutCredentialsAndABucketWithoutSlashes() {
        fun bad(extra: String) = issues(s3(extra = extra)).any { it.error == ProxyServerValidationError.UnsupportedValue }

        assertTrue(bad("""{"s3":{"endpoint":"ftp://s3.example.com","region":"r","bucket":"b"}}"""))
        assertTrue(bad("""{"s3":{"endpoint":"https://u:p@s3.example.com","region":"r","bucket":"b"}}"""))
        assertTrue(bad("""{"s3":{"endpoint":"https://s3.example.com?x=1","region":"r","bucket":"b"}}"""))
        assertTrue(bad("""{"s3":{"endpoint":"https://s3.example.com","region":"r","bucket":"a/b"}}"""))
        val minio = """{"s3":{"endpoint":"http://minio.local:9000","region":"r","bucket":"b"}}"""
        assertTrue(issues(s3(extra = minio)).isEmpty())
    }

    @Test
    fun extraMustBeAJsonObject() {
        val issues = issues(googleDrive().copy(extra = "[1,2]"))

        assertTrue(issues.any { it.error == ProxyServerValidationError.JsonObjectRequired })
    }

    @Test
    fun realityCannotCarryXdrive() {
        val issues = issues(googleDrive().copy(security = "reality", pbk = "x", fp = "chrome"))

        assertTrue(issues.any { it.error == ProxyServerValidationError.RealityTransportUnsupported })
    }

    @Test
    fun xmuxCannotSetBothMaxConnectionsAndMaxConcurrency() {
        val both = issues(googleDrive().copy(extra = """{"xmux":{"maxConnections":"2","maxConcurrency":"4"}}"""))
        val one = issues(googleDrive().copy(extra = """{"xmux":{"maxConnections":"2"}}"""))
        val zeroed = issues(googleDrive().copy(extra = """{"xmux":{"maxConnections":"2","maxConcurrency":"0"}}"""))

        assertTrue(both.any { it.error == ProxyServerValidationError.UnsupportedValue })
        assertTrue(one.isEmpty())
        assertTrue(zeroed.isEmpty())
    }
}
