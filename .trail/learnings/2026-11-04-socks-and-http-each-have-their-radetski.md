---
title: "SOCKS and HTTP each have their own auth mode and login, and the HTTP inbound listens like SOCKS"
author: Radetski
date: 2026-11-04
branch: main
cost: { minutes: 30, tokens: 304137 }
id: l-5c882d9e
scope: repo
status: active
replaces: l-a0b96aae
src: agent
touches:
  - app/src/main/kotlin/data/AppSettingsPreferences.kt
  - app/src/main/kotlin/engine/proxy/AndroidProxyEngine.kt
  - app/src/main/kotlin/engine/vpn/SkipiVpnService.kt
  - app/src/main/kotlin/engine/vpn/VpnXrayConfig.kt
  - app/src/main/kotlin/features/settings/LocalProxySettingsPage.kt
  - app/src/test/kotlin/data/AppSettingsPreferencesCompatibilityTest.kt
  - app/src/test/kotlin/data/repository/AndroidRuntimePreferencesContractTest.kt
  - app/src/test/kotlin/engine/vpn/VpnHttpInboundTest.kt
  - shared/core/src/commonMain/kotlin/app/AppStateLocalProxy.kt
  - shared/core/src/commonTest/kotlin/app/AppStateLocalProxyTest.kt
---

## Decided
The user now wants SOCKS and the HTTP inbound protected independently, so the earlier "one login covers both" rule is gone. Each inbound has a mode: auto (the login a custom JSON server carries on its own inbound, else the stored generated one), manual (the typed one), from JSON (only the JSON's, open without it) and off. HTTP defaults to off, SOCKS keeps its platform default. The HTTP inbound is a setting of its own on every platform; the Windows system proxy and Android's VPN proxy only need it. It listens on the same address as SOCKS, so "listen on all interfaces" shares both, and the system proxy still points at 127.0.0.1. With a dynamic port the HTTP port is dynamic too.

## Tried and ruled out
Resolving the login before the selected server is known: from JSON and auto need the custom JSON, so the listener options are built after the server is picked and the resolved options are what hev reads. Keeping the old SOCKS-only switch as the only flag: it stays as the stored form of "off" so older settings and backups read correctly.

## Watch out
A system proxy cannot carry a login, so with HTTP auth on, browsers still answer 407 with a prompt (the earlier accepted cost); the settings screen says so. A custom JSON only supplies a login for an inbound SKIPI writes itself; with override off the JSON's inbounds are used as they are.

In the code: platform/LocalProxyAuth.kt (modes, resolution, reading a JSON), AppStateLocalProxy.kt, LocalProxyXrayConfigFactory.buildHttpInbound (httpUsername/httpPassword), DesktopLocalProxyRuntime.kt, VpnLocalProxyConfig.kt.
