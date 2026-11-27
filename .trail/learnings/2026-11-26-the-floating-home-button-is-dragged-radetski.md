---
title: "The floating Home button is dragged only by a dotted grip, rests anywhere and sticks near corners and side middles"
author: Radetski
date: 2026-11-26
branch: main
cost: { minutes: 9, tokens: 20549 }
id: l-9fb934db
scope: repo
status: active
replaces: l-890c5dc0
src: agent
---

## Decided
- A slim dotted grip on the start side is the only part that moves the capsule; it drags at once, with a vibration as it comes loose. The user wants the compact button to stay concise.
- Touching, holding or sliding on its buttons never moves it; holding the ping button still pings everything.
- It can be left anywhere on the screen, header included; the four corners and four side middles only attract it when it is dropped close. The place is saved as a share of the free room, mirrored for right-to-left.
In the code: FloatingToolbarDock, DragGrip, ProxyHomeButtonPosition, settings floatingButtonX / floatingButtonY.

## Tried and ruled out
- Eight fixed places only: rejected by the user while it was being built.
- Dragging from anywhere on the capsule as soon as the finger moves: it moved when its buttons were pressed.
- Long press then drag with a vibration: built, then replaced by the grip on the user's choice.

## Watch out
- The default stays the bottom-end corner and the grip grows the capsule towards the start, so button coordinates in the desktop click tests hold there.
