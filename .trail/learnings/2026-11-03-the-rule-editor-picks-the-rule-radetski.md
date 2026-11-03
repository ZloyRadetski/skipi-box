---
title: "The rule editor picks the rule type from the typed value, but only for unambiguous input"
author: Radetski
date: 2026-11-03
branch: main
cost: { minutes: 2, tokens: 29905 }
id: l-a36cbb3d
scope: repo
status: active
src: agent
touches:
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/config/SkipiTrafficConfigRuleEditorForm.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/config/TrafficConfigRuleIntent.kt
  - shared/ui/src/commonTest/kotlin/app/skipi/ui/config/TrafficConfigRuleIntentTest.kt
---

## Decided
Typing a prefix such as keyword:google into the rule value field switches the type and strips the prefix. Unprefixed input changes the type only for an IP or CIDR, an http(s) link, or a value with a star. Geosite and ext values go to the domain-set type and keep their prefix, because that type stores the Xray form.

## Tried and ruled out
Detecting bare numbers as ports and bare tcp/udp as network: a keyword like 1 would flip a type the user chose by hand.

## Watch out
Detection runs inside the text field's input transformation and rewrites the field there, so the typed prefix never shows. Only the traffic-config rule editor has it. The Xray route rule editor already takes Xray prefixes directly and has no type picker. Not run in the real app, only unit tested.

In the code:
- shared/ui/src/commonMain/kotlin/app/skipi/ui/config/TrafficConfigRuleIntent.kt: detectTrafficConfigRuleIntent
- shared/ui/src/commonMain/kotlin/app/skipi/ui/config/SkipiTrafficConfigRuleEditorForm.kt: RuleValueField
