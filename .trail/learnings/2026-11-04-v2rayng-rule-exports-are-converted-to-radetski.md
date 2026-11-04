---
title: v2rayNG rule exports are converted to a Shadowrocket profile at import; bare domains stay substring matches
author: Radetski
date: 2026-11-04
branch: main
cost: { minutes: 5, tokens: 76975 }
id: l-ebfecce1
scope: repo
status: active
src: agent
touches:
  - shared/app/src/commonMain/kotlin/app/skipi/app/config/TrafficConfigDocumentImport.kt
  - shared/app/src/commonTest/kotlin/app/skipi/app/config/TrafficConfigDocumentImportTest.kt
  - shared/core/src/commonMain/kotlin/features/config/V2RayNgRoutingRules.kt
  - shared/core/src/commonTest/kotlin/features/config/V2RayNgRoutingRulesTest.kt
---

## Decided
A pasted v2rayNG rule array is converted inside the shared profile import, so every import path accepts it. Fields of one rule are ANDed, entries of one field ORed (as Xray reads it); a single-field rule becomes one plain line per value. A bare domain becomes DOMAIN-KEYWORD because Xray treats it as a substring. Disabled rules, DNS-only rules (ruleType 2) and unknown outbound tags are skipped; tcp+udp and port 0-65535 mean no condition; a rule without conditions becomes FINAL.

## Tried and ruled out
Remarks as # comment lines: the importer takes any # line containing "name" as the profile name.

## Watch out
Values with commas or parentheses (some regexp entries) are dropped. Run only as unit tests, not in the app.

In the code:
- shared/core/.../features/config/V2RayNgRoutingRules.kt
- shared/app/.../app/config/TrafficConfigDocumentImport.kt
