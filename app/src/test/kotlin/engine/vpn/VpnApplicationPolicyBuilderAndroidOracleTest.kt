// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import android.content.pm.PackageManager
import android.net.VpnService
import app.modes.ProxyAppListModeBlacklist
import app.modes.ProxyAppListModeGlobal
import app.modes.ProxyAppListModeWhitelist
import engine.proxy.LocalProxyOptions
import engine.xray.XrayCoreLogPaths
import java.lang.reflect.InvocationTargetException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject

/** Records only Builder application-policy calls; it never establishes a TUN. */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(
    sdk = [35],
    application = android.app.Application::class,
    shadows = [VpnApplicationPolicyBuilderAndroidOracleTest.BuilderRecorderShadow::class],
)
class VpnApplicationPolicyBuilderAndroidOracleTest {

    @Before
    fun clearRecordedCalls() {
        BuilderRecorderShadow.reset()
    }

    @Test
    fun globalPolicyDeniesOnlyTheVpnAppAndIgnoresSelectedPackages() {
        val (service, builder) = serviceAndBuilder()

        applyPolicy(
            service,
            builder,
            VpnApplicationPolicy(mode = ProxyAppListModeGlobal, packageNames = listOf("com.example.ignored")),
        )

        assertEquals(
            listOf(BuilderCall(BuilderCallType.Disallowed, service.packageName)),
            BuilderRecorderShadow.acceptedCalls,
        )
    }

    @Test
    fun blacklistDeniesSelectedPackagesAndSelfWhileIgnoringOnlyNotFoundPackages() {
        val (service, builder) = serviceAndBuilder()
        installSyntheticPackage(service, "com.skipi.r03.vpn.blocked", uid = 23_003)
        BuilderRecorderShadow.failWithNameNotFound("com.example.removed")

        applyPolicy(
            service,
            builder,
            VpnApplicationPolicy(
                mode = ProxyAppListModeBlacklist,
                packageNames = listOf(
                    " com.skipi.r03.vpn.blocked ",
                    "com.example.removed",
                    "com.skipi.r03.vpn.blocked",
                ),
            ),
        )

        assertEquals(
            listOf(
                BuilderCall(BuilderCallType.Disallowed, "com.skipi.r03.vpn.blocked"),
                BuilderCall(BuilderCallType.Disallowed, service.packageName),
            ),
            BuilderRecorderShadow.acceptedCalls,
        )
        assertEquals(
            listOf(
                BuilderCall(BuilderCallType.Disallowed, "com.skipi.r03.vpn.blocked"),
                BuilderCall(BuilderCallType.Disallowed, "com.example.removed"),
                BuilderCall(BuilderCallType.Disallowed, service.packageName),
            ),
            BuilderRecorderShadow.attemptedCalls,
        )
    }

    @Test
    fun whitelistExcludesTheVpnAppAndAllowsOnlyTheRemainingSelectedPackage() {
        val service = Robolectric.buildService(SkipiVpnService::class.java).create().get()
        installSyntheticPackage(service, "com.skipi.r03.vpn.allowed", uid = 23_004)
        val builder = service.Builder()

        applyPolicy(
            service,
            builder,
            VpnApplicationPolicy(
                mode = ProxyAppListModeWhitelist,
                packageNames = listOf(service.packageName, "com.skipi.r03.vpn.allowed"),
            ),
        )

        assertEquals(
            listOf(BuilderCall(BuilderCallType.Allowed, "com.skipi.r03.vpn.allowed")),
            BuilderRecorderShadow.acceptedCalls,
        )
        assertFalse(BuilderRecorderShadow.attemptedCalls.any { call -> call.packageName == service.packageName })
    }

    @Test
    fun emptyEffectiveWhitelistFallsBackToDenyingSyntheticInstalledPackages() {
        val service = Robolectric.buildService(SkipiVpnService::class.java).create().get()
        installSyntheticPackage(service, "com.skipi.r03.vpn.alpha", uid = 23_001)
        installSyntheticPackage(service, "com.skipi.r03.vpn.beta", uid = 23_002)
        val builder = service.Builder()
        BuilderRecorderShadow.failWithNameNotFound("com.example.removed")

        applyPolicy(
            service,
            builder,
            VpnApplicationPolicy(
                mode = ProxyAppListModeWhitelist,
                packageNames = listOf(service.packageName, "com.example.removed"),
            ),
        )

        assertEquals(
            listOf(BuilderCall(BuilderCallType.Allowed, "com.example.removed")),
            BuilderRecorderShadow.attemptedCalls.take(1),
        )
        assertTrue(BuilderRecorderShadow.acceptedCalls.none { call -> call.type == BuilderCallType.Allowed })
        assertTrue(
            BuilderRecorderShadow.acceptedCalls.contains(
                BuilderCall(BuilderCallType.Disallowed, "com.skipi.r03.vpn.alpha"),
            ),
        )
        assertTrue(
            BuilderRecorderShadow.acceptedCalls.contains(
                BuilderCall(BuilderCallType.Disallowed, "com.skipi.r03.vpn.beta"),
            ),
        )
        assertTrue(
            BuilderRecorderShadow.acceptedCalls.contains(
                BuilderCall(BuilderCallType.Disallowed, service.packageName),
            ),
        )
    }

    @Test
    fun nonPackageManagerFailureFromBuilderIsPropagated() {
        val service = Robolectric.buildService(SkipiVpnService::class.java).create().get()
        installSyntheticPackage(service, "com.skipi.r03.vpn.denied", uid = 23_005)
        val builder = service.Builder()
        BuilderRecorderShadow.failWithSecurityException("com.skipi.r03.vpn.denied")

        val error = assertThrows(SecurityException::class.java) {
            applyPolicy(
                service,
                builder,
                VpnApplicationPolicy(
                    mode = ProxyAppListModeBlacklist,
                    packageNames = listOf("com.skipi.r03.vpn.denied"),
                ),
            )
        }

        assertEquals("synthetic builder failure", error.message)
        assertEquals(
            listOf(BuilderCall(BuilderCallType.Disallowed, "com.skipi.r03.vpn.denied")),
            BuilderRecorderShadow.attemptedCalls,
        )
    }

    private fun serviceAndBuilder(): Pair<SkipiVpnService, VpnService.Builder> {
        val service = Robolectric.buildService(SkipiVpnService::class.java).create().get()
        return service to service.Builder()
    }

    private fun applyPolicy(
        service: SkipiVpnService,
        builder: VpnService.Builder,
        policy: VpnApplicationPolicy,
    ) {
        val config = VpnServiceStartConfig(
            sessionName = "R03 policy test",
            dnsServers = emptyList(),
            xrayConfigJson = "{}",
            applicationPolicy = policy,
            localProxyOptions = LocalProxyOptions("127.0.0.1", 1080, "", ""),
            appendHttpProxyOptions = VpnAppendHttpProxyOptions.Disabled,
            coreLogPaths = XrayCoreLogPaths("", ""),
        )
        val method = SkipiVpnService::class.java.getDeclaredMethod(
            "applyApplicationPolicy",
            VpnService.Builder::class.java,
            VpnServiceStartConfig::class.java,
        ).apply { isAccessible = true }
        try {
            method.invoke(service, builder, config)
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
    }

    private fun installSyntheticPackage(service: SkipiVpnService, packageName: String, uid: Int) {
        val applicationInfo = android.content.pm.ApplicationInfo().apply {
            this.packageName = packageName
            this.uid = uid
            nonLocalizedLabel = packageName
        }
        val packageInfo = android.content.pm.PackageInfo().apply {
            this.packageName = packageName
            this.applicationInfo = applicationInfo
        }
        org.robolectric.Shadows.shadowOf(service.packageManager).installPackage(packageInfo)
    }

    data class BuilderCall(val type: BuilderCallType, val packageName: String)

    enum class BuilderCallType {
        Allowed,
        Disallowed,
    }

    @Implements(VpnService.Builder::class)
    class BuilderRecorderShadow {
        @RealObject
        private lateinit var realBuilder: VpnService.Builder

        @Implementation
        fun addAllowedApplication(packageName: String): VpnService.Builder = record(
            BuilderCall(BuilderCallType.Allowed, packageName),
        )

        @Implementation
        fun addDisallowedApplication(packageName: String): VpnService.Builder = record(
            BuilderCall(BuilderCallType.Disallowed, packageName),
        )

        private fun record(call: BuilderCall): VpnService.Builder {
            attemptedCalls += call
            when (call.type) {
                BuilderCallType.Allowed -> allowedFailures[call.packageName]
                BuilderCallType.Disallowed -> disallowedFailures[call.packageName]
            }?.let { error -> throw error }
            acceptedCalls += call
            return realBuilder
        }

        companion object {
            val attemptedCalls = mutableListOf<BuilderCall>()
            val acceptedCalls = mutableListOf<BuilderCall>()
            private val allowedFailures = mutableMapOf<String, Exception>()
            private val disallowedFailures = mutableMapOf<String, Exception>()

            fun reset() {
                attemptedCalls.clear()
                acceptedCalls.clear()
                allowedFailures.clear()
                disallowedFailures.clear()
            }

            fun failWithNameNotFound(packageName: String) {
                allowedFailures[packageName] = PackageManager.NameNotFoundException(packageName)
                disallowedFailures[packageName] = PackageManager.NameNotFoundException(packageName)
            }

            fun failWithSecurityException(packageName: String) {
                allowedFailures[packageName] = SecurityException("synthetic builder failure")
                disallowedFailures[packageName] = SecurityException("synthetic builder failure")
            }
        }
    }
}
