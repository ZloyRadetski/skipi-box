---
title: OR and AND routing rules are stored as Shadowrocket lines and flattened to Xray rules; NOT is not supported
author: Radetski
date: 2026-11-03
branch: main
cost: { minutes: 30, tokens: 157830 }
id: l-22de5f22
scope: repo
status: active
src: agent
touches:
  - shared/core/src/commonMain/kotlin/features/config/ShadowrocketLogicalRuleEditing.kt
  - shared/core/src/commonTest/kotlin/features/config/ShadowrocketLogicalRuleTest.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/config/SkipiTrafficConfigRulesList.kt
  - shared/ui/src/commonMain/composeResources/values-ru/strings.xml
  - shared/ui/src/commonMain/composeResources/values/strings.xml
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/config/SkipiTrafficConfigLogicalConditionEditor.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/config/SkipiTrafficConfigRuleEditorForm.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/config/SkipiTrafficConfigRuleEditorScreens.kt
  - shared/ui/src/commonTest/kotlin/app/skipi/ui/config/TrafficConfigLogicalConditionEditorTest.kt
  - shared/core/src/commonMain/kotlin/engine/xray/TrafficProfileXrayPlanner.kt
---

## Decided
One routing rule can hold many conditions as a nested OR / AND tree, stored as a Shadowrocket line such as OR,((GEOSITE,google),(AND,((DOMAIN-SUFFIX,a.com),(DST-PORT,443)))),DIRECT. In the app it is one card and one editor tree. Xray gets the tree flattened to an OR of ANDs: each AND over different fields is one Xray rule, all sharing the outbound, and ORed single-field rules are merged into one list.

## Tried and ruled out
NOT: Xray has no inversion, so it is not modelled; NOT lines stay preserved text with a parse warning. A plain Xray-style list typed into one field (domain:x, geosite:y): ruled out by the user, because a bare domain means substring in Xray, not suffix.

## Watch out
AND of two conditions on one Xray field (two domains, two IP lists) cannot be expressed. The whole rule is then unsupported: still saved and exported, but skipped by the app, with a red warning on the card and in the editor. Never drop only the unsupported branch, that silently narrows an OR. More than 64 flattened rules is also rejected. The value of an OR/AND rule is the serialised body, so the old save path (type,value,policy) writes it back unchanged. Values inside OR/AND cannot contain commas or parentheses; the editor strips them. Not run in the real app, only unit tested.

In the code:
- shared/core/.../features/config/ShadowrocketLogicalRule.kt: tree and parser (depth capped at 16)
- shared/core/.../features/config/ShadowrocketLogicalRouteConversion.kt: flattening to Xray rules
- shared/ui/.../config/SkipiTrafficConfigLogicalConditionEditor.kt: editor tree (3 levels)
