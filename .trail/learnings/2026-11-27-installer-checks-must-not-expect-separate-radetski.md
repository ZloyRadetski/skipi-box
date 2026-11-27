---
title: "Installer checks must not expect separate hev files, because SKIPI Core carries hev"
author: Radetski
date: 2026-11-27
branch: main
cost: { minutes: 1, tokens: 43635 }
id: l-7976c6d8
scope: repo
status: active
src: agent
touches:
  - app/src/main/kotlin/engine/xray/XrayOutboundPlanner.kt
  - app/src/test/kotlin/engine/xray/XrayConfigTest.kt
  - app/src/test/kotlin/features/config/TrafficConfigProxyGroupPersistenceTest.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/config/DesktopTrafficConfigCollectionScreenIntegrationTest.kt
  - shared/app/src/commonMain/kotlin/app/skipi/app/proxy/ConfigProxyGroupReconciler.kt
  - shared/app/src/commonMain/kotlin/app/skipi/app/proxy/XrayOutboundPlanner.kt
  - shared/app/src/commonTest/kotlin/app/skipi/app/proxy/ConfigProxyGroupReconcilerTest.kt
  - shared/app/src/commonTest/kotlin/app/skipi/app/proxy/XrayOutboundPlannerTest.kt
  - shared/app/src/commonTest/kotlin/app/skipi/app/server/ProxyServerDraftFactoryTest.kt
  - shared/app/src/desktopTest/kotlin/data/backup/BackupFixtureCompatibilityTest.kt
---

## Decided
The desktop package check lists only the app's own files, the Core library and the geo data. hev is linked into the Core on Linux and embedded in it on Windows and macOS; the Core's release workflow is what guarantees it is there.

Why: the first desktop release run after hev moved into the Core failed on Windows and Linux. The workflow's inline file lists had been updated, the shared check script and its tests had not, and the tests kept passing in CI because they agreed with the stale script.

In the code: desktopApp/packaging/verify-listing.sh and its test; skipi-box release workflow; skipi-core build-desktop.yml.

## Watch out
The script only runs against real packages in the release workflow, so a change to what ships shows up there first. Check it when adding or removing a bundled file.
