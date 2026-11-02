---
title: "Dropdowns: keep both Miuix hosts (overlay and window), and leave the home screen menus on their custom Material3 style for now"
author: Radetski
date: 2026-11-02
branch: main
cost: { minutes: 8, tokens: 78522 }
id: l-121b6509
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/main/kotlin/app/skipi/desktop/config/DesktopConfigsScreen.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/settings/DesktopSettingsScreen.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/routing/RouteRuleOutboundChoice.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/routing/SkipiRouteRuleCard.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/routing/SkipiRouteRuleEditorForm.kt
  - shared/ui/src/commonTest/kotlin/app/skipi/ui/routing/RouteRuleOutboundChoiceTest.kt
---

## Decided
- Selecting a value in a form goes through the app wrappers over Miuix. The overlay and window variants stay separate: overlay renders in the root scaffold, window is used inside sheets, dialogs and the config editor screens. The layer cannot be detected reliably, so there is no auto-choosing wrapper.
- Raw Material3 dropdown menus and raw Miuix preferences outside the wrappers were replaced (route rule outbound picker, domain strategy card).
- Commits in this repo carry no AI attribution line (skipi-dev rule).

## Tried and ruled out
- Merging overlay and window into one component that picks itself from context.
- Moving the home screen menus to the cascading popup in the same pass: they have nested animated navigation, accent check marks and custom item colors, and popup positioning near the server card and group chip cannot be checked without running the UI.

## Watch out
- A route rule can point at a removed outbound tag; the picker must keep showing that raw tag as selected.
- shared/ui has no Compose UI tests, only logic tests in commonTest.

In the code: shared/ui/.../components/AppMiuixWindows.kt, routing/RouteRuleOutboundChoice.kt, home/SkipiHomeDropdownMenu.kt
