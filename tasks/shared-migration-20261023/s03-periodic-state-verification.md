# S03: shared subscription periodic timing

Canonical contract: the current Android gateway's PeriodicWorkRequest requires CONNECTED, UPDATE and exponential 15-minute base backoff. Cached WorkManager2.11.2 bytecode confirms first-run eligibility at enqueue, next interval from completion for both periodic SUCCESS and FAILURE, and RETRY exponential backoff capped at five hours. This was local library bytecode evidence, not source-JAR or remote documentation. Details are pinned in [s03-subscription-periodic-spec.md](../../outputs/drafts/s03-subscription-periodic-spec.md).

Before production, five common state tests and seven Desktop host tests were authored/frozen. Runs failed at compile/API because SubscriptionPeriodicWorkState and DesktopSubscriptionAutoUpdater were absent. The second preproduction Desktop compile also saw concurrent absent install APIs; the report does not call these executed behavior failures. Logs/hashes are under `outputs/s03-periodic-reports-20261023/red`.

The common state now preserves WorkManager timing and reuses the existing same-core capped backoff helper. Existing SubscriptionScheduler spec construction is extracted without behavior changes to `SubscriptionRefreshTarget.subscriptionRefreshWorkSpecOrNull()`, shared with the future Desktop host. State holds no I/O, clock or platform scheduler. Profile/resource P03 work remains separate.

Core timing/scheduler tests after production: **8/8 on Desktop and 8/8 on Android**, in the combined 218-test GREEN gate (the additional three Desktop-core classifier cases are a separate slice). Source test SHA `84753c7cacb1037957a659604a359338cc5636cc8386f23a4f9d5061c66680ef` unchanged. XML/log/counts are in `outputs/s03-common-install-periodic-reports-20261023/green`. Root reviewed the pure-helper extraction, update-state preservation, outcome deadlines and cancellation/identity guard proposal.

This checkpoint accepts the common state/spec. Desktop host, coordinator and Main wiring still need their focused GREEN and final integrated gate; S03 remains open.
