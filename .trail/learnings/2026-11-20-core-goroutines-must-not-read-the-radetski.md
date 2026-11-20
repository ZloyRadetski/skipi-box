---
title: Core goroutines must not read the test-swapped package variables; go test -race only runs in CI
author: Radetski
date: 2026-11-20
branch: main
cost: { minutes: 10, tokens: 51910 }
id: l-2d29810a
scope: repo
status: active
src: agent
touches:
  - buildSrc/src/main/kotlin/BuildConfig.kt
  - ".github/workflows/skipi-version-platform.yml"
  - desktopApp/build.gradle.kts
---

## Decided
- Goroutines that outlive a call (the hev watcher, the config parse in StartSession) get the seam values (hevTun, poll interval, loadXrayConfig) as arguments read by the caller. Otherwise a test that swaps the variable in its cleanup races with the previous test's goroutine, and CI fails with DATA RACE.

## Watch out
- The Windows dev machine has no gcc, so go test -race cannot run locally. v1.0.36 was tagged and failed in CI because of this, and v1.0.37 replaced it. Run the race detector on Linux or WSL before tagging a Core release.
- A control func called by Go's resolver runs from parallel goroutines (A and AAAA queries); count calls with atomics.

In the code: skipi-core session_hev.go watchHev, session.go StartSession, hev_session_test.go fakeHev.
