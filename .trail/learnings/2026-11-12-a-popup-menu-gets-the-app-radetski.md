---
title: "A popup menu gets the app's font only if it is handed the text style; the menu opens toward the middle of the window"
author: Radetski
date: 2026-11-12
branch: main
cost: { minutes: 25, tokens: 261270 }
id: l-b1dcce38
scope: repo
status: active
src: agent
---

## Decided
- SkipiMenuPopup passes the app's own text style (MiuixTheme.textStyles.main, color cleared) to its rows with ProvideTextStyle, and the row label merges the 13 sp size onto it. A Popup is not under a Material surface, so a Material3 Text in it reads the library default style, not the font from the appearance settings.
- The menu's side follows the anchor: a card wider than the menu opens it at the card's trailing end (where the card's "more" button is), a button opens it toward the middle of the window.

## Watch out
- The style's own color must stay cleared, or it overrides the dimmed, muted and accent colors rows set through LocalContentColor.

In the code: shared/ui/.../components/SkipiMenuPopup.kt (skipiMenuPosition, menuTextStyle), SkipiMenuPositionTest.kt
