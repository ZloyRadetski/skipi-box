---
title: "SKIPI Core's Android AAR can be built and used locally before a release; the desktop DLL cannot without gcc"
author: Radetski
date: 2026-11-19
branch: main
cost: { minutes: 3, tokens: 20764 }
id: l-0db47ab9
scope: repo
status: active
src: agent
touches:
  - settings.gradle.kts
---

## Decided
- To develop app changes against an unreleased Core, build the AAR with gomobile (it uses the Android NDK's clang, so no gcc is needed), put it at ../skipi-core/skipicore.aar and write the version to ../skipi-core/skipicore.aar.version; settings.gradle.kts then prefers it over the GitHub release. Both files are git-ignored in skipi-core.
- Bumping SKIPI_CORE_VERSION before the Core tag exists means CI and other machines cannot build skipi-box until that release is published.
- After the Core was split into a root facade and internal/ packages, the bound Java API was checked by building the AAR and reading classes.jar with javap; the public_api.golden test in skipi-core pins the exported Go API.

## Watch out
- The desktop c-shared library (cmd/skipicore-desktop) needs cgo with gcc (MSYS2 UCRT64 on Windows); without it only CI can build and test the C exports, and `-race` cannot run either.

In the code: ANDROID_NDK_HOME=<sdk>/ndk/28.2.13676358, CGO_LDFLAGS="-Wl,-z,max-page-size=16384", `gomobile bind -target=android -androidapi 24 -javapkg=app.skipi.core -ldflags=-checklinkname=0 -o skipicore.aar .` (about 30 s for arm64, longer for all four ABIs); buildSrc BuildConfig.kt SKIPI_CORE_VERSION.
