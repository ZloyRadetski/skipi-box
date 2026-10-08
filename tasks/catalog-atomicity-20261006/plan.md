# Proxy catalogue guarantees — 2026-10-06

## Scope and execution order

Android and Desktop JVM only. Research actual shared contracts, adapters, consumers and persistence before choosing requirements. Write behavior tests against current production code, verify expected RED failures, then delegate the smallest implementation and independently review it. Preserve user-owned storage and native libraries. Only code and tests may enter commits.

## Findings confirmed before implementation

- Android catalogue transformations run inside one synchronized AppState update. Persistence is asynchronous; method completion does not acknowledge a Room/SharedPreferences durable write.
- Desktop inherits multi-step collection/catalogue defaults. Its existing primitive commits save before publishing, but a bulk change can publish and persist partial states.
- Desktop JSON currently persists records and selection, not the shared next-ID counter. The fallback reconstructs selection from the first record.
- Desktop storage already uses a temporary file and replacement with an atomic-move fallback. Power-loss durability and cross-file transactions are separate concerns.

## Acceptance criteria finalized after research

1. One catalogue transform observes current records, selection and next-ID metadata; successful bulk changes preserve requested ordering and normalize invalid selection consistently.
2. Transform/validation errors do not mutate state. Desktop persistence failure does not expose part of a bulk change.
3. Successful Desktop updates publish complete state, preserve metadata across reload and serialize repository-owned mutations; limits of direct host writes are explicit.
4. User decision: deleting a selected Desktop server selects the first remaining server; an empty list has no selection. An unchanged null selection remains null.
5. Unknown-format raw Desktop records survive visible catalogue replacement, preserving payload and association. Their IDs participate in allocation and cannot be overwritten by a visible record with the same ID.
6. Persisted next-ID high water is honored by manual addition, imports and subscription reconciliation; deletion does not rewind it. Old JSON without the counter derives it from all stored IDs. Allocation cannot wrap to negative IDs.

## RED evidence

Before any production edits, the repository batch ran 14 tests with 11 expected behavioral failures and 3 passing controls. The allocator batch ran 6 tests with 5 expected behavioral failures and the legacy JSON control passing. No compile errors or skips. Logs are outside the repository under `/tmp/skipi-catalog-20261006`.

## Steps

1. Complete three read-only investigations and reconcile disagreements.
2. State the minimal contract, compatibility decisions and any product questions.
3. Add targeted RED/PASS controls using existing adapter seams and temporary files; no new test framework.
4. Root executes focused tests and verifies each failure is behavioral.
5. Implement only confirmed requirements, then run GREEN and integrated JVM checks/compilation.
6. Independent review; separate code/test commits from all planning artifacts.

## Out of scope

Native engine changes, UI redesign, iOS, mandatory Desktop routing/resources parity, power-loss guarantees, cross-file catalogue/subscription transactions, and wholesale Android persistence migration.
