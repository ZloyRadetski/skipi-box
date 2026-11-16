---
title: "Windows TUN gets its DNS on the adapter, and a stale system proxy lease is reconciled before every connect"
author: Radetski
date: 2026-11-16
branch: main
cost: { minutes: 1, tokens: 849 }
id: l-9be412c8
scope: repo
status: active
src: agent
---

## Decided
On Windows hev creates the Wintun adapter and nothing gave it DNS, so the DNS client kept the physical adapter's servers and queries never entered the tunnel (Core's port-53 hijack was never asked). After the routes, the adapter now gets the plan's DNS (Set-DnsClientServerAddress) and interface metric 1, and the cache is flushed; detach flushes again. Before every connect, in both modes, the app releases a SKIPI-owned system proxy lease (reconcileSystemProxy), and the Windows acquire hands back an own Active lease on another port and re-takes the proxy instead of failing with 'already active for <old port>'.

## Watch out
The dynamic port moves at every start, so a lease left by a killed app made every later proxy-mode connect fail. The DNS and TUN end-to-end path needs an elevated run, which the unit tests cannot cover.

In the code: windowsTunDnsCommand in DesktopWindowsTunRoutes.kt, DesktopWindowsSystemProxyLeaseManager.acquire, DesktopTunnelController.reconcileSystemProxy.
