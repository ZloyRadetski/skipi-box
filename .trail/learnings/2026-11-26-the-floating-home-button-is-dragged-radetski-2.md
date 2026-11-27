---
title: The floating Home button is dragged by a dotted grip and vibrates only on entering and sticking to a zone
author: Radetski
date: 2026-11-26
branch: main
cost: { minutes: 17, tokens: 32839 }
id: l-d37ac1b8
scope: repo
status: active
replaces: l-9fb934db
src: agent
---

## Decided
- A slim dotted grip on the start side is the only part that moves the capsule; the user wants the compact button to stay concise.
- No vibration when taking hold (the user asked to remove it). It vibrates each time the held capsule comes into reach of one of the eight zones, and again when it is let go there and sticks.
- Zone reach is 52 dp; the user asked for a bit more than 40. On a phone the top and bottom zones then nearly touch.
- It can be left anywhere on the screen outside the zones; the place is saved as a share of the free room, mirrored for right-to-left.
In the code: FloatingToolbarDock (SnapRadius), DragGrip, settings floatingButtonX / floatingButtonY.

## Tried and ruled out
- Eight fixed places only, dragging from anywhere on the capsule, and long press then drag: each replaced on the user's feedback.

## Watch out
- The default stays the bottom-end corner and the grip grows the capsule towards the start, so button coordinates in the desktop click tests hold there.
