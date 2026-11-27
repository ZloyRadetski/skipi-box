---
title: "The floating Home button is moved only after a long press, rests anywhere and sticks near corners and side middles"
author: Radetski
date: 2026-11-26
branch: main
cost: { minutes: 14, tokens: 50486 }
id: l-890c5dc0
scope: repo
status: active
replaces: l-b99de3bb
src: agent
---

## Decided
- Picking the capsule up needs a long press, confirmed by a vibration once it is ready; touching or sliding on its buttons must not move it (the user's correction).
- Holding the ping button keeps meaning "ping everything", so a move cannot start from it while it has that long press; it starts from the connect button or the capsule itself.
- It can be left anywhere on the screen, header included; the four corners and four side middles only attract it when it is dropped close. The place is saved as a share of the free room, mirrored for right-to-left.
In the code: FloatingToolbarDock, ProxyHomeButtonPosition, settings floatingButtonX / floatingButtonY.

## Tried and ruled out
- Eight fixed places only: rejected by the user while it was being built.
- Dragging as soon as the finger moves: on a phone the capsule started moving when its buttons were pressed.

## Watch out
- After the pickup the drag consumes touches before the buttons see them, so releasing never clicks.
- The default stays the bottom-end corner; desktop click tests depend on those coordinates.
