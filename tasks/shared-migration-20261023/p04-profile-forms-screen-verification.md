# P04 — profile forms screen verification

23 October 2026. This report records the Desktop Basics/forms screen gate and the test-only corrections made to make the raw editor capture prove a real scroll. P04 remains open: this representative forms matrix does not implement the Per-App editor. See [P04 remaining per-app research](p04-remaining-per-app-research.md).

## Covered behavior

The integration test opens the inactive target profile through its card's public UI Edit action, which lands on Basics. It covers Basics Back and Basics-to-General navigation, plus Save and toolbar Back from each of General, DNS, Tunnel, and Network. Each detail interaction changes one public switch, verifies the saved target row, and checks that the screen returns to Basics.

| Screen or action | Representative behavior checked |
| --- | --- |
| Basics toolbar Back | Edited profile name is persisted to the temporary config JSON and shown after returning to the collection. |
| Basics → General | An edited name is committed before General opens. |
| General | IPv6 switch persists `ipv6 = true`; existing `prefer-ipv6` and other represented settings are checked. |
| DNS | Local DNS switch persists `vpn-local-dns = true`; direct DNS and other represented settings are checked. |
| Tunnel | Sniffing switch persists `sniffing = true`; mux concurrency and other represented settings are checked. |
| Network | Network activation switch persists enabled state and the existing Wi-Fi transport. |
| Detail Save and toolbar Back | Each of the four detail edits is exercised through both actions; each returns to Basics with the edit saved. |
| Raw editor rendering | Metadata is captured before scrolling; after a verified scroll, the metadata name is outside the viewport and the raw `.conf` field remains intersecting the viewport and exposes public SetText semantics. |

The fixture stores an inactive target profile ID 7 beside selected sibling ID 3. Each persistence path reloads the actual temporary `configs.json` and checks target-only change, unchanged selection, profile order `[3, 7]`, next ID 29, preserved timestamp/source URL/update lock, byte-for-byte preservation of the sibling profile, representative update/resource metadata, and unrelated `[Proxy]`, `[Rule]`, and `[Vendor]` document content. Each screen gets an isolated temporary directory and the test deletes it after use.

Rendering covers Basics, General, DNS, Tunnel, and Network at **390×844** and **1100×800**, plus Raw metadata and Raw document at both sizes: fourteen PNGs total. The baseline images are in `desktopApp/build/reports/desktop-traffic-config-profile-forms`; the copied gate evidence is under [the baseline report directory](../../outputs/p04-profile-forms-screen-reports-20261023/).

This is representative widget and viewport coverage, not exhaustive interaction over every field. The raw `.conf` document is intentionally too large to require full-field visibility: the test checks real scroll progress, that the name field has left the viewport, and that editable raw content remains reachable. These are rendered `ImageComposeScene` tests, not physical desktop input, OS file picker, clipboard, VPN, or package-inventory coverage.

## Baseline and harness corrections

The first draft failed to compile in 19 seconds with three test-harness errors and executed no tests. Two `requireNotNull` calls passed an eager `String` instead of the lazy message lambda, and a pointer event passed `PointerButtons` positionally into the `scrollDelta` parameter. Those two message arguments and the named pointer-event argument were corrected; no expectations or production code changed. The compile evidence is [the initial baseline log](../../outputs/p04-profile-forms-screen-baseline-20261023.log).

The corrected original matrix completed **6/6 GREEN** in 96 seconds, with 10 Save/Back/navigation interactions and 14 PNG outputs. That result established persistence and render-harness coverage, but was not final visual acceptance: the two Raw metadata/document PNGs at each size were byte-identical. Root inspected all fourteen images and found that after DNS scrolling, form content showed through the transparent toolbar at both viewport sizes.

The test then gained one behavioral raster regression, `dnsToolbarBackgroundDoesNotChangeWhenTheFormScrolls`. At both viewport sizes it opens the real UI Edit → DNS route, waits for a settled render, samples background pixels at `y=6` and `x=20`, `width/2`, and `width-20`, scrolls through the public semantics actions until the DNS Direct and Hosts sections are reached, settles again, and requires the same sampled colors. The assertion has no golden-image dependency and checks the toolbar's empty background points rather than its text or icons.

The first regression run was **7 tests, 2 failures, 15 seconds**. The toolbar assertion failed with changed pixels at 390×844. The raw-capture assertion also showed that a scroll had not been demonstrated. The next run was **2/2 failures, 8 seconds**: the toolbar remained RED, and the raw scroll position had moved but the asynchronous scroll had not yet completed far enough for the metadata-name visibility assertion.

Only the raw test harness was then adjusted. It now finds the raw field's scrollable parent with remaining vertical range, requests `height × 0.65`, computes `target = min(initial position + requested delta, initial maximum)`, and polls with render/frame delay for up to three seconds until it reaches the target within one pixel. It separately requires positive movement, metadata-name absence from the viewport, and raw editable content still reachable. Failure diagnostics include before, target, after, and maximum positions. The persistence assertions, toolbar regression, and production source were unchanged.

## Final pre-fix RED and remaining gate

The final pre-production run completed **2 tests, 1 failure, 12 seconds**. The raw metadata/document case passed at both viewport sizes after the bounded target wait. The DNS toolbar pixel regression failed at 390×844: the three sampled colors changed from `[-657672, -657672, -657672]` to `[-1, -9539984, -1]`. The test loop stopped at the narrow failure, so this run did not execute the new assertion at 1100×800; root had independently observed the same transparent-toolbar defect in the existing wide DNS PNG. Evidence: [final RED counts](../../outputs/p04-toolbar-opacity-final-red-reports-20261023/counts.json), [final RED log](../../outputs/p04-toolbar-opacity-final-red-20261023.log). The RED test source SHA-256 is `fa9644728cf9010a168b406cdd7594b7f3b160cf451f332f2164acf5a1631ae7`.

## Completed correction and GREEN

Only `SkipiTrafficConfigFullScreenScaffold` now passes `color = SkipiTheme.colors.background` to its existing `AdaptiveTopAppBar`. That is the same theme color already used by the scaffold container and content Box. The global app-bar default, layout, insets, scroll behavior, controls, profile state, Save and Back callbacks are unchanged. Android and Desktop consume the same corrected shell. Independent source review and root review found no required issue.

The post-fix command completed **275/275 GREEN**, failures/errors/skips 0, **46 seconds**, plus Android debug APK assembly. Shared UI Desktop **120**, shared UI Android **120**, Desktop screen/render cases **35**: collection **26**, profile forms **7**, Rules responsive renderer **1**, ProxyGroups responsive renderer **1**. Fresh XML timestamps span `2026-10-23T05:32:54Z`–`05:33:26Z`. [GREEN XML/counts and fourteen final PNGs](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/outputs/p04-toolbar-opacity-green-reports-20261023/counts.json). The form test source SHA is identical to the final RED: `fa9644728cf9010a168b406cdd7594b7f3b160cf451f332f2164acf5a1631ae7`.

Root inspected all fourteen original PNGs, then inspected the four changed final images: DNS and the genuinely scrolled raw document at each viewport size. The other ten final PNGs are byte-identical to the already inspected baseline images. Both final DNS panels keep their title/Back/Save readable without body content showing through. Raw metadata and document captures now differ and show actual scrolling while preserving a readable toolbar. This completes raster QA of the scoped forms/raw matrix; it is not physical Android/Desktop window testing or a new inspection of every Rules/Groups PNG.

Command: offline `:shared:ui:allTests :desktopApp:test` with the four class filters above, followed by `:app:assembleDebug`; JDK 26, Android test JDK 21, native HevTun build/sync tasks excluded. Log: `outputs/p04-toolbar-opacity-green-20261023.log`. This is a scoped gate; the previous full P03 2751 checkpoint remains the latest full cross-module/Core inventory. No APK installation or user-app restart was performed.

P04 remains open for the source-backed shared Per-App editor/state and its Desktop entry point. The forms use the direct card UI Edit action. A subsequent [foundation gate](p04-per-app-foundation-verification.md) also verifies actual More → UI Edit in 1/1 GREEN with no route implementation change. The Per-App editor remains outstanding.
