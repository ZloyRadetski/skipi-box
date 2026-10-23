# S03 Desktop single-subscription refresh operation

## Current behavior and host boundaries

The Proxy screen owns the only current single-refresh call in [`Main.kt`](../../desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt#L1623). It rejects concurrent/manual missing/blank-URL requests, captures the current subscription and fetch options, then launches work in Compose's `rememberCoroutineScope` ([line 137](../../desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt#L137)). The callback builds `DesktopSubscriptionRefreshCommitAdapter`, calls shared `refreshSubscription`, formats the complete commit receipt for the UI, and uses the adapter's library-change flags to request a tunnel reconnect ([lines 1639–1708](../../desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt#L1639)). Install preparation persists the record first, then the UI calls the same refresh-by-ID callback; do not gate that first refresh on `enabled` ([`DesktopSubscriptionInstallOperations.kt`](../../desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSubscriptionInstallOperations.kt#L21), [`DesktopProxyHome.kt`](../../desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProxyHome.kt#L461)).

The fetch adapter moves each synchronous HTTP request to `Dispatchers.IO` ([`DesktopSubscriptionFetcher.kt`](../../desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSubscriptionFetcher.kt#L51)), but the shared importer resumes in the caller context. Since the current call uses the Compose scope, parsing/import currently runs on the UI dispatcher. The new operation should move the full shared load/import to `Dispatchers.Default`, then run commit on the caller context so Desktop repository publication and Compose state callbacks remain on their current side of the boundary.

The real [`DesktopSubscriptionRefreshCommitAdapter`](../../desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSubscriptionRefreshCommitAdapter.kt#L51) already reads latest persisted server, subscription, latency, and profile state during commit; it applies server, subscription, and embedded profile scopes in shared order and records visible server/profile changes. Reuse this adapter and the shared `loadSubscriptionRefresh` / `commitSubscriptionRefresh` functions. Do not create another reconciliation or persistence path.

## Proposed API

Add a small Desktop operation wrapper outside `Main.kt`:

```kotlin
internal data class DesktopSubscriptionRefreshOperationResult(
    val refresh: SubscriptionRefreshResult,
    val serverLibraryChanged: Boolean,
    val profileLibraryChanged: Boolean,
)

internal class DesktopSubscriptionRefreshOperation(
    private val subscriptionRepository: DesktopSubscriptionRepository,
    private val proxyServerRepository: DesktopProxyServerRepository,
    private val readConfigs: () -> DesktopConfigLibrary,
    private val saveConfigs: (DesktopConfigLibrary) -> Result<Unit>,
    private val publishConfigs: (DesktopConfigLibrary) -> Unit,
    private val readLatencyByServerId: () -> Map<Int, DesktopServerLatencyResult>,
    private val publishLatencyByServerId: (Map<Int, DesktopServerLatencyResult>) -> Unit,
    private val fetchResponse: suspend (String, String, DesktopSubscriptionRefreshFetchOptions) -> SubscriptionFetchResponse,
    private val parsers: List<ProxyServerPayloadParser>,
    private val refreshedAtMillis: () -> Long,
) {
    suspend fun run(
        request: SubscriptionRefreshLoadRequest<DesktopSubscriptionRefreshFetchOptions>,
    ): DesktopSubscriptionRefreshOperationResult
}
```

`run` should create the existing single commit adapter using `request.subscription.id`, load with `withContext(Dispatchers.Default) { loadSubscriptionRefresh(request, fetchResponse, parsers) }`, then invoke `commitSubscriptionRefresh(loaded, refreshedAtMillis(), adapter)` on the caller context. Return `SubscriptionRefreshResult(loaded, commit)` together with both adapter flags, including flags when the shared commit result reports a later-scope failure. Let load errors and cancellation propagate unchanged. This keeps the complete shared load details and partial commit receipt available to UI and scheduled callers.

The caller captures `SubscriptionRefreshLoadRequest` at invocation time from the current catalog row plus `desktopLocalProxyRuntime.subscriptionFetchOptions(desktopSettings, subscription, coreState.isRunning)`. Preserve the current early missing/blank checks for manual refresh and reuse the same refresh-by-ID path after install. Scheduled refresh will pass the scheduled row through this same operation after its existing planner eligibility decision. The operation itself must not reject disabled subscriptions, because an install can be disabled and still requires its initial fetch.

Add a narrow Desktop classifier adapter, `isTransientDesktopSubscriptionRefreshFailure(error)`, that calls the shared JVM classifier with a resolver for `DesktopSubscriptionHttpException.statusCode` and a host connection-failure predicate recognizing `java.net.http.HttpTimeoutException`. This treats the JDK HTTP-client timeout as the Desktop transport's connection timeout without inventing an HTTP response status. Direct `SocketTimeoutException` and the other exact network classes are already handled by the shared classifier. Do not make generic `IOException` transient.

## Tests to add before the wrapper

- Add an operation test with the real Desktop repositories, real `DesktopSubscriptionRefreshCommitAdapter`, and production subscription parser. A fake response should import a valid server row; assert the returned `refresh.loaded` details, persisted server row, refreshed subscription metadata/timestamp, latency-map pruning/preservation, and `serverLibraryChanged` result. Add an embedded-profile case if the fixture can exercise it without duplicating the adapter's existing profile tests.
- Verify dispatch at the real boundary: invoke `run` on a dedicated caller dispatcher; record the parser/import callback thread and the repository save/publish callback thread. Assert parsing runs off the caller and commit publication stays on the caller. Keep the fetch callback free to use its existing IO context; assert result and persisted output as well as thread identity.
- Cancel while the injected fetch callback is suspended; assert cancellation propagates and no persisted server/subscription/profile write occurs. Shared commit tests and adapter tests already cover per-scope cancellation/partial-write semantics, so avoid duplicating their whole matrix.
- Add Desktop classifier adapter cases for direct and wrapped `DesktopSubscriptionHttpException` statuses (408, 429, 500–599 retry; boundary/permanent statuses fail), direct and wrapped `HttpTimeoutException` retry, listed socket/network exceptions retry, and generic `IOException` / invalid payload fail.

Existing coverage to retain: `DesktopSubscriptionRefreshCommitAdapterTest` verifies current-state server merging, metadata timestamps, profile and latency updates; `DesktopSubscriptionRefreshBatchRunnerCallbackTest` covers commit change callbacks; shared tests cover load/commit ordering and cancellation; `DesktopSubscriptionRefreshFailureMessageTest` covers user-facing failure text. This proposal adds the missing single-operation integration and host dispatch/classification coverage without cloning those suites.
