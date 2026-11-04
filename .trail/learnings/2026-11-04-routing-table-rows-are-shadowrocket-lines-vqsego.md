---
title: "The routing table shows each rule as a v2rayN-style row but stores it as an ordinary profile line"
author: VQSeGo
date: 2026-11-04
branch: main
id: l-routing-table-rows
scope: repo
status: active
src: agent
touches:
  - shared/core/src/commonMain/kotlin/features/config/RoutingTableRow.kt
  - shared/core/src/commonMain/kotlin/features/config/RoutingTable.kt
  - shared/core/src/commonMain/kotlin/features/config/V2rayRoutingImport.kt
  - shared/core/src/commonMain/kotlin/features/config/ShadowrocketLogicalRouteConversion.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/config/SkipiTrafficConfigRoutingTable.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/config/DesktopTrafficConfigRulesEditor.kt
---

## Decided
A row is domains, addresses and processes (any of them matches) AND port AND network AND protocol, as in v2rayN. It is written as a plain line, or an OR line for one field, or an AND over the fields. Shadowrocket stays the storage format. A rule that is switched off keeps its place and carries the option skipi-disabled, which every Xray conversion ignores. Alternatives that differ in one list field fold into one Xray rule, so a long list is not capped at 64. Windows 900 dp wide or more show the table, narrower ones keep the cards.

## Tried and ruled out
Changing the old v2rayNG profile import to OR: v2rayNG puts domain and ip in one Xray rule, which is AND, so that import is right for it. Only the table import follows v2rayN. A row with no condition cannot be stored, so a new rule stays a draft until it has one.

## Watch out
Values with a comma or a parenthesis (a regexp with a group) cannot be written into a line; they are left out and the screen says so. Processes do not work reliably with TUN, from reading the code, not tried. Drag to reorder is missing, there are Up and Down buttons. Not run in the real app, only tests and rendered screenshots; strings exist in English and Russian.
