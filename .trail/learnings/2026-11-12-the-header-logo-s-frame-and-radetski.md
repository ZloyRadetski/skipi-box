---
title: "The header logo's frame and dots follow the theme accent; only the glyph follows the text color"
author: Radetski
date: 2026-11-12
branch: main
cost: { minutes: 1, tokens: 1280 }
id: l-c4e8024c
scope: repo
status: active
src: agent
---

## Decided
The Home header logo is two layers: the glyph in the text color and a second layer (frame plus two dots) in the theme accent, because the user asked for exactly that after a plan-first discussion.

## Tried and ruled out
Tinting the whole logo with the accent, or mixing accent into the glyph: not chosen because it changes more than the user asked for, and the glyph must stay readable on any background.

## Watch out
- The "frame" is the first path of the dots drawable, so tinting that one layer colors frame and dots together; there is no separate frame layer.
- Not checked on a device or screenshot, only compiled. An accent close to the background (AMOLED, custom colors, Material You) may make the logo hard to read.
- The tap-tap-boom easter egg keeps its own colors and was left alone.

In the code: SkipiHeaderMark in shared/ui/.../home/SkipiProxyHomeHeader.kt; ic_skipi_mark_dots.xml.
