# ADR — Kern Operational Runtime and Service Model

## Status

**Proposed**

## Purpose

This ADR defines the operational model required to run Kern reliably in embedded and server deployments.

The semantic Event Store model is assumed to be stable and is not redefined here.

This document describes how the runtime must support those semantics in production, including:

- lifecycle;
- concurrency;
- append admission;
- durability;
- asynchronous subscriptions;
- overload;
- diagnostics;
- security;
- readiness;
- recovery;
- backup;
- operational limits;
- observability.

The goal is to preserve the public semantics while allowing the runtime implementation to optimize aggressively underneath them.

---

# 1. Runtime versus EventStore

`EventStore` represents the semantic capability exposed to applications.

It is intended to be:

```text
long-lived
thread-safe
shared
location-independent
```

It should not necessarily own physical infrastructure.

The lifecycle of physical resources belongs to a separate runtime abstraction.

Conceptually:

```text
KernRuntime
 ├── EventStore
 ├── storage engine
 ├── append execution infrastructure
 ├── async waiting infrastructure
 ├── metrics
 ├── diagnostics
 └── operational services
```

The runtime owns resource creation and destruction.

---

# 2. KernRuntime

A possible initial abstraction is:

```java
public interface KernRuntime
    extends AutoCloseable {

    EventStore store();

    @Override
    void close();
}
```

This interface is intentionally infrastructure-oriented.

Unlike `EventStore`, `KernRuntime` may legitimately expose lifecycle semantics because it represents ownership of external resources.

In embedded mode:

```text
Application
    │
    ▼
KernRuntime
    │
    ├── EventStore
    └── storage engine
```

In server mode:

```text
Kern Server
    │
    ▼
KernRuntime
    │
    ├── EventStore
    ├── append runtime
    ├── subscription waiters
    └── storage engine
```

---

# 3. Runtime lifecycle

The expected lifecycle is:

```text
STARTING
   ↓
READY
   ↓
RUNNING
   ↓
STOPPING
   ↓
CLOSED
```

The exact lifecycle state machine need not be exposed publicly.

Operationally, however, Kern must distinguish at least:

```text
can accept reads?
can accept writes?
can satisfy follow().next()?
is storage healthy?
is shutdown in progress?
```

---

# 4. Startup

Runtime startup must ensure that the storage engine is usable before exposing Kern as ready.

Typical checks include:

```text
storage opens successfully
physical format version is supported
required metadata exists
head information is readable
authoritative event data is accessible
critical indexes are available or recoverable
```

A runtime must not advertise readiness while initialization or mandatory recovery is still in progress.

---

# 5. Shutdown

Shutdown must be controlled.

The runtime should:

```text
stop accepting new work
allow or terminate in-flight operations according to policy
complete/abort pending appends safely
complete/cancel pending subscription waits
flush required durable state
close storage resources
close executors and transport resources
```

Shutdown must never acknowledge writes that are not guaranteed by the normal append durability contract.

---

# 6. Thread safety

`EventStore` implementations must be safe for concurrent use.

Multiple clients may execute:

```java
store.events(namespaceA, filterA, afterA);

store.events(namespaceB, filterB, afterB);

history.tail().append(events);

history.follow().next(100);
```

simultaneously.

The client must not need external synchronization.

---

# 7. Append execution model

The runtime may internally serialize append execution even though the public API allows concurrent calls.

A particularly suitable initial implementation is:

```text
concurrent append callers
          ↓
bounded queue
          ↓
single logical append coordinator
          ↓
storage commit
```

This is an implementation strategy, not a public semantic constraint.

It is attractive because it simplifies:

```text
Tail validation
Position assignment
EventId idempotency
atomic batch assembly
head advancement
group commit
```

---

# 8. Bounded append admission

The append pipeline must have bounded capacity.

Unbounded queues are forbidden because they transform overload into uncontrolled memory growth and increasingly poor latency.

Conceptually:

```text
requests
   ↓
[ bounded queue ]
   ↓
append coordinator
```

When capacity is exhausted, Kern should reject new work rather than accumulate it indefinitely.

---

# 9. Overload

Overload is operationally distinct from:

```text
StaleTailException
storage failure
event identity collision
```

An overloaded server is saying:

> the operation has not been admitted because current runtime capacity is exhausted.

It is not saying:

> the consistency boundary is stale.

Nor:

> storage committed and the result is ambiguous.

This distinction must be preserved across remote protocols.

---

# 10. Overload and retry

A request rejected before admission is safe to retry.

For append, the intrinsic `EventId` still protects against duplicate persistence if the caller cannot determine precisely whether admission or execution occurred because of transport failure.

Operational error mapping should avoid encouraging clients to rebuild domain state unnecessarily when the problem is simply temporary capacity.

---

# 11. Durability policy

Kern v1 defines one normative durability mode:

> successful append is durable.

Therefore:

```java
tail.append(events);
```

returning normally means that the batch has satisfied the storage engine's durable commit guarantee.

Relaxed durability modes are intentionally excluded from the initial contract.

This keeps operational behavior predictable.

---

# 12. No per-append durability flag

Durability is runtime configuration, not event-domain behavior.

The API must not become:

```java
append(events, Durability.SYNC);
```

A domain decision should not decide how the database flushes its WAL.

If alternative durability policies are introduced later, they belong to runtime configuration.

---

# 13. Visibility after append

A successfully acknowledged append must be visible to new observations.

The following sequence must be valid:

```java
tail.append(event);

StoredEvents refreshed =
    store.events(namespace, filter, after);
```

If `event` matches the filter and falls within the requested range, the refreshed observation must be capable of seeing it.

Storage implementations must therefore commit:

```text
event record
indexes
EventId state
head/watermark advancement
```

with consistent visibility semantics.

---

# 14. Append failure ambiguity

A failure response does not always imply that no commit occurred.

This is especially important for remote usage:

```text
server commits
     ↓
network fails
     ↓
client receives error
```

The runtime and protocol must therefore preserve safe retry semantics.

The client retries the exact same event identities.

Kern determines whether the postcondition is already satisfied.

---

# 15. Idempotency is semantic, not operational cache state

Runtime-level retry must not depend solely on an ephemeral request cache.

The authoritative idempotency mechanism is:

```text
EventId
+
canonical Event fingerprint
```

Operational request caching may improve performance but must not be required for correctness.

---

# 16. Subscription runtime model

`Subscription.next(count)` is asynchronous:

```java
CompletionStage<StoredEvents> next(int count);
```

Operationally, Kern must support waiting for future events without allocating one blocked platform thread per pending subscription.

Suitable implementations include:

```text
event-loop wait registration
asynchronous long polling
future completion
network async I/O
lightweight notification structures
```

The semantic result remains the same.

---

# 17. No event buffering as source of truth

The persisted event log is authoritative.

The runtime may notify pending subscriptions that new events may be available, but notifications are only wake-up hints.

The correct model is:

```text
append commits
     ↓
head advances
     ↓
wake relevant waiters
     ↓
waiter reads persisted log after its watermark
```

Not:

```text
append
   ↓
push transient Java object directly to subscriber
```

This guarantees recoverability.

---

# 18. Subscription wake-up

An append may signal waiting subscriptions after durable commit.

Wake-up should occur only after the committed events are visible.

Conceptually:

```text
commit
  ↓
head visible
  ↓
notify
  ↓
next(count) reads authoritative log
```

Waking before commit could cause races and false empty reads.

---

# 19. Natural back-pressure

The pull model provides back-pressure through demand.

A consumer does not receive more events than requested:

```java
subscription.next(100);
```

The runtime therefore does not need an unbounded per-consumer event queue.

The event log itself acts as the durable backlog.

A slow consumer merely delays its next request.

---

# 20. Pending next requests

Each `next(count)` may remain pending until at least one matching event is available.

The runtime must impose operational limits on pending requests, such as:

```text
maximum concurrent pending next requests
maximum pending requests per principal
maximum pending requests per namespace
```

Exact defaults belong to configuration.

---

# 21. Cancellation

The semantic `Subscription` does not expose cancellation.

Cancellation belongs to the pending asynchronous computation or transport request.

For example, a Java client may cancel the returned future.

A remote protocol may cancel an in-flight request.

Cancellation of one `next()` does not invalidate the immutable subscription from which it originated.

---

# 22. Timeout

Timeout is operational policy rather than Event Store semantics.

The core `next(count)` API therefore contains no mandatory timeout parameter.

Possible runtime/client policies include:

```text
request timeout
idle timeout
transport timeout
server long-poll timeout
```

A client can retry the same immutable subscription after timeout without losing semantic position.

---

# 23. Namespace isolation

All operational behavior must preserve namespace isolation.

This includes:

```text
read execution
append validation
EventId uniqueness
subscription wake-up
authorization
metrics
backup/export
diagnostics
```

An operation in namespace A must not become semantically dependent on ordinary writes in namespace B.

---

# 24. Authentication

Authentication is a server concern, not a core EventStore concern.

The standalone server should authenticate callers before translating requests into EventStore operations.

The authentication technology remains deployment-specific.

Potential mechanisms include:

```text
mTLS
JWT/OIDC
API credentials
service identity
```

No authentication contract belongs in `kern-api`.

---

# 25. Authorization

Authorization should be evaluated at least at namespace level.

Conceptually:

```text
principal
   ↓
allowed namespaces/actions
   ↓
EventStore operation
```

Possible permissions include:

```text
read events
append events
follow events
administrative operations
backup/export
diagnostics
```

The exact authorization model is outside the semantic API.

---

# 26. Administrative API separation

Operational and administrative capabilities should not be mixed into `EventStore`.

Avoid additions such as:

```java
store.backup();
store.rebuildIndexes();
store.metrics();
store.shutdown();
```

These operations belong to runtime/control-plane APIs.

This preserves the semantic purity of `EventStore`.

---

# 27. Diagnostics

Kern should treat diagnostics as a first-class operational capability.

Important events include:

```text
append accepted
append rejected
StaleTailException
identity collision
storage failure
overload
subscription wait duration
subscription completion
recovery
index rebuild
```

Diagnostics should identify operations without exposing sensitive event data unnecessarily.

---

# 28. Conflict diagnostics

For `StaleTailException`, Kern should be able to report at least:

```text
namespace
conflicting EventId
conflicting Position
EventType
observation age
```

Potential extended diagnostics include:

```text
matched filter branch
filter expression
index plan
candidate counts
selected index
```

These details are operational and may be expensive to compute, so they need not all live directly inside the exception.

---

# 29. Sensitive Data

Event `Data` must be considered potentially sensitive.

Operational logs should not include serialized event data by default.

Preferred diagnostic fields are structural:

```text
EventId
EventType
Position
Namespace
Tag names where allowed
payload/data size
fingerprint
```

Even tag values may need redaction depending on deployment policy.

---

# 30. Metrics

The runtime should expose metrics for at least:

```text
append throughput
append latency
append queue depth
append conflicts
idempotent retries
identity violations
read latency
events scanned
events returned
pending subscriptions
subscription wait duration
overload rejections
storage failures
recovery status
```

Storage-engine-specific metrics may be added separately.

---

# 31. Tracing

Operations should support distributed tracing where available.

Particularly useful spans include:

```text
events()
Tail.append()
Subscription.next()
storage query
storage commit
filter planning
remote request
```

Trace propagation belongs to adapters/runtime, not to semantic API method parameters.

---

# 32. Readiness

A server is ready only when it can safely satisfy its advertised operations.

Readiness should fail if conditions such as these exist:

```text
storage unavailable
unsupported physical format
mandatory recovery incomplete
critical corruption detected
write path disabled by fatal storage state
```

Readiness is stronger than process liveness.

---

# 33. Liveness

Liveness indicates whether the runtime process itself is functioning.

A storage failure does not necessarily imply immediate process death.

Separating:

```text
liveness
readiness
```

allows orchestration systems to make more appropriate decisions.

---

# 34. Storage degradation

The runtime should distinguish recoverable and fatal storage states.

Examples:

```text
temporary I/O failure
disk full
background storage corruption
unsupported format
failed durability sync
```

Writes must never be acknowledged when durability cannot be guaranteed.

---

# 35. Recovery

On startup Kern must be able to validate the integrity of authoritative state.

The authoritative source is the event log.

Derived structures such as indexes may be rebuilt.

Recovery may include:

```text
physical format validation
head verification
event record verification
EventId index validation
index consistency checks
rebuild scheduling
```

---

# 36. Authoritative versus derived state

The runtime must preserve a clear distinction:

```text
authoritative
    stored event log

derived
    type indexes
    tag indexes
    compound indexes
    query planning statistics
```

Derived state should be reconstructible from authoritative events.

This principle significantly improves recovery and migration options.

---

# 37. Index rebuild

If an index is missing or corrupt but the authoritative log remains intact, Kern should be capable of rebuilding the index.

Whether the runtime remains partially available during rebuild is an operational policy.

Potential modes include:

```text
offline rebuild
online degraded rebuild
background rebuild
```

The initial implementation may choose the simplest safe option.

---

# 38. Backup

Backup must preserve all non-reconstructible authoritative state.

At minimum this includes:

```text
event records
EventId identity information where not trivially derivable
physical metadata required to interpret the log
```

Derived indexes need not necessarily be backed up if deterministic rebuild is supported.

However, including them may improve restore time.

---

# 39. Restore

Restore must preserve:

```text
EventId identity
Event ordering
Positions
Namespace separation
storedAt values
canonical event content
```

A restore operation must not silently renumber positions.

Positions are part of observable Event Store history.

---

# 40. Physical format versioning

Every persistent implementation must version its physical storage representation.

This versioning is distinct from:

```text
EventType version
Data Metadata/schema version
application version
```

The runtime must detect unsupported physical formats before becoming ready.

---

# 41. Operational limits

The server/runtime may impose configurable limits on:

```text
maximum batch size
maximum event data size
maximum tag count
maximum tag size
maximum filter tree depth
maximum filter node count
maximum next(count)
maximum concurrent observations
maximum pending subscription waits
```

These limits protect the runtime from pathological or hostile workloads.

They do not change the semantic meaning of valid operations.

---

# 42. EventFilter complexity

Because `EventFilter` is a recursive Composite, arbitrary depth and size must not be assumed safe.

The runtime should validate filter complexity before expensive planning/execution.

For example:

```text
max depth
max composite children
max total nodes
```

should be configurable.

---

# 43. Batch limits

Atomic append batches must also have bounded size.

A single append containing millions of events could otherwise monopolize:

```text
memory
WAL
write coordinator
storage lock time
network request size
```

The public API permits any iterable, but operational policy may reject an excessively large batch.

---

# 44. Remote protocol mapping

Remote errors should preserve semantic distinctions.

At minimum the protocol should distinguish:

```text
success
stale tail
identity violation
invalid request
overloaded
authentication failure
authorization failure
storage/runtime failure
```

The Java remote client then maps protocol outcomes back to Kern-level semantics.

---

# 45. Server statelessness where possible

The server should avoid durable per-client state unless required.

In particular:

```text
StoredEvents
Tail
Subscription
```

can conceptually be represented by immutable coordinates such as:

```text
Namespace
EventFilter
Position boundaries
Watermark
```

The protocol may encode these directly or through opaque tokens.

This makes horizontal server scaling easier.

---

# 46. Opaque continuation tokens

A remote protocol may choose to represent internal observation coordinates with an opaque token instead of exposing raw watermarks.

For example:

```text
StoredEvents response
    → continuation token

Tail append
    ← token

Subscription next
    ← token
```

The Java semantic objects continue to hide watermark information.

Token design belongs to the protocol ADR/implementation.

---

# 47. Runtime concurrency invariant

No operational optimization may violate:

> every successful `Tail.append()` behaves as if validation and append occurred atomically against the same logical namespace history.

This invariant holds regardless of:

```text
append queueing
group commit
parallel reads
async I/O
storage batching
```

Optimizations are permitted only when they preserve this behavior.

---

# 48. Group commit

The runtime may group multiple logically independent append operations into one physical storage commit.

However, validation must respect logical ordering within the group.

Suppose:

```text
Append A
Append B
```

are grouped.

If A creates an event that invalidates B's Tail, B must fail even though A has not yet been physically committed separately.

Therefore group commit requires a logical overlay containing preceding pending writes.

---

# 49. Append coordinator

An initial runtime may use a single logical append coordinator.

Conceptually:

```text
append request
     ↓
admission
     ↓
idempotency check
     ↓
Tail validation
     ↓
position assignment
     ↓
batch assembly
     ↓
durable commit
     ↓
completion
```

This model is simple and provides a strong baseline for correctness.

More concurrent implementations may be introduced later if profiling demonstrates a need.

---

# 50. Testing strategy

Operational behavior must be tested independently from storage internals.

Important test categories include:

```text
concurrent append races
overload
crash during commit
lost acknowledgement
retry
slow storage
slow consumer
subscription wake-up races
shutdown during operation
disk-full behavior
recovery after abnormal termination
```

---

# 51. Fault injection

Storage and server implementations should support fault-injection testing.

Useful fault points include:

```text
before Tail validation
after validation
before durable commit
after durable commit
before response
during index update
during shutdown
during subscription completion
```

This is essential for validating ambiguous failure and idempotency behavior.

---

# 52. Runtime object model

The operational architecture can be summarized as:

```text
                 KernRuntime
                     │
          ┌──────────┼───────────┐
          │          │           │
          ▼          ▼           ▼
      EventStore   Append     Operational
                   Runtime      Services
          │
          ├── StoredEvents
          │       │
          │       ├── Tail
          │       └── Subscription
          │
          ▼
       Storage Engine
```

`EventStore` remains semantically clean.

Operational complexity lives around it.

---

# 53. Initial deployment model

The initial production target should prefer simplicity:

```text
single Kern runtime
single authoritative storage engine
single logical append coordinator
multiple namespaces
parallel reads
asynchronous subscription waits
durable synchronous append acknowledgement
```

This provides a strong baseline before introducing distributed replication or multi-writer physical architectures.

---

# 54. Deliberately deferred concerns

The following are intentionally deferred:

```text
replicated durability/quorum
consumer groups
server-side durable subscriptions
exactly-once consumer processing
cross-region replication
multi-leader writes
automatic sharding
distributed consensus
relaxed durability modes
```

They should not complicate the initial runtime unless a concrete requirement demands them.

---

# 55. Decision summary

Kern operationally adopts the following model:

```text
EventStore
    long-lived semantic capability

KernRuntime
    owner of physical/runtime resources

append
    bounded admission
    atomic validation + write
    durable by default

Subscription.next()
    asynchronous pull
    no required platform-thread blocking
    natural back-pressure

event log
    authoritative source

indexes
    derived and rebuildable

server
    preserves semantic distinctions
    applies authentication/authorization/limits

runtime
    observable, diagnosable and recoverable
```

The operational implementation must support the public Event Store semantics without leaking runtime complexity into the domain API.