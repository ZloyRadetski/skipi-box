---
title: "When a bundled file stops shipping, search the whole repo for its name, since packaging checks live in several places"
author: Radetski
date: 2026-11-27
branch: main
cost: { minutes: 1, tokens: 20689 }
id: l-6b92ecb8
scope: repo
status: active
src: agent
touches:
  - desktopApp/packaging/tests/verify-listing-test.sh
  - desktopApp/packaging/verify-listing.sh
  - desktopApp/packaging/linux/build-appimage.sh
  - desktopApp/packaging/tests/build-appimage-test.sh
---

## Decided
After a file leaves the desktop packages, search every tracked file for its name before starting a release run. The AppImage build script has its own presence checks, separate from the shared package listing check and from the workflow's inline lists, and now has its own test in CI.

Why: the release run failed twice in a row on the same removed hev library. The first fix updated only the shared listing check, found by searching for that check's callers, so the AppImage script kept requiring the file and Linux failed again.

In the code: desktopApp/packaging/linux/build-appimage.sh, desktopApp/packaging/tests/build-appimage-test.sh, desktopApp/packaging/verify-listing.sh, .github/workflows.

## Tried and ruled out
Fixing only the check named by the first failure: the later checks in the same job never ran, so one failed run shows only the first stale one.

## Watch out
Job logs of this repo need a signed-in GitHub client; without one only the failed step names are visible.
