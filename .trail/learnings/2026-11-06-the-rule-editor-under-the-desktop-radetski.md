---
title: "The rule editor under the Desktop routing table is resized by dragging its top edge, and the size is not saved"
author: Radetski
date: 2026-11-06
branch: main
cost: { minutes: 22, tokens: 107873 }
id: l-5b804ece
scope: repo
status: active
src: agent
touches:
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/config/SkipiTrafficConfigRoutingTable.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiPaneDivider.kt
  - buildSrc/src/main/kotlin/BuildConfig.kt
---

## Decided
"Bottom panel in routing settings" was read as the rule editor under the Desktop routing table, which had a fixed height. A handle above it resizes it between 160 dp and 80% of the window; the height is read while measuring so a drag relayouts only the editor. The size resets when the table is reopened.

## Tried and ruled out
A resize cursor: common Compose has no pointer icon for it, so only a thin line shows on hover (same as the Home divider).

## Watch out
- The table is zoomed through a replaced LocalDensity, so drag deltas and limits are in zoomed dp; the handle must stay inside that provider.
- The request was ambiguous; if the user meant another panel, this is the one to revisit.

In the code: SkipiTrafficConfigRoutingTable.kt (RoutingTableEditor, editorExtra), SkipiPaneDivider.kt (SkipiHorizontalPaneDivider)
