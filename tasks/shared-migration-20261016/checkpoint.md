# Android → shared: checkpoint 16 October 2026

The overall migration remains unfinished. All production and test changes in this session were written by GPT-6 Luna agents with reasoning max. Root coordinated contracts, reviewed source/diffs, ran Gradle/native checks and recorded this checkpoint. Existing dirty work was preserved; no commit, push, device installation or data reset was performed.

**Resumed continuation:** Luna is available again. The next wave completed common Tunnel/Network stateful editors in both hosts, Android's typed shared outbound planner, and shared dynamic-bind retry policy with Desktop retry/rebuild wiring. Current verification: shared/app 293 tests per target, shared/ui 82 per target, Android 474, Desktop 406, APK assembly and a separate real Core **4/4** run including actual port collision/retry/authentication. The earlier credit blocker below is historical. New Rules tests are entering their tests-first gate. See [continuation-verification.md](continuation-verification.md) for exact gates, reports and remaining scope.

**Second continuation wave GREEN:** shared Rules controller/stateful Android screens, portable outbound renderer, routing suggestions/presets/filename sanitizer and shared geo picker have completed their tests-first gates. Full run `outputs/shared-migration-20261016-rules-renderer-suggestions-green.log`: core **257/256**, app **301/301**, UI **87/87**, Android **488**, Desktop **409** — **2,186 test executions**, zero failures/errors/skips; APK assembled. Reports: `outputs/continuation-reports-20261016/second-wave/`. The separate native **4/4** evidence remains as above. Desktop policy-picker/complete rule flow, selected composite runtime wiring, scoped custom-resource adapters and broader plan criteria remain open; upcoming helper/factory tests may intentionally re-enter RED before their production gates.

## Connected changes

- P04 sub-slice: shared stateful Basics, General and DNS screens and editor controller are used by Android and Desktop. Android system Back and top-bar Back use the same save-before-navigation action. All six Android section routes remain available. Desktop UI Edit and Raw Edit are distinct, and Raw Back submits the current text. The selected-profile updater preserves Android's known-SKIPI read/write semantics and Desktop's existing library/reconnect/group callback. Missing profiles are not recreated; unchanged canonical Basics skips the write; failed Desktop saves remain in the editor and expose the existing inline failure message. The wider P04 list/routing/groups/resources/raw-editor work remains open.
- X01 sub-slice: Desktop profile matching now calls the existing Android `toXrayRouteRule` converter. Duplicate match/wildcard/slash parsing was removed. Literal Android fixtures pin CIDR validation, DOMAIN-SET, IP-ASN prefixes, external references, URL/unknown-rule omission and resolver ordering. Target-policy resolution, FINAL shape and runtime resource sanitization remain separate pending work; the Desktop IP-ASN capability flag is not evidence of a native limitation.
- T02 sub-slice: actual Desktop startup now prepares the local listener options at start time, rebuilds the full selected configuration using current profile/server/settings snapshots, and publishes the endpoint and credentials only after successful Core startup. Subscription single/batch/scheduled refresh uses the captured running endpoint. Windows proxy readiness/acquisition uses the same actual endpoint. Preview/export does not allocate a port. Invalid explicitly stored port text uses Android's parser/default, without changing the persisted raw value or legacy numeric-only JSON records.
- The incomplete starting Desktop checkpoint was repaired: Custom Xray uses the shared authenticated SOCKS inbound; test-only compiler/fixture issues were corrected. The cancellation test was corrected against Android's cancellation-request-before-callback behavior, without adding a cleanup join. Font loading is checked using the installed Compose/Skia APIs and the bundled assets.

## Tests first and verification

Tests and source contracts preceded the production gates. Details for P04 and X01 are in [p04-verification.md](p04-verification.md) and [x01-verification.md](x01-verification.md).

T02 initially reached the intended missing-runtime-API RED in `outputs/shared-migration-20261016-editor-green-t02-api-red.log`. After lifecycle implementation, `outputs/shared-migration-20261016-t02-invalid-port-behavior-red.log` ran 20 focused tests with exactly one failure: invalid raw port text retained the old parsed numeric port instead of Android's default. The helper correction was made only after that behavioral RED. The final full Desktop run is GREEN.

The latest successful module reports contain **2,062 test executions**, with no failures, errors or reported skips. These are the latest relevant suite runs, not one monolithic invocation. Common tests are counted separately for the two targets.

| Suite | Tests |
| --- | ---: |
| shared/core Desktop / Android host | 244 / 243 |
| shared/app Desktop / Android host | 277 / 277 |
| shared/ui Desktop / Android host | 82 / 82 |
| Android app | 458 |
| Desktop app, full suite | 399 |

- Core: `outputs/shared-migration-20261016-x01-green.log`.
- Shared application and Android tests: `outputs/shared-migration-20261016-editor-green-t02-api-red.log`; its overall failure was the then-missing T02 Desktop test API, not these successful suites.
- Final shared UI and Android APK: `outputs/shared-migration-20261016-p04-final-ui-apk.log`, `BUILD SUCCESSFUL`.
- Final complete Desktop suite: `outputs/shared-migration-20261016-desktop-final.log`, `BUILD SUCCESSFUL`.
- Separate real native run: `outputs/shared-migration-20261016-real-core-ffm.log`, `BUILD SUCCESSFUL`, **3/3 FFM tests** with `SKIPI_CORE_INTEGRATION_DIR` pointing at the existing generated runtime directory. This executed Core start/stop, traffic/geosite checks, authenticated SOCKS handshake and dynamic listener/captured-endpoint authentication. The ordinary unit-suite early-return behavior is not used as native evidence.
- Full module XML reports were preserved under `outputs/final-reports-20261016/` before the focused native run; its XML is `outputs/final-reports-20261016/real-core-ffm.xml`. `git diff --check` passes.

Native source artifacts were not rebuilt. Physical Android UI/VPN QA and rendered Desktop QA at two window sizes were not performed. Native integration checks do not establish complete profile-routing or visual parity.

## Remaining work and current execution blocker

The user requested continuation. The next proposed P04 slice is the existing Android Tunnel/Network editor state and coordination, followed by list, routing/rules, proxy groups, resources and complete raw-editor parity. It has not been implemented in this session. Existing plan cards remain open unless their full acceptance was previously established.

T02 still needs Android's two-attempt local-bind collision retry contract to be transferred and tested; the current Desktop lifecycle slice does not claim that behavior. Remaining X01/X02/X03 policy/resource/outbound work must also be completed before full runtime equivalence can be claimed.

After the final T02 default fix had landed, all three Luna agents returned: `Your workspace is out of credits. Ask your workspace owner to refill in order to continue.` Root completed the available checks on their existing changes, but cannot continue production/test authoring under the user's Luna-only condition until agent credits are available. This is an execution-resource blocker, not a user-requested pause or a claim that the migration is complete.
