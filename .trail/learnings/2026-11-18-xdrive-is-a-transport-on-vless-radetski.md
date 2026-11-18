---
title: "XDRIVE is a transport on VLESS, VMess and Trojan, its tuning lives in one raw JSON field merged flat into xdriveSettings"
author: Radetski
date: 2026-11-18
branch: main
cost: { minutes: 9, tokens: 84332 }
id: l-7e727b02
scope: repo
status: active
src: agent
touches:
  - shared/core/src/commonTest/kotlin/features/proxy/server/model/XdriveTransportTest.kt
  - shared/core/src/commonTest/kotlin/engine/xray/XrayConfigLogRedactionTest.kt
  - shared/core/src/commonTest/kotlin/features/proxy/server/model/V2RayTransportTest.kt
---

## Decided
XDRIVE is a transport type (xdrive) of V2RayParameters, so it works for every server that has transport parameters. Service, folder and secrets are separate fields (xdService, xdFolder, xdSecrets, comma-separated secrets); everything else (segmentBytes, intervals, concurrency, s3, template, xmux) is the existing raw-JSON "extra" field, as xhttp does.
Link form: type=xdrive&service=&folder=&secrets=&extra= . The core defines no link format, this one is ours.
Unlike xhttp, extra is merged flat into xdriveSettings, not nested under "extra": the core reads the tuning fields from xdriveSettings itself. The structured fields win over the same keys in extra.
Validation mirrors the core: service is google-drive, s3, template or local; google-drive needs exactly 3 secrets (ClientID, ClientSecret, RefreshToken); template and s3 need that object in extra; xmux must not set maxConnections and maxConcurrency together (0 counts as unset); REALITY cannot carry it.

## Tried and ruled out
Fourteen separate fields for every tuning value: too much editor for rarely changed numbers, the raw JSON field is what xhttp already does.

## Watch out
The log redaction did not know "secrets", so Google Drive tokens would have reached the log. It now also masks template, endpoint and bucket, and a sensitive key masks its whole subtree. Before, only strings directly under a sensitive key or inside its arrays were masked, so a sensitive object (such as the DNS "hosts" map) kept its values.
A new transport must be added in the places that list transports: V2RayTransportOptions, the URL parse and get, validation, the display name, Custom config labels, the editor section and the five string files.
The editor stores the dropdown's default service into the model; the core requires a service, and a dropdown that only displays google-drive would leave it empty.
Not done: Mihomo import, a real run against the core and a real Google Drive account.

In the code: shared/core/.../model/ProxyServer.kt (V2RayParameters), XrayOutbound.kt (putXdriveSettings), ProxyServerValidation.kt (validateXdrive), XrayConfigLogRedaction.kt; test XdriveTransportTest.kt
