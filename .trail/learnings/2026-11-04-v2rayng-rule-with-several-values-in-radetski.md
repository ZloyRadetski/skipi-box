---
title: "v2rayNG rule with several values in one field becomes one OR card, not one line per value"
author: Radetski
date: 2026-11-04
branch: main
cost: { minutes: 11, tokens: 31325 }
id: l-d234d0c7
scope: repo
status: active
replaces: l-a7daae19
src: agent
touches:
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiProxyHomeFloatingToolbar.kt
  - shared/ui/src/commonTest/kotlin/app/skipi/ui/home/FloatingToolbarVisibilityTest.kt
  - shared/app/src/commonMain/kotlin/app/skipi/app/config/TrafficConfigEditorController.kt
  - shared/app/src/commonTest/kotlin/app/skipi/app/config/TrafficConfigRuleEditorControllerTest.kt
  - shared/core/src/commonMain/kotlin/features/config/ShadowrocketConfig.kt
  - shared/core/src/commonMain/kotlin/features/config/V2RayNgRoutingRules.kt
  - shared/core/src/commonTest/kotlin/features/config/ShadowrocketRuleRemarksTest.kt
  - shared/core/src/commonTest/kotlin/features/config/V2RayNgRoutingRulesTest.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/config/SkipiTrafficConfigRulesList.kt
---

## Decided
A v2rayNG rule becomes exactly one rule line: one value stays a plain line, several values in one field become one OR line, several fields become AND over fields with OR inside a field. Repeated values are dropped. A single copied rule object (not only an array) is accepted. The name from remarks stays after " // " at the end of that one line.

## Tried and ruled out
One plain line per value: a 13-domain rule turned into 13 cards. A comment line above the rule for the name: it is left behind when a rule is dragged or deleted, and a # line with "name" in it is taken as the profile name.

## Watch out
Bare domains stay substring matches (DOMAIN-KEYWORD), and domain plus IP in one rule are ANDed as Xray does. Values with commas or parentheses are dropped. The name cannot be edited in the rule editor yet. Only unit tested, not run in the app.

In the code:
- shared/core/.../features/config/V2RayNgRoutingRules.kt
- shared/core/.../features/config/ShadowrocketConfig.kt: remarks parsing, RuleRemarksSeparator
- shared/app/.../config/TrafficConfigEditorController.kt: saveRuleDraft keeps the name
