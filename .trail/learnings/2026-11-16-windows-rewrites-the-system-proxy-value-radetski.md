---
title: "Windows rewrites the system proxy value it is refreshed with, so a SKIPI lease must recognise its own endpoint in any form and never restore a dead local proxy"
author: Radetski
date: 2026-11-16
branch: main
cost: { minutes: 30, tokens: 113515 }
id: l-5e2737f5
scope: repo
status: active
src: agent
---

## Decided
WinINet turns the ProxyServer value 'http=127.0.0.1:P;https=127.0.0.1:P' into '127.0.0.1:P' when it is refreshed. The lease took that for somebody else's setting, discarded itself and left the proxy enabled on a port nobody served (browser: 'the proxy server refuses connections'); the next lease then snapshotted that dead proxy as the user's original and restored it enabled. Now a value that names only the lease's endpoint, in any form, is ours; a dead loopback proxy is snapshotted as disabled; and every connect first releases the old lease and switches off an enabled proxy that points at a dead loopback port.
TUN connect and disconnect also got fast: the netsh/PowerShell commands run in parallel, DNS and the firewall rule are set in the background, and the dead adapter is removed in the background (start 1.9 s -> 0.4 s).

## Tried and ruled out
Blaming the dynamic port allocation or the TUN/HTTP-inbound combination: an isolated elevated run with dynamic port, HTTP inbound off and TUN worked end to end. The failure was the registry value, seen only by polling it every 300 ms while the user toggled settings.

## Watch out
The packaged app must run with XDG_RUNTIME_DIR, APPDATA and LOCALAPPDATA pointed at a throwaway folder or it hands off to the running instance. A killed test app leaves hev-socks5-tunnel.exe running.

In the code: DesktopWindowsSystemProxy.kt (matchesEndpoint, readSnapshot, repairStale), DesktopTunnelControllerFactory.kt (reconcileSystemProxy), DesktopWindowsTunRoutes.kt (parallel, background), DesktopWindowsTunDevice.kt (close).
