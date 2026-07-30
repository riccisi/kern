# Invariant Test Matrix v0.1

Issue: [KERN-007](https://github.com/riccisi/kern/issues/7)

This matrix turns the release invariants from the technical specification into
explicit test targets. The suite starts before the full event store exists, so
not every invariant can execute against real behavior yet. Pending invariants
must point to the issue that will replace the pending catalog entry with an
executable test.

## Current Policy

- Executable invariants run in the root Maven test command.
- Pending invariants are not disabled tests; they are catalog entries verified
  by `EventStoreInvariantSuiteTest`.
- A pending invariant must name its verification layer and tracking issue.
- New append, storage, recovery, rebuild, or subscription work must move the
  related invariant from `PENDING` to `EXECUTABLE` in the same pull request.

## Matrix

| Invariant | Layer | Current status | Current coverage | Tracking |
| --- | --- | --- | --- | --- |
| Committed positions are unique, strictly increasing, and have no ordinary-path gaps | Storage integration | Pending | RocksDB commit position invariant test | [#15](https://github.com/riccisi/kern/issues/15) |
| Events inside an accepted append batch keep the caller-provided order | Storage integration | Pending | RocksDB append ordering invariant test | [#15](https://github.com/riccisi/kern/issues/15) |
| Every subject advances by contiguous revisions starting from the first committed event | Storage integration | Pending | RocksDB subject revision invariant test | [#15](https://github.com/riccisi/kern/issues/15) |
| An append acknowledged as durable is present after storage restart | Storage integration | Pending | RocksDB restart invariant test | [#15](https://github.com/riccisi/kern/issues/15) |
| A committed append batch is totally present or totally absent | Storage integration | Pending | RocksDB atomic write-batch invariant test | [#15](https://github.com/riccisi/kern/issues/15) |
| Replaying the same idempotency key and request returns the original result without appending events | Core unit | Pending | Idempotency replay invariant test | [#16](https://github.com/riccisi/kern/issues/16) |
| Reusing an idempotency key with a different request is rejected | Core unit | Pending | Idempotency conflict invariant test | [#16](https://github.com/riccisi/kern/issues/16) |
| A DCB append does not commit when at least one expected consistency revision differs | Concurrency | Pending | DCB conflict invariant test | [#17](https://github.com/riccisi/kern/issues/17) |
| Indexes rebuilt from the event log produce the same query results as the original indexes | Fault injection | Pending | Index rebuild equivalence invariant test | [#18](https://github.com/riccisi/kern/issues/18) |
| A subscription from position N does not omit matching committed events after N | Subscription integration | Pending | Subscription no-omission invariant test | [#19](https://github.com/riccisi/kern/issues/19) |

## Verification Layers

Core unit tests cover pure core contracts and append coordination behavior that
does not require RocksDB, Armeria, Spring, or external services.

Storage integration tests cover RocksDB persistence, atomic write batches,
restart, and read/query behavior over real local temporary directories.

Concurrency tests cover append races, DCB conflicts, overload, cancellation,
and ordering under bounded local concurrency. They must use timeouts and avoid
unbounded sleeps.

Fault-injection tests cover crash/restart, corrupted or missing derived
indexes, and rebuild equivalence. These tests belong in integration or RocksDB
storage tests depending on the failure mode.

Subscription integration tests cover catch-up, live wake-up, reconnect, and
filtered no-omission semantics without relying on exactly-once delivery.
