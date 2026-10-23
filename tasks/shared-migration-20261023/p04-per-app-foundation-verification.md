# P04 — Per-App shared foundation verification

23 October 2026. This is a foundation-only checkpoint; P04 remains open for the shared Per-App editor and host-specific inventory boundary described in [the P04 research](p04-remaining-per-app-research.md).

## Read-only source review

The clipboard codec moved from the Android app module into [`shared/core`](../../shared/core/src/commonMain/kotlin/features/proxy/app/usecase/ProxyAppListClipboardCodec.kt) without changing its package or fully qualified names. A direct comparison with the captured [Android baseline source](../../outputs/p04-per-app-android-oracle-baseline-reports-20261023/ProxyAppListClipboardCodec.android-baseline.kt) shows only the five intended `internal` → public visibility changes: the two result/data types and the encode, decode, and import-application functions. The parsing, mode handling, ordering, filtering, normalization, and error paths are otherwise source-identical.

The Android app already depends on `shared:core`. Its UI adapter retains the app-specific `ProxyAppListItem` overload and forwards `item.selectionKeys` unchanged to the common primitive-list helper in [`ProxyAppListSelection.kt`](../../shared/core/src/commonMain/kotlin/features/proxy/app/ProxyAppListSelection.kt). This keeps Android package and shared-UID models in the app module while making the selection transform portable. Checking appends every selection key once in order and retains configured values not represented by installed rows. Unchecking removes all supplied selection keys and preserves other configured IDs in order, including legacy IDs. The Android wrapper's signature and call site remain in [`ProxyAppListPageState.kt`](../../app/src/main/kotlin/features/proxy/app/ProxyAppListPageState.kt) and [`ProxyAppListPage.kt`](../../app/src/main/kotlin/features/proxy/app/ProxyAppListPage.kt).

I found no required source-equivalence, module-boundary, checked/uncheck, or unknown-key issue in this foundation diff. This review was read-only; no build or test commands were run here.

## Tests-first evidence and current gate

Before the shared production API was added, the Android oracle baseline completed **11/11 GREEN**: two selection tests and nine clipboard codec tests. The report includes the captured source and XML results: [Android baseline counts](../../outputs/p04-per-app-android-oracle-baseline-reports-20261023/counts.json) and [baseline log](../../outputs/p04-per-app-android-oracle-baseline-20261023.log).

The new common tests then produced the expected API RED: `compileTestKotlinDesktop` failed in four seconds on the missing common selection and clipboard APIs, with zero tests executed. Evidence: [API RED counts](../../outputs/p04-per-app-shared-api-red-reports-20261023/counts.json) and [compiler errors](../../outputs/p04-per-app-shared-api-red-reports-20261023/compiler-errors.txt).

The post-implementation scoped GREEN gate completed in **30 seconds**: shared core Desktop **372/372**, shared core Android **316/316**, Android oracle **11/11**, and the new actual More → UI Edit route case **1/1**. Totals: **700/700**, failures/errors/skips 0. The Android debug APK task also passed (`assembleDebug`; no installation). Evidence and source hashes are in [GREEN counts](../../outputs/p04-per-app-foundation-green-reports-20261023/counts.json). The Android oracle test sources match the baseline source hashes; the two common-test source hashes match their API RED evidence.

The collection class was filtered to the new route case only; its other 26 cases were not rerun in this gate and remain covered by the earlier GREEN result. This scoped run does not replace the prior full P03 inventory of 2,751 checks. It also does not claim that Per-App UI, Desktop package inventory, or VPN enforcement has been migrated; P04 remains open.

The source baseline took 26 seconds before the move. These tests operate on strings/lists and isolated configuration files; OS clipboard reads/writes, installed-package enumeration, Android VPN policy, and physical-device UI were not executed. The Desktop route case opens inactive profile 7 from More → UI Edit, verifies its Basics name, reads the unchanged temporary JSON library, and requires zero transforms while sibling 3 remains selected. It characterizes existing behavior; no route implementation change or behavioral RED was needed.
