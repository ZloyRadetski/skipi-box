---
title: "The Windows TUN adapter must not receive multicast or broadcast, because discovery traffic fills hev's 1023-session limit and TCP stops"
author: Radetski
date: 2026-11-04
branch: main
cost: { minutes: 1, tokens: 2514 }
id: l-087ba331
scope: repo
status: active
src: agent
---

## Decided
Windows sends SSDP (1900), LLMNR (5355) and mDNS (5353) discovery to a new adapter, about 1000 UDP flows in 3 seconds. hev-socks5-tunnel on Windows serves at most 1023 sessions at once; after that every new socket fails ("socks5 client socket"), TCP included, so the tunnel looks connected and carries nothing. A firewall block rule for 224.0.0.0/4, 255.255.255.255 and ff00::/8, scoped to the tunnel adapter by interface alias, stops the storm (99 sessions in a stub test without the rule, 0 with it) and leaves LAN discovery on the physical adapters alone. The rule is installed after the routes and removed on detach; failing to install it does not fail the tunnel.

## Tried and ruled out
Core's adapter choice (a real bug, fixed, but the failure stayed). hev itself: against a stub SOCKS server it carried TCP and held 1000 connections. The Hysteria2 server: TUN failed on every server. A netsh advfirewall rule: it cannot be scoped to one interface, so it would cut LAN discovery everywhere. A shorter hev UDP timeout: it only drains the sessions, it does not stop the burst.

## Watch out
The signature is in Core's error.log: exactly 1023 "client UDP connection" entries to 239.255.255.250, 224.0.0.252 and 224.0.0.251, and no TCP entry at all.

In the code: windowsTunMulticastBlockCommand and windowsTunMulticastUnblockCommand in DesktopWindowsTunRoutes.kt; tests in DesktopWindowsTunRoutesTest.kt.
