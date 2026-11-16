---
title: "Every icon goes through one wrapper, so the Icon color setting reaches all of them; status colors stay"
author: Radetski
date: 2026-11-16
branch: main
cost: { minutes: 27, tokens: 700100 }
id: l-00938463
scope: repo
status: active
src: agent
touches:
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/SkipiSettingIcon.kt
---

## Decided
All icons are drawn through SkipiIcon (SkipiContentIcon on Material surfaces). Its color is, in order: an explicit tint (a status or brand color, never repainted), the color the user picked, a state color the screen computes (selected, expanded), the on-accent color, the theme's own icon color. A plain theme has no theme icon color, so its icons keep the host's default look. The four named themes carry their own icon color (palette field `icon`, chosen for 3:1 contrast on background and surface). The picked color applies to every icon including those on accent fills, as chosen, with no contrast fixing. Connection state icons (power button, connecting hourglass, ping signal) and status, protocol and permission colors stay as they are. The storage field and backup format keep the old name; only labels and the picker target were renamed.

## Tried and ruled out
Letting a picked color override every explicit tint: it would repaint status colors. Giving disabled icons the picked color: they would look enabled, so a disabled icon passes its own disabled tint. Making the on-accent flag depend on a named theme: in plain themes it left the host default on an accent fill, a regression.

## Watch out
A guard test fails if any screen imports the library Icon directly. Icons the app draws on a Canvas are not covered by it; the decorative ones use skipiDrawnIconColor, the status ones keep their color. Named-theme tests compare AppColors for equality, so adding a field to AppColors needs the expected values updated (the desktop theme contract test failed once for exactly this). The Android module was not compiled in this session.

In the code: shared/ui .../components/SkipiIcon.kt (resolveIconTint, resolveDrawnIconColor), theme/NamedThemePalette.kt and AppColors.kt (icon), settings/SkipiSettingIcon.kt (LocalIconColor, ProvideIconColor), host roots App.kt and DesktopVisualTheme.kt, guard test desktopApp/src/test/.../IconColorGuardTest.kt.
