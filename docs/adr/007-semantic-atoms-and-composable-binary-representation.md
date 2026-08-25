# ADR — Semantic Atoms and Composable Binary Representation

## Status

**Accepted**

This ADR introduces breaking refinements to the semantic API and is accepted
together with the corresponding `kern-api` changes.

---

## Context

Kern's semantic API currently contains several small immutable value types:

```text
EventId
EventType
Namespace
TagName
TagValue
Position
```

Their current implementations are primarily wrappers around Java primitives:

```text
EventId    → String
EventType  → String
Namespace  → String
TagName    → String
TagValue   → String
Position   → long
```

These objects provide useful guarantees:

* semantic type distinction;
* validation at construction time;
* equality by semantic value;
* prevention of primitive parameter confusion;
* structural immutability.

For example:

```java
EventId id
```

is stronger than:

```java
String id
```

because arbitrary text cannot accidentally be substituted for an event identity.

However, modeling such types as records or wrappers exposing:

```java
value()
```

has an undesirable consequence.

The semantic object is frequently unpacked immediately:

```java
event.id().value();

event.type().value();

namespace.value();

position.value();
```

and the primitive representation is then manipulated procedurally.

For a RocksDB implementation this would naturally lead to code such as:

```java
namespace.value().getBytes(...)

type.value().getBytes(...)

position.value()

namespace.value() + ":" + type.value()
```

This partially defeats the purpose of introducing semantic objects.

The object protects its representation while being constructed, but subsequently exposes that representation to arbitrary manipulation.

The resulting model becomes:

```text
semantic object
      ↓ getter
primitive
      ↓
arbitrary implementation logic
```

rather than preserving object semantics until the physical system boundary.

---

# 1. Semantic atoms

This ADR introduces the term **semantic atom**.

A semantic atom is an immutable value whose complete meaning is represented by a primitive-like domain, but whose legal values and semantic role are narrower than that primitive domain.

Examples:

```text
EventId
EventType
TagName
TagValue
NamespaceId
Position
```

A semantic atom is not primarily:

> a container around a primitive.

It is:

> a refinement of an existing behavioral value abstraction.

Conceptually:

```text
EventId
    = textual value
    + Event identity semantics
    + EventId invariants
```

```text
EventType
    = textual value
    + event classification semantics
    + EventType invariants
```

```text
Position
    = numeric value
    + event-log coordinate semantics
    + Position invariants
    + ordering
```

---

# 2. Refinement rather than containment

The intended conceptual relationship is:

```text
EventId   IS Text
EventType IS Text
TagName   IS Text
TagValue  IS Text

Position  IS Number
```

rather than:

```text
EventId   HAS String
EventType HAS String
Position  HAS long
```

Java cannot express refinement types directly.

Objects therefore represent those refinements.

In a language with refinement types the conceptual declarations might resemble:

```text
EventId =
    String where validEventId

Position =
    long where value >= 0
```

Kern models these concepts explicitly through objects.

---

# 3. Textual semantic atoms

The following values become implementations of Cactoos `Text`:

```text
EventId
EventType
TagName
TagValue
NamespaceId
```

Conceptually:

```java
public final class EventId implements Text {

    private final String origin;

    public EventId(@NonNull final String origin) {
        if (origin.isBlank()) {
            throw new IllegalArgumentException(
                "Event id cannot be blank"
            );
        }
        this.origin = origin;
    }

    @Override
    public String asString() {
        return this.origin;
    }

    // semantic equality and hash code
}
```

The exact implementation may use Lombok where useful.

The important API decision is:

```text
EventId does not expose value().
```

Instead it satisfies the behavioral contract:

```text
Text
```

The same applies to:

```text
EventType
TagName
TagValue
NamespaceId
```

---

# 4. Why Text

`Text` expresses a capability:

> this object can represent itself textually.

It does not express:

> this object contains a String field that callers may retrieve.

This distinction permits object composition before reaching a primitive representation.

For example:

```java
new BytesOf(event.id())
```

is possible directly when `EventId` implements `Text`.

The implementation does not need:

```java
new BytesOf(
    event.id().value()
)
```

or:

```java
event.id()
    .value()
    .getBytes(StandardCharsets.UTF_8)
```

The intended flow becomes:

```text
EventId
   ↓
Text
   ↓
Bytes
   ↓
byte[]
```

The primitive appears only at the terminal infrastructure boundary.

---

# 5. Text does not erase semantic identity

The fact that:

```java
EventId implements Text
```

does not imply that:

```text
EventId == EventType
```

or that arbitrary `Text` can be substituted for either.

The type system continues to distinguish:

```java
EventId
EventType
TagName
TagValue
NamespaceId
```

`Text` describes a common representational capability.

The concrete semantic type still carries the meaning.

Therefore:

```java
void append(EventId id, EventType type)
```

remains type-safe even though both arguments implement `Text`.

---

# 6. Text conversion is terminal behavior

Although a caller may technically invoke:

```java
id.asString()
```

this operation should normally be considered **terminal representation behavior**.

Application and storage code should prefer to compose the semantic object directly with collaborators that understand `Text`.

Prefer:

```java
new BytesOf(event.id())
```

over:

```java
new BytesOf(event.id().asString())
```

Prefer:

```java
someTextOperation(event.type())
```

over:

```java
someStringOperation(event.type().asString())
```

The governing principle is:

> **Primitive extraction should occur only where a primitive is actually required by an external boundary.**

---

# 7. Semantic atoms are not records

Textual semantic atoms should no longer be modeled as Java records whose components expose generated accessors such as:

```java
value()
```

For these types, the record representation encourages the interpretation:

```text
object = transparent tuple containing String
```

which conflicts with the intended semantic model.

They should instead be immutable final classes.

Example:

```java
public final class EventType implements Text {

    private final String origin;

    // constructor
    // asString()
    // equals()
    // hashCode()
    // toString(), if useful
}
```

Their internal representation remains private.

---

# 8. Equality remains semantic-type-specific

Implementing the same representational abstraction does not imply cross-type equality.

For example:

```java
new EventId("42")
```

must not equal:

```java
new EventType("42")
```

even though both are textual.

Equality is defined by:

```text
same semantic type
+
same canonical semantic value
```

Third-party `Text` implementations do not participate automatically in equality.

---

# 9. Position is a numeric semantic atom

`Position` represents:

> a point in the logical event log.

ADR 001 already treats `Position` as a semantic concept rather than exposing a primitive offset, and ADR 003 explicitly states that the public API does not promise contiguity or arithmetic.

`Position` should therefore refine Java's numerical abstraction:

```java
public final class Position
    extends Number
    implements Comparable<Position> {
```

rather than expose:

```java
long value()
```

Conceptually:

```text
Position IS Number
+
non-negative invariant
+
event-log ordering semantics
```

---

# 10. Position contract

`Position` preserves:

```text
non-negative values

natural ordering

Position.beginning()

semantic equality

stable hash code
```

It does not introduce public arithmetic such as:

```java
next()
plus()
minus()
increment()
```

because the semantic API does not define positions as an arithmetic sequence.

RocksDB v1 may allocate positions contiguously as an implementation choice, but that remains outside the semantic contract. This preserves the distinction already established by ADR 003.

---

# 11. Number conversion is representational behavior

As a `Number`, `Position` necessarily supports Java's numeric conversion methods:

```java
intValue()
longValue()
floatValue()
doubleValue()
```

These methods must not be interpreted as additional Kern domain semantics.

The canonical physical representation for RocksDB remains the implementation's responsibility.

Storage code should therefore prefer:

```java
new PositionBytes(position)
```

or:

```java
new BigEndianBytes(position)
```

over arbitrary use of:

```java
position.longValue()
```

throughout the implementation.

---

# 12. Namespace is currently an identifier

The current `Namespace` object does not itself represent an active event-space object with behavior.

It is used as the identifier selecting an isolated event-log scope:

```java
store.events(namespace, filter, after)
```

Its semantic role is therefore more accurately described as:

```text
NamespaceId
```

This ADR renames:

```java
Namespace
```

to:

```java
NamespaceId
```

and makes it a textual semantic atom:

```java
public final class NamespaceId implements Text
```

---

# 13. Why NamespaceId

The distinction leaves room for a future behavioral object:

```java
Namespace
```

if a real namespace abstraction ever emerges.

For example, a future API could conceivably introduce:

```java
Namespace namespace =
    store.namespace(
        new NamespaceId("academic-year-2026")
    );
```

with behavior such as:

```java
namespace.events(filter)
```

This ADR does **not** introduce that API.

It only avoids consuming the stronger name `Namespace` for what is currently an identifier.

---

# 14. Semantic API changes

The primary semantic API becomes conceptually:

```java
public interface Event {

    EventId id();

    EventType type();

    Tags tags();

    Data data();
}
```

unchanged in shape.

However:

```text
EventId   implements Text
EventType implements Text
```

and expose no primitive `value()` accessor.

`Tag` remains:

```java
public interface Tag {

    TagName name();

    TagValue value();
}
```

Here `value()` is **not a primitive getter**.

It returns another semantic object:

```text
TagValue
```

and is therefore acceptable.

The issue addressed by this ADR is primitive extraction such as:

```java
String value()
long value()
```

not semantic navigation from one object to another.

---

# 15. EventStore Namespace change

Every semantic operation currently accepting:

```java
Namespace
```

is changed to:

```java
NamespaceId
```

For example:

```java
StoredEvents events(
    NamespaceId namespace,
    EventFilter filter,
    Position after
);
```

Default overloads are changed accordingly.

This is intentionally a breaking pre-1.0 API refinement.

---

# 16. Tags remain composed semantic values

`EventTag` remains an object composed of:

```text
TagName
TagValue
```

For example:

```java
new EventTag(
    new TagName("courseId"),
    new TagValue("c7")
)
```

Convenience constructors accepting strings may remain:

```java
new EventTag("courseId", "c7")
```

provided they immediately construct the corresponding semantic atoms and preserve their invariants.

---

# 17. Representation objects

Storage and transport implementations need primitive representations eventually.

That responsibility belongs to **representation objects**, not to arbitrary users of semantic objects.

The desired pipeline is:

```text
semantic object
      ↓
representation object
      ↓
representation abstraction
      ↓
primitive required by external API
```

For RocksDB:

```text
EventId
   ↓
BytesOf(Text)
   ↓
Bytes
   ↓
byte[]

Position
   ↓
BigEndianBytes
   ↓
Bytes
   ↓
byte[]
```

---

# 18. Bytes as the RocksDB representation abstraction

`kern-rocksdb` should use Cactoos `Bytes` as the primary internal abstraction for binary representations.

RocksDB itself ultimately requires:

```java
byte[]
```

but byte arrays should appear only at the terminal call boundary.

Prefer:

```java
Bytes key =
    new EventKey(namespace, position);

rocks.put(
    key.asBytes(),
    value.asBytes()
);
```

over:

```java
rocks.put(
    manuallyAssembledKey(...),
    manuallySerializedValue(...)
);
```

ADR 003 already establishes the principle that RocksDB key layout should be modeled by cohesive objects rather than repeated raw byte concatenation.

This ADR selects `Bytes` as the concrete object model for that representation.

---

# 19. BytesEnvelope

Because Cactoos does not provide a suitable binary envelope for this design, `kern-rocksdb` may introduce:

```java
public abstract class BytesEnvelope
    implements Bytes {

    private final Bytes origin;

    protected BytesEnvelope(
        final Bytes origin
    ) {
        this.origin =
            Objects.requireNonNull(
                origin,
                "bytes origin must not be null"
            );
    }

    @Override
    public final byte[] asBytes()
        throws Exception {
        return this.origin.asBytes();
    }
}
```

The role of `BytesEnvelope` is structural.

It enables small binary decorators and semantic key objects to be represented entirely through composition.

---

# 20. JoinedBytes

`kern-rocksdb` introduces a binary composition object:

```java
public final class JoinedBytes
    implements Bytes {

    private final Iterable<Bytes> parts;

    public JoinedBytes(
        final Bytes... parts
    ) {
        this(new IterableOf<>(parts));
    }

    public JoinedBytes(
        final Iterable<Bytes> parts
    ) {
        this.parts =
            Objects.requireNonNull(
                parts,
                "byte parts must not be null"
            );
    }

    @Override
    public byte[] asBytes()
        throws Exception {
        final ByteArrayOutputStream stream =
            new ByteArrayOutputStream();
        for (final Bytes part : this.parts) {
            stream.write(
                Objects.requireNonNull(
                    part,
                    "byte part must not be null"
                ).asBytes()
            );
        }
        return stream.toByteArray();
    }
}
```

The implementation may later be optimized.

Its semantic responsibility is simply:

> concatenate several binary representations in declaration order.

---

# 21. JoinedBytes is not a key format

`JoinedBytes` performs concatenation.

It does **not** itself guarantee that the resulting encoding is unambiguous.

Therefore this is unsafe:

```java
new JoinedBytes(
    new BytesOf(namespace),
    new BytesOf(type)
)
```

because:

```text
"ab" + "c"
```

and:

```text
"a" + "bc"
```

produce the same byte sequence.

Prefix safety belongs to the representation of individual variable-length segments.

---

# 22. Length-prefixed textual segments

Variable-length textual values must use a canonical, prefix-safe representation.

ADR 003 already specifies canonical variable-length segments such as:

```text
[length][UTF-8 bytes]
```

for textual key components.

A storage object may therefore be introduced conceptually as:

```java
public final class SegmentBytes
    extends BytesEnvelope {

    public SegmentBytes(final Text text) {
        super(
            new JoinedBytes(
                new LengthBytes(
                    new BytesOf(text)
                ),
                new BytesOf(text)
            )
        );
    }
}
```

Exact naming and length encoding remain implementation details.

The important invariant is:

> every variable-length key segment has one deterministic and unambiguous binary representation.

---

# 23. Namespace scoping as decoration

Namespace scoping is common to almost every namespace-local RocksDB key.

It should therefore be represented compositionally.

Conceptually:

```java
public final class WithNamespace
    extends BytesEnvelope {

    public WithNamespace(
        final NamespaceId namespace,
        final Bytes bytes
    ) {
        super(
            new JoinedBytes(
                new NamespacePrefix(namespace),
                bytes
            )
        );
    }
}
```

The implementation should **not** directly concatenate raw UTF-8 namespace bytes.

`NamespacePrefix` is responsible for the canonical prefix-safe namespace representation.

---

# 24. Why WithNamespace

Without composition, each key class would duplicate namespace encoding:

```text
EventKey
EventIdKey
TypeIndexKey
TagIndexKey
HeadKey
```

With the decorator:

```java
new WithNamespace(
    namespace,
    key
)
```

the concern is expressed once.

The code states:

> use this key inside this namespace.

rather than:

> prepend these particular bytes to that byte array.

---

# 25. Numeric binary representation

A generic RocksDB-side object may represent numbers using fixed-width big-endian encoding:

```java
public final class BigEndianBytes
    implements Bytes {

    private final Number number;

    // ...
}
```

The exact supported widths must be explicit.

For `Position`, v1 uses eight bytes, as already decided by ADR 003.

A domain-specific decorator may make that rule explicit:

```java
public final class PositionBytes
    extends BytesEnvelope {

    public PositionBytes(
        final Position position
    ) {
        super(
            new BigEndianLongBytes(position)
        );
    }
}
```

This ensures:

```text
numeric Position order
=
lexicographic RocksDB byte order
```

---

# 26. Semantic objects do not implement Bytes

Semantic API objects must not directly implement storage representation contracts such as:

```java
Bytes
```

For example, this is rejected:

```java
public final class Position
    implements Bytes
```

because binary encoding is not intrinsic to a semantic event-log position.

Likewise:

```java
EventType implements Bytes
```

is rejected.

The correct separation is:

```text
EventType implements Text
        ↓
BytesOf(EventType)
```

and:

```text
Position extends Number
        ↓
PositionBytes
```

This allows alternative storage or protocol encodings without changing `kern-api`.

---

# 27. First RocksDB key object model

The initial physical key objects are:

```text
EventKey
EventIdKey

TypeIndexPrefix
TypeIndexKey

TagIndexPrefix
TagIndexKey

HeadKey
FormatVersionKey
```

This refines the key-object direction already established by ADR 003.

---

# 28. EventKey

The authoritative event key represents:

```text
NamespaceId | Position
```

Conceptually:

```java
public final class EventKey
    extends BytesEnvelope {

    public EventKey(
        final NamespaceId namespace,
        final Position position
    ) {
        super(
            new WithNamespace(
                namespace,
                new PositionBytes(position)
            )
        );
    }
}
```

The exact composition may include an internal key-family discriminator if required by the selected Column Family layout.

Because `events` has its own Column Family, no redundant event-key type marker is required unless a later format version introduces one.

---

# 29. EventIdKey

The idempotency lookup key represents:

```text
NamespaceId | EventId
```

Conceptually:

```java
public final class EventIdKey
    extends BytesEnvelope {

    public EventIdKey(
        final NamespaceId namespace,
        final EventId id
    ) {
        super(
            new WithNamespace(
                namespace,
                new SegmentBytes(id)
            )
        );
    }
}
```

Because `EventId` implements `Text`, no primitive extraction is necessary.

---

# 30. TypeIndexPrefix

A type index prefix represents:

```text
NamespaceId | EventType
```

Conceptually:

```java
public final class TypeIndexPrefix
    extends BytesEnvelope {

    public TypeIndexPrefix(
        final NamespaceId namespace,
        final EventType type
    ) {
        super(
            new WithNamespace(
                namespace,
                new SegmentBytes(type)
            )
        );
    }
}
```

This prefix can be used directly for bounded prefix scans.

---

# 31. TypeIndexKey

A complete type index key represents:

```text
NamespaceId | EventType | Position
```

Conceptually:

```java
public final class TypeIndexKey
    extends BytesEnvelope {

    public TypeIndexKey(
        final NamespaceId namespace,
        final EventType type,
        final Position position
    ) {
        super(
            new JoinedBytes(
                new TypeIndexPrefix(
                    namespace,
                    type
                ),
                new PositionBytes(position)
            )
        );
    }
}
```

Its lexicographic ordering naturally produces positions in event-log order within a type.

---

# 32. TagIndexPrefix

A tag index prefix represents:

```text
NamespaceId
|
TagName
|
TagValue
```

Conceptually:

```java
public final class TagIndexPrefix
    extends BytesEnvelope {

    public TagIndexPrefix(
        final NamespaceId namespace,
        final Tag tag
    ) {
        super(
            new WithNamespace(
                namespace,
                new JoinedBytes(
                    new SegmentBytes(tag.name()),
                    new SegmentBytes(tag.value())
                )
            )
        );
    }
}
```

Notice that:

```java
tag.value()
```

is not primitive extraction.

It yields:

```java
TagValue
```

which remains a semantic object implementing `Text`.

---

# 33. TagIndexKey

The complete tag index key represents:

```text
NamespaceId
|
TagName
|
TagValue
|
Position
```

Conceptually:

```java
public final class TagIndexKey
    extends BytesEnvelope {

    public TagIndexKey(
        final NamespaceId namespace,
        final Tag tag,
        final Position position
    ) {
        super(
            new JoinedBytes(
                new TagIndexPrefix(
                    namespace,
                    tag
                ),
                new PositionBytes(position)
            )
        );
    }
}
```

Again the final position segment preserves ordering.

---

# 34. HeadKey

Namespace head metadata represents:

```text
NamespaceId | HEAD
```

A dedicated object should encode it:

```java
new HeadKey(namespace)
```

rather than building a string or byte array procedurally.

The value is represented through:

```java
new PositionBytes(head)
```

---

# 35. FormatVersionKey

Database-wide metadata such as:

```text
FORMAT_VERSION
```

does not belong to a namespace.

It should therefore not use `WithNamespace`.

This distinction remains explicit in the key model.

---

# 36. Physical key overview

The v1 layout remains semantically equivalent to ADR 003:

```text
events
    key:
        NamespaceId | Position

event_ids
    key:
        NamespaceId | EventId

type_index
    key:
        NamespaceId | EventType | Position

tag_index
    key:
        NamespaceId | TagName | TagValue | Position

metadata
    key:
        NamespaceId | HEAD

    or:
        FORMAT_VERSION
```

The difference introduced by this ADR is not primarily the byte layout.

It is **how that layout is represented in code**.

---

# 37. Object-composed key construction

The desired implementation style is:

```java
final Bytes key =
    new TypeIndexKey(
        namespace,
        event.type(),
        position
    );
```

not:

```java
final byte[] key =
    concatenate(
        namespace.asString().getBytes(...),
        event.type().asString().getBytes(...),
        encodeLong(position.longValue())
    );
```

The former expresses:

```text
what the key represents
```

while the latter expresses:

```text
how bytes happen to be assembled.
```

---

# 38. Terminal primitive boundary

The RocksDB Java API ultimately requires:

```java
byte[]
```

Therefore some object must finally call:

```java
bytes.asBytes()
```

This is expected.

The important boundary rule is:

> **Raw primitive extraction is allowed only in objects or methods whose explicit responsibility is interfacing with a primitive-based external API.**

For example:

```java
rocks.put(
    key.asBytes(),
    value.asBytes()
);
```

is legitimate terminal code.

This does not make `byte[]` part of Kern's internal semantic model.

---

# 39. No arbitrary primitive manipulation

Inside `kern-rocksdb`, code outside representation classes should not normally invoke:

```java
event.id().asString()

event.type().asString()

namespace.asString()

position.longValue()
```

for key construction.

Instead it should delegate to:

```text
BytesOf
SegmentBytes
PositionBytes
EventKey
EventIdKey
TypeIndexKey
TagIndexKey
WithNamespace
```

Exceptions require a representation-specific reason.

---

# 40. Benefits

This design provides several advantages.

### Stronger encapsulation

Semantic values no longer expose transparent primitive state through `value()`.

### Type safety

`EventId`, `EventType`, `TagName`, `TagValue` and `NamespaceId` remain distinct types.

### Composability

Textual values compose directly with Cactoos `Text` and `Bytes`.

### Storage isolation

RocksDB encoding policy remains in `kern-rocksdb`.

### Determinism

Every semantic segment has one canonical encoding path.

### Reduced duplication

Namespace and segment encoding are implemented once.

### Testability

Every binary representation object can be tested independently.

### Readability

Storage code describes semantic keys rather than byte-array manipulation.

### Future protocols

A remote protocol can choose its own representation without changing semantic atoms.

---

# 41. Rejected alternative — primitive getters

Keep:

```java
record EventId(String value)
record EventType(String value)
record Position(long value)
```

and use generated accessors everywhere.

Rejected because it encourages primitive extraction throughout infrastructure code and models semantic values primarily as transparent data carriers.

---

# 42. Rejected alternative — semantic objects implement Bytes

For example:

```java
EventId implements Bytes
Position implements Bytes
```

Rejected because storage encoding is not intrinsic semantic behavior.

A semantic atom may have multiple physical representations depending on implementation or protocol.

---

# 43. Rejected alternative — custom Kern Text abstraction

Introduce:

```java
interface TextValue {
    String text();
}
```

Rejected initially because Cactoos already provides the desired behavioral abstraction.

A new Kern abstraction would duplicate it without adding Event Store semantics.

---

# 44. Rejected alternative — generic StringValue

For example:

```java
interface StringValue {
    String value();
}
```

Rejected because it formalizes exactly the container/getter model this ADR intends to avoid.

---

# 45. Rejected alternative — delimiter-based key encoding

For example:

```text
namespace + ":" + type + ":" + position
```

Rejected because variable-length segments become ambiguous unless escaping rules are introduced.

Canonical length-prefixed or otherwise prefix-safe representations remain required, consistent with ADR 003.

---

# 46. Consequences for kern-api

The following breaking changes are required:

```text
EventId
    record → final class implementing Text
    remove value()

EventType
    record → final class implementing Text
    remove value()

TagName
    record → final class implementing Text
    remove value()

TagValue
    record → final class implementing Text
    remove value()

Namespace
    renamed NamespaceId
    final class implementing Text
    remove value()

Position
    record → final class extending Number
             implementing Comparable<Position>
    remove value()
```

Constructors accepting the current primitive representations may remain for ergonomic API usage:

```java
new EventId("...")
new EventType("...")
new NamespaceId("...")
new TagName("...")
new TagValue("...")
new Position(42L)
```

The primitive is accepted at object creation.

It is not subsequently exposed as public state.

---

# 47. Consequences for EventStore API

For example:

```java
StoredEvents events(
    Namespace namespace,
    EventFilter filter,
    Position after
);
```

becomes:

```java
StoredEvents events(
    NamespaceId namespace,
    EventFilter filter,
    Position after
);
```

Equivalent changes apply to default overloads and all dependent code.

No other semantic behavior of `EventStore`, `StoredEvents`, `Tail`, `Subscription`, `EventFilter`, or `EventReduction` changes.

---

# 48. Consequences for conformance tests

Current conformance tests that inspect:

```java
.value()
```

must be rewritten to test semantic behavior rather than transparent representation.

Tests should cover:

```text
construction invariants
semantic equality
semantic inequality across types
Text composition
Position ordering
Number behavior where relevant
canonical binary conversion in storage tests
```

For example, instead of testing:

```java
new EventType("CourseCreated").value()
```

prefer testing its semantic use through filtering or its textual capability where specifically required.

---

# 49. Consequences for ADR 001

ADR 001 should be amended to introduce **semantic atoms** and explicitly state:

> Primitive-like domain concepts should refine behavioral value abstractions rather than expose transparent primitive state where practical.

Its primary abstraction list should use:

```text
NamespaceId
```

instead of:

```text
Namespace
```

unless a later behavioral `Namespace` abstraction is introduced.

---

# 50. Consequences for ADR 003

ADR 003 remains valid in its storage schema and access patterns.

This ADR refines its sections concerning:

```text
Key model
Cactoos usage
Composable key objects
Variable-length segments
Position encoding
```

Specifically:

```text
Bytes
```

becomes the preferred binary representation contract.

Key objects should be `Bytes` or `BytesEnvelope` implementations.

Textual semantic atoms should be converted through `Text → Bytes`.

`Position` should be converted through a dedicated fixed-width big-endian `Bytes` representation.

---

# 51. Migration order

Recommended implementation order:

```text
1. introduce textual semantic atom implementations;

2. migrate EventId;

3. migrate EventType;

4. migrate TagName and TagValue;

5. rename Namespace → NamespaceId;

6. migrate Position to Number;

7. update semantic API conformance tests;

8. merge the semantic API refinement;

9. introduce BytesEnvelope;

10. introduce JoinedBytes;

11. introduce prefix-safe textual segment encoding;

12. introduce PositionBytes / BigEndianBytes;

13. implement RocksDB key objects;

14. add binary encoding conformance tests;

15. only then begin RocksDB persistence logic.
```

This order ensures that RocksDB is designed against the corrected semantic model rather than requiring a later representation refactor.

---

# 52. Governing principles

This ADR establishes four related principles.

> **A semantic atom is a refinement of a value abstraction, not a transparent container of a primitive.**

> **Semantic objects should remain objects until a physical boundary actually requires a primitive.**

> **Physical representations are responsibilities of representation objects, not of the semantic objects they encode.**

> **Storage layout should be composed through objects that describe what bytes represent, not procedures that assemble byte arrays.**

---

# 53. Final decision

Kern adopts behavioral refinement for primitive-like semantic values.

Textual atoms:

```text
EventId
EventType
TagName
TagValue
NamespaceId
```

implement Cactoos:

```text
Text
```

`Position` becomes a numeric semantic atom extending:

```text
Number
```

and retaining its event-log ordering semantics.

Primitive `value()` accessors are removed.

`Namespace` is renamed `NamespaceId` because its current role is identifier rather than behavioral event-space abstraction.

`kern-rocksdb` uses Cactoos:

```text
Bytes
```

as its internal binary representation contract.

Binary composition is expressed through small objects such as:

```text
BytesEnvelope
JoinedBytes
WithNamespace
SegmentBytes
PositionBytes

EventKey
EventIdKey
TypeIndexPrefix
TypeIndexKey
TagIndexPrefix
TagIndexKey
HeadKey
```

Raw:

```text
String
long
byte[]
```

representations are permitted only at their legitimate construction or external-system boundaries.

The intended architecture is therefore:

```text
semantic concept
       ↓
semantic atom
(Text / Number)
       ↓
representation object
       ↓
Bytes
       ↓
byte[]
       ↓
RocksDB
```

rather than:

```text
semantic wrapper
       ↓ value()
primitive
       ↓
procedural encoding
       ↓
RocksDB
```

---

Questo ADR, secondo me, è importante quasi quanto quello sul modello semantico originale, perché stabilisce **come mantenere quel modello OO quando iniziamo finalmente a toccare la materia fisica dello storage**.

E la cosa che trovo più convincente è che non stiamo creando astrazioni artificiali: Java ci dà già `Number`, Cactoos ci dà già `Text` e `Bytes`; stiamo semplicemente facendo sì che `EventId`, `Position`, ecc. dichiarino **ciò che sono**, mentre gli oggetti RocksDB dichiarano **come vengono rappresentati**.
