# ADR — Kern Semantic Event Store Model and Public API

## Status

**Accepted**

## Purpose

This ADR defines the semantic model and public API of Kern.

Its purpose is not to describe a particular storage engine, transport protocol, runtime framework, or deployment topology. Those concerns are deliberately separated from the model defined here.

The objective is to describe **what Kern is**, what its public objects represent, and which semantic guarantees every implementation must preserve.

The same model must remain valid whether Kern is:

- embedded directly into an application;
- backed by RocksDB;
- implemented in memory;
- exposed by a standalone server;
- accessed through a remote Java client;
- implemented by another future storage technology.

The physical implementation is allowed to vary substantially.

The semantics are not.

---

# 1. Design philosophy

Kern is designed from the domain of an Event Store outward.

The public API must therefore model the concepts that naturally exist in an event log rather than exposing concepts inherited from a storage engine.

The primary abstractions are:

```text
EventStore
Event
StoredEvent
StoredEvents
EventFilter
Tail
Subscription
Position
Namespace
Tags
Data
```

Each abstraction exists because it represents something meaningful in the Event Store domain.

The API intentionally avoids exposing concepts such as:

```text
Repository
DTO
QueryResult
AppendRequest
AppendResult
ExpectedVersion
StorageRecord
RocksDB key
ColumnFamily
Database transaction
```

Those may exist internally, but they are implementation mechanisms rather than domain concepts.

---

# 2. Objects represent concepts, not bags of state

A recurring design principle is that an object should answer:

> What does this object represent?

rather than:

> Which fields does this object contain?

For example:

```text
Event
    a domain fact

StoredEvent
    that fact after it has been recorded in the log

StoredEvents
    a bounded observation of persisted events

Tail
    the capability to continue that observation through a write

Subscription
    the capability to continue that observation through future reads

Position
    a point in the logical event log

EventFilter
    a declarative description of which events are relevant

Data
    structured information carried by an Event
```

This distinction influences both the API shape and implementation strategy.

---

# 3. Structural immutability

Kern objects should be structurally immutable by default.

An object may represent mutable external reality without becoming a mutable container itself.

For example:

```java
tail.append(event);
```

changes the event log.

It does not need to mutate the structural state of `Tail`.

Similarly:

```java
StoredEvents events =
    store.events(namespace, filter, after);
```

represents a stable observation of an event log that may continue evolving after the object is created.

The general model is:

```text
immutable object
       │
       │ represents / animates
       ▼
mutable external reality
```

Structural immutability is therefore distinct from:

- absence of side effects;
- functional purity;
- constancy of the represented reality.

---

# 4. Event lifecycle

The fundamental lifecycle is:

```text
Command
   │
   ▼
Decision
   │
   ▼
 Event
   │
   │ append
   ▼
StoredEvent
```

Kern does not need to know anything about commands or decisions.

Those concepts belong to the client domain.

Kern begins its responsibility when it receives an `Event`.

---

# 5. Event

An `Event` represents a fact that has occurred according to the client domain and is ready to be recorded.

A minimal contract is:

```java
public interface Event {

    EventId id();

    EventType type();

    Tags tags();

    Data data();
}
```

An `Event` is complete when created.

It should not expose mutating operations such as:

```java
event.setType(...);
event.setData(...);
event.addTag(...);
```

The event itself does not contain persistence coordinates because it exists before persistence.

---

# 6. Event identity

Every event has an intrinsic identity:

```java
EventId id();
```

The identity exists before persistence.

This is an important semantic choice.

An event does not become a different thing when persisted.

Conceptually:

```text
Event E42
    │
    │ stored
    ▼
StoredEvent E42 @ P127
```

The persistence process enriches the event with storage-related information, but its identity remains unchanged.

---

# 7. Why EventId belongs to Event

Making `EventId` intrinsic provides several important properties.

It supports:

- stable identification;
- retry;
- deduplication;
- idempotent append;
- correlation;
- diagnostics;
- cross-system references.

Most importantly, it resolves one of the hardest problems in distributed persistence: **ambiguous failure**.

Suppose:

```java
tail.append(event);
```

is committed by the server, but the connection fails before the client receives acknowledgement.

The client cannot know whether the write succeeded.

It can safely retry the same event because the identity remains the same.

```text
append E42
    ↓
commit succeeds
    ↓
response lost
    ↓
retry E42
    ↓
E42 already exists identically
    ↓
success
```

No separate idempotency token is required for the fundamental event append semantics.

---

# 8. StoredEvent

A `StoredEvent` represents an `Event` after it has been recorded in Kern.

```java
public interface StoredEvent extends Event {

    Position position();

    Instant storedAt();
}
```

Persistence adds two primary properties:

```text
Position
    where the event was recorded

storedAt
    when the event was recorded
```

The original properties remain unchanged:

```text
EventId
EventType
Tags
Data
```

Therefore:

```text
StoredEvent IS-A Event
```

is semantically correct.

---

# 9. Position

`Position` represents a point in the logical event log.

For a `StoredEvent`:

```java
Position position =
    event.position();
```

means:

> this event occupies this point in the Event Store log.

The primary semantic requirement is **stable total ordering**.

For any two stored events belonging to the same logical log, Kern must be able to determine:

```text
P1 < P2
P1 = P2
P1 > P2
```

Positions need not be numerically contiguous.

An implementation could theoretically produce:

```text
100
104
110
125
```

provided the ordering remains stable and meaningful.

The API must therefore not depend on arithmetic such as:

```java
position + 1
```

as a fundamental semantic operation.

---

# 10. Beginning of the log

A client may need to express:

> observe from the beginning.

For this reason `Position` may represent a logical point in the log that is not necessarily occupied by an event.

For example:

```java
Position.beginning()
```

represents the point immediately before the first possible stored event.

This allows replay to use exactly the same API as normal observation.

---

# 11. Namespace

Kern may host multiple logical event partitions.

A `Namespace` identifies one such partition.

It is not:

- an event tag;
- an `EventFilter`;
- a property of `Event`;
- a property of `StoredEvent`.

It is part of the addressing of Event Store operations.

The conceptual hierarchy is:

```text
EventStore
   │
   ├── Namespace A
   │      └── logical event log
   │
   ├── Namespace B
   │      └── logical event log
   │
   └── Namespace C
          └── logical event log
```

`EventStore` remains one long-lived capability.

The namespace determines which logical log an operation addresses.

---

# 12. Namespace and Position

Positions are scoped to the logical event log represented by a namespace.

Conceptually:

```text
Namespace A
1 2 3 4 5 ...

Namespace B
1 2 3 ...
```

An event written in namespace B does not advance the logical watermark of namespace A.

This keeps:

- observations;
- DCB validation;
- replay;
- subscriptions;
- checkpoints;

logically independent across namespaces.

---

# 13. EventStore lifecycle

`EventStore` is intended to be a long-lived, thread-safe capability.

It is **not** modeled as a short-lived connection that is created for each operation.

Typical lifecycle:

```text
application/server lifecycle
          │
          ▼
      EventStore
          │
          ├── StoredEvents
          ├── StoredEvents
          ├── Tail
          └── Subscription
```

An implementation such as a RocksDB-backed store may itself be managed by a larger runtime responsible for physical resource ownership.

The semantic `EventStore` API therefore does not necessarily need to own or expose lifecycle methods such as `close()`.

---

# 14. EventStore API

The core observation operation is:

```java
public interface EventStore {

    StoredEvents events(
        Namespace namespace,
        EventFilter filter,
        Position after
    );
}
```

It means:

> observe all matching stored events belonging to the namespace, strictly after `after`, up to a consistent point in the current log.

Convenience methods may be supplied as defaults.

For example:

```java
public interface EventStore {

    StoredEvents events(
        Namespace namespace,
        EventFilter filter,
        Position after
    );

    default StoredEvents events(
        Namespace namespace,
        EventFilter filter
    ) {
        return this.events(
            namespace,
            filter,
            Position.beginning()
        );
    }

    default StoredEvents events(
        EventFilter filter
    ) {
        return this.events(
            Namespace.DEFAULT,
            filter
        );
    }
}
```

Additional overloads should only be introduced when they provide real ergonomic value.

---

# 15. StoredEvents

`StoredEvents` represents an immutable, bounded observation of persisted events.

Its public API is intentionally small:

```java
public interface StoredEvents
    extends Iterable<StoredEvent> {

    Tail tail();

    Subscription follow();
}
```

It does not expose:

```java
count();
first();
last();
list();
stream();
project();
reduce();
state();
watermark();
```

unless future requirements demonstrate that one of those capabilities intrinsically belongs to the abstraction.

---

# 16. Formal semantics of StoredEvents

Conceptually a `StoredEvents` instance contains four coordinates:

```text
Namespace N
EventFilter F
Position A
Watermark W
```

The watermark is intentionally not exposed publicly.

The object represents:

```text
StoredEvents(N,F,A,W)
=
{
    e ∈ Log(N)
    |
    A < e.position <= W
    AND
    F matches e
}
```

This definition is fundamental.

---

# 17. Watermark

A watermark represents:

> the point up to which the logical log was known when the observation was created.

Suppose:

```text
current namespace log:

1 ... 99 100
           ▲
           │
       watermark
```

Then:

```java
StoredEvents events =
    store.events(namespace, filter, after);
```

represents all relevant events through position 100.

If new events are subsequently appended:

```text
101 102 103
```

they do not enter the existing observation.

---

# 18. Why Watermark is not public

The watermark is essential to the semantics of `StoredEvents`.

That does not imply that the client should read it.

The client generally needs to **do something relative to the watermark**, not inspect its primitive value.

Two objects already express those behaviors:

```text
tail()
    continue the observation through writing

follow()
    continue the observation through reading
```

Exposing:

```java
events.watermark();
```

would encourage clients to unpack object state and manually reconstruct behavior already represented by objects.

The watermark therefore remains an internal semantic coordinate.

---

# 19. StoredEvents is Iterable

`StoredEvents` represents a sequence, therefore:

```java
extends Iterable<StoredEvent>
```

is considered part of its nature.

The client can write:

```java
for (StoredEvent event : events) {
    process(event);
}
```

without requiring Kern to expose a collection-specific representation.

This avoids forcing:

```text
List
Array
Stream
Reactive sequence
```

onto clients.

---

# 20. StoredEvents is lazy

A history may contain millions of events.

Materializing an entire observation at construction time would make replay and long histories unnecessarily expensive.

`StoredEvents` should therefore be allowed to resolve its events lazily.

For example:

```text
embedded
    bounded storage scan

remote
    paged protocol reads
```

The client sees the same abstraction.

---

# 21. StoredEvents is repeatably iterable

`StoredEvents` is not itself a cursor.

Calling:

```java
events.iterator();
```

creates a traversal of the observation.

Calling it again should create another traversal of the same logical window.

Therefore:

```text
StoredEvents
    ≠ Iterator
    ≠ Cursor
    ≠ ResultSet
```

It represents an immutable observation from which independent traversals can be created.

---

# 22. StoredEvents should not normally be a resource

The observation should ideally not require the client to manage:

```java
close();
```

or:

```java
try (StoredEvents events = ...) {
}
```

The object should contain enough immutable information to reproduce the bounded observation.

Physical resources required by an iterator should be owned by the iterator or lower-level implementation.

This avoids coupling the public semantic model to storage-specific snapshot lifecycles.

---

# 23. Tail

A `Tail` represents:

> the capability to conditionally continue a specific `StoredEvents` observation through writing.

Conceptually:

```text
StoredEvents(N,F,A,W)
        │
        │ tail()
        ▼
Tail(N,F,W)
```

Notice that `after A` is no longer relevant to the future write.

The consistency boundary depends on:

```text
Namespace N
EventFilter F
Watermark W
```

---

# 24. Tail API

The essential operation is batch append:

```java
public interface Tail {

    void append(
        Iterable<? extends Event> events
    );
}
```

Convenience can be provided through varargs:

```java
public interface Tail {

    default void append(Event... events) {
        this.append(
            new IterableOf<>(events)
        );
    }

    void append(
        Iterable<? extends Event> events
    );
}
```

The actual iterable adapter is not semantically important.

The important choice is that implementations only need one essential append method.

---

# 25. append is a manipulator

`append()` changes the event log.

It therefore returns:

```java
void
```

rather than:

```text
StoredEvent
StoredEvents
AppendResult
Position
Watermark
```

If the client needs to observe the new state, it performs another observation.

For example:

```java
history.tail().append(event);

StoredEvents updated =
    store.events(
        namespace,
        filter,
        checkpoint
    );
```

Observation and manipulation remain separate.

---

# 26. Dynamic Consistency Boundary

The consistency boundary of a `Tail` is defined by the same `EventFilter` used for the original observation.

Suppose:

```text
Tail(N,F,W0)
```

and the current head of namespace N is `W1`.

The append remains valid if and only if no event matching `F` exists in:

```text
(W0, W1]
```

Therefore:

```text
if matching event exists
    → Tail is stale

otherwise
    → append is allowed
```

This is the core Dynamic Consistency Boundary semantic.

---

# 27. Why the last matching Position is insufficient

Suppose:

```text
matching events end at P37
```

but the observation was created when the namespace head was:

```text
W100
```

The observation includes the information:

> there were no matching events between P38 and P100.

Therefore the consistency boundary must be based on:

```text
W100
```

not:

```text
P37
```

This is why `StoredEvents` has a hidden watermark distinct from the position of its last returned event.

---

# 28. Empty observations are meaningful

A filtered observation may contain no events.

Example:

```java
StoredEvents account =
    store.events(
        namespace,
        new TaggedAs("accountId", "A"),
        Position.beginning()
    );
```

The result may be empty.

It still represents valuable information:

> no event matching this condition existed through watermark W.

Its `Tail` can safely be used for conditional creation:

```java
account.tail()
       .append(accountCreated);
```

The append succeeds only if no relevant event appeared after W.

No special `NO_STREAM` version is required.

---

# 29. Batch atomicity

Calling:

```java
tail.append(a, b, c);
```

represents one atomic logical write.

Valid outcomes are:

```text
A, B and C are all persisted
```

or:

```text
none are persisted
```

Partial visibility is forbidden.

The supplied relative order must also be preserved:

```text
position(A)
<
position(B)
<
position(C)
```

Contiguous numeric positions are not required as a high-level semantic guarantee.

---

# 30. Atomic conditional append

The sequence:

```text
check conflict
append
```

is insufficient unless both operations are atomic relative to concurrent writers.

The storage implementation must provide the semantic equivalent of:

```text
validate Tail
+
append batch
```

as one atomic action.

Otherwise two concurrent writers could both validate successfully and subsequently write incompatible facts.

---

# 31. Tail immutability

A successful append does not advance the `Tail`.

```java
Tail tail = history.tail();

tail.append(event);
```

The object continues to represent the same original continuation:

```text
Tail(N,F,W)
```

If the newly appended event itself matches `F`, attempting to reuse the same Tail will naturally fail because a relevant event now exists after W.

No mutable flag such as:

```java
used = true;
```

is necessary.

The external reality determines whether the capability remains valid.

---

# 32. Append success semantics

If:

```java
tail.append(events);
```

returns normally, Kern guarantees that:

1. all supplied events were accepted;
2. the complete batch was written atomically;
3. their relative order was preserved;
4. stable positions were assigned;
5. EventId uniqueness state was updated;
6. required indexes became atomically visible;
7. the namespace head advanced consistently;
8. subsequent observations can see the committed events;
9. the active durability policy was satisfied.

This is the semantic meaning of successful completion.

---

# 33. Durability

The default production behavior should provide durable acknowledgement.

Conceptually:

```text
append()
   ↓
durable commit
   ↓
return
```

A crash immediately after normal return must not cause an acknowledged batch to disappear under the standard durability policy.

The exact mechanism belongs to the storage implementation.

For example, a RocksDB implementation may use synchronous WAL persistence.

Future replicated implementations may define stronger durability policies without changing the `Tail` API.

---

# 34. Ambiguous failure

Failure during append may be ambiguous.

A remote request may be committed successfully while the acknowledgement is lost.

Therefore an exception cannot always mean:

> nothing was written.

The caller must be able to retry the same logical events safely.

This is enabled by intrinsic `EventId`.

---

# 35. Idempotent append

Given an event:

```text
EventId E42
```

if the same event has already been persisted identically, retrying:

```java
tail.append(event);
```

is considered successful.

The requested postcondition is already satisfied.

This remains true even if the original `Tail` would otherwise now appear stale because of that same previously committed event.

Idempotency therefore has semantic precedence over stale-tail detection for an exact retry.

---

# 36. Batch idempotency

For a batch:

```text
[A, B, C]
```

three situations are possible.

## None already exists

Perform the normal conditional append.

## All already exist identically

Treat as successful idempotent replay.

## Only some already exist

Treat as an error.

Given atomic append semantics, partial existence cannot be the result of a previous successful execution of the exact same batch.

It therefore indicates inconsistent event identity reuse or a different prior operation.

---

# 37. Event equivalence

Two events sharing the same `EventId` must represent the same semantic fact.

Kern must therefore be able to distinguish:

```text
same EventId
same semantic content
    → idempotent retry
```

from:

```text
same EventId
different semantic content
    → identity violation
```

Java object equality is not sufficient as a universal contract.

Instead, Kern requires a deterministic semantic fingerprint.

---

# 38. Event fingerprint

The canonical event fingerprint is conceptually derived from:

```text
EventType
Tags
Data
```

but not from:

```text
EventId
Position
storedAt
Namespace
```

Conceptually:

```text
Fingerprint(E)
=
H(
  canonical(E.type),
  canonical(E.tags),
  canonical(E.data)
)
```

The concrete canonical encoding and hash algorithm belong to the serialization/data layer.

The semantic requirement is determinism.

---

# 39. StaleTailException

A stale `Tail` is a normal concurrency outcome, not a storage failure.

The high-level API should expose a dedicated runtime error:

```java
public final class StaleTailException
    extends RuntimeException {
    ...
}
```

Its meaning is:

> at least one relevant event was written after the observation represented by this Tail.

A normal recovery strategy is:

```text
re-read
   ↓
re-evaluate decision
   ↓
append again
```

---

# 40. Conflict explanation

Every stale-tail conflict should be explainable in terms of at least the concrete `StoredEvent` that invalidated the observation.

A minimal conceptual model may be:

```java
public interface Conflict {

    StoredEvent event();
}
```

and:

```java
public final class StaleTailException
    extends RuntimeException {

    private final Conflict conflict;
}
```

More detailed diagnostic explanation may be provided separately.

Potential details include:

```text
matching filter branch
observation age
query/index plan
conflicting Position
event type
tags
```

Those belong to diagnostics rather than to the minimum semantic contract.

---

# 41. EventFilter

`EventFilter` represents a declarative description of which events are relevant.

It must not be modeled as:

```java
boolean matches(StoredEvent event);
```

because that would force every storage implementation to evaluate arbitrary Java logic over loaded events.

Instead, filters must form a declarative expression tree that implementations can translate into efficient native operations.

---

# 42. Filter composition

The initial algebra is:

```text
AllEvents
AnyEvents
TypedBy
TaggedAs
```

Example:

```java
EventFilter boundary =
    new AllEvents(
        new AnyEvents(
            new TypedBy("CourseCreated"),
            new TypedBy("StudentEnrolled")
        ),
        new TaggedAs("courseId", "c1"),
        new TaggedAs("studentId", "s1")
    );
```

Conceptually:

```text
ALL
 ├── ANY
 │    ├── type = CourseCreated
 │    └── type = StudentEnrolled
 │
 ├── courseId = c1
 └── studentId = s1
```

The names describe sets of events rather than implementation mechanics such as `AndFilter` or `OrFilter`.

---

# 43. EventFilter describes itself

A filter is responsible for describing itself to an abstraction capable of interpreting event selection.

```java
public interface EventFilter {

    <T> T describe(
        EventSelection<T> selection
    );
}
```

The interpretation contract is:

```java
public interface EventSelection<T> {

    T all(
        Iterable<T> selections
    );

    T any(
        Iterable<T> selections
    );

    T typedBy(
        EventType type
    );

    T taggedAs(
        Tag tag
    );
}
```

This avoids an external visitor inspecting filter internals procedurally.

---

# 44. Example leaf

```java
public final class TypedBy
    implements EventFilter {

    private final EventType type;

    @Override
    public <T> T describe(
        final EventSelection<T> selection
    ) {
        return selection.typedBy(
            this.type
        );
    }
}
```

The object knows how to describe its own semantics.

---

# 45. Example Composite

Conceptually:

```java
public final class AllEvents
    implements EventFilter {

    private final Iterable<EventFilter> filters;

    @Override
    public <T> T describe(
        final EventSelection<T> selection
    ) {
        return selection.all(
            // children describing themselves
            // through the same selection
        );
    }
}
```

The recursion remains inside the composite.

No external component needs:

```java
filter.children();
filter.operator();
```

or `instanceof` traversal.

---

## Textual representation with KeQL

The semantic `EventFilter` algebra may also be constructed from a compact textual
representation through **KeQL — Kern Event Query Language**.

For example:

```text
type = CourseCreated | StudentEnrolled
& courseId = c1
& studentId = s1
````

is semantically equivalent to constructing:

```java
new AllEvents(
    new AnyEvents(
        new TypedBy("CourseCreated"),
        new TypedBy("StudentEnrolled")
    ),
    new TaggedAs("courseId", "c1"),
    new TaggedAs("studentId", "s1")
)
```

The corresponding `KeqlEventFilter` is still an ordinary `EventFilter`.

KeQL does not introduce an alternative filtering model, query engine, or
storage-specific execution path. Parsing merely constructs the same
`EventFilter` Composite that could have been created programmatically.

The KeQL syntax, parsing model, grammar, compatibility rules, and implementation
are defined by the dedicated [**ADR — KeQL: Kern Event Query Language**](005-kern-event-query-language.md).

---

# 46. EventSelection as interpretation boundary

The same `EventFilter` can be translated into different representations.

Examples:

```text
in memory
    EventSelection<Predicate<StoredEvent>>

RocksDB
    EventSelection<Index>

SQL
    EventSelection<SqlExpression>

remote protocol
    EventSelection<ProtocolFilter>
```

The public filter algebra remains storage-independent.

---

# 47. Tags

Tags represent application-defined indexed coordinates associated with an event.

They are distinct from the event type and event data.

Conceptually:

```text
Event
 ├── id
 ├── type
 ├── tags
 └── data
```

A type answers:

> what happened?

A tag answers:

> to which application-relevant coordinates is this fact associated?

The data answers:

> what structured information describes the fact?

---

# 48. Tags API

A minimal design is:

```java
public interface Tags
    extends Iterable<Tag> {
}
```

with:

```java
public interface Tag {

    TagName name();

    TagValue value();
}
```

Potential concrete value objects:

```java
TagName
TagValue
```

may enforce syntax and size constraints.

---

# 49. Tag invariants

The initial semantic rules are:

1. a `TagName` occurs at most once per event;
2. tag order is not semantically significant;
3. tags are immutable;
4. tag values use a canonical representation;
5. tags participate in event fingerprinting;
6. tags are application metadata, not Kern system metadata.

Therefore:

```text
courseId=c1
studentId=s1
```

is equivalent to:

```text
studentId=s1
courseId=c1
```

---

# 50. System properties are not tags

Kern structural information should not be duplicated into tags.

For example:

```text
EventId
EventType
Position
storedAt
Namespace
```

should not require equivalent entries in `Tags`.

This preserves clear semantics and avoids redundant indexes.

---

# 51. Data

The information carried by an Event is represented as:

```java
Data data();
```

rather than:

```text
byte[]
Object
JSON string
POJO
Payload DTO
```

An event carries structured data.

Its physical representation is a separate concern.

---

# 52. Data as a first-class abstraction

The minimal external structured-data contract is expected to resemble:

```java
public interface Data {

    Metadata meta();

    <T> T value(
        Attribute<T> attribute
    );
}
```

with:

```java
public interface Metadata
    extends Iterable<Attribute<?>> {

    String name();
}
```

and:

```java
public interface Attribute<T> {

    String name();

    DataType<T> type();
}
```

A simpler first implementation may use `Class<T>` while the dedicated Data library evolves.

---

# 53. Data does not mean POJO

A POJO is only one possible source of `Data`.

Possible implementations include:

```text
DataFromPojo
DataFromMap
DataFromJson
DataFromHttpRequest
DataFromResultSet
DataFromProtobuf
```

For example:

```java
@RequiredArgsConstructor
public final class DataFromPojo
    implements Data {

    private final Object origin;

    @Override
    public Metadata meta() {
        return new MetadataFromPojo(
            this.origin.getClass()
        );
    }

    @Override
    public <T> T value(
        final Attribute<T> attribute
    ) {
        // obtain value from origin
    }
}
```

The `Data` object gives the source a meaningful structured-data role.

---

# 54. Data and Metadata are separate from Kern

The structured data model has utility beyond Event Sourcing.

It should therefore live in a separate reusable project.

Kern should depend only on its minimal contracts.

Potential modules:

```text
data-core
data-pojo
data-json
data-http
...
```

Kern must not require advanced features unrelated to Event Store semantics.

---

# 55. Serialization is separate from Data

`Data` represents structured information.

Serialization describes how that information is represented externally.

Therefore `Data` should not expose convenience methods such as:

```java
byte[] bytes();
String json();
```

Serialization belongs to a separate representation layer.

---

# 56. Printable representation

A generic printing/representation framework can bridge structured `Data` to external forms.

Conceptually:

```text
Data
  │
  ▼
PrintableData
  │
  ▼
Printer
  │
  ▼
serialized representation
```

The data abstraction itself should not need to extend the printable abstraction.

Composition is preferred:

```text
Data
    wrapped by
PrintableData
```

rather than:

```java
Data extends Printable
```

---

# 57. Serialization pipeline

The representation pipeline is conceptually:

```text
Data
  ↓
serialization
  ↓
canonical representation
  ↓
optional compression
  ↓
optional encryption
  ↓
storage / transport
```

Serialization, compression, and encryption are distinct responsibilities.

This separation supports future features such as:

- multiple content formats;
- remote protocols;
- cryptographic deletion;
- compression policies;
- canonical fingerprinting.

---

# 58. EventType and Metadata are distinct

`EventType` and `Data.meta()` describe different concerns.

Example:

```text
EventType:
    StudentEnrolled

Metadata:
    StudentEnrollmentDataV2
```

`EventType` identifies the semantic fact.

`Metadata` identifies the structure of its data.

Keeping them distinct allows data schema evolution without necessarily changing event semantics.

---

# 59. Canonical Data representation

Kern requires deterministic event fingerprinting.

Therefore the Data serialization layer must eventually support a canonical representation.

Equivalent logical data must produce equivalent canonical bytes regardless of incidental details such as:

```text
map iteration order
field declaration order
reflection order
serializer implementation quirks
```

The exact encoding is outside this ADR.

The deterministic property is mandatory.

---

# 60. Reading from a Position

The `after` argument of:

```java
events(namespace, filter, after)
```

defines the lower boundary of the observation.

The returned `StoredEvents` contains only:

```text
position > after
```

This provides a uniform basis for:

- initial replay;
- consumer resume;
- checkpoint recovery;
- catch-up.

---

# 61. Replay

A full replay requires no special API.

The client simply requests:

```java
StoredEvents replay =
    store.events(
        namespace,
        filter,
        Position.beginning()
    );
```

Replay is therefore ordinary observation from the beginning.

This is preferable to introducing a dedicated `replay()` operation with separate semantics.

---

# 62. Consumer checkpoint

A consumer may persist the:

```java
event.position();
```

of the last event it has successfully processed.

After restart:

```java
StoredEvents remaining =
    store.events(
        namespace,
        filter,
        checkpoint
    );
```

The checkpoint belongs to the consumer.

Kern does not initially own durable consumer progress.

This naturally supports at-least-once processing.

---

# 63. follow()

`StoredEvents.follow()` represents:

> continue observing future matching events from exactly the upper boundary of this observation.

Conceptually:

```text
StoredEvents(N,F,A,W)
        │
        │ follow()
        ▼
Subscription(N,F,W)
```

The subscription begins **after the hidden watermark W**, not after the position of the last returned matching event.

This distinction prevents gaps.

---

# 64. Why follow() belongs to StoredEvents

Suppose the last matching event is at:

```text
P150
```

while the observation is known through:

```text
W300
```

Starting a subscription after P150 would reprocess the interval:

```text
151 ... 300
```

or require the client to understand hidden observation state.

Starting from W300 is correct.

Only `StoredEvents` knows this boundary.

Therefore:

```java
history.follow();
```

is safer and semantically stronger than asking clients to reconstruct a subscription manually from their last matching event.

---

# 65. Read/write symmetry

The model produces an important symmetry:

```text
                  StoredEvents
                 /            \
                /              \
             tail()          follow()
               │                │
               ▼                ▼
        continue by write   continue by read
```

Both capabilities continue the same observation.

`Tail` moves the log forward by writing.

`Subscription` waits for and observes future log evolution.

---

# 66. Subscription

A `Subscription` represents an immutable read continuation from a specific observation boundary.

Its minimal API is:

```java
public interface Subscription {

    CompletionStage<StoredEvents> next(
        int count
    );
}
```

No mutable subscription state needs to be exposed.

---

# 67. Pull-based subscription

Kern deliberately uses pull semantics rather than a push observer model.

The client explicitly requests the maximum amount of work it is ready to receive:

```java
subscription.next(100);
```

This means:

> give me the next non-empty bounded observation containing at most 100 matching events.

This provides natural back-pressure.

---

# 68. Why not Observer

A push model creates immediate secondary problems:

```text
unbounded delivery
buffer management
slow consumer handling
pause/resume
flow control
callback threading
subscription lifecycle state
```

These are often consequences of the delivery model rather than intrinsic Event Store requirements.

A pull model avoids introducing them.

The client controls demand directly.

---

# 69. Asynchronous next()

`next()` returns:

```java
CompletionStage<StoredEvents>
```

because future events may not yet exist.

A synchronous call would otherwise need to block a thread waiting for data.

With asynchronous completion:

```java
history.follow()
       .next(100)
       .thenAccept(batch -> {
           ...
       });
```

the implementation can wait efficiently through:

- event-loop notification;
- long polling;
- async network I/O;
- other non-blocking mechanisms.

---

# 70. Subscription is immutable

Calling:

```java
subscription.next(100);
```

does not mutate the semantic subscription.

It represents a request for the next observation after the same boundary.

For continuation:

```java
StoredEvents next =
    subscription.next(100)
                .toCompletableFuture()
                .join();

Subscription continuation =
    next.follow();
```

Therefore:

```text
StoredEvents S0
   ↓ follow
Subscription Q0
   ↓ next
StoredEvents S1
   ↓ follow
Subscription Q1
```

Each object remains conceptually stable.

---

# 71. Repeated next() calls

Because a `Subscription` is immutable, two independent calls:

```java
s.next(100);
s.next(100);
```

are semantically requests from the same continuation boundary.

They may therefore produce equivalent observations.

Clients advance by calling:

```java
next.follow()
```

on the returned `StoredEvents`.

This should be documented clearly.

---

# 72. Semantics of next(count)

For:

```java
CompletionStage<StoredEvents> next(
    int count
);
```

the following rules apply:

1. `count` must be greater than zero;
2. the same namespace is retained;
3. the same filter is retained;
4. reading begins strictly after the previous hidden watermark;
5. the stage waits until at least one matching event is available;
6. at most `count` matching events are returned;
7. events are ordered by `Position`;
8. the returned `StoredEvents` has a new consistent upper watermark;
9. `next()` does not mutate the subscription.

---

# 73. next(count) and upper watermark

Suppose:

```text
previous watermark = P100
```

and currently matching events exist at:

```text
P105
P110
P120
P130
P140
```

Calling:

```java
next(3)
```

may return:

```text
P105
P110
P120
```

The resulting observation boundary must not skip:

```text
P130
P140
```

Therefore its upper watermark should be consistent with the consumed bounded window, conceptually no later than the boundary required to include P120.

The next continuation can then safely begin after that boundary.

This prevents data loss when more than `count` events are already available.

---

# 74. No need for pause/resume

The pull model makes methods such as:

```java
pause();
resume();
```

unnecessary.

If the consumer is not ready, it simply does not call:

```java
next(...)
```

When ready, it continues from the latest `StoredEvents`.

Back-pressure is represented by absence of demand.

---

# 75. No durable server-side subscription state required

A subscription can be represented entirely by:

```text
Namespace
EventFilter
Watermark
```

The server does not necessarily need to maintain a durable subscription entity.

The authoritative recovery mechanism remains:

```java
store.events(
    namespace,
    filter,
    checkpoint
);
```

The consumer owns its checkpoint.

This keeps initial subscription semantics simple and robust.

---

# 76. Failure and retry for next()

A failed `next()` request does not change the semantic subscription.

The client may safely retry:

```java
subscription.next(count);
```

because the subscription still represents the same continuation boundary.

This is particularly useful for remote implementations.

---

# 77. EventStore implementations

The public API is intentionally location-transparent.

Possible implementations include:

```text
InMemoryEventStore
RocksEventStore
RemoteEventStore
```

A client should be able to write:

```java
StoredEvents history =
    store.events(
        namespace,
        filter,
        checkpoint
    );

history.tail()
       .append(event);

history.follow()
       .next(100);
```

without knowing whether the underlying store is embedded or remote.

---

# 78. Embedded deployment

Conceptually:

```text
Application
    │
    ▼
Kern API
    │
    ▼
Embedded EventStore implementation
    │
    ▼
Storage engine
```

An embedded implementation can provide extremely low-latency access and direct local persistence.

---

# 79. Remote deployment

Conceptually:

```text
Application
    │
    ▼
Kern API
    │
    ▼
RemoteEventStore
    │
    ▼
wire protocol
    │
    ▼
Kern server adapter
    │
    ▼
EventStore implementation
    │
    ▼
storage engine
```

The remote protocol adapts the same semantic model.

It does not define a second Event Store domain model.

---

# 80. Remote proxies

A remote implementation may provide:

```text
RemoteEventStore
RemoteStoredEvents
RemoteTail
RemoteSubscription
RemoteStoredEvent
```

These objects implement the same public interfaces.

For example, a `RemoteTail` may internally retain:

```text
Namespace
EventFilter
Watermark
protocol client
```

without exposing those fields publicly.

---

# 81. Language-neutral event data

Remote communication must not rely on Java-specific mechanisms such as:

```text
Java Object serialization
fully qualified POJO class names
bytecode identity
ObjectInputStream
```

`Data` must be serialized through language-neutral representations.

This preserves the possibility of future clients implemented in other languages.

---

# 82. Suggested module boundaries

The semantic model suggests an initial modular architecture.

```text
kern
│
├── kern-api
├── kern-spi
├── kern-client
├── kern-server
├── kern-protocol
├── kern-storage-memory
├── kern-storage-rocksdb
└── kern-testkit
```

Generic supporting projects remain independent:

```text
data
printables
data-printables
```

---

# 83. kern-api

Contains the stable semantic contracts.

Candidate contents:

```text
EventStore

Event
StoredEvent
StoredEvents

Tail
Subscription

EventId
EventType
Position
Namespace

Tags
Tag
TagName
TagValue

EventFilter
AllEvents
AnyEvents
TypedBy
TaggedAs
EventSelection

Conflict
StaleTailException
```

The module should have minimal dependencies.

It may depend on the external `data-core` abstraction.

It must not depend on:

```text
RocksDB
Spring
Armeria
gRPC
Jackson
SQL
Reactor
```

---

# 84. kern-spi

Contains implementation-oriented contracts that are needed to implement the public semantics but are not part of normal client code.

Possible concepts include:

```text
Watermark
observation descriptor
serialization bridge
storage coordination abstractions
internal conflict information
```

The exact SPI should be introduced only as implementation needs emerge.

---

# 85. kern-storage-memory

An in-memory implementation should be developed early.

Its purpose is to provide:

- executable semantic specification;
- contract-test target;
- simple examples;
- rapid validation of DCB behavior.

It should not be treated merely as a test fake.

It should implement the same observable semantics as production stores.

---

# 86. kern-storage-rocksdb

The RocksDB implementation contains physical concerns such as:

```text
storage layout
key encoding
event records
EventId indexes
type indexes
tag indexes
filter compilation
index planning
watermark/head persistence
atomic write batches
WAL
recovery
index rebuild
```

These details do not belong to `kern-api`.

---

# 87. kern-client

Contains remote proxy implementations:

```text
RemoteEventStore
RemoteStoredEvents
RemoteTail
RemoteSubscription
```

and transport adaptation.

Applications continue to depend primarily on the semantic interfaces from `kern-api`.

---

# 88. kern-server

Hosts an EventStore as a network service.

Its responsibilities include:

```text
protocol endpoints
authentication
authorization
request validation
stream/long-poll management
operational limits
mapping protocol requests to EventStore operations
```

It must delegate semantic Event Store behavior to an `EventStore` implementation rather than reimplementing the model independently.

---

# 89. kern-protocol

Defines the wire representation of:

```text
Namespace
EventFilter
Event
StoredEvent
Position
observation boundaries
Tail append
Subscription next
errors
```

Wire messages are transport representations, not public domain DTOs.

---

# 90. kern-testkit

Provides a reusable EventStore conformance suite.

Every implementation should satisfy the same semantic tests.

Candidate implementations include:

```text
InMemoryEventStore
RocksEventStore
RemoteEventStore
```

---

# 91. Required conformance properties

At minimum, contract tests should verify:

### Observation

- events are ordered by Position;
- `after` is strictly exclusive;
- only matching events are returned;
- observations remain bounded by their hidden watermark;
- repeated iteration observes the same logical window;
- empty observations remain valid.

### Tail

- irrelevant concurrent events do not cause conflict;
- relevant concurrent events produce `StaleTailException`;
- batch append is atomic;
- batch order is preserved;
- Tail reuse does not mutate the Tail;
- empty history has a usable Tail.

### Identity

- EventId retry is idempotent;
- identical retry creates no duplicate;
- EventId collision with different semantic content fails;
- partial duplicate batch fails.

### Subscription

- `follow()` begins after the observation watermark;
- no gap exists between bounded history and follow;
- `next(n)` returns at most `n`;
- `next(n)` preserves ordering;
- continuation through returned `StoredEvents.follow()` loses no events;
- retrying the same Subscription remains safe.

### Namespace

- events never leak between namespaces;
- positions and consistency boundaries are namespace-local;
- identical EventIds may be isolated according to namespace rules.

---

# 92. Example — domain decision

Define the relevant boundary:

```java
EventFilter boundary =
    new AllEvents(
        new AnyEvents(
            new TypedBy("CourseCreated"),
            new TypedBy("StudentEnrolled")
        ),
        new TaggedAs("courseId", "c1"),
        new TaggedAs("studentId", "s1")
    );
```

Observe relevant history:

```java
StoredEvents history =
    store.events(
        namespace,
        boundary,
        Position.beginning()
    );
```

Interpret the history:

```java
Course course =
    new EventSourcedCourse(history);
```

Produce a new fact:

```java
Event enrolled =
    course.enroll(student);
```

Persist relative to the exact observation:

```java
history.tail()
       .append(enrolled);
```

If a relevant fact was written concurrently, the append fails with `StaleTailException`.

---

# 93. Example — replay and live continuation

Start from a durable consumer checkpoint:

```java
StoredEvents current =
    store.events(
        namespace,
        filter,
        checkpoint
    );
```

Process the bounded history:

```java
for (StoredEvent event : current) {
    process(event);
    checkpoints.save(event.position());
}
```

Continue asynchronously:

```java
current.follow()
       .next(100)
       .thenAccept(next -> {
           for (StoredEvent event : next) {
               process(event);
               checkpoints.save(event.position());
           }
       });
```

To continue again:

```java
StoredEvents next = ...;

CompletionStage<StoredEvents> future =
    next.follow()
        .next(100);
```

The same concepts provide:

```text
replay
catch-up
checkpoint resume
live consumption
```

without separate APIs for each mode.

---

# 94. Core conceptual symmetry

The central object model can be summarized as:

```text
                 EventStore
                     │
                     │ events(N,F,A)
                     ▼
                StoredEvents
             ┌───────┼────────┐
             │       │        │
             │       │        │
          iterate   tail()   follow()
             │       │        │
             ▼       ▼        ▼
           past    future    future
           read    write      read
```

This symmetry is intentional.

A `StoredEvents` observation becomes the causal origin for both:

- the next write;
- the next read.

The client does not need to manually carry hidden concurrency tokens or watermarks.

---

# 95. Fundamental invariant

The most important invariant of the API is:

> **A `StoredEvents` object represents an immutable bounded observation of a namespace log, and its continuations operate relative to the exact upper boundary of that observation.**

`Tail` provides the write continuation.

`Subscription` provides the read continuation.

The relevant consistency boundary is determined by `EventFilter`.

---

# 96. Final public API shape

The initial API is intentionally small.

```java
public interface EventStore {

    StoredEvents events(
        Namespace namespace,
        EventFilter filter,
        Position after
    );

    default StoredEvents events(
        Namespace namespace,
        EventFilter filter
    ) {
        return this.events(
            namespace,
            filter,
            Position.beginning()
        );
    }

    default StoredEvents events(
        EventFilter filter
    ) {
        return this.events(
            Namespace.DEFAULT,
            filter
        );
    }
}
```

```java
public interface StoredEvents
    extends Iterable<StoredEvent> {

    Tail tail();

    Subscription follow();
}
```

```java
public interface Tail {

    default void append(Event... events) {
        this.append(
            new IterableOf<>(events)
        );
    }

    void append(
        Iterable<? extends Event> events
    );
}
```

```java
public interface Subscription {

    CompletionStage<StoredEvents> next(
        int count
    );
}
```

```java
public interface Event {

    EventId id();

    EventType type();

    Tags tags();

    Data data();
}
```

```java
public interface StoredEvent
    extends Event {

    Position position();

    Instant storedAt();
}
```

```java
public interface EventFilter {

    <T> T describe(
        EventSelection<T> selection
    );
}
```

```java
public interface EventSelection<T> {

    T all(
        Iterable<T> selections
    );

    T any(
        Iterable<T> selections
    );

    T typedBy(
        EventType type
    );

    T taggedAs(
        Tag tag
    );
}
```

---

# 97. Deliberately excluded from the core API

The following concepts are intentionally not part of this semantic API:

```text
QueryResult
AppendResult
AppendRequest
ExpectedVersion
IdempotencyKey
CursorRequest
Observer callback
pause/resume
server-side consumer checkpoint
RocksDB index
SQL query
storage transaction
ContentType + byte[]
repository
aggregate revision
```

Some may exist in adapters, protocols, operational tooling, or storage implementations.

They are not fundamental concepts of the Event Store domain model.

---

# 98. Architectural outcome

The resulting model is deliberately small because behavior is concentrated in meaningful objects rather than distributed through procedural request/result structures.

The essential flow is:

```text
observe
   ↓
StoredEvents
   ├── interpret
   ├── tail → write
   └── follow → future read
```

Concurrency is expressed naturally through the relationship between:

```text
EventFilter
+
hidden Watermark
+
Tail
```

Replay and live consumption are expressed through:

```text
Position
+
StoredEvents
+
Subscription
```

Event identity and safe retry are expressed through:

```text
EventId
+
canonical semantic fingerprint
```

Structured event content is expressed through:

```text
Data
```

rather than through storage or transport representations.

---

# 99. Decision

Kern adopts this object model as the semantic foundation of its public Event Store API.

Storage engines, server runtimes, remote clients, serialization layers, and operational infrastructure must implement this model rather than redefine it.

The implementation may change.

The semantics defined here do not.