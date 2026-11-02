---
title: "JSON subscriptions are read from decoded strings, because encoders escape & and / and that corrupted awg links"
author: Radetski
date: 2026-11-02
branch: main
cost: { minutes: 12, tokens: 54715 }
id: l-62ce5a24
scope: repo
status: active
src: agent
touches:
  - shared/core/src/commonMain/kotlin/features/proxy/server/usecase/importer/ProxyServerPayloadParserRegistry.kt
  - shared/core/src/commonTest/kotlin/features/proxy/server/usecase/importer/JsonLinkSubscriptionImportTest.kt
---

## Decided
- A subscription whose body is JSON (an array of link strings) is first decoded, and links are taken from the decoded string values; if that finds no links, the raw text is scanned as before.
- The supported JSON subscription shape is an array of full link strings. An array of Xray config objects is a different format and imports as a Custom server.

## Tried and ruled out
- Scanning links out of the raw JSON text with the existing regex: it kept the escapes inside the link, so "\u0026" (Go and .NET encoders) and "\/" (PHP) broke the query and the base64 keys, and the obfuscation parameters of awg links were lost.

## Watch out
- The cause was reproduced with synthetic payloads, not with the reporter's real subscription; if it still fails, ask for one sample line with the keys masked.
- Edits through shell heredocs and python strings silently eat backslashes; check escaped test strings in the file after writing them.

In the code: shared/core/.../usecase/importer/ProxyServerPayloadParserRegistry.kt (parseProxyUrlProxyServerPayload), test JsonLinkSubscriptionImportTest
