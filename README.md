# Kern

Kern is an Event Store for Java built around a simple idea: events form an
ordered history that applications can observe, extend, replay, and follow over
time.

It provides a small object-oriented API for recording events, reading stable
observations of the event log, performing optimistic conditional writes, and
waiting asynchronously for new events.

Kern keeps storage, indexing, concurrency, subscription, and transport mechanics
behind its domain model. Applications work with concepts such as `Event`,
`StoredEvents`, `Tail`, `Subscription`, `Position`, and `EventFilter`, while
interchangeable implementations take responsibility for turning those semantics
into a complete Event Store.

> **A small model on the outside. A serious Event Store underneath.**

```text
EventStore
    |
    | events(namespace, filter, after)
    v
StoredEvents
    |_____________ 
    |             |
    | tail()      | follow()
    v             v
Tail          Subscription
````

## Why Kern

The name **Kern** began as a stylized rendering of *cairn*: a stone marker left along a path, a durable sign of the journey that was taken.

Events play a similar role. Each event is an immutable marker of something that happened; together, they form the history from which a system can be reconstructed and understood.

The name also evokes the computing concept of a kernel and, furthermore, in German, *Kern* means **core** — a fortunate second meaning for something that represents the authoritative heart of an event-sourced system.

**Kern — every event leaves a mark.**

## A first look

Reading starts by observing the part of the event log that matters to the
application:

```java
EventFilter filter =
    new AllEvents(
        new TypedBy("StudentEnrolled"),
        new TaggedAs("courseId", "c1")
    );

StoredEvents history =
    store.events(namespace, filter, after);

for (StoredEvent event : history) {
    // process persisted events
}
```

Writing continues from that same observation:

```java
Event enrolled = new StudentEnrolled(/* ... */);

history.tail().append(enrolled);
```

The `Tail` carries the consistency boundary of the observation that created it.
The application does not have to extract and pass an expected version or
watermark explicitly.

The same observation can instead be continued into the future:

```java
history.follow()
       .next(100)
       .thenAccept(next -> {
           for (StoredEvent event : next) {
               // process newly available events
           }
       });
```

The consumer controls back-pressure by requesting at most the number of events
it is ready to process. If no matching event is currently available, the
asynchronous request remains pending until one becomes available.

## Core Model

Kern starts from an `Event`: a fact accepted by the client domain and ready to
be recorded.

Persistence turns it into a `StoredEvent` by adding storage facts such as
`Position` and storage time, while preserving the identity, type, tags, and
payload of the original event.

`StoredEvents` is not merely a query result or a cursor.

It represents a bounded, immutable observation of a namespace log: matching
events strictly after a lower `Position` and up to an internal observation
watermark.

That observation naturally provides two ways forward:

* `tail()` returns a `Tail`: the capability to continue the observed history
  through a conditional append.
* `follow()` returns a `Subscription`: the capability to continue the
  observation into the future.

The watermark itself remains an implementation detail.

Applications use the capabilities derived from the observation rather than
reconstructing consistency or continuation manually from versions, offsets,
watermarks, or cursor tokens.

## One Observation, Two Directions

The symmetry between reading and writing is intentional:

```java
StoredEvents history =
    store.events(namespace, filter, after);

history.tail().append(events);

history.follow().next(100);
```

Both operations continue from exactly the same observation boundary.

`tail()` moves the history forward by writing.

`follow()` moves the observation forward by reading.

This allows concurrency, replay, and live continuation to remain properties of
the object model instead of becoming bookkeeping performed by application code.

```text
                       StoredEvents
                  bounded observation
                         /     \
                        /       \
                       /         \
                  tail()         follow()
                     |              |
                     v              v
                    Tail        Subscription
                     |              |
                  append()        next(n)
                     |              |
                     v              v
                 continue        continue
                 history         observation
```

## Dynamic Consistency Boundaries

Kern uses `EventFilter` not only to select events, but also to define the
consistency boundary of a conditional append.

A `Tail` becomes stale only when an event matching the original filter appears
after the observation watermark.

Unrelated concurrent activity does not invalidate the write.

For example, an operation whose observation is bounded by:

```java
new AllEvents(
    new TypedBy("StudentEnrolled"),
    new TaggedAs("courseId", "c1")
)
```

is interested in concurrent enrollment events for `courseId = c1`.

An event concerning another course does not necessarily invalidate that
observation.

Optimistic concurrency therefore depends on **what is relevant to the
operation**, rather than on a fixed aggregate version or a globally changing
counter.

If the append is stale, Kern reports a `StaleTailException` carrying a
`Conflict` describing the event that invalidated the observation.

## Event Identity and Safe Retry

Event identity is intrinsic through `EventId`.

This matters especially when failures are ambiguous.

A client may submit an append successfully while losing the acknowledgement
because of a network or process failure. Retrying the same semantic event should
not accidentally create another event.

Kern implementations can use the event identity to recognize that the same
event has already been accepted and make retries safe without forcing the
application to invent an external deduplication protocol.

## Event Filters

`EventFilter` is a small declarative algebra describing which events are
relevant to an observation.

Filters compose naturally:

```java
EventFilter boundary =
    new AllEvents(
        new AnyEvents(
            new TypedBy("CourseCreated"),
            new TypedBy("StudentEnrolled")
        ),
        new TaggedAs("courseId", "c1")
    );
```

The filter describes intent.

It does not describe how that intent must be executed.

An embedded RocksDB implementation may compile this composition into indexes
and intersections of positions. A different implementation may translate the
same filter into an entirely different native representation.

This separation allows the public model to remain independent from the physical
storage strategy.

### KeQL

Filters may also be expressed textually through **KeQL — Kern Event Query
Language**.

For example:

```text
type = CourseCreated | StudentEnrolled
& courseId = c1
& studentId = s1
```

represents the same semantic filter as:

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

KeQL does not introduce a second filtering model.

Parsing a KeQL expression simply constructs the same `EventFilter` composition
that could have been created programmatically.

The language remains optional and does not change the `EventStore` API.

## Simple API, Complete Event Store

Kern deliberately keeps its public model small.

That does **not** imply a small implementation.

Behind `EventStore`, implementations are responsible for the mechanics expected
from an Event Store, including concerns such as:

* durable ordered event storage;
* atomic appends;
* optimistic concurrency;
* event identity and deduplication;
* efficient filtered reads;
* secondary indexing;
* consistent observations;
* replay;
* subscriptions;
* asynchronous waiting;
* client-controlled back-pressure;
* transactional consistency between the event log and its indexes;
* recovery after failures.

These responsibilities do not need to leak into the application API.

They belong to the implementation behind it.

```text
             Application
                  |
                  | Kern API
                  v
             EventStore
                  |
        semantic contracts
                  |
          +-------+-------+
          |       |       |
          v       v       v
       Embedded  Remote  In-memory
          |       |
          v       v
       RocksDB  Kern Server
```

Different implementations can have radically different internal architectures
while preserving the same Kern semantics.

> **The abstraction is intentionally simple. The work behind it does not have
> to be.**

## Embedded Kern

Kern can be used as an embedded Event Store.

In this model the Event Store runs in the same process as the application:

```text
Application
     |
     | EventStore
     v
Embedded Kern
     |
     v
Storage Engine
```

The planned RocksDB implementation follows this model.

RocksDB provides low-level storage primitives; the Kern implementation is
responsible for translating those primitives into Event Store semantics.

Among other responsibilities, the implementation can:

* maintain the ordered namespace event log;
* assign and persist positions;
* store event identities;
* maintain secondary indexes;
* update the log and indexes transactionally;
* compile `EventFilter` compositions into index operations;
* resolve index results into `StoredEvent` sequences;
* enforce conditional appends represented by `Tail`;
* detect conflicts;
* provide replay;
* implement asynchronous subscription continuation.

Applications do not manipulate RocksDB column families, keys, snapshots,
iterators, or transactions directly.

Those are implementation concepts, not Kern API concepts.

This makes the embedded model useful when an application wants the semantics of
an Event Store without requiring a separately deployed Event Store service.

## Kern Server

The same model can be exposed remotely through a Kern server.

```text
Application
     |
     | Kern API
     v
Remote EventStore
     |
     | protocol
     v
Kern Server
     |
     | Kern API
     v
Storage EventStore
     |
     v
RocksDB
```

On the client side, the remote implementation behaves as an `EventStore`.

Its responsibility is to adapt the Kern semantic model to the transport
protocol.

On the server side, the endpoint reconstructs the same semantic operations and
delegates them to an actual `EventStore` implementation.

The server therefore does not define another Event Store model.

It hosts the existing one.

This distinction is important:

```text
Client API
    ↓
Kern semantics
    ↓
transport representation
    ↓
Kern semantics
    ↓
storage implementation
```

Transport is a deployment concern rather than part of the Event Store domain.

## Interchangeable Implementations

`EventStore` is the boundary between applications and implementations.

The same application model can therefore be backed by different implementations
according to its deployment and operational requirements.

Possible implementations include:

```text
EventStore
    |
    +-- RocksDB EventStore
    |      embedded, durable
    |
    +-- Remote EventStore
    |      proxy to Kern Server
    |
    +-- In-memory EventStore
    |      lightweight / testing
    |
    +-- future storage engines
```

They do not need to share the same internal design.

An in-memory implementation does not need RocksDB indexes.

A remote implementation does not persist events itself.

A RocksDB implementation may use column families, transactions, snapshots,
ordered binary keys, and composed indexes.

What they share is the behavior promised by the Kern contracts.

This allows storage and deployment choices to evolve without forcing the
application to adopt a different Event Store model.

## RocksDB Storage

The planned RocksDB implementation is more than a thin adapter around a
key/value database.

It is responsible for realizing the Kern model efficiently on top of RocksDB.

Conceptually:

```text
EventFilter
     |
     | describe(...)
     v
RocksDB filter interpretation
     |
     v
Index
     |
     | positions()
     v
Iterable<Position>
     |
     v
StoredEvents
```

An `Index` represents a source of matching event positions.

Specialized indexes can represent event type, tags, or other supported
dimensions, while composite indexes can implement boolean filtering through
intersection and union.

For example:

```text
type = StudentEnrolled
& courseId = c1
```

may conceptually become:

```text
TypeIndex(StudentEnrolled)
          \
           INTERSECT
          /
TagIndex(courseId,c1)
          |
          v
 Iterable<Position>
```

The resulting positions are then resolved against the event log.

This keeps query planning close to the physical storage implementation while
allowing the public API to continue speaking exclusively in terms of
`EventFilter`.

## Subscriptions and Back-pressure

A subscription continues a `StoredEvents` observation into the future.

It does not push an unlimited stream of events toward the client.

Instead, the consumer asks for the amount of work it is prepared to process:

```java
subscription.next(100);
```

The result is asynchronous.

If matching events are already available, the request may complete immediately.

If no matching event is currently available, the request remains pending until
at least one becomes available.

This avoids polling loops while preserving client-controlled back-pressure.

The exact waiting mechanism is implementation-specific.

An embedded implementation may coordinate waiting directly with the local
storage runtime, while a remote implementation may translate the same operation
into a long-lived protocol request.

The semantic contract remains the same.

## Replay Is Just Observation

Kern does not need a separate replay subsystem.

A replay is simply another observation starting from an earlier `Position`.

For example, observing from the beginning of the namespace naturally replays
the matching history.

The same filter can then be followed into the future:

```text
historical events
       |
       | events(..., beginning)
       v
 StoredEvents
       |
       | follow()
       v
 Subscription
       |
       v
 future events
```

Historical replay and live consumption are therefore two phases of the same
observation model rather than separate APIs.

## Design Philosophy

Kern is designed from the Event Store domain inward.

Storage engines are implementation tools, not the source of the public model.

This means that concepts such as:

```text
ColumnFamily
WriteBatch
Snapshot
Iterator
transaction handle
network request
cursor token
```

should not determine the shape of the application API.

The public model instead represents concepts that exist because Kern is an
Event Store:

```text
Event
StoredEvent
Position
EventFilter
StoredEvents
Tail
Conflict
Subscription
```

Implementation-specific abstractions remain free to speak the language of their
technology where doing so is useful.

The goal is not to hide that RocksDB is RocksDB or that a remote call is a
remote call.

The goal is to prevent those mechanisms from defining what an Event Store is.

## Modules

Kern is intended to evolve as a set of focused modules with dependencies
pointing toward the semantic core.

Conceptually:

```text
                    kern-api
                       ^
                       |
          +------------+-------------+
          |            |             |
          |            |             |
      kern-keql    kern-rocksdb   protocol model
                                      ^
                                      |
                               +------+------+
                               |             |
                         kern-client    kern-server
```

`kern-api` contains the stable Event Store model.

`kern-keql` provides the optional textual representation of `EventFilter`.

`kern-rocksdb` implements the Event Store using RocksDB.

A client module can expose the same API through a remote proxy.

A server module can receive those operations and delegate them to a concrete
`EventStore`.

The exact module structure may evolve as implementation work progresses, but
dependencies should preserve one fundamental direction:

> implementations depend on the Kern model; the Kern model does not depend on
> its implementations.

## Current Repository State

Kern is being developed incrementally.

The semantic API is the foundation on which storage, runtime, transport, and
optional language modules are built.

Not every module described above necessarily exists in the repository yet.
Planned implementations should therefore be distinguished from currently
available artifacts until they are released.

## Build

Kern requires Java 24 and Maven.

Make sure Maven is running on a JDK that supports Java 24:

```sh
mvn -version
```

```sh
mvn clean test
```

API documentation can be generated with:

```sh
mvn -pl kern-api javadoc:javadoc
```

## Architectural Decisions

Kern's ADRs are the architectural authority for the project, you can find it here: 

- [001-kern-semantic-model.md](docs/adr/001-kern-semantic-model.md)
- [002-runtime-architecture.md](docs/adr/002-runtime-architecture.md)
- [003-rocksdb-storage-engine.md](docs/adr/003-rocksdb-storage-engine.md)
- [004-subscription-continuation.md](docs/adr/004-subscription-continuation.md)
- [005-kern-event-query-language.md](docs/adr/005-kern-event-query-language.md)
- [006-constraint-oriented-decision-model.md](docs/adr/006-constraint-oriented-decision-model.md)

They document not only implementation choices but, more importantly, the
reasoning and invariants behind the public model, runtime behavior, storage
engine, subscriptions, and optional query language.

Implementation work should preserve those invariants.

Storage engines, protocols, clients, servers, and textual representations
implement or represent the Kern model.

They do not define alternative Event Store models.

---

Kern aims to make the common interaction with an Event Store small enough to
understand at a glance, without pretending that durable event storage,
concurrency, indexing, subscriptions, failure recovery, and distributed access
are simple problems.

The complexity is still there.

It is simply placed where it belongs.
