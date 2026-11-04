// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopMacTunTest {
    private fun spec(
        interfaceName: String = "utun9",
        routes: List<String> = listOf("0.0.0.0/1", "128.0.0.0/1"),
        hev: String = "/Applications/SKIPI.app/Contents/Resources/hev/hev-socks5-tunnel",
    ) = MacTunSupervisorSpec(
        interfaceName = interfaceName,
        mtu = 1400,
        ipv4Address = "172.19.0.1",
        routes = routes,
        hevExecutable = hev,
        hevConfig = "/Users/test/SKIPI/run/tun-hev.yaml",
        stopFile = "/Users/test/SKIPI/run/tun-stop",
        readyFile = "/Users/test/SKIPI/run/tun-ready",
        errorFile = "/Users/test/SKIPI/run/tun-error",
        appPid = 4242,
        userUid = 501,
    )

    // ---- the privileged supervisor script ----------------------------------------------------------

    @Test
    fun theScriptStartsHevWaitsForTheInterfaceAndInstallsEveryRouteOnIt() {
        val script = macTunSupervisorScript(spec())

        assertTrue("'/Applications/SKIPI.app/Contents/Resources/hev/hev-socks5-tunnel' '/Users/test/SKIPI/run/tun-hev.yaml'" in script, script)
        assertTrue("ifconfig \"\$IFACE\"" in script, "it waits for the interface hev creates")
        assertTrue("ifconfig \"\$IFACE\" inet '172.19.0.1' '172.19.0.1' mtu 1400 up" in script, script)
        assertTrue("route -n add -net '0.0.0.0/1' -interface \"\$IFACE\"" in script, script)
        assertTrue("route -n add -net '128.0.0.0/1' -interface \"\$IFACE\"" in script, script)
    }

    @Test
    fun ipv6RoutesUseTheInet6Family() {
        val script = macTunSupervisorScript(spec(routes = listOf("0.0.0.0/1", "::/1", "8000::/1")))

        assertTrue("route -n add -inet6 -net '::/1' -interface \"\$IFACE\"" in script, script)
        assertTrue("route -n add -inet6 -net '8000::/1' -interface \"\$IFACE\"" in script, script)
        assertTrue("route -n add -net '0.0.0.0/1' -interface" in script, "IPv4 keeps the plain form")
    }

    @Test
    fun everyRouteThatWasAddedIsDeletedOnCleanup() {
        val script = macTunSupervisorScript(spec(routes = listOf("0.0.0.0/1", "::/1")))
        val cleanup = script.substringAfter("cleanup()").substringBefore("\n}")

        assertTrue("route -n delete -net '0.0.0.0/1' -interface \"\$IFACE\"" in cleanup, cleanup)
        assertTrue("route -n delete -inet6 -net '::/1' -interface \"\$IFACE\"" in cleanup, cleanup)
        assertTrue("kill \"\$HEV_PID\"" in cleanup, "hev is stopped")
        assertTrue("rm -f \"\$READY\"" in cleanup, "the ready marker goes with the session")
    }

    @Test
    fun theSessionEndsWhenTheAppAsksHevDiesOrTheAppIsGone() {
        val script = macTunSupervisorScript(spec())

        assertTrue("APP_PID=4242" in script)
        assertTrue("kill -0 \"\$APP_PID\"" in script, "the supervisor must not outlive a crashed app")
        assertTrue("kill -0 \"\$HEV_PID\"" in script)
        assertTrue("[ ! -e \"\$STOP\" ]" in script, "the app asks for a stop through the stop file")
        assertTrue("trap " in script)
    }

    @Test
    fun theMarkersAreHandedBackToTheUserBecauseRootCreatesThem() {
        val script = macTunSupervisorScript(spec())

        assertTrue("chown 501 \"\$READY\"" in script, script)
        assertTrue("chown 501 \"\$ERR\"" in script, script)
    }

    @Test
    fun theSupervisorMakesHevExecutableBeforeStartingItBecausePackagingMayDropTheBit() {
        // A disk image built by jpackage can lose the executable bit of a bundled helper; root can
        // always restore it, so the privileged side repairs it instead of failing with "Permission denied".
        val script = macTunSupervisorScript(spec())
        val hev = "'/Applications/SKIPI.app/Contents/Resources/hev/hev-socks5-tunnel'"

        val repair = script.indexOf("[ -x $hev ] || chmod 755 $hev")
        val start = script.indexOf("$hev '/Users/test/SKIPI/run/tun-hev.yaml'")
        assertTrue(repair >= 0, script)
        assertTrue(start > repair, "the repair must come before hev is started")
        assertTrue("|| fail" in script.substring(repair, start), "a failed repair is reported, not ignored")
    }

    @Test
    fun theSupervisorRaisesTheDescriptorLimitBeforeStartingHev() {
        // hev serves two descriptors per session. Its own attempt to raise the limit asks for an
        // unlimited soft value, which macOS refuses, so a launchd child keeps the default of 256 and
        // runs out of sockets after about a hundred sessions.
        val script = macTunSupervisorScript(spec())
        val hev = "'/Applications/SKIPI.app/Contents/Resources/hev/hev-socks5-tunnel'"

        val raise = script.indexOf("ulimit -n 10240")
        val start = script.indexOf("$hev '/Users/test/SKIPI/run/tun-hev.yaml'")
        assertTrue(raise >= 0, script)
        assertTrue(start > raise, "the limit must be raised before hev is started")
        assertTrue("|| true" in script.substring(raise, start).lines().first(), "a refused raise must not stop the tunnel")
    }

    @Test
    fun aFailureAfterStartRemovesWhatWasInstalled() {
        val script = macTunSupervisorScript(spec())
        val fail = script.substringAfter("fail()").substringBefore("\n}")

        assertTrue("cleanup" in fail, fail)
        assertTrue("\"\$ERR\"" in fail, fail)
    }

    @Test
    fun pathsAreQuotedSoSpacesAndQuotesCannotBreakOutOfTheScript() {
        val script = macTunSupervisorScript(spec(hev = "/Applications/My App/it's/hev"))

        assertTrue("'/Applications/My App/it'\\''s/hev'" in script, script)
    }

    @Test
    fun anInterfaceNameThatIsNotAUtunNameIsRejected() {
        listOf("skipi0", "utun", "utun9; rm -rf /", "../utun9", "utun9 ", "").forEach { name ->
            assertFailsWith<IllegalArgumentException>(name) { macTunSupervisorScript(spec(interfaceName = name)) }
        }
    }

    @Test
    fun aRouteThatIsNotACidrIsRejected() {
        listOf("0.0.0.0", "0.0.0.0/1; reboot", "default", "1.2.3.4/33", "::/129", "").forEach { route ->
            assertFailsWith<IllegalArgumentException>(route) { macTunSupervisorScript(spec(routes = listOf(route))) }
        }
    }

    // ---- asking for administrator rights ---------------------------------------------------------

    @Test
    fun theScriptTravelsAsBase64SoNoQuotingSurvivesIntoTheShell() {
        val script = macTunSupervisorScript(spec())
        val command = macPrivilegedShellCommand(script)

        val encoded = command.substringAfter("echo ").substringBefore(" |")
        assertEquals(script, String(Base64.getDecoder().decode(encoded)))
        assertTrue(command.endsWith("&"), "the supervisor keeps running after the prompt returns: $command")
        assertTrue("/usr/bin/base64 -D" in command && "/bin/sh" in command, command)
    }

    @Test
    fun theAppleScriptEscapesBackslashesAndQuotes() {
        assertEquals(
            "do shell script \"echo \\\"a\\\\b\\\"\" with administrator privileges",
            macAdminAppleScript("echo \"a\\b\""),
        )
        assertEquals(
            listOf("/usr/bin/osascript", "-e", macAdminAppleScript("true")),
            macPrivilegedLaunchCommand("true"),
        )
    }

    // ---- choosing the utun interface -----------------------------------------------------------------

    @Test
    fun theFirstFreeUtunNumberFromNineIsChosen() {
        assertEquals("utun9", pickMacTunInterfaceName(emptyList()))
        assertEquals("utun9", pickMacTunInterfaceName(listOf("lo0", "en0", "utun0", "utun1", "utun2")))
        assertEquals("utun10", pickMacTunInterfaceName(listOf("utun0", "utun9")))
        assertEquals("utun11", pickMacTunInterfaceName(listOf("utun9", "utun10", "en0")))
    }

    @Test
    fun theInterfaceListOfIfconfigIsSplitOnSpaces() {
        assertEquals(listOf("lo0", "gif0", "en0", "utun0"), parseMacInterfaceList("lo0 gif0 en0 utun0\n"))
        assertEquals(emptyList(), parseMacInterfaceList(""))
    }

    // ---- the tunnel session ---------------------------------------------------------------------------

    private class Session(val directory: Path = Files.createTempDirectory("skipi-mac-tun")) {
        val launches = mutableListOf<List<String>>()
        var launchResult: Result<Unit> = Result.success(Unit)
        var onLaunch: (Path) -> Unit = { dir -> Files.writeString(dir.resolve("tun-ready"), "7777") }
        var aliveProcesses = setOf(7777L)
        var now = 0L

        val tunnel = DesktopMacHevTunnel(
            hevExecutable = Path.of("/Applications/SKIPI.app/Contents/Resources/hev/hev-socks5-tunnel"),
            directory = directory,
            planJson = { """{"name":"utun9","mtu":1400,"addresses":["172.19.0.1/30"],"routes":["0.0.0.0/1","128.0.0.0/1"]}""" },
            appPid = 4242,
            userUid = 501,
            launcher = { command ->
                launches += command
                launchResult.also { if (it.isSuccess) onLaunch(directory) }
            },
            isAlive = { pid -> pid in aliveProcesses },
            now = { now },
            sleep = { millis -> now += millis; Thread.sleep(2) },
            readyTimeoutMillis = 5_000,
        )

        fun close() = directory.toFile().deleteRecursively()
    }

    @Test
    fun startingAsksForRightsRunsTheSupervisorAndWaitsUntilItIsReady() {
        val session = Session()

        session.tunnel.start("tunnel:\n  mtu: 1400\n", -1).getOrThrow()

        val launch = session.launches.single()
        assertEquals("/usr/bin/osascript", launch.first())
        assertTrue(Files.readString(session.directory.resolve("tun-hev.yaml")).contains("mtu: 1400"), "hev reads the generated config")
        val shell = Regex("do shell script \"echo ([A-Za-z0-9+/=]+) ").find(launch.last())!!.groupValues[1]
        val script = String(Base64.getDecoder().decode(shell))
        assertTrue("route -n add -net '0.0.0.0/1'" in script, script)
        assertTrue(session.tunnel.isRunning())
        session.close()
    }

    @Test
    fun cancellingThePasswordPromptIsReportedInPlainWords() {
        val session = Session()
        session.launchResult = Result.failure(IllegalStateException("execution error: User canceled. (-128)"))

        val error = session.tunnel.start("y", -1).exceptionOrNull()

        assertNotNull(error)
        assertTrue("administrator" in error.message.orEmpty().lowercase(), error.message)
        assertFalse(session.tunnel.isRunning())
        session.close()
    }

    @Test
    fun aSupervisorThatReportsAnErrorFailsTheStartWithItsText() {
        val session = Session()
        session.onLaunch = { dir -> Files.writeString(dir.resolve("tun-error"), "The tunnel interface did not appear") }

        val error = session.tunnel.start("y", -1).exceptionOrNull()

        assertTrue("did not appear" in error?.message.orEmpty(), error?.message)
        session.close()
    }

    @Test
    fun aSupervisorThatNeverBecomesReadyTimesOut() {
        val session = Session()
        session.onLaunch = { }

        val error = session.tunnel.start("y", -1).exceptionOrNull()

        assertNotNull(error)
        assertTrue(session.now >= 5_000, "it waited for the whole timeout")
        session.close()
    }

    @Test
    fun stoppingAsksThroughTheStopFileAndWaitsForTheSupervisorToClean() {
        val session = Session()
        session.tunnel.start("y", -1).getOrThrow()
        // The supervisor, on seeing the stop file, removes its ready marker as its last cleanup step.
        session.onLaunch = { }
        val ready = session.directory.resolve("tun-ready")
        val stopFile = session.directory.resolve("tun-stop")
        val watcher = Thread {
            while (!Files.exists(stopFile)) Thread.sleep(5)
            Files.deleteIfExists(ready)
        }.also { it.start() }

        session.tunnel.stop().getOrThrow()
        watcher.join(2_000)

        assertFalse(Files.exists(ready))
        assertFalse(session.tunnel.isRunning())
        session.close()
    }

    @Test
    fun aTunnelThatWasNeverStartedStopsQuietly() {
        val session = Session()
        session.tunnel.stop().getOrThrow()
        assertFalse(session.tunnel.isRunning())
        session.close()
    }

    @Test
    fun theTunnelIsNotRunningOnceTheSupervisorProcessIsGone() {
        val session = Session()
        session.tunnel.start("y", -1).getOrThrow()

        session.aliveProcesses = emptySet()

        assertFalse(session.tunnel.isRunning())
        session.close()
    }

    @Test
    fun leftoversOfAnEarlierSessionAreClearedBeforeStarting() {
        val session = Session()
        Files.writeString(session.directory.resolve("tun-error"), "old failure")
        Files.writeString(session.directory.resolve("tun-stop"), "")
        session.onLaunch = { dir -> Files.writeString(dir.resolve("tun-ready"), "7777") }

        session.tunnel.start("y", -1).getOrThrow()

        assertFalse(Files.exists(session.directory.resolve("tun-error")))
        assertFalse(Files.exists(session.directory.resolve("tun-stop")))
        session.close()
    }
}
