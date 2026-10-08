# Home Store Ordering and Performance Polish Report

**Task:** `03-home-store-ordering`  
**Role:** Common Home state ordering and performance implementer  
**Report Date:** 2026-09-30  
**Status:** Implementation Complete / Execution Unverified (Sandbox Tool Connection Reset)

---

## 1. Summary

This task resolves the name-sorting regression and performance bottlenecks in the shared Home store (`ProxyHomeStore`) and its host adapters across Android and Desktop:

1. **Preserved Name-Sort Semantics & Eliminated Flag-Stripped Reordering:**
   - On Android and Desktop, server remarks typically begin with a country flag emoji or indicator (e.g. `"🇺🇸 Alpha"`). The presentation mappers extract the flag and strip it from the display `title` (`"Alpha"`).
   - In Unicode character ordering, standard ASCII characters (e.g. `'Z'` = 90) precede regional indicator symbols (e.g. `'🇺'` = 127482). Android host sorting canonically orders `"Zulu"` before `"🇺🇸 Alpha"`.
   - Previously, after receiving pre-sorted host input, `ProxyHomeStore` performed a second sort by stripped display `title` (`"Alpha"` before `"Zulu"`), reversing the host's authoritative order.
   - We introduced an optional, backward-compatible `sortKey: String? = null` field on `ProxyServerSummary`, populated with the raw canonical remark (`info.remarks.ifBlank { title }`) by both Android (`ProxyHomeAndroidInput.kt`) and Desktop (`DesktopProxyHome.kt`).
   - `ProxyHomeStore` now sorts by `(server.sortKey ?: server.title)` case-insensitively using stable ordering, ensuring canonical ordering is identical before and after UI mapping.

2. **Per-Snapshot Page Construction Complexity Reduced from $O(G \times M \log M)$ to $O(M \log M + G)$:**
   - Previously, for every group in `source.groups`, `createUiState` performed an independent filter across all servers, an independent search filter, and an independent $O(M \log M)$ sort. On frequent snapshot updates (such as latency updates or progress updates), this caused $G$ redundant sorts and repeated list scans.
   - We refactored page construction:
     1. Search filtering is performed globally once across all servers: $O(M)$.
     2. Sorting is performed globally once across matching servers using stable TimSort: $O(M \log M)$. If input is already in order, TimSort detects the run in $O(M)$ time.
     3. Selection mapping (`server.copy(selected = ...)`) is applied globally once: $O(M)$.
     4. Group membership indices (`serverIdToExplicitGroupIds` and `legacyGroupIdToGroupIds`) are built in $O(\text{memberships})$ time.
     5. Servers are distributed into group buckets in a single pass over the globally sorted servers.
     6. Page UI states are created with direct bucket lookups in $O(G)$ time.
   - All group semantics are preserved: explicit overlapping `serverIds`, `All` group, legacy `groupId` matching, search filtering, globally projected `selectedServer` outside search filters, empty groups, and per-group collapsed states.

3. **Selected-Group-Membership Fallback Preserved:**
   - The recently fixed regression requiring actual group membership before trusting a stale `selectedServer.groupId` remains intact in `resolveSelectedGroupId`.

---

## 2. Changed Files and Rationale

### 1. `shared/app/src/commonMain/kotlin/app/skipi/app/home/ProxyHomeStore.kt`
- **Added `sortKey: String? = null` to `ProxyServerSummary`:** Allows hosts to pass canonical raw remarks for sorting, preventing stripped display labels from corrupting order. The default `null` parameter ensures complete source and binary backward compatibility for all callers.
- **Refactored `createUiState`:**
  - Performs global search query filtering once instead of per-group.
  - Performs global sorting once instead of per-group.
  - Builds inverted group membership lookup tables (`serverIdToExplicitGroupIds` for groups with explicit `serverIds`, and `legacyGroupIdToGroupIds` for legacy groups where `serverIds == null`).
  - Distributes sorted servers into group buckets in a single linear pass, preserving global order and handling overlapping memberships without duplicating entries.
  - Populates each `ProxyHomePageUiState` in $O(1)$ from direct bucket lookups.
  - Reuses the globally sorted and filtered list for `projectionServers` when `groups.isEmpty()`.
- **Updated `sortServers`:**
  - Evaluates `val keyA = a.sortKey ?: a.title` and `val keyB = b.sortKey ?: b.title` for `ProxyHomeSortMode.Name`, maintaining stable original order on ties.
  - Latency and Default sorting preserve original order on ties / nulls.

### 2. `app/src/main/kotlin/features/proxy/server/list/ProxyHomeAndroidInput.kt`
- **Updated `toProxyHomeSummary`:** Sets `sortKey = info.remarks.ifBlank { title }`, providing the canonical raw remarks that Android's list sorter uses.

### 3. `desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProxyHome.kt`
- **Updated `allProxyServers` mapping:** Sets `sortKey = info.remarks.ifBlank { title }`.
- **Added `sortDesktopProxyServers`:** Implements Desktop host sorting for `Default`, `Name`, and `Latency` sort modes.
- **Updated `homeInput`:** Wraps `allProxyServers` in `sortedProxyServers = remember(allProxyServers, sortMode) { sortDesktopProxyServers(allProxyServers, sortMode) }`, ensuring the Desktop host supplies pre-sorted servers in the authoritative `sortMode` order.

### 4. `shared/app/src/commonTest/kotlin/app/skipi/app/home/ProxyHomeStoreTest.kt`
- Added `nameSortingUsesCanonicalSortKeyAndDoesNotReorderByFlagStrippedDisplayTitle`: Proves that flag-prefixed titles (e.g. `"🇺🇸 Alpha"`) and unflagged titles (e.g. `"Zulu"`) sort canonically by `sortKey` rather than stripped `title`.
- Added `nameSortingFallsBackToDisplayTitleWhenSortKeyIsNull`: Proves backward compatibility when `sortKey` is null.
- Added `reusablePageConstructionHandlesOverlappingGroupsEmptyGroupsAndLegacyMembership`: Proves page construction with overlapping explicit memberships, empty groups, legacy `groupId` matching, and selection state.
- Added `globalSortingAndSearchFilterAppliedOnceAndPreservesOriginalOrderOnTies`: Proves single-pass global filtering, tie-breaking stability in Name and Latency modes, and global `selectedServer` resolution even when hidden by search.

### 5. `app/src/test/kotlin/features/proxy/server/list/ProxyHomeAndroidInputTest.kt`
- Verified `sortKey` mapping on `alphaSummary` (`"🇺🇸 Alpha"`) and `zuluSummary` (`"Zulu"`).
- Added `canonicalSortingIsIdenticalBeforeAndAfterUiMappingWithFlagStrippedDisplayTitles`: Verifies that passing `ProxyHomeInput` to `ProxyHomeStore` retains the exact `listOf("20", "10")` order in pages and projected server list.
- Added `latencyAndDefaultSortingArePreservedThroughUiMapping`: Verifies order preservation for Latency and Default sort modes across UI mapping.

### 6. `desktopApp/src/test/kotlin/app/skipi/desktop/DesktopProxyHomeOrderingTest.kt`
- Created focused Desktop unit test verifying:
  - `sortDesktopProxyServers` honors `sortKey` for flag-prefixed titles.
  - `sortDesktopProxyServers` maintains stable tie-breaking for equal sortKeys.
  - `sortDesktopProxyServers` sorts latency ascending with unmeasured servers last.
  - Desktop-mapped summaries passed to `ProxyHomeStore` maintain canonical ordering without reordering from flag-stripped display titles.

---

## 3. Proved Original Issues

1. **The Double-Sorting & Flag-Stripping Inversion Bug:**
   - In `ProxyHomeAndroidInputTest.kt`, `alpha` has remarks `"🇺🇸 Alpha"` (id 10) and `zulu` has remarks `"Zulu"` (id 20).
   - Android sorts by raw remarks: `'Z'` (Unicode 90) < `'🇺'` (Unicode 127482), so `input.servers` is `["20", "10"]`.
   - Android then stripped the flag for presentation, setting `title = "Alpha"`.
   - `ProxyHomeStore` took `input.servers` and sorted by `a.title.compareTo(b.title, ignoreCase = true)`: `"Alpha"` < `"Zulu"`.
   - Result: `ProxyHomeStore` flipped the order to `["10", "20"]`, violating the host's authoritative sorting and causing visual discrepancies between Android list sorting and shared Home store presentation.

2. **$O(G \times M \log M)$ Redundant Computations:**
   - Every call to `createUiState` (on every latency check, status update, search keystroke, or subscription refresh) iterated through every group, filtered all $M$ servers, filtered for search, and performed a full sort.
   - For $G=5$ groups and $M=500$ servers, this resulted in 5 independent sorts and 5 full list traversals per snapshot.

---

## 4. Regression Coverage

The following test suites now explicitly guard against regressions:
- **`ProxyHomeStoreTest.kt`:**
  - `nameSortingUsesCanonicalSortKeyAndDoesNotReorderByFlagStrippedDisplayTitle`
  - `nameSortingFallsBackToDisplayTitleWhenSortKeyIsNull`
  - `reusablePageConstructionHandlesOverlappingGroupsEmptyGroupsAndLegacyMembership`
  - `globalSortingAndSearchFilterAppliedOnceAndPreservesOriginalOrderOnTies`
  - `explicitGroupMembershipTakesPriorityOverStaleServerGroupId` (preserved original regression test)
  - `sortingMaintainsStableOriginalOrderForEqualNameAndLatencyValues` (preserved original regression test)
  - `pagesCalculateMembershipInOriginalOrderAndApplySearchFilter` (preserved original regression test)
  - `localSearchSurvivesInputUpdatesAndMissingGroupSelectionFallsBackWithoutRestoringStaleGroup` (preserved)
- **`ProxyHomeAndroidInputTest.kt`:**
  - `mapsAllGroupMembershipSortedServersAndSubscriptionMetadata` (enhanced with `sortKey` checks)
  - `canonicalSortingIsIdenticalBeforeAndAfterUiMappingWithFlagStrippedDisplayTitles`
  - `latencyAndDefaultSortingArePreservedThroughUiMapping`
- **`DesktopProxyHomeOrderingTest.kt`:**
  - `sortDesktopProxyServers_honors_canonical_sortKey_for_flag_prefixed_titles`
  - `sortDesktopProxyServers_maintains_stable_tie_breaking_for_equal_sortKeys`
  - `sortDesktopProxyServers_sorts_latency_ascending_with_unmeasured_last_and_stable_order`
  - `desktop_mapped_summaries_passed_to_store_preserve_canonical_order_after_ui_mapping`

---

## 5. Verification Commands and Results

Per Mandatory Constraint 6:
> *"If the CLI sandbox command tool fails (previously: recvmsg connection reset by peer), finish source changes and explicitly report execution unverified; do not bypass sandbox settings or invent results."*

### Execution Record
- **Attempted Command:** `find desktopApp -name "*Test*.kt"`
- **Working Directory:** `/home/vqsego/TorvaldsVPN/Skipi/skipi-box`
- **Command Output:**
  ```
  Encountered error in tool execution: connecting to sandbox server: read unix @->@: recvmsg: connection reset by peer
  ```
- **Execution Outcome:** Command tool connection reset by peer in the CLI sandbox.
- **Verification Status:** **Execution Unverified**. In strict compliance with Mandatory Constraint 6, sandbox bypass was not attempted and build/test execution was not faked.

### Static Verification Analysis
- **Code Inspection:**
  - Verified `ProxyServerSummary` has `sortKey: String? = null` with backward-compatible default.
  - Verified `ProxyHomeStore.sortServers` comparator compares `a.sortKey ?: a.title` to `b.sortKey ?: b.title` case-insensitively, with stable TimSort semantics on ties.
  - Verified `createUiState` bucket distribution:
    - `groupBuckets` initialized for each group ID.
    - Explicit groups indexed via `serverIdToExplicitGroupIds`.
    - Legacy groups (`serverIds == null`) indexed via `legacyGroupIdToGroupIds`.
    - No group is registered in both explicit and legacy indices.
    - Each server in `processedServers` is distributed via $O(1)$ lookups into buckets.
    - No server is duplicated within any group bucket.
    - Direct lookup `groupBuckets[group.id] ?: emptyList()` constructs each page in $O(1)$.
  - Verified `AndroidTunnelController` and `ProxyServerListPage` references remain untouched (READ ONLY).
  - Verified `ProxyHomeAndroidInput.kt` and `DesktopProxyHome.kt` both provide `sortKey = info.remarks.ifBlank { title }`.

---

## 6. Algorithmic Complexity Comparison

| Phase | Previous Implementation | Refactored Implementation |
| :--- | :--- | :--- |
| **Search Filtering** | $O(G \times M)$ (filtered per group) | $O(M)$ (filtered globally once) |
| **Sorting** | $O(G \times M \log M)$ ($G$ independent sorts) | $O(M \log M)$ (sorted globally once; $O(M)$ if already sorted) |
| **Selection Mapping** | $O(G \times M)$ (mapped per group) | $O(M)$ (mapped globally once) |
| **Group Membership Resolution** | $O(G \times M)$ (linear `contains` check per group per server) | $O(\text{memberships} + M)$ (hash table index + single pass bucket routing) |
| **Page Construction** | $O(G)$ | $O(G)$ |
| **Total Snapshot Overhead** | $O(G \times M \log M)$ | $O(M \log M + M + \text{memberships} + G)$ |

---

## 7. Remaining Issues and Limitations

1. **Sandbox Tool Limitation:** The CLI sandbox socket disconnection (`recvmsg: connection reset by peer`) prevented running Gradle test tasks live inside this environment. Test tasks should be run when executing outside the sandbox:
   ```bash
   ANDROID_HOME=/home/vqsego/Android/Sdk flock /tmp/skipi-home-polish-gradle.lock ./gradlew :shared:app:testDebugUnitTest :app:testDebugUnitTest :desktopApp:test -x :hevtun:buildHevTun -x :hevtun:syncHevSocks5TunnelVersion
   ```
2. **Subscription Toggle UI Contract:** The `ToggleSubscriptionEnabled` contract remains intact per task specification D; actual UI presentation removal is delegated to Agent 01.
