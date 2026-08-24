# ADR — Constraint-Oriented Decisions and Reduced Event Observations

## Status

**Accepted**

## Context

Kern represents a read from the Event Store as an immutable and bounded observation:

```java
StoredEvents history =
    store.events(
        namespace,
        filter,
        after
    );
```

`StoredEvents` is not a cursor or an eagerly materialized result.

It represents a stable observation of matching persisted events up to a hidden watermark. It is lazy and repeatably iterable: independent traversals may reproduce the same logical observation without requiring the object itself to retain the materialized events.

The same observation also establishes the consistency boundary used by:

```java
history.tail().append(...)
```

The `Tail` is determined conceptually by:

```text
Namespace
EventFilter
Watermark
```

and an append becomes stale if an event matching the original filter appears after that watermark.

This model makes it possible to construct a write model around the facts relevant to a decision rather than around a traditional aggregate version.

However, two additional requirements emerge when the write model is organized around explicit business constraints.

First, a command may depend on several independent constraints:

```text
EnrollStudentInCourse

requires
    StudentIsEnrolledInAcademicYear
    CourseExists
    StudentHasAvailableCourseSlot
    CourseHasAvailableCapacity
```

Each constraint depends on its own event history.

The consistency boundary of the complete decision must therefore include the union of all those dependencies.

Second, the complete history required to protect the decision is not necessarily the history that each individual constraint needs to inspect.

If:

```text
Constraint A requires FA
Constraint B requires FB
Constraint C requires FC
```

the decision must observe:

```text
D = FA OR FB OR FC
```

but `Constraint A` should not have to traverse events belonging exclusively to `FB` or `FC`.

A further problem occurs when even the history of one constraint is very large.

Examples include:

* determining whether another active course already has the same title;
* verifying that no more than fifty courses currently exist;
* obtaining the current value of a frequently changed property;
* determining current state from create/change/remove lifecycle events.

In such cases, the complete relevant history is necessary to establish correctness, but many historical events are no longer necessary to evaluate the current constraint.

This ADR introduces two cooperating concepts:

1. **Constraint-oriented decisions**, where business constraints explicitly declare their event dependencies;
2. **reduced event observations**, where a `StoredEvents` observation may expose a smaller representation of the same bounded history without changing its consistency boundary.

---

# 1. Core principle

A command represents an intended change.

An event certifies an accepted change.

The decision between them is governed by business constraints:

```text
Command
   ↓
Constraints
   ↓
Decision
   ↓
Event
```

A useful formulation is:

> **Without constraints, every command would succeed. The write model exists to explain why some commands must not.**

Constraints are therefore treated as first-class domain objects.

---

# 2. Constraint

A `Constraint` represents a business rule that must be satisfied before a command may produce its resulting events.

Examples include:

```text
StudentIsEnrolledInAcademicYear
CourseExists
CourseTitleIsAvailable
StudentHasAvailableCourseSlot
CourseHasAvailableCapacity
```

The names should come from the business domain rather than from the mechanism used to verify them.

Prefer:

```java
new StudentHasAvailableCourseSlot(...)
```

over:

```java
new CountLessThan(...)
```

The latter may still exist as an implementation collaborator but is not the business rule.

---

# 3. Constraint responsibilities

A constraint has two intrinsically related responsibilities:

```text
1. declare which historical facts may influence its result;
2. determine whether the rule is satisfied from those facts.
```

Conceptually:

```java
public interface Constraint {

    EventFilter dependency();

    ConstraintResult verify(
        StoredEvents evidence
    );
}
```

The exact method names may evolve.

The semantic responsibilities are normative.

---

# 4. Constraint dependencies

`dependency()` answers:

> **Which events may change whether this constraint is satisfied?**

For example:

```text
StudentIsEnrolledInAcademicYear(S1, 2026)
```

may depend on:

```text
type =
    StudentEnrolledInAcademicYear
  | StudentWithdrawnFromAcademicYear

studentId = S1
academicYear = 2026
```

while:

```text
StudentHasAvailableCourseSlot(S1, 2026)
```

may depend on:

```text
StudentEnrolledInCourse
StudentWithdrawnFromCourse
```

for the same student and academic year.

The dependency is a correctness boundary, not merely a query optimization.

---

# 5. Dependency completeness

For a constraint `C` with dependency `F`, the following must hold:

> **Every event capable of changing the result of C must match F.**

`F` may be broader than strictly necessary.

It must never be narrower.

A broader dependency may create additional optimistic conflicts.

A narrower dependency may allow an invalid decision to commit.

Correctness therefore requires conservative inclusion.

---

# 6. Constraints interpret history

A constraint is not necessarily a predicate on an individual event.

Many business rules depend on event order.

For example:

```text
StudentEnrolled(2026)
StudentWithdrawn(2026)
```

means that the student is currently not enrolled.

While:

```text
StudentWithdrawn(2026)
StudentEnrolled(2026)
```

produces the opposite current state.

A constraint may therefore internally use:

```text
filtering
mapping
folding
counting
last relevant fact
state transitions
set reduction
```

The iteration is not the model.

The business constraint is the model.

---

# 7. Constraint results

Constraint verification should not normally return a bare boolean.

A violated constraint contains business information.

Conceptually:

```text
ConstraintResult
     |
     +-- Satisfied
     |
     `-- Violation
```

Possible violations include:

```text
StudentNotEnrolledInAcademicYear
CourseDoesNotExist
CourseTitleAlreadyUsed
StudentCourseLimitReached
CourseCapacityReached
```

This improves error reporting, testing and documentation.

---

# 8. Constraint composition

Constraints may be composed into larger constraints.

For example:

```java
Constraint allowed =
    new AllConstraints(
        new StudentIsEnrolledInAcademicYear(
            student,
            year
        ),
        new CourseExists(course),
        new StudentHasAvailableCourseSlot(
            student,
            year
        ),
        new CourseHasAvailableCapacity(
            course
        )
    );
```

When a stable business concept exists, the composite should preferably receive a business name:

```java
new StudentMayEnrollInCourse(
    student,
    course,
    year
)
```

rather than exposing the generic composition everywhere.

---

# 9. Dependency composition

Suppose:

```text
A depends on FA
B depends on FB
C depends on FC
```

A decision requiring all three constraints depends on:

```text
D = FA OR FB OR FC
```

not:

```text
FA AND FB AND FC
```

The reason is simple:

> evaluating all constraints requires all events capable of affecting any one of them.

Thus:

```text
AND between business constraints
             ↓
OR between their event dependencies
```

This union becomes the consistency boundary of the complete decision.

---

# 10. The composite observation

The decision therefore obtains one bounded observation:

```java
StoredEvents history =
    store.events(
        namespace,
        new AnyEvents(
            a.dependency(),
            b.dependency(),
            c.dependency()
        ),
        after
    );
```

Conceptually:

```text
                    decision observation

                 D = FA | FB | FC
                         |
                         v
                   StoredEvents
                         |
                         |
                    watermark W
```

This observation protects the entire decision.

---

# 11. The composite history is not necessarily child evidence

Although the observation must protect:

```text
FA | FB | FC
```

`Constraint A` needs only the events belonging to `FA`.

Passing the complete iterable directly to every child would cause several problems:

```text
each child sees irrelevant events
each traversal may repeat storage reads
remote implementations may transfer unnecessary events
larger histories increase interpretation cost
```

The distinction is therefore:

```text
decision observation
    all events that protect the complete decision

constraint evidence
    events that one constraint actually needs to inspect
```

The two must not be confused.

---

# 12. StoredEvents gains reduction

`StoredEvents` is extended with:

```java
public interface StoredEvents
    extends Iterable<StoredEvent> {

    StoredEvents reduce(
        EventReduction reduction
    );

    Tail tail();

    Subscription follow();
}
```

This is a deliberate evolution of the previous API.

`reduce()` belongs to `StoredEvents` because it derives another representation of the **same bounded observation**.

It does not create another Event Store query or another source of truth.

---

# 13. Definition of EventReduction

An `EventReduction` derives a smaller ordered representation of a bounded event observation.

Formally, for an event sequence `S` and reduction `R`:

```text
R(S) ⊆ S
```

where `⊆` means that the result contains only events already present in `S`.

A reduction:

```text
may discard events
```

but may not:

```text
create events
modify events
replace events with synthetic events
aggregate into a scalar
reorder surviving events
change event positions
change the observation watermark
change the consistency dependency
```

The result remains a `StoredEvents`.

---

# 14. Reduction is information compression

The purpose of reduction is not arbitrary querying.

It is:

> **remove historical events that are no longer necessary for a particular interpretation while preserving the bounded event observation from which they came.**

A useful governing rule is:

> **Reduction is information compression, not aggregation.**

Therefore:

```text
StoredEvents → StoredEvents
```

is allowed.

Operations such as:

```text
StoredEvents → long
StoredEvents → Map
StoredEvents → arbitrary state object
```

do not belong to `EventReduction`.

Those operations remain the responsibility of domain objects or utility libraries such as Cactoos.

---

# 15. Reduction preserves the consistency boundary

This is the central semantic rule.

Suppose the original observation is conceptually:

```text
StoredEvents(
    Namespace N,
    Dependency F,
    after A,
    watermark W
)
```

Then:

```java
StoredEvents reduced =
    history.reduce(R);
```

must still be protected by:

```text
Namespace N
Dependency F
Watermark W
```

regardless of how many events `R` removes from iteration.

Formally:

```text
boundary(reduce(S,R))
=
boundary(S)
```

Therefore:

> **Reduction changes representation, never dependency.**

---

# 16. Tail after reduction

Because the consistency boundary is preserved:

```java
history.tail()
```

and:

```java
history.reduce(R).tail()
```

represent semantically equivalent append capabilities.

Both protect the decision against new events matching the **original observation dependency** after watermark `W`.

The events discarded by a reduction are not discarded from concurrency semantics.

---

# 17. Why this matters

Suppose:

```text
CourseCreated(C1)
CourseTitleChanged(C1)
CourseRemoved(C1)
```

and a reduction exposes only the latest event:

```text
CourseRemoved(C1)
```

The previous historical events disappear from iteration.

However, the consistency boundary still includes every event type declared by the original dependency.

A later matching event can still invalidate the `Tail`.

The reduced history does not redefine what may influence the decision.

---

# 18. Reduction is lazy

Calling:

```java
StoredEvents reduced =
    history.reduce(reduction);
```

must not imply immediate traversal of `history`.

Just as `store.events(...)` creates a lazy bounded observation, `reduce(...)` creates a lazy derived observation.

Conceptually:

```text
history

N
F
A
W
reductions = []

        ↓ reduce(R1)

N
F
A
W
reductions = [R1]

        ↓ reduce(R2)

N
F
A
W
reductions = [R1,R2]
```

Execution begins only when traversal is requested.

---

# 19. Reduction composition is ordered

Multiple reductions form an ordered pipeline.

For:

```java
history
    .reduce(R1)
    .reduce(R2);
```

the semantics are:

```text
R2(R1(history))
```

not an unordered set of operations.

This distinction is fundamental.

---

# 20. Example — course lifecycle

Consider:

```text
P1  CourseCreated(C1)
P2  CourseCreated(C2)
P3  CourseTitleChanged(C1)
P4  CourseRemoved(C1)
```

The correct derivation of currently active courses is:

```text
latest event per course
        ↓
exclude courses whose latest event is removal
```

not:

```text
exclude removals
        ↓
latest event per course
```

The second interpretation would incorrectly resurrect C1.

Reduction order is therefore part of the semantic contract.

---

# 21. Initial EventReduction algebra

The initial algebra is deliberately small:

```text
Latest
LatestBy
Matching
Excluding
```

Additional reductions should only be introduced in response to concrete decision-model requirements.

Kern must not evolve into an alternative Java Stream API.

---

# 22. Latest

```java
new Latest()
```

keeps only the event having the greatest `Position` in the current representation.

For:

```text
P10 A
P20 B
P30 C
```

the result is:

```text
P30 C
```

A common usage is obtaining the current fact for a property whose latest change supersedes previous ones.

---

# 23. LatestBy

```java
new LatestBy(
    new TagName("courseId")
)
```

keeps the event with the greatest `Position` for every distinct value of the specified tag.

For:

```text
P10 C1 CourseCreated
P11 C2 CourseCreated
P15 C1 CourseTitleChanged
P18 C3 CourseCreated
P21 C2 CourseRemoved
P27 C1 CourseTitleChanged
```

the result is:

```text
P18 C3 CourseCreated
P21 C2 CourseRemoved
P27 C1 CourseTitleChanged
```

The surviving events remain ordered by `Position`.

No `Map` or grouped collection becomes part of the public API.

---

# 24. Matching

```java
new Matching(filter)
```

keeps only events in the current representation satisfying the supplied `EventFilter`.

For example:

```java
history.reduce(
    new Matching(
        new TypedBy("CourseCreated")
    )
);
```

This does **not** replace the dependency of the source observation.

The supplied filter describes only the reduced representation.

---

# 25. Excluding

```java
new Excluding(filter)
```

removes events in the current representation satisfying the supplied filter.

For example:

```java
history.reduce(
    new Excluding(
        new TypedBy("CourseRemoved")
    )
);
```

is particularly useful after a lifecycle reduction such as `LatestBy`.

---

# 26. Why Matching is different from EventStore.events()

These two operations look similar:

```java
store.events(namespace, F, after)
```

and:

```java
history.reduce(new Matching(G))
```

but their consistency semantics are different.

The first establishes:

```text
dependency = F
```

The second preserves the existing dependency.

Conceptually:

```text
store.events(F)
    dependency = F
    visible selection = F
```

while:

```text
store.events(F)
     .reduce(Matching(G))
```

has:

```text
dependency = F

visible representation =
    matching F
    subsequently reduced by G
```

`G` cannot narrow the `Tail` boundary.

---

# 27. StoredEvents has two semantic dimensions

The introduction of reduction reveals that a bounded observation has two distinct concerns:

```text
Consistency scope
    which events may invalidate the decision?

Traversal representation
    which events are currently exposed to iteration?
```

At initial creation they coincide.

After reduction they may not.

This distinction is central to this ADR.

---

# 28. Formal observation model

A `StoredEvents` observation can now be modeled conceptually as:

```text
StoredEvents(
    N,
    D,
    A,
    W,
    R
)
```

where:

```text
N = Namespace

D = dependency EventFilter
    defining consistency

A = exclusive lower Position

W = observation watermark

R = ordered reduction pipeline
    defining traversal representation
```

The initial call:

```java
store.events(N, F, A)
```

produces:

```text
StoredEvents(
    N,
    F,
    A,
    W,
    Identity
)
```

A reduction produces:

```text
StoredEvents(
    N,
    F,
    A,
    W,
    Rnext ∘ R
)
```

without changing `F` or `W`.

---

# 29. Formal traversal semantics

Define the original bounded sequence:

```text
B(N,D,A,W)
=
[
    e ∈ Log(N)
    |
    A < e.position <= W
    AND D matches e
]
```

ordered by increasing `Position`.

Then:

```text
iterate(
    StoredEvents(N,D,A,W,R)
)
=
R(
    B(N,D,A,W)
)
```

where `R` is the ordered reduction pipeline.

For every legal `R`:

```text
R(B)
```

is a subsequence of `B`.

---

# 30. Formal Tail semantics

For:

```text
S = StoredEvents(N,D,A,W,R)
```

the tail is:

```text
Tail(N,D,W)
```

`A` and `R` do not participate in future append validation.

The Tail is stale at current head `H` iff:

```text
∃ e ∈ Log(N)
such that
W < e.position <= H
AND
D matches e
```

Reduction never alters this rule.

---

# 31. Constraint evidence through Matching

This distinction solves composite constraints naturally.

Suppose:

```text
A depends on FA
B depends on FB
C depends on FC
```

The decision acquires:

```java
StoredEvents history =
    store.events(
        namespace,
        new AnyEvents(
            a.dependency(),
            b.dependency(),
            c.dependency()
        ),
        after
    );
```

Each child receives only its own evidence:

```java
StoredEvents evidenceA =
    history.reduce(
        new Matching(
            a.dependency()
        )
    );
```

and similarly for B and C.

---

# 32. Same observation, different evidence

The model becomes:

```text
                    StoredEvents
                 dependency FA|FB|FC
                       W
                   /   |   \
                  /    |    \
                 /     |     \
       Matching(FA) Matching(FB) Matching(FC)
            |           |           |
            v           v           v
       Evidence A   Evidence B   Evidence C
```

All child views:

```text
share Namespace
share after boundary
share watermark
share decision consistency dependency
```

but expose different event representations.

---

# 33. A constraint may further reduce its evidence

A child constraint may apply additional reductions appropriate to its own business interpretation.

For example:

```java
StoredEvents courses =
    evidence
        .reduce(
            new LatestBy(
                new TagName("courseId")
            )
        )
        .reduce(
            new Excluding(
                new TypedBy("CourseRemoved")
            )
        );
```

A constraint such as:

```text
CourseTitleIsAvailable
```

may then inspect only currently active course representatives rather than the complete lifecycle history.

---

# 34. Example — unique course title

Suppose the command is:

```text
CreateCourse("Advanced Java")
```

The constraint:

```text
CourseTitleIsAvailable
```

may depend on:

```text
CourseCreated
CourseTitleChanged
CourseRemoved
```

for every course.

Its source history may contain millions of events.

The constraint can derive:

```text
all relevant lifecycle events
        ↓
LatestBy(courseId)
        ↓
Excluding(CourseRemoved)
        ↓
one current representative per active course
```

and inspect the remaining events for title equality.

No application-level persistent projection is required.

No custom materialized write index becomes part of the semantic model.

---

# 35. Example — maximum number of courses

The constraint:

```text
CourseLimitNotReached(50)
```

may use the same reduced evidence:

```text
LatestBy(courseId)
        ↓
Excluding(CourseRemoved)
```

and then use an ordinary iterable utility to determine the number of remaining active courses.

For example, Cactoos may provide the counting mechanism.

Kern does not need:

```java
StoredEvents.count();
```

The Event Store remains responsible for producing a meaningful bounded event sequence.

The domain remains responsible for interpreting it.

---

# 36. Example — current property

Suppose:

```text
CourseTitleChanged
```

may occur hundreds of times for one course.

A constraint needing only the current title can derive:

```java
evidence
    .reduce(
        new Matching(
            new TypedBy("CourseTitleChanged")
        )
    )
    .reduce(
        new Latest()
    );
```

Only the most recent matching fact remains visible.

---

# 37. Constraints remain outside Kern core

`Constraint`, `ConstraintResult` and business violations do not belong to Kern.

Kern remains an Event Store and does not know:

```text
commands
constraints
business decisions
violations
```

The dependency direction is:

```text
constraint / decision model
          ↓
       kern-api
```

never:

```text
kern-api
   ↓
constraint framework
```

This preserves the existing responsibility boundary: Kern begins when the client domain has produced an `Event`; commands and decisions remain client-domain concerns.

---

# 38. EventReduction does belong to Kern

`EventReduction`, unlike `Constraint`, belongs to the Kern semantic API.

The reason is that it describes a transformation of a Kern concept:

```text
bounded StoredEvents observation
        ↓
bounded reduced StoredEvents observation
```

and must preserve Kern-specific semantics including:

```text
watermark
Tail consistency
lazy traversal
remote execution
repeatable iteration
```

It is therefore an Event Store capability rather than a business-rule abstraction.

---

# 39. EventReduction must be declarative

Do not expose:

```java
history.reduce(
    events -> arbitraryJavaCode(events)
);
```

A Java lambda would make reduction:

```text
uninspectable
non-serializable
storage-unaware
impossible to optimize remotely
```

Instead `EventReduction` is a declarative object algebra, similar in spirit to `EventFilter`.

Conceptually:

```java
public interface EventReduction {

    <T> T describe(
        EventReductionSelection<T> selection
    );
}
```

---

# 40. EventReductionSelection

A possible initial interpreter contract is:

```java
public interface EventReductionSelection<T> {

    T latest();

    T latestBy(TagName tag);

    T matching(EventFilter filter);

    T excluding(EventFilter filter);
}
```

The exact Java signature may evolve during implementation.

The important architectural property is:

> each implementation interprets the same reduction semantics using its own native mechanisms.

---

# 41. Embedded interpretation

An in-memory implementation may interpret reductions using iterable operations.

For example:

```text
Matching
    → filtered iterable

Excluding
    → complementary filtering

Latest
    → final element

LatestBy
    → ordered keyed reduction
```

Cactoos primitives may be used internally where they fit naturally.

---

# 42. RocksDB observation creation remains lazy

Calling:

```java
store.events(namespace, filter, after)
```

does not materialize either events or a `List<Position>`.

The RocksDB architecture already defines `Index` as an ordered lazy source of positions, and authoritative event records are loaded only after final matching positions are resolved.

Conceptually:

```text
store.events(...)
       ↓
capture W
       ↓
compile / retain lazy Index
       ↓
RocksStoredEvents
```

Traversal later performs:

```text
Index.positions()
      ↓
Position
      ↓
Events.at(position)
      ↓
StoredEvent
```

---

# 43. RocksDB reduction execution

Calling:

```java
history.reduce(R)
```

must likewise avoid executing the reduction immediately.

The reduction is appended to the immutable observation description.

At traversal time the implementation has access to:

```text
Namespace
Dependency EventFilter
after
Watermark
complete reduction pipeline
```

and may compile them together into an efficient traversal.

Conceptually:

```text
EventFilter
     +
Reduction pipeline
        ↓
RocksDB observation interpretation
        ↓
Iterable<Position>
        ↓
StoredEvent
```

---

# 44. Push reduction toward positions

Whenever possible, reduction should occur before authoritative event decoding.

Preferred:

```text
indexes
   ↓
positions
   ↓
reduce candidate positions
   ↓
load surviving StoredEvents
```

over:

```text
load millions of StoredEvents
   ↓
reduce them in Java
```

This is not always possible for every reduction/filter combination, but it is the desired RocksDB implementation strategy.

---

# 45. Matching can often be pushed down

Consider:

```java
history.reduce(
    new Matching(
        child.dependency()
    )
);
```

If no preceding reduction changes the meaning of this operation, RocksDB can execute the child dependency directly as a bounded index selection:

```text
child dependency
      ↓
RocksEventSelection
      ↓
Index
      ↓
positions
```

rather than iterating the union dependency and filtering afterwards.

This is particularly important for composite constraints.

---

# 46. Reduction rewrites must preserve semantics

The implementation may optimize a reduction pipeline.

For example:

```text
Matching(G)
```

may be pushed into storage selection.

However, rewrites are permitted only when they preserve the declared reduction order.

For example:

```text
LatestBy(courseId)
then
Excluding(CourseRemoved)
```

must never be reordered into:

```text
Excluding(CourseRemoved)
then
LatestBy(courseId)
```

because the two expressions are not equivalent.

Semantic correctness takes priority over query optimization.

---

# 47. LatestBy and RocksDB

`LatestBy(TagName)` is the most demanding initial reduction and should be the primary implementation benchmark.

The current RocksDB tag index is organized conceptually as:

```text
Namespace | TagName | TagValue | Position
```

which groups positions for each tag value and orders them by `Position`.

This layout is promising for:

```text
LatestBy(courseId)
```

because it may allow the implementation to determine one bounded maximum position for each distinct `courseId` value without decoding every event.

The precise algorithm remains an implementation concern and must be benchmarked.

The semantic API does not guarantee a particular complexity class.

---

# 48. No hidden materialized projections

`LatestBy` does not require Kern to maintain an application-specific materialized projection during append.

This ADR explicitly avoids making arbitrary domain projections part of Kern's synchronous write path.

The event log and existing physical indexes remain authoritative.

Reduction is computed from the bounded observation when required, with implementation-specific optimization.

---

# 49. Remote StoredEvents

A remote `StoredEvents` cannot contain RocksDB iterators or eagerly transferred event histories.

It can be represented by immutable observation coordinates such as:

```text
Namespace
Dependency EventFilter
after
Watermark
Reduction pipeline
```

or an opaque token encoding equivalent information.

The runtime ADR already permits `StoredEvents`, `Tail` and `Subscription` to be represented remotely through immutable observation coordinates or opaque continuation tokens.

---

# 50. Remote iteration

Calling:

```java
remoteEvents.iterator();
```

may create a paged traversal:

```text
CLIENT                         SERVER

observation description
+ reductions
+ page request
      ------------------------>

                         execute bounded
                         reduced observation

      <------------------------
        StoredEvent page
        continuation
```

The client does not need to download the unreduced source history.

Reduction should execute as close to the storage engine as practical.

---

# 51. Remote constraint evidence

This is particularly useful for composed constraints.

The client may retain one decision observation:

```text
D = FA | FB | FC
W = observed watermark
```

while requesting child evidence such as:

```text
Matching(FA)
```

The server can execute:

```text
FA bounded to W
```

directly and transfer only the relevant events.

Thus the aggregate consistency boundary does not force every child to download the aggregate event union.

---

# 52. Repeatable iteration

A reduced `StoredEvents` remains repeatably iterable.

Calling:

```java
reduced.iterator();
```

twice must produce two traversals of the same reduced logical observation.

It does not imply that Kern caches materialized elements.

The existing principle remains:

```text
StoredEvents
    ≠ Iterator
    ≠ Cursor
    ≠ ResultSet
```



---

# 53. No implicit CachedStoredEvents

Kern must not automatically materialize every observation merely because constraints may traverse it more than once.

An observation may contain millions of events.

Implicit caching could convert lazy storage reads into unbounded memory retention.

Therefore:

> **repeatable iteration does not imply cached iteration.**

---

# 54. Optional caching

A decorator such as:

```java
new CachedStoredEvents(origin)
```

may be provided by higher-level infrastructure when appropriate.

An implementation may use mechanisms such as Cactoos sticky iterables if they satisfy the required semantics.

Caching is an optimization policy.

It is not part of the fundamental `StoredEvents` contract.

---

# 55. Multiple constraint execution strategies

Because the constraint model explicitly exposes child dependencies, a higher-level decision runtime may choose different physical strategies without changing the constraints.

### Selective strategy

```text
A → FA read
B → FB read
C → FC read
```

Useful when dependencies are selective.

### Shared strategy

```text
FA | FB | FC
      ↓
single materialized traversal
      ↓
child filtering in memory
```

Useful when the history is small or dependencies overlap heavily.

### Hybrid strategy

Closely overlapping dependencies may share work while unrelated ones remain separate.

No planner is required initially.

The important property is that the semantic model permits such optimization later.

---

# 56. Constraints do not choose transport strategy

A constraint only declares:

```text
dependency
business verification
optional reductions inside verification
```

It should not know whether evidence came from:

```text
local RocksDB scan
remote paged query
cached iterable
shared composite fetch
```

These are runtime concerns.

---

# 57. Same observation protects the final append

After all constraints have been verified, the resulting events must be appended through the `Tail` of the **composite decision observation**:

```java
StoredEvents history =
    store.events(
        namespace,
        constraints.dependency(),
        after
    );

ConstraintResult result =
    constraints.verify(history);

if (result.isSatisfied()) {
    history.tail().append(events);
}
```

A child evidence view must not create a narrower consistency boundary.

---

# 58. Child Tail is intentionally still the composite Tail

If:

```java
StoredEvents evidence =
    history.reduce(
        new Matching(
            child.dependency()
        )
    );
```

then:

```java
evidence.tail()
```

still represents the same composite Tail as:

```java
history.tail()
```

because reduction preserves the original dependency.

This is essential.

Otherwise a command could be verified using several constraints but accidentally commit using only one child constraint's boundary.

---

# 59. Constraint Composite behavior

A conceptual implementation of `AllConstraints` may therefore operate as:

```java
for each child:
    evidence =
        history.reduce(
            new Matching(
                child.dependency()
            )
        );

    result =
        child.verify(evidence);
```

The loop is illustrative, not normative.

The important semantic rule is:

> each child sees its own evidence while all children remain protected by the same decision observation.

---

# 60. Business-named composites remain preferred

Although:

```java
new AllConstraints(a, b, c)
```

is useful infrastructure, stable business compositions should receive domain names.

For example:

```text
StudentMayEnrollInCourse
```

may internally compose:

```text
StudentIsEnrolledInAcademicYear
CourseExists
StudentHasAvailableCourseSlot
CourseHasAvailableCapacity
```

This makes the constraint graph readable as a representation of the business rather than as a boolean-expression tree.

---

# 61. Constraint graph

The decision model can be represented as:

```text
Command
   ↓ requires
Constraint
   ↓ composed of
Constraint
   ↓ depends on
EventFilter
   ↓ describes
Events
```

Reduction adds another relation:

```text
Constraint
   ↓ derives evidence through
EventReduction
```

The complete conceptual graph is therefore:

```text
                   Command
                      |
                      | requires
                      v
                  Constraint
                 /          \
       composed of            depends on
           |                      |
           v                      v
      Constraint             EventFilter
           |                      |
           | evidence             |
           | reduction            |
           v                      |
      EventReduction              |
                 \                /
                  \              /
                   v            v
                    StoredEvents
```

---

# 62. Business-analysis value

This model suggests a domain-analysis process centered on:

```text
What may the user attempt?

What must be true for that action to succeed?

Which events may make each rule true or false?

Which parts of those histories are actually needed
to establish the current result?

Which constraints are shared by several commands?
```

This produces explicit:

```text
Commands
Constraints
Event dependencies
Evidence reductions
Events
```

which form a meaningful alternative view of the write model.

---

# 63. Testing constraints

Constraints should be testable independently.

For example:

```text
given event history
when CourseTitleIsAvailable is verified
then Satisfied
```

or:

```text
given event history
when CourseHasAvailableCapacity is verified
then CourseCapacityReached
```

Reduction becomes part of the executable rule rather than setup code duplicated across command tests.

---

# 64. Testing reduction semantics

`EventReduction` requires independent conformance tests.

At minimum:

### Subset

Every output event existed in the input.

### Ordering

Surviving events remain in increasing `Position` order.

### Boundary preservation

`tail()` remains semantically equivalent before and after reduction.

### Laziness

Calling `reduce()` itself does not traverse the observation.

### Repeatability

Repeated iteration yields the same reduced logical observation.

### Composition

Reductions execute in declaration order.

---

# 65. Latest tests

For:

```text
P1 A
P2 B
P3 C
```

`Latest` returns:

```text
P3 C
```

For an empty input it returns an empty `StoredEvents`.

---

# 66. LatestBy tests

For:

```text
P1 C1 A
P2 C2 A
P3 C1 B
P4 C3 A
P5 C2 B
```

`LatestBy(courseId)` returns:

```text
P3 C1 B
P4 C3 A
P5 C2 B
```

ordered by final event position.

---

# 67. Reduction ordering test

For:

```text
P1 CourseCreated(C1)
P2 CourseRemoved(C1)
```

this:

```text
LatestBy(courseId)
→ Excluding(CourseRemoved)
```

must produce:

```text
empty
```

while:

```text
Excluding(CourseRemoved)
→ LatestBy(courseId)
```

would produce:

```text
CourseCreated(C1)
```

and is therefore semantically different.

An optimizer must never exchange these operations.

---

# 68. Composite constraint evidence tests

Given:

```text
A depends on FA
B depends on FB
```

and history containing events matching both filters:

```text
A.verify(...)
```

must observe only `FA` evidence.

```text
B.verify(...)
```

must observe only `FB` evidence.

Yet both reduced observations must produce a Tail protected by:

```text
FA | FB
```

This is one of the most important conformance tests introduced by this ADR.

---

# 69. Remote conformance

A remote implementation must preserve the same semantics as an embedded implementation.

In particular:

```text
reduced result sequence
watermark boundary
Tail conflict behavior
reduction order
repeatable traversal
```

must not depend on transport.

The client may use paged requests and opaque tokens; these remain implementation details.

---

# 70. No count, groupBy or arbitrary map in Kern v1

This ADR deliberately does not add:

```java
StoredEvents.count();
StoredEvents.groupBy(...);
StoredEvents.map(...);
StoredEvents.fold(...);
StoredEvents.project(...);
```

These operations either leave the `StoredEvents` abstraction or introduce generic collection-processing responsibilities.

Generic transformations belong to utilities such as Cactoos or to domain-specific objects.

The new API is deliberately restricted to **subsequence-preserving reductions**.

---

# 71. Why no GroupedStoredEvents

A `groupBy` operation immediately raises new semantic questions:

```text
Map<Key, StoredEvents>?
Iterable<Group>?
GroupedStoredEvents?
one Tail per group?
one shared Tail?
```

`LatestBy`, by contrast, may use grouping internally while still returning one ordered `StoredEvents`.

The latter preserves the simplicity of the existing model.

---

# 72. Why no scalar observation

This ADR also rejects introducing scalar values such as:

```text
Observed<Long>
Observed<Boolean>
```

into Kern for this requirement.

A scalar would no longer expose the event evidence itself and would introduce another observation hierarchy.

Constraint interpretation can cheaply derive scalars after Kern has reduced the history to a useful bounded subsequence.

The Event Store should first attempt to solve the problem while preserving event semantics.

---

# 73. Decision-model implementation utilities

Constraint implementations are free to use Cactoos for in-memory operations such as:

```text
Filtered
Mapped
Folded
LengthOf
Scalar
Func
```

Kern should not duplicate those utilities.

The new responsibility of Kern ends at:

```text
producing the bounded and possibly reduced StoredEvents evidence
```

The interpretation of that evidence remains a domain concern.

---

# 74. Module boundary

The architecture is conceptually:

```text
                decision / constraint model
                          |
                          v
                       kern-api
                          |
             +------------+------------+
             |                         |
             v                         v
        kern-rocksdb               kern-client
                                      |
                                      v
                                  kern-server
```

`Constraint` belongs above `kern-api`.

`EventReduction` and the `StoredEvents.reduce()` capability belong in `kern-api`.

Storage and remote modules implement the reduction algebra.

---

# 75. Initial implementation order

The recommended implementation sequence is:

```text
1. add EventReduction semantic contracts;

2. add StoredEvents.reduce();

3. implement Matching and Excluding;

4. implement Latest;

5. implement LatestBy(TagName);

6. add in-memory conformance implementation;

7. add RocksDB execution;

8. add protocol representation;

9. validate composite Constraint evidence;

10. optimize only after profiling.
```

`Matching` should be implemented early because it is the mechanism that makes composite constraint evidence efficient.

---

# 76. RocksDB performance validation

Before considering `LatestBy` complete for production use, benchmark at least:

```text
large number of historical events
small number of distinct keys

large number of historical events
large number of distinct keys

LatestBy following selective Matching

LatestBy followed by Excluding

remote paging of reduced results
```

The semantic abstraction remains valid even if the first RocksDB implementation is not optimally fast.

However, the feature should not claim to solve large-history decision costs until those workloads have been measured.

---

# 77. Open implementation questions

The following details remain implementation decisions rather than semantic uncertainties:

* exact `EventReductionSelection<T>` signature;
* physical RocksDB algorithm for `LatestBy`;
* whether reduction plans are normalized internally;
* protocol encoding of reduction pipelines;
* page sizing for remote reduced observations;
* optional client/runtime caching policy;
* whether a future planner combines overlapping child constraint reads.

These decisions must preserve the semantics defined by this ADR.

---

# 78. Follow semantics

Reduction is defined for the current bounded observation.

It does **not** redefine the future dependency of that observation.

Therefore:

```java
reduced.follow()
```

continues from the same watermark using the original dependency.

The reduction pipeline is not implicitly promoted into a durable or stateful future projection.

This avoids problematic semantics where, for example, `LatestBy` would produce results dependent on arbitrary subscription page boundaries.

If future events need to be reduced, the client may reduce each returned bounded `StoredEvents` explicitly.

This distinction is intentional:

```text
reduce()
    bounded representation of known history

follow()
    continuation of the underlying observation
```

---

# 79. Why reductions do not automatically follow

Consider:

```text
LatestBy(courseId)
```

and future updates:

```text
P101 C1 TitleChanged
P102 C1 TitleChanged
```

If reduction were implicitly applied inside:

```java
subscription.next(n)
```

the visible result could depend on whether the transport delivered:

```text
P101 and P102 in one batch
```

or:

```text
P101
then P102
```

That would make `next(n)` batching influence semantic results.

Therefore v1 keeps subscription continuation based on the original event dependency, not on the bounded reduction pipeline.

---

# 80. Governing distinction

The architecture now distinguishes three concepts:

```text
EventFilter
    defines which events may influence the observation
    and therefore its consistency boundary

EventReduction
    defines which already-observed events are necessary
    for the current representation

Constraint
    interprets that evidence as a business rule
```

In short:

```text
EventFilter
    "What may matter?"

EventReduction
    "What do I still need to see?"

Constraint
    "Given that evidence, is the operation allowed?"
```

This is the conceptual center of this ADR.

---

# 81. Complete decision flow

The intended model is:

```text
Command
   ↓
Composite Constraint
   ↓ dependencies
EventFilter D
   ↓
EventStore.events(...)
   ↓
StoredEvents(N,D,A,W)
   |
   +---------------------------+
   |                           |
   v                           v
Matching(child A)         Matching(child B)
   |                           |
other reductions          other reductions
   |                           |
   v                           v
Evidence A                Evidence B
   |                           |
verify                    verify
   +------------+--------------+
                |
                v
          all satisfied?
                |
         +------+------+
         |             |
        no            yes
         |             |
         v             v
     Violation       Events
                       |
                       v
                original Tail
                       |
                       v
                    append
```

The Tail remains:

```text
Tail(N,D,W)
```

for the entire decision.

---

# 82. Architectural consequence

The consistency model no longer requires the same event sequence to serve simultaneously as:

```text
the complete concurrency boundary
and
the exact evidence consumed by every rule
```

Those responsibilities are separated while remaining attached to the same immutable observation.

This enables:

* composable constraints;
* precise Dynamic Consistency Boundaries;
* child-specific evidence;
* lazy storage access;
* reduced network transfer;
* large-history compression;
* storage-aware optimization;
* reusable domain rules;
* direct constraint testing;
* business-oriented system documentation.

---

# 83. Final decision

The write model adopts **Constraint** as its primary business-rule abstraction.

Each constraint declares the event history that may influence its result and verifies itself using a bounded `StoredEvents` evidence.

Constraints may be composed.

The dependency of a composite constraint is the union of the dependencies of its children.

The resulting aggregate `EventFilter` establishes one Kern observation and therefore one consistency boundary for the entire decision.

Kern extends `StoredEvents` with:

```java
StoredEvents reduce(
    EventReduction reduction
);
```

A reduction derives another lazy `StoredEvents` representation from the same bounded observation.

The initial reduction algebra consists of:

```text
Latest
LatestBy(TagName)
Matching(EventFilter)
Excluding(EventFilter)
```

Reductions:

* only discard events;
* preserve surviving `StoredEvent` objects unchanged;
* preserve `Position` order;
* preserve namespace;
* preserve the original dependency;
* preserve the watermark;
* preserve Tail semantics;
* compose in declaration order;
* remain lazy and repeatably iterable.

A composite constraint may therefore acquire one consistency observation while each child receives a reduced evidence view:

```java
StoredEvents evidence =
    history.reduce(
        new Matching(
            child.dependency()
        )
    );
```

and may further reduce that evidence according to its own domain semantics.

The central relationship is:

> **A decision is protected by every event that may influence its constraints, while each constraint only needs to inspect the evidence required to establish its own truth.**

And the three roles are deliberately distinct:

> **EventFilter defines what may matter.
> EventReduction defines what still needs to be seen.
> Constraint decides what that evidence means.**