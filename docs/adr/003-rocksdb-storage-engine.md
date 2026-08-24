# ADR — Kern RocksDB Storage Engine Design

## Status

**Accepted**

## Purpose

This ADR defines the RocksDB-based storage engine used to implement the Kern semantic Event Store model.

The goal is not to wrap RocksDB behind a generic persistence abstraction.

The goal is to implement Kern efficiently and correctly **using RocksDB as the physical storage technology**, while preserving:

- the semantic guarantees of `EventStore`;
- Dynamic Consistency Boundaries;
- idempotent append;
- ordered replay;
- lazy bounded observations;
- asynchronous `follow()` continuations;
- namespace isolation;
- durable append;
- recoverability.

The implementation is allowed to speak the language of RocksDB where that is useful:

```text
RocksDB
ColumnFamilyHandle
RocksIterator
WriteBatch
WriteOptions
ReadOptions
WAL
compaction
block cache
Bloom filters
```

However, low-level RocksDB primitives should not dominate the internal design when a small cohesive object can represent a clearer concept.

The guiding principle is:

> **Hide accidental complexity, not the storage technology itself.**

---

# 1. Architectural direction

The RocksDB engine is derived from the Kern semantic model.

The direction of design is:

```text
Kern semantics
      ↓
required storage behavior
      ↓
RocksDB-oriented object model
      ↓
physical RocksDB layout
```

and not:

```text
RocksDB API
      ↓
wrappers
      ↓
public Kern concepts
```

This distinction is fundamental.

The storage engine must implement the semantics already defined by Kern. It must not redefine them according to what happens to be convenient in RocksDB.

---

# 2. Main semantic requirements

The RocksDB implementation must support the following fundamental operations efficiently:

```text
1. read a StoredEvent by Position

2. observe StoredEvents within:
      Namespace N
      EventFilter F
      Position after A
      Watermark W

3. find positions matching EventType

4. find positions matching Tag

5. compose filters through AND / OR

6. check whether a Tail is stale

7. verify EventId idempotency

8. assign new Positions

9. append an entire batch atomically

10. update event records, indexes and head atomically

11. durably acknowledge successful writes

12. wake pending Subscription.next() requests after commit

13. recover derived indexes from the authoritative event log
```

These access patterns drive the physical schema.

---

# 3. RocksDB-specific internal model

The implementation should introduce only a small number of abstractions whose responsibilities are clear.

The current core concepts are:

```text
Events
    physical persisted event stream

EventIds
    identity and idempotency lookup

Head
    current namespace frontier

Index
    ordered set of Positions

AppendCoordinator
    serialization point for append operations

EventEntry
    physical representation of one event insertion

EncodedEvent
    canonical serialized representation of an Event
```

These are storage-engine concepts.

They are not promoted to `kern-api`.

---

# 4. Events

`Events` represents the authoritative persisted stream of events.

Its minimum read responsibility is conceptually:

```java
interface Events {

    StoredEvent at(Position position);
}
```

The exact concrete interface may remain package-private.

The important semantic point is:

> `Events` represents persisted event records, not a generic Column Family wrapper.

Its RocksDB implementation may internally depend on:

```text
RocksDB
events ColumnFamilyHandle
Namespace
event decoder
```

but that detail should remain local.

---

# 5. Event log as authoritative state

The `events` Column Family is the authoritative source of truth.

For each persisted event it must contain enough information to reconstruct all derived structures.

A stored record must therefore contain at least:

```text
EventId
EventType
storedAt
Tags
Data
```

From this authoritative record Kern must be able to rebuild:

```text
event_ids
type_index
tag_index
Head
```

This is a central recovery invariant.

---

# 6. Position

Within the RocksDB implementation, `Position` should initially be represented as a monotonically increasing 64-bit integer per namespace.

This implementation detail does not change the public semantic contract.

The public API does not promise:

```text
contiguity
arithmetic
Position.next()
```

but the RocksDB v1 engine is free to allocate:

```text
P1
P2
P3
...
```

contiguously because it greatly simplifies:

- key ordering;
- replay;
- range scanning;
- head management;
- append allocation.

---

# 7. Namespace-local position space

Each namespace maintains its own position sequence.

Conceptually:

```text
Namespace A
1 2 3 4 5 ...

Namespace B
1 2 3 ...
```

A write in namespace B does not advance namespace A.

This affects all RocksDB keys and metadata.

---

# 8. Physical Column Families

The initial schema uses five explicit Kern Column Families:

```text
events
event_ids
type_index
tag_index
metadata
```

The RocksDB default Column Family remains unused for Kern data.

These CFs exist because they represent distinct access patterns and operational responsibilities.

---

# 9. Physical schema overview

```text
┌──────────────────────────────────────────────────┐
│ events                                           │
│                                                  │
│ key                                              │
│   Namespace | Position                           │
│                                                  │
│ value                                            │
│   version | flags | EventId | EventType          │
│   | storedAt | Tags | Data                       │
├──────────────────────────────────────────────────┤
│ event_ids                                        │
│                                                  │
│ key                                              │
│   Namespace | EventId                            │
│                                                  │
│ value                                            │
│   Position | EventFingerprint                    │
├──────────────────────────────────────────────────┤
│ type_index                                       │
│                                                  │
│ key                                              │
│   Namespace | EventType | Position               │
│                                                  │
│ value                                            │
│   empty / marker                                 │
├──────────────────────────────────────────────────┤
│ tag_index                                        │
│                                                  │
│ key                                              │
│   Namespace | TagName | TagValue | Position      │
│                                                  │
│ value                                            │
│   empty / marker                                 │
├──────────────────────────────────────────────────┤
│ metadata                                         │
│                                                  │
│ Namespace | HEAD -> Position                     │
│ FORMAT_VERSION   -> PhysicalFormatVersion        │
└──────────────────────────────────────────────────┘
```

---

# 10. Why these Column Families are separated

The CFs are intentionally separate because their workloads differ.

`events` is:

```text
append-heavy
large-value
range-scan oriented
authoritative
```

`event_ids` is:

```text
point-lookup heavy
small key/value
critical to every append
```

`type_index` and `tag_index` are:

```text
small-value
range/prefix scan oriented
write-amplified
derived
```

`metadata` is:

```text
tiny
point-read/write oriented
```

This separation allows different RocksDB tuning and independent rebuild of derived indexes.

---

# 11. Key model

RocksDB physically accepts:

```java
byte[]
```

but raw byte concatenation should not be spread across the implementation.

Keys should be represented by cohesive objects.

For example:

```java
interface Key {

    byte[] bytes();
}
```

or, if ergonomic in the implementation, a Cactoos-compatible `Bytes` abstraction.

The exact interface is less important than the principle:

> key layout must be represented through objects, not repeated manual byte-array manipulation.

---

# 12. Cactoos usage

Cactoos is an appropriate implementation utility because its object-oriented primitives align well with Kern's design style.

It may be used for:

```text
Bytes
Mapped
Filtered
Joined
Scalar
Iterable adapters
```

and similar operations.

However, Kern's internal semantic contracts should not be distorted merely to expose Cactoos types.

Java's own:

```java
Iterable<Position>
```

remains the correct contract for `Index`.

Cactoos is an implementation aid, not part of the storage semantics.

---

# 13. Composable key objects

Suggested key classes include:

```text
EventKey
EventIdKey

TypeIndexPrefix
TypeIndexKey

TagIndexPrefix
TagIndexKey

HeadKey
```

The distinction between a prefix and a complete key is useful because RocksDB queries often operate on logical prefixes.

Example:

```text
TypeIndexPrefix
    Namespace + EventType

TypeIndexKey
    TypeIndexPrefix + Position
```

Likewise:

```text
TagIndexPrefix
    Namespace + TagName + TagValue

TagIndexKey
    TagIndexPrefix + Position
```

This makes key composition explicit and reusable.

---

# 14. Variable-length key segments

String-like segments must be prefix-safe and unambiguous.

Avoid layouts such as:

```text
namespace + ":" + type + ":" + position
```

because delimiters require escaping and create ambiguity.

Variable-length segments should use a canonical representation such as:

```text
[length][UTF-8 bytes]
```

for:

```text
Namespace
EventType
TagName
TagValue
```

All producers of the same semantic type must use the same encoding implementation.

---

# 15. Unicode and canonical textual values

Values such as:

```text
Namespace
EventType
TagName
TagValue
```

must have canonical textual representation before RocksDB encoding.

Any Unicode normalization policy should belong to the corresponding domain value object rather than be invented independently by RocksDB.

The storage engine should encode already-canonical values deterministically as UTF-8.

---

# 16. Position encoding

`Position` is encoded using fixed-width big-endian bytes.

For v1:

```text
8 bytes
```

is the preferred representation.

The reason is critical:

> RocksDB sorts keys lexicographically.

Fixed-width big-endian encoding allows:

```text
numeric Position order
=
RocksDB byte ordering
```

and therefore efficient range scans.

---

# 17. Physical key format versioning

The physical key format must be explicitly versioned.

The initial approach is a database-level format version:

```text
metadata:
FORMAT_VERSION -> 1
```

rather than embedding a format byte in every key.

Startup must validate the version before the runtime becomes ready.

Unsupported formats require migration rather than accidental interpretation.

---

# 18. events Column Family

The authoritative log uses:

```text
key:
    Namespace | Position

value:
    encoded StoredEvent record
```

The key ordering naturally produces:

```text
all events in namespace N
ordered by Position
```

This also means the authoritative log can itself serve as the implementation of `AllIndex`.

---

# 19. Stored event record

The v1 record envelope should be versioned.

Conceptually:

```text
recordVersion
recordFlags

EventId
EventType
storedAt

tagCount
    TagName
    TagValue
    ...

Data metadata identity
canonical Data bytes
```

The exact binary encoding of lengths and nested Data is delegated to the serialization layer.

The RocksDB contract requires only deterministic decode/encode behavior.

---

# 20. Position is not duplicated in the event record

The position is already encoded in the `events` key.

The v1 record therefore does not duplicate it in the value.

This reduces redundancy.

The key is authoritative for physical position.

---

# 21. Namespace is not duplicated in the event record

The namespace is also part of the key and does not need to be repeated in the value.

A `StoredEvent` reconstructed from the record receives its semantic context from the storage operation that addressed the namespace and position.

---

# 22. EncodedEvent

Canonical event encoding should occur once.

A useful storage-side concept is:

```java
interface EncodedEvent {

    Bytes bytes();

    EventFingerprint fingerprint();
}
```

The object represents:

> the canonical physical representation of an Event together with its semantic fingerprint.

The intended pipeline is:

```text
Event
   ↓
Canonical encoder
   ↓
EncodedEvent
   ├── bytes
   └── fingerprint
```

This avoids serializing the event once for storage and again for fingerprinting.

---

# 23. Event fingerprint

The event fingerprint is derived from canonical:

```text
EventType
Tags
Data
```

and excludes:

```text
EventId
Position
storedAt
Namespace
```

The exact hashing algorithm is not defined by this ADR.

The RocksDB format must, however, preserve enough version information to reproduce the historical fingerprint algorithm during rebuild.

---

# 24. event_ids Column Family

Physical layout:

```text
key:
    Namespace | EventId

value:
    Position | EventFingerprint
```

This supports the critical append path:

```text
incoming EventId
      ↓
point lookup
      ↓
missing
    -> new event

same fingerprint
    -> idempotent success

different fingerprint
    -> identity violation
```

The stored Position also provides a direct reference to the authoritative event record.

---

# 25. EventIds object

The CF should be represented internally by an object with a cohesive responsibility.

For example:

```java
interface EventIds {

    Optional<EventIdentity> identity(EventId id);
}
```

with:

```java
interface EventIdentity {

    Position position();

    EventFingerprint fingerprint();
}
```

This is intentionally pragmatic.

There is no need to create a larger abstraction solely to avoid `Optional`.

---

# 26. type_index Column Family

Physical layout:

```text
key:
    Namespace | EventType | Position

value:
    empty
```

The presence of the key expresses membership.

For a namespace N:

```text
[N][StudentEnrolled][P5]
[N][StudentEnrolled][P9]
[N][StudentEnrolled][P20]
```

constitutes the posting list for:

```text
TypedBy(StudentEnrolled)
```

already ordered by Position.

---

# 27. tag_index Column Family

Physical layout:

```text
key:
    Namespace | TagName | TagValue | Position

value:
    empty
```

For:

```text
courseId=c1
studentId=s1
```

at P42:

```text
[N][courseId][c1][P42]
[N][studentId][s1][P42]
```

are written.

The authoritative event record still contains both tags.

---

# 28. Why Position is the final index segment

The key must be shaped as:

```text
Namespace
selection coordinates
Position
```

not:

```text
Namespace
Position
selection coordinates
```

because Kern's query path requires all positions belonging to a given type or tag to be contiguous and naturally ordered.

This layout is what makes lazy streaming intersections and unions possible.

---

# 29. Index

`Index` represents:

> an ordered selection of positions.

The contract is intentionally minimal:

```java
interface Index {

    Iterable<Position> positions();
}
```

This abstraction is internal to the RocksDB engine.

It does not represent:

```text
ColumnFamily
query builder
query plan
range
RocksIterator
```

It represents positions.

---

# 30. Index invariants

Every `Index.positions()` must guarantee:

```text
1. lazy iteration

2. Position order strictly increasing

3. no duplicates

4. only positions belonging to the represented selection

5. positions already bounded to:
       after < Position <= watermark
```

These invariants allow composite indexes to remain simple and efficient.

---

# 31. TypeIndex

`TypeIndex` represents positions of one `EventType` within a bounded observation window.

Conceptually:

```text
TypeIndex(
    Namespace N,
    EventType T,
    after A,
    watermark W
)
```

Its `positions()` performs a bounded RocksDB scan over the corresponding posting list.

---

# 32. TagIndex

`TagIndex` similarly represents positions matching one complete `Tag` within:

```text
(A, W]
```

It scans:

```text
Namespace | TagName | TagValue | Position
```

without loading event records.

---

# 33. AllIndex

`AllIndex` represents every event position in a namespace within the observation window.

It does not require a dedicated physical index.

The authoritative:

```text
events
```

CF is already naturally indexed by:

```text
Namespace | Position
```

and therefore directly implements the universal event set.

---

# 34. EmptyIndex

`EmptyIndex` represents an empty position set.

It is useful as the interpretation of:

```text
AnyEvents()
```

with no children.

Its `positions()` is simply empty.

---

# 35. AndIndex

`AndIndex` is a Composite of child indexes.

Its semantic result is:

```text
intersection(child positions)
```

Example:

```text
A = 1, 4, 8, 12
B = 2, 4, 9, 12

A AND B
    = 4, 12
```

The intersection must remain lazy and streaming.

---

# 36. Two-way intersection algorithm

For two ordered iterators:

```text
a < b
    advance A

a > b
    advance B

a == b
    emit
    advance both
```

The worst-case cost is:

```text
O(|A| + |B|)
```

with constant additional memory.

No `Set<Position>` materialization is required.

---

# 37. Multi-index intersection

For multiple children, the implementation can maintain one current position per child.

Conceptually:

```text
target = maximum current Position

advance every child below target

if all reach target:
    emit target
```

Example:

```text
A: 3  8 15 20 33
B: 2  8 16 20 30
C: 1  8 12 20 40

result:
8, 20
```

The operation remains lazy.

---

# 38. OrIndex

`OrIndex` represents ordered union.

For:

```text
A = 1, 4, 8, 12
B = 2, 4, 9, 12
```

the result is:

```text
1, 2, 4, 8, 9, 12
```

Duplicates are eliminated during merge.

---

# 39. Multi-index union

For N children, a k-way merge can use a small priority queue containing at most one current Position per child.

Memory usage is therefore proportional to the number of indexes, not the number of matching positions.

---

# 40. No mandatory planner in v1

The first implementation deliberately avoids:

```text
cost model
query planner
cardinality statistics
compound index selection
```

The initial interpretation is direct:

```text
TypedBy
    -> TypeIndex

TaggedAs
    -> TagIndex

AllEvents(children)
    -> AndIndex

AllEvents()
    -> AllIndex

AnyEvents(children)
    -> OrIndex

AnyEvents()
    -> EmptyIndex
```

This is already semantically complete.

---

# 41. Future query optimization

If profiling later shows that streaming intersection is insufficient, the implementation may introduce:

```text
seek-aware intersection
cardinality estimates
compound indexes
adaptive strategies
```

without changing:

```text
EventFilter
EventSelection
Index
```

This is an important architectural benefit.

---

# 42. No compound indexes initially

The initial physical schema deliberately excludes:

```text
tag_type_index
multi_tag_index
adaptive index CFs
```

because:

```text
TypeIndex
TagIndex
AndIndex
OrIndex
```

already implement the complete semantic filter algebra.

New physical indexes should be introduced only in response to measured workload requirements.

---

# 43. RocksEventSelection

The public `EventFilter` is compiled through a RocksDB-specific interpreter:

```java
final class RocksEventSelection
    implements EventSelection<Index> {
    ...
}
```

It is constructed with at least:

```text
Namespace
after Position
watermark
relevant RocksDB index access
```

and produces bounded indexes.

---

# 44. Filter compilation pipeline

The complete path is:

```text
EventFilter
      │
      │ describe(...)
      ▼
RocksEventSelection
      │
      ▼
Index
      │
      │ positions()
      ▼
Iterable<Position>
```

This same machinery is reused for:

```text
StoredEvents reads
Tail validation
Subscription continuation
```

---

# 45. Event loading after index resolution

Indexes operate only on Position.

They should not load event records.

The pipeline remains:

```text
EventFilter
    ↓
Index
    ↓
Position
    ↓
Events.at(Position)
    ↓
StoredEvent
```

This keeps AND/OR composition inexpensive.

Only final matching positions cause authoritative event records to be decoded.

---

# 46. RocksStoredEvents

A RocksDB implementation of `StoredEvents` conceptually contains:

```text
Namespace
EventFilter
after
Watermark
Events
Index
```

The exact fields need not mirror this list literally, but the semantics do.

Iteration maps:

```text
Index.positions()
    ↓
Events.at(position)
    ↓
StoredEvent
```

lazily.

---

# 47. StoredEvents does not require a long-lived RocksDB Snapshot

Because Kern is append-only and the observation is explicitly bounded by a captured watermark, a long-lived RocksDB Snapshot is not required for correctness.

The observation is:

```text
after < Position <= watermark
```

All later appends receive greater positions.

Therefore later data can simply be excluded by the upper boundary.

This avoids forcing:

```java
StoredEvents extends AutoCloseable
```

or keeping snapshots alive for slow consumers.

---

# 48. Visibility invariant enabling snapshot-free bounded reads

The snapshot-free strategy is valid only if every append atomically commits:

```text
event record
EventId mapping
type index entries
tag index entries
new Head
```

If a reader can observe:

```text
Head = P100
```

then all required state through P100 must already be visible.

The write path must therefore preserve this invariant strictly.

---

# 49. metadata Column Family

The metadata CF initially contains:

```text
Namespace | HEAD -> Position

FORMAT_VERSION -> PhysicalFormatVersion
```

It should remain intentionally small.

It must not become a generic miscellaneous storage area.

Independent categories of persistent state should get their own representation when needed.

---

# 50. Head

`Head` represents the current persisted frontier of one namespace.

Conceptually:

```java
interface Head {

    Position position();
}
```

If the namespace has no events:

```text
missing HEAD key
```

represents:

```java
Position.beginning()
```

No metadata record needs to be pre-created for a new namespace.

---

# 51. Head is persisted but reconstructible

The persisted head is a performance and coordination structure.

The true semantic head can always be derived as:

```text
max Position in events for Namespace
```

Therefore:

```text
events
    authoritative

HEAD
    critical persisted derivative
```

If HEAD is missing or inconsistent, it can be reconstructed from the event log.

---

# 52. Write path overview

A `Tail.append(events)` is implemented conceptually as:

```text
input Iterable<Event>
        ↓
materialize batch once
        ↓
validate batch
        ↓
canonical encode + fingerprint
        ↓
EventId idempotency check
        ↓
current Head
        ↓
Tail conflict Index over (W, Head]
        ↓
position allocation
        ↓
EventEntry creation
        ↓
single RocksDB WriteBatch
        ↓
WAL + sync commit
        ↓
wake subscriptions
        ↓
complete caller
```

---

# 53. Input batch materialization

The public append contract accepts:

```java
Iterable<? extends Event>
```

The RocksDB write path must materialize it exactly once before execution.

The source iterable may be:

```text
lazy
one-shot
expensive
backed by mutable state
```

so repeated traversal must not be assumed safe.

A package-private object such as:

```text
EventsBatch
```

may represent the immutable materialized append batch.

---

# 54. Empty append

An empty append is treated as a successful no-op.

```java
tail.append();
```

does not:

```text
validate Tail
allocate Position
write RocksDB
advance Head
```

because no state change has been requested.

---

# 55. Duplicate EventId inside one batch

A single append batch must not contain the same `EventId` more than once.

Example:

```text
[E42, E42]
```

is invalid even if the event contents are identical.

This is not an idempotent retry.

It is an internally inconsistent request.

The condition should be detected before storage access where practical.

---

# 56. Canonical encoding before identity lookup

Each incoming event is encoded canonically and fingerprinted before `event_ids` validation.

This provides:

```text
EventId
EventFingerprint
EncodedEvent bytes
```

for each event without duplicate serialization work.

---

# 57. Idempotency check order

Idempotency must be evaluated **before** Tail staleness.

This ordering is semantically mandatory.

Consider:

```text
original append succeeds
acknowledgement lost
same Tail and Event retried
```

The Tail is now stale precisely because the original event was written.

If stale detection ran first, a valid retry would incorrectly fail.

Therefore:

```text
EventIds first
Tail conflict second
```

---

# 58. Idempotency outcomes

For the incoming batch:

### No EventIds exist

Continue with normal append.

### Every EventId exists with identical fingerprint

Return success immediately.

The requested postcondition is already satisfied.

### Only some EventIds exist

Fail.

Atomic append semantics mean this cannot represent a retry of the same successfully committed batch.

### Same EventId, different fingerprint

Fail with identity violation.

---

# 59. Current head

After idempotency validation, the append coordinator determines:

```text
H = current namespace Head
```

The Tail carries its observed watermark:

```text
W
```

Conflict detection examines:

```text
(W, H]
```

with the original Tail filter.

---

# 60. Tail conflict detection

The filter is compiled into an Index:

```java
Index conflicts =
    filter.describe(
        new RocksEventSelection(
            namespace,
            watermark,
            head
        )
    );
```

Because `positions()` is lazy, the conflict check only needs:

```java
conflicts.positions()
         .iterator()
         .hasNext();
```

The first matching Position is enough to invalidate the Tail.

---

# 61. Conflict explanation

When a conflicting Position is found, the implementation loads:

```java
events.at(position)
```

and creates the core `Conflict` used by `StaleTailException`.

No complete scan of all conflicting events is required.

The first matching event in Position order is a natural explanation.

---

# 62. Position allocation

The RocksDB v1 engine assigns contiguous positions after current head.

Example:

```text
Head = P100

batch:
A
B
C

assigned:
A -> P101
B -> P102
C -> P103
```

This remains an implementation choice rather than a public guarantee of contiguous arithmetic.

---

# 63. EventEntry

`EventEntry` represents the complete RocksDB-side insertion of one assigned Event.

Conceptually it knows:

```text
Namespace
Position
Event
storedAt
EncodedEvent
Fingerprint
```

Its responsibility is to materialize all physical entries required for that event into the atomic write batch.

---

# 64. EventEntry physical effects

For:

```text
Event E at Position P
```

the entry produces:

```text
events:
    [N][P]
        -> encoded StoredEvent

event_ids:
    [N][E.id]
        -> P + fingerprint

type_index:
    [N][E.type][P]
        -> marker

tag_index:
    [N][tagName][tagValue][P]
        -> marker
```

for every tag.

---

# 65. EventEntry may write directly to WriteBatch

Inside the RocksDB implementation it is acceptable for a cohesive storage object to depend directly on:

```java
WriteBatch
```

For example:

```java
void addTo(WriteBatch batch);
```

This is intentionally RocksDB-specific.

There is no need to create a generic transaction abstraction simply to hide the storage technology.

---

# 66. No independent mutations on Events, Head or indexes

Avoid APIs such as:

```java
events.add(event);
head.set(position);
typeIndex.add(...);
tagIndex.add(...);
```

because each would appear to be a valid independent operation even though Kern requires them to be atomic together.

The normal write path should materialize all mutations into one `WriteBatch`.

This preserves the true transaction boundary.

---

# 67. Atomic WriteBatch

One successful append must use one RocksDB `WriteBatch`.

The batch contains:

```text
all authoritative event records
all EventId mappings
all type index entries
all tag index entries
new namespace Head
```

RocksDB provides atomic visibility of the batch across Column Families in the same database.

This is why all Kern CFs belong to one RocksDB database.

---

# 68. Head update in the same WriteBatch

The new head must be written in the same atomic batch.

Never:

```text
commit events/indexes
then update Head separately
```

A reader observing the new head must be guaranteed that all state up to that head is already visible.

This invariant is required for bounded lazy reads without long-lived snapshots.

---

# 69. Durability

The initial RocksDB engine uses durable append semantics.

The expected configuration is:

```text
WAL enabled
WriteOptions.sync(true)
```

or equivalent.

The caller is completed only after the durable RocksDB write succeeds.

---

# 70. Commit failure

If `db.write(...)` fails:

```text
no in-memory authoritative state is advanced
no subscriber wake-up is emitted
caller completes exceptionally
```

The persistent RocksDB database remains authoritative.

If failure happens after physical commit but before acknowledgement is delivered, EventId idempotency resolves retry ambiguity.

---

# 71. AppendCoordinator

The first implementation uses a single logical append coordinator.

This object represents the serialization point for concurrent append attempts.

Conceptually:

```text
concurrent callers
       ↓
bounded append queue
       ↓
AppendCoordinator
       ↓
RocksDB
```

The coordinator is justified because it represents a real responsibility: coordinating concurrent conditional writes to the log.

---

# 72. Why a single logical writer

Although RocksDB supports concurrent writes, Kern append semantics coordinate:

```text
EventId lookup
Head
Tail validation
Position allocation
WriteBatch construction
Head advancement
```

Using one logical writer initially avoids:

```text
global locks
CAS loops
TransactionDB
OptimisticTransactionDB
per-namespace locking
complex conflict races
```

and provides a simple correctness baseline.

Reads remain concurrent.

---

# 73. Single writer across namespaces

The initial coordinator serializes appends across all namespaces.

This does not violate namespace semantic independence.

It is an implementation throughput trade-off.

The design intentionally avoids creating one coordinator per namespace because namespaces may be numerous and dynamic.

Parallel append strategies may be considered after profiling.

---

# 74. Append queue

The coordinator receives work through a bounded queue.

The queue must not grow without limit.

When full, the runtime rejects new append admission according to the operational overload semantics.

The queue is infrastructure, not part of the Event Store domain API.

---

# 75. Optional in-memory head cache

The coordinator may maintain a cache:

```text
Namespace -> Position
```

to avoid repeated metadata reads.

However:

```text
persistent Head
    authoritative operational state

in-memory cache
    optimization
```

The cache must only advance after successful durable commit.

On startup it must be reconstructed from persistent state.

The initial implementation may omit the cache entirely.

---

# 76. Group commit

Group commit is intentionally deferred but supported by the architecture.

A future implementation may combine:

```text
Append A
Append B
Append C
```

into one physical RocksDB `WriteBatch` and one WAL sync.

However, the appends remain semantically separate.

They must be validated in logical order.

---

# 77. Group commit overlay requirement

Suppose A and B are grouped.

If A writes an event that invalidates B's Tail, B must fail even though A has not yet been separately committed to RocksDB.

Therefore group commit requires an in-memory overlay representing accepted writes earlier in the group.

Conflict detection must observe:

```text
persisted DB
+
pending accepted group entries
```

Group commit must not be introduced without preserving this rule.

---

# 78. Subscription implementation

For:

```text
Subscription(N,F,W)
```

the implementation can wait asynchronously until the namespace head advances.

Wake-up is only a hint.

When awakened:

```text
H = current Head
```

and the same filter is compiled over:

```text
(W, H]
```

using `RocksEventSelection`.

---

# 79. Subscription.next(count)

The implementation retrieves at most `count` Positions from the resulting Index.

It then maps them through:

```text
Events.at(Position)
```

into a new bounded `StoredEvents`.

No transient subscriber event buffer is required.

The authoritative log remains the backlog.

---

# 80. New watermark for Subscription.next()

The returned `StoredEvents` requires a new upper boundary.

Two cases exist.

## Current window exhausted before count

Suppose the current Head is H and fewer than `count` matching events exist in:

```text
(W, H]
```

After fully scanning through H:

```text
new watermark = H
```

because the implementation has established there are no additional matching events through H.

## count reached before current window is exhausted

Suppose more than `count` matching events already exist.

If the last returned matching event is at P:

```text
new watermark = P
```

or an equivalent safe boundary that does not cross the next undispatched matching event.

This prevents gaps.

---

# 81. Example next(count)

Given:

```text
previous watermark = P100
current head       = P500

matching positions:
P105
P110
P120
P130
P140
```

For:

```java
next(3)
```

return:

```text
P105
P110
P120
```

with new observation boundary:

```text
P120
```

The next:

```java
returned.follow().next(...)
```

will continue after P120 and therefore still observe P130 and P140.

---

# 82. Subscription waiters

Pending subscription requests should not hold event records in memory.

A waiter only needs enough information to retry the authoritative bounded query when the head changes.

Conceptually:

```text
Namespace
Filter
Watermark
requested count
completion handle
```

These are runtime objects rather than persistent storage records.

---

# 83. Wake-up after commit

The append path order is:

```text
WriteBatch durable commit
        ↓
persistent Head visible
        ↓
wake namespace waiters
        ↓
waiters query persisted log
```

Never wake before durable commit.

---

# 84. Index scan bounds

Every physical leaf index must enforce its observation window at RocksDB level as much as practical.

Semantics:

```text
Position > after
Position <= watermark
```

The implementation must not scan the full index and filter bounds only afterward.

---

# 85. Exclusive lower bound

Because public `Position` does not expose arithmetic, the implementation should not rely semantically on:

```text
after + 1
```

A straightforward strategy is:

```text
seek to key ending in after
if exact after exists:
    advance once
```

Then continue.

A more optimized lower-bound key may be introduced if useful.

---

# 86. Upper bound

The implementation may use:

```text
ReadOptions iterateUpperBound
```

where convenient, but correctness must not depend on advanced RocksDB bound configuration.

The baseline implementation may simply stop iteration when:

```text
prefix changes
OR
decoded Position > watermark
```

This is easy to verify.

---

# 87. Prefix correctness

An index iterator must stop when its logical prefix changes.

Example for:

```text
[N][courseId][c1][Position]
```

the iterator must stop before:

```text
[N][courseId][c2][Position]
```

even if the position remains within the watermark.

Prefix ownership belongs inside the concrete index implementation.

It should not be exposed through getters for an external executor to interpret.

---

# 88. Reading Position from index keys

Because Position is the final fixed-width segment, an index iterator can efficiently decode the final 8 bytes.

It does not need to decode the entire prefix on every entry.

This is a useful low-level optimization.

The implementation should still copy or decode values safely rather than retain invalid references to RocksDB iterator buffers.

---

# 89. Column Family tuning philosophy

Correctness must not depend on RocksDB tuning options.

Options such as:

```text
Bloom filters
prefix extractors
block cache sizes
compression
compaction strategy
```

are performance choices.

The first configuration should be conservative and measurable.

---

# 90. events CF tuning

Expected workload:

```text
sequential-ish append
ordered range reads
large values
authoritative data
```

Recommended initial direction:

```text
compression enabled
reasonable block cache
normal block-based table
range-scan friendly configuration
```

Exact values should be benchmarked rather than guessed.

---

# 91. event_ids CF tuning

Expected workload:

```text
point lookup on every append
small records
high read hit rate
```

This CF is a strong candidate for:

```text
Bloom filter
index/filter blocks cached
```

because point lookup dominates.

---

# 92. type_index and tag_index tuning

Expected workload:

```text
prefix/range scans
tiny values
large number of entries
high write amplification
```

Optimization should focus on:

```text
efficient seek
compact keys
sequential iteration
compaction behavior
```

`tag_index` is expected to be substantially larger than `type_index`.

---

# 93. Prefix extractors

A custom RocksDB prefix extractor may improve index lookup performance.

However, variable-length prefixes make this more complex.

The initial implementation does not require a custom `SliceTransform`.

Correct range seek works without one.

Prefix extraction should be added only after benchmarking demonstrates value.

---

# 94. Compression

The event record should not be application-compressed merely because RocksDB supports compression.

The initial approach is:

```text
Data
    ↓
canonical serialization
    ↓
event record
    ↓
RocksDB block compression
```

This avoids unnecessary double compression.

Application-level compression remains available for future protocol/encryption requirements.

---

# 95. Encryption

Encryption is not required by the initial physical record format.

However, the record envelope should remain extensible through:

```text
recordVersion
flags
```

so future encrypted Data representation, key identifiers or other transformations can be introduced without redesigning the complete log.

---

# 96. Record checksum

No additional application-level record checksum is required initially.

RocksDB already provides physical block/SST checksums.

`EventFingerprint` has a different responsibility: semantic identity verification.

An application-level checksum can be added later if backup/export requirements justify it.

---

# 97. Write amplification

For an event with T tags, a normal append writes approximately:

```text
1 events record
1 event_ids record
1 type_index entry
T tag_index entries
```

plus one Head update per batch.

Therefore logical write amplification is approximately:

```text
3 + T
```

entries per event, excluding RocksDB internal compaction amplification.

This directly motivates an operational limit on tag count.

---

# 98. Query amplification

For:

```text
AllEvents(
    TaggedAs(A),
    TaggedAs(B),
    TypedBy(T)
)
```

the initial query cost is approximately:

```text
scan posting list A
scan posting list B
scan posting list T
streaming intersection
read authoritative events only for final matches
```

This cost model is predictable and measurable.

---

# 99. Recovery model

The authoritative hierarchy is:

```text
events
    authoritative

event_ids
type_index
tag_index
Head
    derived / reconstructible
```

This distinction drives startup validation and rebuild.

---

# 100. event_ids is derived but correctness-critical

`event_ids` can be rebuilt from `events`.

However, append must not proceed if `event_ids` cannot be trusted, because idempotency and EventId uniqueness depend on it.

Therefore:

```text
rebuildable
≠
optional during writes
```

---

# 101. type/tag indexes are derived

`type_index` and `tag_index` are optimization structures.

They can be rebuilt entirely from authoritative event records.

If unavailable, the engine may optionally fall back to scanning `events`.

---

# 102. ScanningIndex

A useful correctness fallback is:

```java
final class ScanningIndex implements Index {
    ...
}
```

It represents matching positions obtained by scanning authoritative event records within the bounded window.

This allows:

```text
missing optimization index
    ≠
semantic impossibility
```

provided the runtime policy permits degraded scans.

---

# 103. EventFilter evaluation during fallback scan

A fallback scan can interpret the same `EventFilter` through an in-memory/single-event `EventSelection<Boolean>` or equivalent matcher.

Thus the filter algebra remains the only semantic source of truth.

No separate procedural matching model is required.

---

# 104. Degraded versus unavailable behavior

Possible runtime behavior:

```text
tag index unavailable
    -> degraded scan if enabled

event_ids unavailable
    -> writes disabled / recovery required

events corrupt
    -> fatal/not ready
```

These operational states belong to the runtime, not `kern-api`.

---

# 105. Head recovery

If the persisted Head is missing or inconsistent, derive:

```text
Head(N)
=
maximum Position in events for N
```

A new namespace with no events has:

```text
Position.beginning()
```

---

# 106. Startup validation

A normal startup should perform inexpensive checks:

```text
open database
validate FORMAT_VERSION
validate required CF presence
verify events readability
verify/reconstruct namespace heads
validate critical derived structures sufficiently for safe operation
```

It should not perform a full multi-million-event consistency scan every time.

---

# 107. Deep integrity verification

A separate administrative operation may verify:

```text
for every event:

event_ids mapping correct
type index membership exists
all tag index memberships exist
fingerprint recomputes correctly
```

This is intentionally outside the normal startup path.

---

# 108. Physical invariants

For every authoritative event E in namespace N at Position P:

```text
event_ids[N,E.id]
    =
(P, fingerprint(E))
```

and:

```text
type_index[N,E.type,P]
    exists
```

and for every tag T:

```text
tag_index[N,T,P]
    exists
```

Additionally:

```text
Head(N)
=
max Position(events in N)
```

These are the principal physical consistency invariants.

---

# 109. Why WriteBatch is sufficient for normal crash safety

With:

```text
single logical writer
one atomic WriteBatch per append
WAL enabled
sync=true
```

a normal process crash should leave either:

```text
the whole append visible
```

or:

```text
none of it visible
```

Therefore normal restart relies on RocksDB WAL recovery and does not require index rebuild.

Rebuild is for:

```text
corruption
manual index loss
format migration
explicit maintenance
```

not normal crash recovery.

---

# 110. Offline rebuild v1

The initial index rebuild strategy may be offline.

Procedure:

```text
stop writes / mark runtime not ready

drop/recreate derived CFs

scan events in Position order

for each event:
    decode
    recompute fingerprint
    write event_ids
    write type_index
    write tag_index

derive heads

validate

return to READY
```

This is deliberately simpler than online rebuild.

---

# 111. Why online rebuild is deferred

Online rebuild requires additional semantics:

```text
new events during rebuild
cutover
rebuild checkpoint
dual writes
duplicate prevention
index generation management
```

These concerns are unnecessary for the initial version.

They should only be introduced when availability requirements justify the complexity.

---

# 112. Physical format migration

Index rebuild and physical format migration are different operations.

```text
rebuild:
    regenerate derived state
    same format

migration:
    transform authoritative persisted representation
    possibly new key/record format
```

A physical format migration may require:

```text
new database
copy/transform
verification
cutover
```

and should be treated independently.

---

# 113. Record versioning

Every authoritative event record contains:

```text
recordVersion
```

The decoder may support multiple historical record versions when appropriate.

This allows controlled evolution of the physical event encoding.

---

# 114. Derived index versioning

Derived indexes may use metadata-level versions:

```text
EVENT_IDS_VERSION
TYPE_INDEX_VERSION
TAG_INDEX_VERSION
```

rather than versioning every entry.

Because indexes are rebuildable, changing their format can simply require regeneration.

---

# 115. Fingerprint versioning

The fingerprint algorithm/canonical format must also be versioned sufficiently to allow:

```text
future rebuild
identity verification
historical compatibility
```

The database cannot silently recompute old `event_ids` values with incompatible fingerprint semantics.

---

# 116. Backup

The authoritative minimum backup consists of:

```text
events
physical format metadata
```

because derived structures can be rebuilt.

In practice, backing up all CFs may significantly reduce restore time.

The conceptual distinction remains:

```text
events must survive

indexes may be regenerated
```

---

# 117. Restore

A restore must preserve exactly:

```text
Namespace
Position
EventId
EventType
storedAt
Tags
Data
```

Positions must never be reassigned.

Consumer checkpoints and external references may depend on them.

Restore is restoration of the same log, not replay into a new sequence.

---

# 118. Default Column Family

RocksDB's required default CF should be opened but left unused for Kern data.

All persisted Kern structures live in explicitly named CFs.

This keeps the physical schema inspectable and deliberate.

---

# 119. Database ownership

All Kern Column Families belong to the same RocksDB database.

This is required to exploit atomic `WriteBatch` behavior across:

```text
events
event_ids
type_index
tag_index
metadata
```

The initial implementation does not split these structures across multiple RocksDB database directories.

---

# 120. Suggested package organization

An initial package structure may be:

```text
it.riccisi.kern.rocks
│
├── RocksEventStore
├── RocksStoredEvents
├── RocksTail
├── RocksSubscription
│
├── append
│   ├── AppendCoordinator
│   ├── EventsBatch
│   └── EventEntry
│
├── index
│   ├── Index
│   ├── AllIndex
│   ├── EmptyIndex
│   ├── TypeIndex
│   ├── TagIndex
│   ├── AndIndex
│   ├── OrIndex
│   ├── ScanningIndex
│   └── RocksEventSelection
│
├── log
│   ├── Events
│   ├── Head
│   └── EventIds
│
├── key
│   ├── EventKey
│   ├── EventIdKey
│   ├── TypeIndexPrefix
│   ├── TypeIndexKey
│   ├── TagIndexPrefix
│   ├── TagIndexKey
│   └── HeadKey
│
└── encoding
    ├── EventEncoder
    ├── EventDecoder
    ├── EncodedEvent
    └── physical record codecs
```

This structure is a starting point, not a rigid requirement.

Packages should remain small enough that unnecessary layering does not emerge.

---

# 121. Naming guidance

Avoid generic low-information names such as:

```text
Manager
Service
Helper
Util
Repository
DAO
```

unless a class genuinely represents that concept.

Prefer names describing the represented object or responsibility:

```text
Head
EventIds
EventEntry
TypeIndex
TagIndex
AppendCoordinator
EventKey
EncodedEvent
```

---

# 122. No generic RocksDB abstraction framework

The implementation should not introduce abstractions such as:

```text
GenericColumn<K,V>
GenericRepository
StorageEngine<T>
KeyValueStoreAdapter
TransactionManager
```

merely to hide RocksDB.

The module is intentionally a RocksDB implementation.

Its code is allowed to use RocksDB directly where doing so is clear and cohesive.

---

# 123. Design rule for wrapping RocksDB API concepts

Introduce an object when it represents a meaningful concept or centralizes real complexity.

Examples where wrapping is useful:

```text
EventKey
    encapsulates physical key layout

TypeIndex
    encapsulates a posting-list scan

Head
    represents namespace frontier

EventEntry
    represents all physical writes for one event
```

Examples where wrapping may not add value:

```text
RocksBatch
    if it only forwards to WriteBatch

RocksIteratorAdapter
    if it merely renames methods

ColumnFamilyManager
    if it only stores handles
```

Pragmatism is preferred over abstraction for abstraction's sake.

---

# 124. Performance philosophy

The initial engine should optimize structural correctness and predictable access patterns first.

The v1 performance strategy is:

```text
ordered physical keys
bounded range scans
streaming index composition
event decoding only after index resolution
single serialized write path
atomic WriteBatch
durable WAL
```

This already provides a strong baseline.

---

# 125. Benchmark-driven optimization

The following should be added only after measurement:

```text
compound indexes
seek-aware intersections
cardinality statistics
custom prefix extractors
group commit
per-namespace write concurrency
advanced compaction tuning
adaptive indexing
```

This keeps the initial implementation understandable and testable.

---

# 126. Required implementation tests

The RocksDB engine should be exercised through the common Kern EventStore conformance suite plus RocksDB-specific tests.

Important categories include:

```text
Position ordering
key ordering
prefix correctness
TagIndex scans
TypeIndex scans
AndIndex intersection
OrIndex union
AllIndex scan
EventId idempotency
identity collision
batch atomicity
Tail conflict races
namespace isolation
subscription boundary correctness
WAL crash recovery
derived index rebuild
physical format validation
```

---

# 127. Key codec property tests

The key encoding deserves dedicated property-based tests.

For Position:

```text
P1 < P2
must imply
bytes(P1) lexicographically < bytes(P2)
```

For variable segments:

```text
decode(encode(X)) = X
```

and composed keys must be unambiguous.

Prefix tests should verify that no valid key for another logical prefix is accidentally included in a scan.

---

# 128. Index property tests

For every `Index` implementation verify:

```text
positions ordered
positions unique
all positions inside (after, watermark]
```

For `AndIndex`:

```text
result == mathematical intersection
```

For `OrIndex`:

```text
result == mathematical union
```

with large randomized input sequences.

---

# 129. Append race tests

Critical concurrency tests include:

```text
two Tails from same observation
first append relevant to second
exactly one succeeds

concurrent irrelevant append
does not invalidate Tail

acknowledgement lost then retry
idempotent success

partial duplicate input batch
fails

same EventId different content
fails
```

The single coordinator simplifies implementation but does not remove the need to test semantics.

---

# 130. Recovery tests

Recovery tests should include:

```text
delete type_index and rebuild
delete tag_index and rebuild
delete event_ids and ensure writes disabled
rebuild event_ids
remove Head and derive from events
simulate unsupported FORMAT_VERSION
verify restored Positions unchanged
```

---

# 131. Final RocksDB object flow

The read side is:

```text
EventStore.events(N,F,A)
        ↓
Head.position() = W
        ↓
F.describe(
    RocksEventSelection(N,A,W)
)
        ↓
Index
        ↓
positions()
        ↓
Events.at(Position)
        ↓
RocksStoredEvents
```

The write side is:

```text
Tail(N,F,W)
        +
Event batch
        ↓
EventsBatch
        ↓
EncodedEvents
        ↓
EventIds
        ↓
Head
        ↓
conflict Index over (W,H]
        ↓
Position allocation
        ↓
EventEntries
        ↓
WriteBatch
        ↓
sync WAL commit
        ↓
Head visible
        ↓
subscription wake-up
```

The follow side is:

```text
Subscription(N,F,W)
        ↓
wait for Head > W
        ↓
F.describe(
    RocksEventSelection(N,W,H)
)
        ↓
Index
        ↓
take at most count Positions
        ↓
Events.at(Position)
        ↓
new RocksStoredEvents
```

---

# 132. Final decision

The Kern RocksDB engine adopts:

```text
events
    as the authoritative append-only log

event_ids
    as EventId/idempotency lookup

type_index
tag_index
    as derived ordered posting lists

metadata/HEAD
    as reconstructible namespace frontier

Index.positions()
    as the internal query composition abstraction

AndIndex / OrIndex
    as lazy streaming set operations

one logical append coordinator
    as the initial concurrency model

one atomic RocksDB WriteBatch
    per successful append

WAL + sync
    as the initial durability mechanism

offline rebuild
    as the initial recovery strategy for derived indexes
```

The engine remains intentionally RocksDB-specific while using object-oriented composition where it improves clarity, cohesion, testability and correctness.

RocksDB is the physical mechanism.

Kern semantics remain the authority.