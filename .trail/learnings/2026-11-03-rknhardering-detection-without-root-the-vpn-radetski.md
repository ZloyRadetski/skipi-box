---
title: "RKNHardering detection without root: the VPN interface signals cannot be hidden, only the checking app can be excluded from the tunnel"
author: Radetski
date: 2026-11-03
branch: main
cost: { minutes: 21, tokens: 123872 }
id: l-1d02cebc
scope: repo
status: active
src: agent
touches:
  - app/src/main/kotlin/engine/vpn/SkipiVpnService.kt
  - app/src/main/kotlin/engine/vpn/VpnXrayConfig.kt
  - shared/core/src/commonMain/kotlin/engine/vpn/VpnTunConfig.kt
  - shared/core/src/jvmMain/kotlin/engine/proxy/LocalProxyConfig.kt
---

## Decided
Hiding the VPN from a checker app is done by excluding that app from the tunnel (per-app policy), not by changing the tunnel. The tunnel subnet stays a user setting and is left alone.

## Tried and ruled out
- Unix socket instead of the loopback SOCKS5 port: hev-socks5-tunnel has no AF_UNIX support, so it needs a native C patch and rebuild of every ABI. Not worth it while it is unclear which of the scanner's open loopback ports are ours.
- Changing the default tun subnet 172.19.0.1/30 or the DNS 1.1.1.1: tiny effect, the default route through the tun interface is detected regardless, and the subnet is a persisted user setting.
- Treating the 127.0.0.1 answer from Yandex as an app bug: it also appears with the VPN fully off, so it comes from the network (router/provider), as do the ICMP reply from instagram and the public IP.

## Watch out
Transport VPN capability, tun0 in getifaddrs and /proc, the default route via the tun interface and SO_BINDTODEVICE on it are produced by any VpnService; with the VPN off the same scan is only REVIEW, with it on it is DETECTED.
In the code: per-app exclusion is applyApplicationPolicy in SkipiVpnService; the local SOCKS5 inbound is built in LocalProxyConfig.kt.
