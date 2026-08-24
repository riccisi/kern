# ADR — Kern Subscription Continuation and Asynchronous Waiting Model

## Status

**Accepted**

## Purpose

This ADR defines the execution semantics of Kern subscription continuations and, in particular, the behavior of:

```java
public interface Subscription {

    CompletionStage<StoredEvents> next(int count);
}
```

The semantic Event Store model defines a `Subscription` as the immutable read continuation of a bounded `StoredEvents` observation.

This ADR specifies what happens operationally when the requested future events do not yet exist and how different Kern implementations should realize that behavior.

The primary objectives are:

* no client-side busy polling;
* natural client-controlled back-pressure;
* no unbounded event buffering;
* no requirement for one blocked platform thread per pending request;
* no event loss between bounded observations;
* retry-safe waiting;
* identical semantics for embedded and remote implementations;
* persisted event log as the only authoritative source of event delivery.

---

## 1. Subscription semantic recap

Given:

```text
StoredEvents(N,F,A,W)
```

where:

```text
N = Namespace
F = EventFilter
A = lower Position boundary
W = observed upper Watermark
```

calling:

```java
Subscription subscription =
    events.follow();
```

produces conceptually:

```text
Subscription(N,F,W)
```

The subscription represents:

> the capability to obtain the next bounded observation of events belonging to namespace `N`, matching filter `F`, and occurring strictly after watermark `W`.

The subscription itself does not advance.

---

## 2. The `next(count)` contract

The public contract is:

```java
CompletionStage<StoredEvents> next(int count);
```

where:

```text
count > 0
```

and `count` represents a **maximum**, not a minimum.

The semantic operation is:

> Produce the next non-empty bounded observation containing at most `count` matching events after the subscription watermark.

Therefore `next(count)` has two possible normal timing behaviors.

If matching events already exist, the stage may complete immediately:

```text
Subscription(N,F,W)
        │
        │ next(100)
        ▼
matching events already persisted
        │
        ▼
CompletionStage completes immediately
```

If no matching events currently exist, the stage remains incomplete:

```text
Subscription(N,F,W)
        │
        │ next(100)
        ▼
no matching events
        │
        ▼
CompletionStage remains pending
```

It is completed only when at least one matching persisted event becomes available.

---

## 3. `next()` must never normally return an empty observation

An empty successful result would force clients into polling:

```java
while (running) {
    StoredEvents events =
        subscription.next(100)
                    .toCompletableFuture()
                    .join();

    if (events.isEmpty()) {
        // try again...
    }
}
```

This would create:

```text
request
empty
request
empty
request
empty
...
```

with unnecessary:

* network traffic;
* CPU usage;
* RocksDB scans;
* latency;
* retry logic.

Instead:

```text
next()
    = wait for meaningful progress
```

Therefore a normally completed `CompletionStage<StoredEvents>` contains at least one matching `StoredEvent`.

---

# 4. `count` means "up to"

Suppose 37 matching events are currently available and the client requests:

```java
subscription.next(100);
```

Kern should return the 37 available events immediately.

It must not wait for another 63 events.

Therefore:

```text
1 <= returned matching events <= count
```

A subscription is intended for low-latency incremental consumption, not batch filling.

---

# 5. Client-controlled back-pressure

The pull model provides back-pressure naturally.

The client explicitly tells Kern how much work it is currently willing to receive:

```java
subscription.next(100);
```

Kern cannot deliver more than 100 matching events through that operation.

After processing them, the client chooses whether and when to continue:

```java
next.follow()
    .next(100);
```

Conceptually:

```text
CLIENT READY
    │
    │ next(100)
    ▼
KERN
    │
    │ <= 100 events
    ▼
CLIENT PROCESSING

(no new request)

KERN sends nothing
```

Absence of demand is therefore the pause mechanism.

No explicit:

```java
pause();
resume();
```

API is required.

---

# 6. Asynchronous waiting rather than blocked threads

The reason `next()` returns `CompletionStage` is not merely API convenience.

The requested event may not exist yet.

A synchronous API such as:

```java
StoredEvents next(int count);
```

would naturally tempt implementations to block a platform thread.

Instead:

```java
CompletionStage<StoredEvents> next(int count);
```

allows Kern to represent:

> this observation will become available later.

The computation is pending.

A thread does not need to be.

---

# 7. The persisted log remains authoritative

A crucial invariant is:

> pending subscriptions are never fed directly from transient append objects.

The wrong architecture would be:

```text
append(Event)
     │
     ├──────────────► subscriber callback
     │
     ▼
  RocksDB
```

because event delivery would then depend on transient runtime state.

Instead:

```text
append
   ↓
durable RocksDB commit
   ↓
Head advances
   ↓
wake possible subscribers
   ↓
subscriber reads persisted log
```

The notification only means:

> something changed; check whether your continuation can now progress.

It does not contain authoritative event data.

---

# 8. Wake-up is a hint

Suppose two subscriptions exist:

```text
Q1:
TaggedAs(courseId,c1)

Q2:
TaggedAs(courseId,c2)
```

and Kern appends:

```text
courseId=c1
```

A very simple implementation may wake both.

Q1 queries:

```text
(W1,H]
```

and finds a matching event.

Its stage completes.

Q2 queries:

```text
(W2,H]
```

and finds nothing.

It returns to waiting.

Therefore:

```text
wake-up
≠
event delivery
```

and:

```text
wake-up
≠
guaranteed filter match
```

This distinction makes the notification infrastructure simple and correctness-independent.

---

# 9. Common `next()` algorithm

Every implementation should behave as if it executes the following conceptual algorithm:

```text
next(count):

    validate count > 0

    H = current namespace Head

    index =
        F.describe(
            Selection(N, W, H)
        )

    read at most count matching Positions

    if at least one exists:
        produce StoredEvents
        complete stage

    otherwise:
        register interest in future namespace changes
        remain pending
```

When notified:

```text
read new Head
re-evaluate bounded filter
```

until:

```text
at least one matching Position exists
```

or the asynchronous operation fails/cancels.

---

# 10. The lost-wakeup race

A naïve implementation contains a classic race:

```text
Thread/request A:

1. query Head
2. no matching event
3. -----------------
4. register waiter
```

An append may occur between 2 and 4:

```text
query empty

                append
                wake waiters
                (A is not registered)

register waiter

wait forever
```

This must not happen.

The implementation must make **check + waiter registration** safe against concurrent append notification.

---

# 11. Correct waiter registration

One possible implementation pattern is:

```text
H1 = Head

query (W,H1]

if match:
    return

register waiter

H2 = Head

if H2 != H1:
    unregister/recheck immediately
else:
    remain waiting
```

Conceptually:

```text
CHECK
  │
  ▼
REGISTER
  │
  ▼
RECHECK HEAD
```

If an append occurred during the registration race, the second Head observation detects it.

Another implementation may coordinate registration and notification differently.

The mechanism is implementation-specific.

The invariant is not:

> every notification must be received.

The invariant is:

> no matching persisted event may leave `next()` indefinitely asleep.

---

# 12. Embedded in-memory implementation

An in-memory implementation can keep waiters in memory.

Conceptually:

```text
Namespace
   │
   └── waiters
        ├── Q1
        ├── Q2
        └── Q3
```

A pending:

```java
CompletionStage<StoredEvents>
```

may be backed by a `CompletableFuture`.

After append:

```text
commit in-memory state
      ↓
advance Head
      ↓
signal namespace waiters
```

Each waiter re-evaluates its `EventFilter`.

The in-memory implementation must preserve exactly the same semantics as RocksDB and remote implementations.

---

# 13. Embedded RocksDB implementation

For `RocksSubscription`:

```text
Subscription(N,F,W)
```

`next(count)` first performs a normal bounded query:

```text
H = Head(N)

F
 ↓
RocksEventSelection(N,W,H)
 ↓
Index
 ↓
positions()
```

If positions exist, it completes immediately.

Otherwise it registers an asynchronous waiter associated at minimum with:

```text
Namespace N
Watermark W
EventFilter F
count
completion handle
```

No RocksDB iterator must remain open while waiting.

This is important.

The pending subscription holds **logical continuation information**, not a database cursor.

---

# 14. No RocksIterator held while waiting

A subscription may remain pending for minutes, hours, or longer.

Keeping a:

```text
RocksIterator
ReadOptions
Snapshot
```

alive for that entire period would be unnecessary and potentially harmful.

Instead:

```text
waiter
   ↓
logical coordinates only
```

and when awakened:

```text
create short-lived RocksDB read
query persisted state
dispose physical iterator
```

This keeps the subscription lifecycle independent from RocksDB cursor lifecycle.

---

# 15. RocksDB append interaction

The append sequence remains:

```text
Tail validation
       ↓
WriteBatch
       ↓
db.write(sync=true)
       ↓
success
       ↓
Head is now visible
       ↓
wake namespace waiters
```

Waiters must never be awakened as a consequence of an uncommitted batch.

If `db.write()` fails:

```text
no wake-up
```

is necessary because no new authoritative event exists.

---

# 16. Waiters should be grouped at least by Namespace

The simplest implementation could keep:

```text
Map<Namespace, Waiters>
```

conceptually.

When namespace N advances:

```text
wake waiters for N
```

and never unrelated namespaces.

This preserves namespace isolation while keeping notification logic simple.

Further indexing of waiters by:

```text
EventType
Tag
```

is a possible optimization but should not exist initially unless measurement justifies it.

---

# 17. Do not duplicate EventFilter indexing for waiters initially

It would be tempting to build an elaborate in-memory structure:

```text
type -> subscriptions
tag -> subscriptions
```

to wake only subscriptions known to match an append.

This creates another index system that must remain consistent with EventFilter semantics.

The initial implementation should prefer:

```text
namespace changed
    ↓
wake namespace waiters
    ↓
authoritative filter/index evaluation
```

This is simpler and safer.

Optimization can follow measurement.

---

# 18. Remote implementation

For a remote client:

```java
RemoteSubscription subscription =
    history.follow();

CompletionStage<StoredEvents> result =
    subscription.next(100);
```

the client sends a request containing conceptually:

```text
Namespace
EventFilter
previous Watermark / continuation token
count
```

The server performs the same semantic operation as embedded Kern.

If matching events exist:

```text
respond immediately
```

Otherwise:

```text
keep operation pending
```

until progress becomes possible.

---

# 19. Remote `next()` is conceptually long polling

The remote behavior resembles long polling:

```text
CLIENT                          SERVER

next(100) -------------------->

                               query
                               no match
                               wait

                               matching append occurs
                               query persisted log

          <-------------------- StoredEvents
```

The term "long polling" describes an implementation technique, not the public API.

The same API could later be implemented using another protocol mechanism.

---

# 20. Server-side asynchronous I/O

A pending remote `next()` must not require:

```text
one platform thread
per waiting client
```

The server should use an asynchronous request model where supported.

Possible implementations include:

```text
Netty event-loop promise
Armeria asynchronous response
gRPC async completion
Servlet async request
virtual thread if deliberately chosen
```

The specific server technology is outside this ADR.

The requirement is resource scalability.

---

# 21. Remote transport timeout

A transport may not permit a request to remain open indefinitely.

For example, infrastructure may impose a 30-second or 60-second timeout.

This does **not** change `Subscription` semantics.

If the transport request expires before a matching event exists:

```text
transport operation ends
```

but:

```text
Subscription(N,F,W)
```

remains semantically unchanged.

The client may issue:

```java
subscription.next(count);
```

again.

No event is lost because the continuation boundary remains W.

---

# 22. Timeout should not produce an empty StoredEvents

A transport timeout should not normally be translated into:

```text
successful empty StoredEvents
```

because that would blur:

```text
meaningful Event Store progress
```

with:

```text
transport lifecycle
```

Prefer:

```text
timeout/cancellation/failure of the CompletionStage
```

followed by safe retry of the same immutable `Subscription`.

---

# 23. Cancellation

Cancellation applies to a specific pending `next()` computation.

It does not mutate the `Subscription`.

Conceptually:

```text
Subscription Q
   │
   ├── next() → Future A
   │              ↓ cancel
   │
   └── Q still valid
```

The client may subsequently execute:

```java
Q.next(100);
```

again.

The implementation should remove the corresponding waiter when cancellation is observed.

---

# 24. Repeated calls on the same Subscription

Because `Subscription` is immutable:

```java
CompletionStage<StoredEvents> a =
    subscription.next(100);

CompletionStage<StoredEvents> b =
    subscription.next(100);
```

both represent requests beginning from the same watermark.

They may consequently return equivalent event windows.

`next()` is not a cursor advance operation.

The client advances through:

```java
StoredEvents next = ...;

Subscription continuation =
    next.follow();
```

---

# 25. No durable server-side subscription required

A `Subscription` can be reconstructed from:

```text
Namespace
EventFilter
Watermark
```

Therefore Kern v1 does not require persistent subscription records.

No RocksDB CF is required for:

```text
subscriptions
consumer cursors
pending next calls
```

Pending requests are ephemeral runtime state.

---

# 26. Crash behavior

Suppose a server crashes while a `next()` is pending.

The future/remote request fails.

After restart, the client retries from the same:

```text
Subscription(N,F,W)
```

or from its durable processing checkpoint using:

```java
store.events(
    namespace,
    filter,
    checkpoint
);
```

Because the event log is authoritative, no subscription recovery journal is necessary.

---

# 27. Consumer processing checkpoint remains separate

Kern knows which events it **delivered**.

It does not know which events the consumer successfully **processed**.

Suppose:

```text
next(100)
    ↓
P101..P150 delivered
```

but consumer processing succeeds only through:

```text
P120
```

before crashing.

The correct durable checkpoint is:

```text
P120
```

not the subscription's delivery boundary.

Therefore consumer progress remains external to Kern v1.

---

# 28. At-least-once processing

The separation between delivery and processing naturally yields an at-least-once model.

A consumer may receive an event again after:

```text
crash
retry
checkpoint lag
transport ambiguity
```

The combination of:

```text
StoredEvent.position()
EventId
```

allows consumer-side deduplication where required.

Kern does not claim exactly-once business processing.

---

# 29. `next(count)` batching semantics

Suppose:

```text
W = P100
H = P500
```

and matching events are:

```text
P105
P110
P120
P130
P140
```

### `next(3)`

returns:

```text
P105
P110
P120
```

with a new safe watermark no later than P120.

### `next(100)`

returns all five immediately and may safely advance the new observation watermark to:

```text
P500
```

because Kern has exhaustively established that no additional matching events exist through P500.

---

# 30. Why watermark may advance beyond the last matching event

Consider:

```text
W0 = P100
H  = P500

only matching event:
P120
```

Calling:

```java
next(100);
```

may return only:

```text
P120
```

but the resulting watermark can be:

```text
P500
```

because the query exhausted `(P100,P500]`.

This captures valuable negative knowledge:

> no other matching event existed through P500.

Therefore subsequent:

```java
result.follow();
```

correctly starts after P500 rather than P120.

---

# 31. Why a full batch uses a smaller boundary

Conversely:

```text
matching:
P105 P110 P120 P130 ...
count = 3
```

Kern stopped because the demand limit was reached, not because it exhausted the current history.

It cannot therefore claim:

> no more matching events exist through Head.

The new boundary must not cross an undispatched matching event.

Using:

```text
last returned Position
```

is the simplest correct boundary in v1.

---

# 32. Empty namespace / no future activity

If:

```java
subscription.next(100);
```

is executed and the namespace never receives another matching event, the `CompletionStage` may remain pending indefinitely at the semantic level.

This is intentional.

Operational policies such as:

```text
timeout
cancellation
server shutdown
connection termination
```

may end the concrete computation.

The semantic subscription itself remains valid.

---

# 33. Append of irrelevant events

Suppose:

```text
Subscription filter:
courseId=c1
```

and the log receives:

```text
courseId=c2
courseId=c3
courseId=c4
```

The subscription must not complete.

It may be awakened and recheck the log, but the stage remains pending until:

```text
at least one courseId=c1 event
```

is persisted.

---

# 34. Many irrelevant writes

An implementation should avoid pathological busy wake/recheck loops when a namespace has high activity unrelated to a subscription.

The baseline namespace-level wake strategy is correct but may become inefficient.

If profiling demonstrates this problem, Kern may introduce smarter waiter routing based on filter structure.

Such optimization must preserve the rule:

> persisted query evaluation remains authoritative.

---

# 35. Possible future waiter optimization

A future runtime may compile EventFilter into a notification selector:

```text
EventType
Tag
```

and use appended event metadata to wake a smaller set of waiters.

For example:

```text
append tags/type
       ↓
candidate waiters
       ↓
wake
       ↓
authoritative query still verifies
```

The notification index would remain an optimization.

It must never become the source of correctness.

---

# 36. Error handling

A pending `next()` may complete exceptionally because of:

```text
runtime shutdown
storage failure
transport failure
cancellation
operational timeout
invalid request
```

After failures that do not alter subscription semantics, retrying the same immutable `Subscription` is safe.

---

# 37. Storage failure while waiting

If the waiter wakes but RocksDB cannot perform the required query, the stage should fail.

It should not remain silently pending indefinitely while the runtime is unhealthy.

The caller may decide when to retry.

---

# 38. Runtime shutdown

On controlled shutdown, pending `next()` operations should be completed exceptionally or cancelled.

They must not prevent shutdown indefinitely.

No durable state needs to be written for them.

---

# 39. Memory bounds

Although event buffering is avoided, waiter registrations themselves consume memory.

The runtime must therefore bound:

```text
total pending next requests
pending requests per principal
pending requests per namespace
```

according to operational configuration.

When limits are reached, new requests may be rejected as overload.

---

# 40. Multiple namespaces

Waiters should at least be partitioned by namespace:

```text
waiters
 ├── Namespace A
 │    ├── Q1
 │    └── Q2
 │
 └── Namespace B
      └── Q3
```

An append in B has no reason to wake Q1 or Q2.

This is both semantically natural and inexpensive.

---

# 41. Implementation-neutral behavioral contract

All implementations must pass equivalent scenarios.

Given:

```text
Subscription(N,F,W)
```

the following must hold:

1. if matching events already exist after W, `next(n)` can complete immediately;
2. if none exist, the stage remains pending;
3. irrelevant writes do not make it complete;
4. a relevant committed write eventually permits completion;
5. at most `n` matching events are returned;
6. the result is never normally empty;
7. result ordering follows `Position`;
8. retrying the same Subscription starts from the same boundary;
9. `next()` does not mutate Subscription;
10. no persisted matching event is skipped by repeatedly following returned observations.

---

# 42. Example — embedded RocksDB

Initial state:

```text
Head = P100
Subscription = Q(N,F,P100)
```

Client:

```java
CompletionStage<StoredEvents> future =
    subscription.next(50);
```

RocksDB query:

```text
(P100,P100]
→ empty
```

Kern registers waiter Q.

Later:

```text
append irrelevant event P101
```

after durable commit:

```text
Head = P101
wake Q
```

Q evaluates:

```text
(P100,P101]
→ no match
```

and remains waiting.

Later:

```text
append matching event P102
```

commit:

```text
Head = P102
wake Q
```

Q evaluates:

```text
(P100,P102]
→ P102
```

and completes:

```text
future
    ↓
StoredEvents(... watermark=P102)
```

---

# 43. Example — remote client

```text
CLIENT                             KERN SERVER

Q.next(100)
   |
   +------------------------------->

                                  query (W,H]
                                  no match
                                  register waiter

                                  ... time passes ...

                                  append matching event
                                  durable commit
                                  wake waiter
                                  query authoritative log

   <-------------------------------
          StoredEvents response
```

If an HTTP intermediary terminates the request before an event arrives:

```text
request fails
```

the client can simply execute:

```java
Q.next(100);
```

again.

---

# 44. Example — client processing loop

A straightforward asynchronous consumer may look conceptually like:

```java
void consume(StoredEvents current) {

    current.follow()
        .next(100)
        .thenAccept(next -> {

            for (StoredEvent event : next) {
                process(event);
                checkpoint(event.position());
            }

            consume(next);
        });
}
```

The actual application should handle errors and avoid uncontrolled recursion according to its execution framework.

The important semantic progression is:

```text
S0
 ↓ follow
Q0
 ↓ next
S1
 ↓ follow
Q1
 ↓ next
S2
```

---

# 45. Example — synchronous client choice

Although Kern itself is asynchronous, a client is free to choose blocking semantics:

```java
StoredEvents next =
    current.follow()
           .next(100)
           .toCompletableFuture()
           .join();
```

With virtual threads this may even be operationally appropriate.

The crucial distinction is:

> Kern does not force the implementation to consume a platform thread while waiting.

The client may choose its own execution model.

---

# 46. Testing requirements

The common Kern test kit should include subscription tests for:

```text
next remains pending when empty

next completes after matching append

irrelevant append does not complete next

multiple irrelevant appends followed by relevant append

events already available produce immediate completion

count is respected

result is non-empty

full batch boundary does not skip undispatched events

exhausted window may advance watermark to Head

same Subscription can be retried

two next() calls on same Subscription observe same origin

namespace isolation

lost-wakeup race

cancellation removes waiter

runtime shutdown releases waiter
```

---

# 47. Lost-wakeup stress test

The race between:

```text
empty check
waiter registration
append
```

should have a dedicated high-contention test.

Repeatedly:

```text
create Subscription
call next()
append concurrently near registration boundary
```

and verify that every committed matching event eventually completes the request.

This is a correctness test, not merely a performance test.

---

# 48. No polling invariant

Implementations should also have tests/metrics capable of detecting accidental polling behavior.

A pending subscription with no namespace changes should not repeatedly issue:

```text
RocksDB queries
network requests
timer-driven scans
```

at short intervals merely to discover whether something changed.

The intended state is genuinely dormant until:

```text
notification
timeout/cancellation
shutdown
```

---

# 49. Metrics

Useful operational metrics include:

```text
pending subscription requests

next() immediate completions

next() waited completions

average wait duration

waiters awakened

wake-ups yielding no matching result

cancelled next operations

timed-out remote operations

waiter registration count

subscription overload rejections
```

The ratio:

```text
wake-ups with match
/
total wake-ups
```

can later indicate whether namespace-level waiter signaling should be optimized.

---

# 50. Design consequences

This model deliberately avoids:

```text
Observer callbacks
push delivery
unbounded queues
pause/resume state
server-side durable cursors
busy polling
per-subscription RocksIterator
long-lived RocksDB Snapshot
mandatory blocked thread
```

while still supporting live event consumption.

The entire mechanism is built from three existing Kern concepts:

```text
StoredEvents
Subscription
Position/Watermark
```

No additional domain abstraction is necessary.

---

# 51. Core semantic pattern

The model can be summarized as:

```text
             StoredEvents(N,F,A,W)
                      │
                      │ follow()
                      ▼
              Subscription(N,F,W)
                      │
                      │ next(count)
                      ▼
             CompletionStage
                      │
         ┌────────────┴────────────┐
         │                         │
 matching events exist       no matching events
         │                         │
         ▼                         ▼
 complete now                remain pending
                                   │
                                   │ relevant
                                   │ committed change
                                   ▼
                              query log
                                   │
                                   ▼
                               complete
```

---

# 52. Decision

Kern adopts **asynchronous demand-driven waiting** for subscription continuations.

`Subscription.next(count)`:

* expresses explicit client demand;
* returns at most `count` events;
* never normally returns an empty observation;
* completes immediately if matching events already exist;
* otherwise remains asynchronously pending until at least one matching persisted event becomes available;
* does not require a blocked platform thread;
* does not mutate the Subscription;
* uses persisted log reads as the authoritative source;
* treats append notifications only as wake-up hints;
* allows timeout, cancellation and transport lifecycle to remain operational concerns;
* can be safely retried from the same immutable continuation boundary.

Embedded, RocksDB and remote implementations may use different waiting mechanisms, but they must preserve exactly these semantics.

The result is intentionally simple:

```text
the client decides how much it can consume;
Kern decides when that amount begins to exist.
```