---
title: "The 24 user-picked colors are converted between settings models through one CustomColors value, not field by field"
author: Radetski
date: 2026-11-17
branch: main
cost: { minutes: 29, tokens: 114335 }
id: l-7de04ce5
scope: repo
status: active
src: agent
---

## Decided
The AppState, backup, persisted record, settings-screen state, Android chrome state and Desktop settings each store the 24 picked colors flat. Each of them now has customColors() and withCustomColors(); every cross-model mapper copies the colors in one line. Persistence (Room, prefs, settings.json, backup JSON) is untouched, so old data still reads.

## Tried and ruled out
Nesting CustomColors inside every model at once: ~900 uses in 30 files plus tests and a persisted-format migration, for the same dedupe of the mappers. A shared interface for reading: it only helps one direction, copy() still has to be written per model.

## Watch out
A new color needs a field in CustomColors and one line in each model's pair; CustomColorsConversionTest pushes 24 distinct values through the whole chain, so a model that forgets one fails it. The legacy category-icon fallback chain in the backup restore is kept outside the pair on purpose.

In the code: shared/core app/CustomColors.kt, the *CustomColors.kt next to each model, desktopApp CustomColorsConversionTest.
