---
title: "A rule's name from v2rayNG remarks is stored at the end of its own line after a slash pair, so it moves with the rule"
author: Radetski
date: 2026-11-04
branch: main
cost: { minutes: 8, tokens: 48292 }
id: l-a7daae19
scope: repo
status: active
replaces: l-ebfecce1
src: agent
touches:
  - shared/app/src/commonMain/kotlin/app/skipi/app/config/TrafficConfigDocumentImport.kt
  - shared/app/src/commonTest/kotlin/app/skipi/app/config/TrafficConfigDocumentImportTest.kt
  - shared/core/src/commonMain/kotlin/features/config/V2RayNgRoutingRules.kt
  - shared/core/src/commonTest/kotlin/features/config/V2RayNgRoutingRulesTest.kt
---

## Decided
The name of a routing rule (v2rayNG remarks) is kept on the rule line itself after " // ", read into ShadowrocketRule.remarks and shown as the card title with the conditions as subtitle. One v2rayNG rule split into several lines repeats its name on each. Editing a rule through the draft keeps its latest name.

## Tried and ruled out
A comment line above the rule: dragging, deleting and replacing work on single line numbers, so the comment would be left behind or stick to the next rule. A # comment also collides with profile-name detection (any # line containing "name").

## Watch out
The separator needs a space before the slashes so https:// in values is not cut. The name cannot be edited in the rule editor yet, only imported and preserved. Bare domains stay substring matches, domain and IP in one rule are ANDed (checked in Xray's condition chain).

In the code:
- shared/core/.../features/config/ShadowrocketConfig.kt: parseRuleLine, toShadowrocketLine, RuleRemarksSeparator
- shared/core/.../features/config/V2RayNgRoutingRules.kt
- shared/app/.../config/TrafficConfigEditorController.kt: saveRuleDraft
