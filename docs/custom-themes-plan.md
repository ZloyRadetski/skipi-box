# Custom colour themes: plan

Goal: the user can save their own colour themes (the existing colours of the Appearance screen), pick them
later, and share one as a short `skipi://theme/...` link or by pasting a code.

## What already exists
- `AppearanceSettings` has the override layer (`customColorsEnabled`, accent, background, surface, surface
  variant, text, secondary text) applied by `applyAppearanceSettings` on both platforms.
- `ColorPickerDialog` and the colour rows are in `SkipiAppearanceSettingsScreen` (shared/ui).
- `skipi://` links are parsed in shared/core (`SkipiDeepLink`) and gated by `SkipiDeepLinkPolicy`.

## Design
A custom theme is a saved set of the override colours: name, light or dark base, six colours.
Applying one writes the existing override fields (`customColorsEnabled = true` plus the six colours, base
Light or Dark). No new `ThemeMode`, no change in how either platform draws a theme.

The shared form is compact: a version byte, a flags byte (dark base), six colours as 3 bytes RGB (alpha is
always opaque), then the UTF-8 name cut to 24 characters. Base64url without padding, so about 40-60 characters:
`skipi://theme/<payload>`. The same code is used to store the library (one string with a code per line), so a
library needs one new string field per platform instead of a new structure.

## Slices (each: tests first, RED, then GREEN, then commit)
1. **Codec in shared/core** (`CustomThemeShare`): encode/decode, bounds, bad input, name rules.
2. **Deep link**: `SkipiDeepLink.Theme`, parsed from `skipi://theme/<payload>`; gate = needs confirmation;
   confirmation text shows the theme name instead of a host.
3. **Library model in shared/app**: add/rename/delete/apply/duplicate on a list of themes, plus the
   mapping of "apply" to `AppearanceSettings`; contrast check for text on background and on accent.
4. **Persistence**: one new string field (Desktop settings JSON, Android preferences, backup mapper), default
   empty so old files load.
5. **UI in the Appearance screen**: "My themes" list, save current colours as a theme, rename, delete,
   share (copy link), import (paste code or link). Strings in en and ru.
6. **Hosts**: Desktop launch/deep link handler and Android `MainActivity` handle `SkipiDeepLink.Theme` with
   the confirmation dialog.

## Out of scope
Layout customisation; status, ping and protocol colours in a shared theme (they stay local overrides);
a theme store or hosting.
