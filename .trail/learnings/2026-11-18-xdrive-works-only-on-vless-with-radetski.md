---
title: "XDRIVE works only on VLESS with real encryption and a mux, and its tuning lives in one raw JSON field"
author: Radetski
date: 2026-11-18
branch: main
cost: { minutes: 15, tokens: 119465 }
id: l-713c0aa7
scope: repo
status: active
replaces: l-7e727b02
src: agent
touches:
  - shared/core/src/desktopTest/kotlin/TmpDumpXrayConfigs.kt
  - shared/core/src/commonTest/kotlin/engine/xray/XdriveMuxOutboundTest.kt
---

## Decided
XDRIVE is the transport "xdrive" of V2RayParameters, but only VLESS may use it: the core refuses every other protocol. The editor offers it only for VLESS.
The core also demands VLESS encryption other than none, no flow, and an enabled mux. The model therefore always emits a mux for xdrive, and the renderer replaces the user's global mux values the core refuses (negative concurrency, xudpProxyUDP443 skip) or a mux that is off.
Service, folder and secrets are separate fields; every other tuning value (segmentBytes, s3, template, xmux) is the raw-JSON extra field, merged flat into xdriveSettings, not nested.
The link form (type=xdrive&service&folder&secrets&extra) is ours, the core has none.

## Tried and ruled out
Reading the rules from the .proto and the config structs only: it missed the VLESS-only, encryption and mux checks, which live in infra/conf/xray.go (validateXDrive). Run generated JSON through the core's own parser instead.

## Watch out
S3 needs a relative remoteFolder, 2 to 3 secrets (key id, secret, optional session token), an http(s) endpoint without credentials, and a bucket without slashes.
Log redaction now masks secrets, template, endpoint and bucket, and a sensitive key masks its whole subtree.

In the code: V2RayParameters in ProxyServer.kt, validateXdrive in ProxyServerValidation.kt, xdriveMuxConfig in XrayOutboundSupport.kt; tests XdriveTransportTest.kt and XdriveMuxOutboundTest.kt
