# Kern — Agent Guidelines

## 1. Purpose

Kern is an Event Store designed around a semantic, object-oriented model rather
than around the capabilities or API shape of a particular storage engine.

The repository contains Architecture Decision Records (ADRs) defining the
architecture, semantics, invariants, runtime behavior, and storage-engine design.

These ADRs are the primary architectural authority for the project.

This document defines permanent rules for AI agents and contributors working
on the repository.

### Build e Java

- The project requires Java 24 to compile and run the tests.
- On the user’s Mac, the global default may remain a different version of Java, such as Java 17.
- To run Maven in this repository without changing the global default, use the JDK 24 installed via Homebrew:

```shell
JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home ./mvnw verify
```

- Do not modify `~/.zshrc`, `PATH`, the global `JAVA_HOME` or the symlinks in `/Library/Java/JavaVirtualMachines` just to run the project’s tests.

## 2. Architectural authority

Before making architectural or non-trivial design changes:

1. Read the relevant ADRs.
2. Understand the semantic model before inspecting implementation details.
3. Preserve the terminology defined by the ADRs.
4. Preserve explicitly documented invariants.
5. Do not silently replace an ADR decision with a more conventional solution.
6. Use the relative OO skills in order to manage the task.

When an ADR and your usual engineering instincts disagree:

> Follow the ADR and report the disagreement instead of silently changing
> the architecture.

If a required decision is not covered by the ADRs and has architectural impact,
do not make it implicitly.

Report it as:

OPEN DECISION

- Problem
- Why a decision is required
- Alternatives
- Consequences
- Recommended option

Small local implementation choices that do not affect public contracts,
semantics, module boundaries, persistence compatibility, or architectural
direction do not require an ADR.

---

## 3. Design direction

Kern follows a top-down design process:

    semantic model
        ↓
    contracts and invariants
        ↓
    implementation model
        ↓
    technology-specific mechanisms

Do not derive the domain model from RocksDB, networking libraries, serialization
formats, frameworks, or other implementation technologies.

Technology implements the Kern model.

It does not define it.

---

## 4. Object-oriented design

Kern deliberately follows a strongly object-oriented design style.

- Always consult the wiki on good OO modelling at `/Users/simone/Library/Mobile Documents/iCloud~md~obsidian/Documents/OO/wiki` before proposing or implementing architectural refactorings, domain modelling, OO contracts and significant design choices.
- Use the OO wiki as the primary reference for cohesion, immutability, decoration, naming, coupling and object responsibilities.

### Objects represent concepts

Prefer objects representing meaningful concepts and responsibilities.

Examples include:

    Event
    StoredEvent
    StoredEvents
    Tail
    Subscription
    Position
    NamespaceId
    EventFilter
    Index
    Head
    EventIds
    EventEntry

Avoid turning these concepts into passive data structures manipulated by
procedural services.

### Immutability

Prefer structurally immutable objects.

An object may animate, observe, or manipulate mutable external state while
remaining structurally immutable itself.

For example, an object containing a reference to RocksDB may still be considered
structurally immutable even though the database changes over time.

Do not automatically classify an object as mutable merely because the external
resource it represents changes state.

### Behavior belongs to objects

Avoid extracting behavior into procedural classes merely so that domain objects
can become collections of getters.

Prefer telling an object what representation or behavior is required over
extracting all of its state and manipulating that state elsewhere.

### Cohesion

Classes should have small, explicit, cohesive responsibilities.

If a class requires many unrelated dependencies or constructor arguments,
consider whether it actually represents multiple concepts.

---

## 5. Naming

Prefer names representing concepts.

Good examples:

    Head
    EventIds
    EventEntry
    TypeIndex
    TagIndex
    AppendCoordinator
    EventKey
    EncodedEvent

Avoid low-information names such as:

    Manager
    Service
    Helper
    Utils
    DAO
    Repository

unless the object genuinely represents that concept.

Do not append architectural suffixes merely to indicate a layer.

---

## 6. Constructors and object creation

Prefer constructors and composition.

Avoid Factory and Builder patterns unless they solve an actual problem.

Do not introduce them simply because construction involves several objects.

If construction becomes excessively complicated, first investigate whether the
object has too many responsibilities.

### Lombok

- Use Lombok to reduce mechanical boilerplate when it improves readability without compromising the object-oriented model.
- Prefer `@RequiredArgsConstructor` for trivial primary constructors of immutable classes with `final` dependencies.
- Prefer `@NonNull` on `final` fields and on parameters of concrete methods when it replaces trivial null checks without obscuring the public contract.
- In abstract contracts or interfaces without a body, do not treat `@NonNull` as runtime enforcement: in such cases, it serves as documentation of the contract, not as a substitute for executable logic.
- Do not introduce Lombok if the benefit is minimal or if the annotation makes the code less explicit than a hand-written constructor or method.
- Do not use Lombok to generate patterns that undermine OO discipline, in particular `@Data`, `@Setter`, superfluous builders, or excessive getters/setters on domain objects.
- The aim is to eliminate boilerplate, not to mask anemic objects, ambiguous contracts, avoidable mutability or poorly distributed responsibilities.

### Cactoos

- Consider `org.cactoos:cactoos` before introducing custom utilities or equivalent solutions based on standard Java procedural helpers, particularly when working with text, scalars, iterables, maps, streams or small, basic transformations.
- Favour Cactoos’ abstractions when they allow you to express object responsibilities, composition and lazy evaluation in a way that is more consistent with the project’s model.
- Do not introduce new static utilities or ad hoc wrappers if Cactoos already provides a suitable and readable object for the problem.
- Do not use Cactoos dogmatically: if using the library makes the code less clear, more contrived or at odds with the module’s local idioms, opt for the simplest and most consistent solution.
- The aim is not to mechanically replace the entire standard Java library, but to avoid procedural style, duplication of primitive utilities and unnecessary custom solutions when the project already has a well-established OO alternative.

---

## 7. Public API discipline

The public Kern API must remain small and semantic.

Do not add convenience methods merely because they are easy to implement.

Every public method increases the conceptual surface of Kern.

Before adding a method ask:

1. Does this operation belong to this object?
2. Does it represent a real semantic operation?
3. Can the same behavior already be obtained through composition?
4. Would adding it expose an implementation detail?

Prefer simple Java contracts where sufficient:

    Iterable<T>
    CompletionStage<T>

Do not expose RocksDB, Cactoos, transport, serialization, or framework-specific
types through the semantic API unless explicitly required by an ADR.

---

## 8. Core semantic concepts

The ADRs are authoritative for exact contracts, but agents must preserve the
following conceptual model.

### Event

An `Event` represents a fact accepted for persistence.

It has semantic identity, type, tags, and payload/data.

### StoredEvent

A `StoredEvent` represents an Event after persistence.

Persistence adds storage facts such as:

    Position
    storedAt

Do not make clients provide storage-generated facts.

### StoredEvents

`StoredEvents` represents a bounded observation of persisted events.

It is not merely a collection.

It carries an observation boundary/watermark even when that boundary is not
exposed directly.

### Tail

A `Tail` represents the capability to conditionally append from the observation
from which it was obtained.

Appending through a Tail is a manipulator operation.

Do not mutate the Tail after append.

### Subscription

A `Subscription` represents an immutable continuation of an observation.

It is not a mutable server-side cursor.

Calling `next(count)` does not advance the Subscription.

Progress is represented by following the returned `StoredEvents`.

### Position

`Position` identifies ordering in the persisted event log.

Do not expose arithmetic assumptions such as `next()` merely because one storage
implementation uses contiguous integers.

---

## 9. Event filters

`EventFilter` is a semantic Composite.

Filters describe themselves through an interpretation contract such as
`EventSelection<T>`.

The intended model includes concepts such as:

    AllEvents
    AnyEvents
    TypedBy
    TaggedAs

Do not implement filter processing by exposing filter internals and building a
procedural visitor that inspects them externally.

The same filter must be interpretable by different representations, including
where appropriate:

    RocksDB indexes
    in-memory matching
    protocol serialization
    diagnostic rendering

---

## 10. NamespaceId semantics

NamespaceId is part of the semantic Event Store boundary.

Do not hide NamespaceId as implicit mutable configuration when doing so would make
the semantics ambiguous.

Positions are namespace-local unless an ADR explicitly changes this decision.

Namespace isolation must hold for:

    reads
    tails
    conflicts
    idempotency
    subscriptions
    storage keys

---

## 11. Bounded observations

Reads are bounded observations.

Conceptually:

    after < Position <= watermark

The watermark is captured when the observation is created.

Do not accidentally turn `StoredEvents` into a live iterator whose result changes
while being consumed.

This bounded-observation model is fundamental to:

    deterministic reads
    Tail semantics
    replay
    follow()
    subscriptions
    snapshot-free RocksDB iteration

---

## 12. Tail and conflict semantics

Tail validity is based on the EventFilter that produced its observation.

A Tail becomes stale only when an event relevant to that consistency boundary
appears after its watermark.

Do not replace this with aggregate-version semantics unless an ADR explicitly
changes the model.

Conflict detection conceptually examines:

    (tail watermark, current head]

using the original EventFilter.

Only one matching event is required to prove staleness.

---

## 13. Event identity and idempotency

EventId provides semantic event identity.

Idempotent retry behavior is part of the Event Store contract.

For append:

- missing EventIds may be appended;
- all EventIds already present with identical semantic fingerprints mean
  idempotent success;
- partial duplicates are invalid;
- same EventId with different semantic content is an identity violation.

Idempotency checking must happen before Tail staleness checking.

This ordering is essential for safe retry after a successful append whose
acknowledgement was lost.

---

## 14. Subscription semantics

`Subscription.next(count)` implements asynchronous demand-driven continuation.

The client controls back-pressure by deciding:

    when to request
    how many events to request

`count` is a maximum, not a minimum.

If matching events already exist:

    complete immediately with 1..count events

If no matching event exists:

    remain asynchronously pending

When at least one matching persisted event becomes available:

    complete with up to count events

A normally completed `next()` must not return an empty `StoredEvents`.

Do not implement busy polling.

Do not require a permanently blocked platform thread.

Notifications are wake-up hints only.

The persisted event log remains authoritative.

---

## 15. Subscription continuation

A Subscription is immutable.

Repeated calls on the same Subscription begin from the same observation
boundary.

Progress is represented as:

    StoredEvents S0
        ↓ follow()
    Subscription Q0
        ↓ next(n)
    StoredEvents S1
        ↓ follow()
    Subscription Q1

Do not introduce mutable `pause()`, `resume()`, or cursor advancement merely to
model back-pressure.

No demand means no delivery.

---

## 16. RocksDB implementation philosophy

The RocksDB module is allowed to speak RocksDB.

Do not create a generic storage framework merely to hide the technology.

Avoid speculative abstractions such as:

    GenericRepository
    StorageEngine<T>
    GenericColumn<K,V>
    TransactionManager
    KeyValueStoreAdapter

unless a concrete architectural requirement emerges.

The principle is:

> Hide accidental complexity, not the storage technology itself.

Use RocksDB types directly inside cohesive implementation objects when that is
clearer.

---

## 17. RocksDB physical model

The initial physical design uses:

    events
    event_ids
    type_index
    tag_index
    metadata

`events` is authoritative.

The following are derived/reconstructible:

    event_ids
    type_index
    tag_index
    Head

However, `event_ids` is correctness-critical during append and must be valid
before writes are accepted.

---

## 18. RocksDB Index model

Inside the RocksDB implementation:

    Index

represents an ordered selection of Positions.

Its semantic contract is intentionally minimal:

    Iterable<Position> positions();

Indexes must provide:

- lazy iteration;
- strictly increasing Position order;
- no duplicates;
- positions bounded to the observation window.

Expected implementations include:

    AllIndex
    EmptyIndex
    TypeIndex
    TagIndex
    AndIndex
    OrIndex
    ScanningIndex

`AndIndex` performs lazy streaming intersection.

`OrIndex` performs lazy streaming union with deduplication.

Do not materialize complete result sets unless measurement demonstrates that
doing so is necessary.

---

## 19. RocksDB query path

Preserve this conceptual pipeline:

    EventFilter
        ↓
    RocksEventSelection
        ↓
    Index
        ↓
    Iterable<Position>
        ↓
    Events.at(Position)
        ↓
    StoredEvent

Indexes operate on Positions.

Do not decode StoredEvents while composing index intersections/unions.

---

## 20. RocksDB keys

Do not scatter manual `byte[]` concatenation throughout the implementation.

Represent meaningful key layouts using cohesive objects such as:

    EventKey
    EventIdKey
    TypeIndexPrefix
    TypeIndexKey
    TagIndexPrefix
    TagIndexKey
    HeadKey

Variable-length segments must use an unambiguous canonical encoding.

Position encoding must preserve numeric order under RocksDB lexicographical
ordering.

For the initial RocksDB implementation this means fixed-width big-endian
encoding.

---

## 21. RocksDB write path

A successful append must atomically write:

    event records
    EventId mappings
    type index entries
    tag index entries
    new Head

Use one RocksDB `WriteBatch`.

The initial durability model requires WAL and synchronous durable acknowledgement.

Never advance in-memory authoritative state or wake subscriptions before the
durable commit succeeds.

---

## 22. Append coordination

The initial RocksDB implementation uses one logical AppendCoordinator.

Do not prematurely introduce:

    TransactionDB
    OptimisticTransactionDB
    per-namespace writers
    lock striping
    distributed coordination

The simple serialized writer is the correctness baseline.

Reads remain concurrent.

Optimization requires measurement.

---

## 23. RocksDB subscriptions

Pending subscriptions must not hold long-lived:

    RocksIterator
    Snapshot
    ReadOptions

while waiting.

Store logical continuation coordinates instead.

After a durable append:

    Head advances
        ↓
    namespace waiters are notified
        ↓
    waiter queries authoritative persisted state
        ↓
    matching data completes next()

Wake-up does not equal event delivery.

Take particular care to prevent the lost-wakeup race between:

    query
    waiter registration
    concurrent append

Use head rechecking or another demonstrably correct strategy.

---

## 24. Recovery

Treat:

    events

as authoritative.

Derived structures may be rebuilt from it.

Normal process crashes should rely on RocksDB WAL/atomic WriteBatch recovery.

Do not rebuild indexes on every startup.

The initial rebuild strategy may be offline.

Do not implement online rebuild unless requirements justify its additional
complexity.

Restore must preserve Position exactly.

---

## 25. Dependencies

The project uses Maven.

Base Maven groupId:

    it.riccisi.kern

Cactoos may be used where its object-oriented primitives simplify implementation,
including concepts such as:

    Bytes
    Mapped
    Filtered
    Joined

Cactoos is an implementation utility.

Do not unnecessarily expose Cactoos types through Kern's public API.

Do not introduce large frameworks without a concrete requirement.

In particular, do not automatically add:

    Spring
    JPA
    Reactor
    RxJava
    Kafka
    Akka

Kern should remain a small, focused Java library.

---

## 26. Module discipline

Keep semantic contracts independent from implementation technologies.

Expected module directions include:

    kern-api
    kern-storage-memory
    kern-storage-rocksdb
    kern-testkit

Additional modules such as:

    kern-client
    kern-server
    kern-protocol
    kern-spi

must only be introduced when their responsibility actually exists.

Do not create an SPI module full of speculative abstractions.

---

## 27. Testing philosophy

Behavior and invariants are more important than implementation structure.

Create reusable conformance tests for EventStore implementations.

Important semantic areas include:

- observation boundaries;
- filter semantics;
- namespace isolation;
- Tail conflicts;
- atomic batch append;
- EventId idempotency;
- subscriptions;
- continuation boundaries;
- no event gaps;
- asynchronous waiting.

RocksDB-specific tests must additionally cover:

- key ordering;
- prefix correctness;
- index intersection/union;
- atomic WriteBatch behavior;
- WAL recovery;
- index rebuild;
- format compatibility;
- lost-wakeup races.

Use property-based testing where it provides meaningful coverage, especially for
ordered indexes and physical key encoding.

### JUnit test layout

JUnit tests must act as precise diagnostics for the production object that most
likely broke.

Name unit test classes after the live class they protect, using the `Test`
suffix in the same package under `src/test/java`.

For example:

    src/main/java/it/riccisi/kern/EventTags.java
    src/test/java/it/riccisi/kern/EventTagsTest.java

Use the `ITCase` suffix for Maven integration tests.

If an integration test belongs to one live class, keep the same mapping:

    StoredEvents.java
    StoredEventsITCase.java

If an integration test exercises a scenario that does not map to one live
class, place it under a test-only `it` package and use a scenario name:

    src/test/java/it/riccisi/kern/it/ConcurrentAppendITCase.java

Do not create test-only support packages full of utilities, helpers, builders,
fixtures, or shared static setup methods.

Test classes should contain test methods only. If a test needs reusable
prerequisites, model them as small fake objects with explicit responsibilities
and test them like other objects, or use narrowly scoped JUnit mechanisms only
when they are simpler and do not hide the object under test.

Assertions should carry enough diagnostic information, either through the test
method name and assertion shape or through an explicit assertion message, for a
failing test to point quickly to the broken behavior.

---

## 28. Development workflow

Work incrementally.

For each meaningful increment:

1. Identify the ADR behavior/invariant being implemented.
2. Implement the smallest coherent change.
3. Add or update tests.
4. Run the relevant test suite.
5. Do not proceed while tests are failing.
6. Avoid speculative refactoring unrelated to the current task.

Prefer:

    simple + correct + composable

over:

    generic + configurable + speculative

Do not implement future capabilities merely because the architecture could
support them.

---

## 29. Architectural changes

If implementation reveals that an ADR decision appears impossible, contradictory,
or unnecessarily harmful:

Do not silently work around it.

Report:

    ARCHITECTURAL ISSUE

    ADR / decision involved:
    ...

    Problem:
    ...

    Evidence:
    ...

    Possible alternatives:
    ...

    Recommended action:
    ...

The architecture can evolve, but intentionally.

---

## 30. Final principle

When choosing between a conventional Java architecture and the Kern semantic
model, preserve the Kern model.

When choosing between an abstraction and direct technology usage, ask whether
the abstraction represents a real concept.

When choosing between cleverness and simplicity, prefer the simplest solution
that preserves all invariants.

The objective is not merely to make Kern work.

The objective is to make its code express clearly what an Event Store is.
