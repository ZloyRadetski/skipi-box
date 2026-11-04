---
title: "The OR/AND rule editor speaks in any-of and all-of with joiner words, not OR and AND"
author: Radetski
date: 2026-11-04
branch: main
cost: { minutes: 5, tokens: 60108 }
id: l-238bfd31
scope: repo
status: active
src: agent
touches:
  - shared/core/src/commonMain/kotlin/features/config/ShadowrocketLogicalRuleEditing.kt
  - shared/core/src/commonTest/kotlin/features/config/ShadowrocketLogicalRuleTest.kt
  - shared/ui/src/commonMain/composeResources/values-ru/strings.xml
  - shared/ui/src/commonMain/composeResources/values/strings.xml
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/config/SkipiTrafficConfigLogicalConditionEditor.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/config/SkipiTrafficConfigRuleEditorForm.kt
---

## Decided
Each group in the multi-condition editor starts with a plain choice, "any of" or "all of", with one sentence explaining it, and the conditions are joined by a visible word (OR or AND in the user's language). A nested group gets a coloured side bar and starts with the opposite mode of its parent. The bottom card reads the whole rule as one line. The type dropdown shows friendly names for OR and AND, and the switch at the top and the dropdown change the same type.

## Tried and ruled out
Nothing dropped. The stored format and the parser are unchanged, only the editor.

## Watch out
The new look was never seen in a running app, only compiled and unit tested (the one-line reading function is tested in core). Strings exist in English and Russian only; fa and zh fall back to English.

In the code:
- shared/ui/.../config/SkipiTrafficConfigLogicalConditionEditor.kt
- shared/core/.../features/config/ShadowrocketLogicalRuleEditing.kt: readableSummary
