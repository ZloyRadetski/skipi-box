---
title: The floating Home button rests anywhere and only sticks near the corners and side middles
author: Radetski
date: 2026-11-26
branch: main
cost: { minutes: 19, tokens: 233607 }
id: l-b99de3bb
scope: repo
status: active
src: agent
---

## Decided
- The user wants to put the floating button anywhere on the screen; the eight zones (four corners, four side middles) only attract it when it is dropped close to them. The centre is not a zone.
- It may rest over the header and the pinned panel, because "anywhere" was the requirement.
- The place is saved as a share of the free room on each axis, mirrored for right-to-left, so it survives other screen sizes and backups.
In the code: FloatingToolbarDock, ProxyHomeButtonPosition, settings floatingButtonX / floatingButtonY.

## Tried and ruled out
- Eight fixed places only: rejected by the user while it was being built.

## Watch out
- The default stays the bottom-end corner with the old margins; desktop click tests depend on those coordinates.
