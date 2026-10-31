// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.hevtun

import kotlin.test.Test
import kotlin.test.assertEquals

/** Contract of the hev-socks5-tunnel YAML that Android has always written; Desktop must produce the same text. */
class HevSocks5TunnelYamlTest {
    @Test
    fun minimalConfigMatchesTheAndroidLayout() {
        val yaml = buildHevSocks5TunnelConfigYaml(
            HevSocks5TunnelYamlSpec(
                mtu = 1400,
                ipv4Address = "172.19.0.1",
                ipv6Address = null,
                socksAddress = "127.0.0.1",
                socksPort = 10808,
                logPath = "/tmp/tun2socks.log",
            ),
        )

        assertEquals(
            """
            tunnel:
              mtu: 1400
              ipv4: '172.19.0.1'
            socks5:
              port: 10808
              address: '127.0.0.1'
              udp: 'udp'
            misc:
              tcp-read-write-timeout: 300000
              udp-read-write-timeout: 60000
              log-file: '/tmp/tun2socks.log'
              log-level: warn
            """.trimIndent() + "\n",
            yaml,
        )
    }

    @Test
    fun optionalSectionsAppearInTheAndroidOrder() {
        val yaml = buildHevSocks5TunnelConfigYaml(
            HevSocks5TunnelYamlSpec(
                tunnelName = "skipi0",
                mtu = 1500,
                enableMultiQueue = true,
                ipv4Address = "10.10.10.1",
                ipv6Address = "fdfe:dcba:9876::1",
                socksAddress = "127.0.0.1",
                socksPort = 1080,
                socksUsername = "user",
                socksPassword = "pa'ss",
                enableTcpFastOpen = true,
                tcpReadWriteTimeoutMillis = 1_000,
                udpReadWriteTimeoutMillis = 2_000,
                logPath = "/var/log/it's.log",
                logLevel = "debug",
            ),
        )

        assertEquals(
            """
            tunnel:
              name: 'skipi0'
              mtu: 1500
              multi-queue: true
              ipv4: '10.10.10.1'
              ipv6: 'fdfe:dcba:9876::1'
            socks5:
              port: 1080
              address: '127.0.0.1'
              udp: 'udp'
              username: 'user'
              password: 'pa''ss'
              tcp-fastopen: true
            misc:
              tcp-read-write-timeout: 1000
              udp-read-write-timeout: 2000
              log-file: '/var/log/it''s.log'
              log-level: debug
            """.trimIndent() + "\n",
            yaml,
        )
    }

    @Test
    fun blankUsernameOmitsBothCredentials() {
        val yaml = buildHevSocks5TunnelConfigYaml(
            HevSocks5TunnelYamlSpec(
                mtu = 1400,
                ipv4Address = "172.19.0.1",
                ipv6Address = null,
                socksAddress = "127.0.0.1",
                socksPort = 10808,
                socksUsername = " ",
                socksPassword = "ignored",
                logPath = "",
            ),
        )

        assertEquals(false, "username" in yaml)
        assertEquals(false, "password" in yaml)
    }
}
