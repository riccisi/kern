# ADR — KeQL: Kern Event Query Language

## Status

**Accepted**

## Context

Kern represents event selection through the semantic abstraction:

```java
public interface EventFilter {

    <T> T describe(EventSelection<T> selection);
}
```

The filter model is intentionally small and compositional.

Its principal semantic building blocks are:

```text
TypedBy
TaggedAs
AllEvents
AnyEvents
```

A non-trivial filter may therefore be expressed programmatically as:

```java
EventFilter filter =
    new AllEvents(
        new AnyEvents(
            new TypedBy("CourseCreated"),
            new TypedBy("StudentEnrolled")
        ),
        new TaggedAs("courseId", "c1"),
        new TaggedAs("studentId", "s1")
    );
```

This representation is appropriate when a Java client constructs the filter directly.

There are, however, contexts in which an object graph is less convenient:

```text
configuration
command-line tools
HTTP interfaces
administrative consoles
dynamic subscriptions
tests
diagnostics
non-Java clients
```

Kern therefore needs a compact textual representation for the same filtering semantics.

That representation must remain subordinate to the `EventFilter` model.

It must not introduce a second query language with independent semantics.

---

# Decision

Kern introduces:

> **KeQL — Kern Event Query Language**

KeQL is a small Domain Specific Language used exclusively to construct `EventFilter`.

The Java entry point is:

```java
public final class KeqlEventFilter
    implements EventFilter
```

For example:

```java
EventFilter filter =
    new KeqlEventFilter(
        """
        type = CourseCreated | StudentEnrolled
        & courseId = c1
        & studentId = s1
        """
    );
```

is semantically equivalent to:

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

The fundamental invariant is:

> **Every valid KeQL expression must compile into the same `EventFilter` algebra that could have been constructed programmatically.**

KeQL adds syntax.

It does not add filtering power.

---

# 1. KeQL is an EventFilter

KeQL does not modify the Kern public API.

Given:

```java
StoredEvents events(
    Namespace namespace,
    EventFilter filter,
    Position after
);
```

both of these are valid:

```java
store.events(
    namespace,
    new TypedBy("CourseCreated"),
    after
);
```

and:

```java
store.events(
    namespace,
    new KeqlEventFilter(
        "type = CourseCreated"
    ),
    after
);
```

The rest of Kern sees only:

```text
EventFilter
```

There is no KeQL-specific branch in:

```text
EventStore
StoredEvents
Tail
Subscription
in-memory implementation
RocksDB implementation
remote client
server runtime
```

---

# 2. KeQL is a construction language

KeQL does not execute queries.

Its complete semantic responsibility is:

```text
KeQL text
    ↓
parse
    ↓
EventFilter Composite
```

Once parsing has produced the `EventFilter`, KeQL has completed its work.

For example:

```text
type = CourseCreated
```

becomes:

```java
new TypedBy("CourseCreated")
```

and:

```text
courseId = c1
```

becomes:

```java
new TaggedAs("courseId", "c1")
```

while:

```text
A & B
```

becomes:

```java
new AllEvents(A, B)
```

and:

```text
A | B
```

becomes:

```java
new AnyEvents(A, B)
```

There is no KeQL query executor.

---

# 3. No second semantic AST

The implementation should preferably construct `EventFilter` objects directly.

It should not introduce a permanent hierarchy such as:

```text
KeqlAnd
KeqlOr
KeqlCondition
KeqlTypeCondition
KeqlTagCondition
```

that duplicates:

```text
AllEvents
AnyEvents
TypedBy
TaggedAs
```

A parser-internal AST may exist if required by the chosen parsing library, but it must remain an implementation detail.

At the semantic boundary there is only:

```text
EventFilter
```

Conceptually:

```text
type = A | B
& courseId = c1

        ↓

AllEvents
├── AnyEvents
│   ├── TypedBy(A)
│   └── TypedBy(B)
└── TaggedAs(courseId,c1)
```

---

# 4. Design goals

KeQL must be:

* compact;
* readable;
* compositional;
* predictable;
* easy to parse;
* easy to type;
* easy to embed in configuration;
* semantically identical to the Object API.

KeQL must deliberately avoid becoming:

```text
SQL
a generic boolean expression engine
a payload query language
a scripting language
```

---

# 5. Basic conditions

The reserved identifier:

```text
type
```

represents `EventType`.

Therefore:

```text
type = CourseCreated
```

means:

```java
new TypedBy("CourseCreated")
```

Every other ordinary identifier represents a tag name.

Therefore:

```text
courseId = c1
```

means:

```java
new TaggedAs("courseId", "c1")
```

and:

```text
studentId = s1
```

means:

```java
new TaggedAs("studentId", "s1")
```

---

# 6. Boolean conjunction

The operator:

```text
&
```

means boolean conjunction.

Thus:

```text
type = StudentEnrolled
& courseId = c1
```

means:

```java
new AllEvents(
    new TypedBy("StudentEnrolled"),
    new TaggedAs("courseId", "c1")
)
```

`&` always means AND.

Its semantic meaning never changes according to context.

---

# 7. Boolean disjunction

The operator:

```text
|
```

means boolean disjunction.

Thus:

```text
type = CourseCreated
| type = StudentEnrolled
```

means:

```java
new AnyEvents(
    new TypedBy("CourseCreated"),
    new TypedBy("StudentEnrolled")
)
```

`|` always means OR.

Its semantic meaning never changes according to context.

---

# 8. Same-attribute shorthand

KeQL allows the attribute to be omitted on the right side of a disjunction when the alternative refers to the same attribute.

Therefore:

```text
type = CourseCreated | StudentEnrolled
```

is shorthand for:

```text
type = CourseCreated
| type = StudentEnrolled
```

and compiles into:

```java
new AnyEvents(
    new TypedBy("CourseCreated"),
    new TypedBy("StudentEnrolled")
)
```

Likewise:

```text
region = eu | us
```

is shorthand for:

```text
region = eu
| region = us
```

and becomes:

```java
new AnyEvents(
    new TaggedAs("region", "eu"),
    new TaggedAs("region", "us")
)
```

---

# 9. Chained shorthand

The inheritance of the current attribute may continue across multiple alternatives.

Therefore:

```text
type = A | B | C
```

is shorthand for:

```text
type = A
| type = B
| type = C
```

and compiles into:

```java
new AnyEvents(
    new TypedBy("A"),
    new TypedBy("B"),
    new TypedBy("C")
)
```

Likewise:

```text
region = eu | us | apac
```

becomes:

```java
new AnyEvents(
    new TaggedAs("region", "eu"),
    new TaggedAs("region", "us"),
    new TaggedAs("region", "apac")
)
```

---

# 10. Attribute inheritance applies only to omitted conditions

The shorthand exists only where the alternative omits a new attribute.

Thus:

```text
type = A | B
```

inherits:

```text
type
```

for `B`.

But:

```text
type = A | region = eu
```

is a true disjunction between two complete conditions:

```java
new AnyEvents(
    new TypedBy("A"),
    new TaggedAs("region", "eu")
)
```

The presence of a new:

```text
identifier =
```

starts a new condition.

---

# 11. Representative expression

A common filter can therefore be written as:

```text
type = CourseCreated | StudentEnrolled
& courseId = c1
& studentId = s1
```

and maps to:

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

This is the preferred KeQL form.

No comma syntax is required.

---

# 12. Why the comma was removed

An earlier version considered:

```text
type = CourseCreated, StudentEnrolled
```

as shorthand for an OR between values.

That syntax was rejected.

The reason is that KeQL already has a boolean OR operator:

```text
|
```

Introducing:

```text
,
```

would create two different textual mechanisms for essentially the same semantic operation.

The final design prefers orthogonality:

```text
& = AND
| = OR
```

The language becomes smaller and easier to remember.

---

# 13. `&` never means value alternatives

The following expression:

```text
type = A & B
```

must not be interpreted as:

```text
type = A | type = B
```

Doing so would overload `&` with a context-dependent meaning.

If shorthand inheritance is applied mechanically, it would instead mean:

```text
type = A
& type = B
```

which is generally unsatisfiable for the single-valued `type` attribute.

KeQL should therefore reject:

```text
type = A & B
```

as invalid shorthand.

A value-only continuation is supported after:

```text
|
```

but not after:

```text
&
```

This keeps the language unambiguous.

---

# 14. Nested boolean expressions

Parentheses may group expressions.

For example:

```text
(type = CourseCreated | StudentEnrolled)
& courseId = c1
```

means:

```java
new AllEvents(
    new AnyEvents(
        new TypedBy("CourseCreated"),
        new TypedBy("StudentEnrolled")
    ),
    new TaggedAs("courseId", "c1")
)
```

And:

```text
type = CourseCreated
| (type = StudentEnrolled & region = eu)
```

means:

```java
new AnyEvents(
    new TypedBy("CourseCreated"),
    new AllEvents(
        new TypedBy("StudentEnrolled"),
        new TaggedAs("region", "eu")
    )
)
```

---

# 15. The expression `type = A & (B | C)`

The form:

```text
type = A & (B | C)
```

is intentionally not valid shorthand.

After `&`, Kern expects a complete condition or grouped expression, not a value-only continuation.

Therefore the intended meaning must be written explicitly:

```text
type = A
& (type = B | C)
```

which is equivalent to:

```text
type = A
& (type = B | type = C)
```

This expression is syntactically valid, although for a single-valued `type` it is normally unsatisfiable.

KeQL does not silently rewrite contradictions into different semantics.

---

# 16. Operator precedence

Boolean precedence is conventional:

```text
& > |
```

Therefore:

```text
type = A
| type = B
& region = eu
```

means:

```text
type = A
|
(
    type = B
    & region = eu
)
```

Parentheses may override this precedence.

The same-attribute shorthand does not create a third operator.

It is purely syntactic inheritance of the omitted attribute in an OR alternative.

---

# 17. Grammar

A conceptual EBNF grammar is:

```ebnf
filter          ::= disjunction ;

disjunction     ::= conjunction
                    { "|" alternative } ;

alternative     ::= conjunction
                  | inherited-value ;

conjunction     ::= primary
                    { "&" primary } ;

primary         ::= condition
                  | "(" disjunction ")" ;

condition       ::= identifier "=" value ;

inherited-value ::= value ;

value           ::= bare-value
                  | quoted-string ;
```

The parser must carry the current condition attribute when parsing an inherited OR value.

A more implementation-oriented grammar may separate condition-level alternatives explicitly.

The grammar in this ADR defines semantics rather than prescribing parser internals.

---

# 18. Context-sensitive shorthand

The syntax:

```text
type = A | B
```

is intentionally context-sensitive in a very limited way.

`B` is interpreted relative to the immediately active condition attribute.

This context is local and deterministic.

For example:

```text
type = A | B | C
```

has active attribute:

```text
type
```

throughout the shorthand sequence.

But:

```text
type = A | region = eu | us
```

means:

```text
type = A
|
region = eu
|
region = us
```

After the explicit:

```text
region =
```

the inherited attribute becomes:

```text
region
```

for the following shorthand alternative.

---

# 19. Whitespace

Whitespace is insignificant outside quoted values.

Therefore:

```text
type=A|B&courseId=c1
```

and:

```text
type = A | B
& courseId = c1
```

are semantically equivalent.

Human-authored expressions should normally prefer spacing for readability.

---

# 20. Bare values

Simple values do not require quotes.

Examples:

```text
type = StudentEnrolled
courseId = c1
region = eu-west
version = v2
```

This keeps the common case compact.

---

# 21. Quoted values

Quotes are required when a value contains whitespace or reserved syntax characters.

For example:

```text
courseName = "Advanced Java Course"
```

or:

```text
externalId = "abc|123"
```

or:

```text
label = "a & b"
```

The initial quoting syntax uses:

```text
"..."
```

with minimal escapes:

```text
\"
\\
\n
\r
\t
```

---

# 22. Reserved syntax

The initial KeQL symbolic vocabulary is deliberately tiny:

```text
=
&
|
(
)
"
```

No comma is reserved.

No SQL-like keywords are required.

This leaves most ordinary textual values available without escaping.

---

# 23. Reserved identifier `type`

The identifier:

```text
type
```

is reserved and maps to:

```java
TypedBy
```

Every other ordinary identifier maps to:

```java
TaggedAs
```

This produces compact expressions such as:

```text
type = StudentEnrolled
& courseId = c1
```

instead of more verbose alternatives such as:

```text
event.type = StudentEnrolled
& tag.courseId = c1
```

---

# 24. Future reserved attributes

Implicit tag names create a compatibility constraint.

Adding future reserved identifiers could change the meaning of an existing tag.

For example:

```text
source = mobile
```

currently means:

```java
new TaggedAs("source", "mobile")
```

If `source` later became a reserved event property, old expressions could change meaning.

Therefore new reserved identifiers must be introduced very conservatively.

Explicit tag qualification may be added later if required.

---

# 25. KeQL is not SQL

Despite the phrase “Query Language”, KeQL does not describe a complete Event Store query.

It deliberately does not contain:

```text
SELECT
FROM
WHERE
ORDER BY
LIMIT
JOIN
AFTER
FOLLOW
```

The surrounding API already represents those concerns.

For example:

```java
store.events(
    namespace,
    new KeqlEventFilter(
        """
        type = StudentEnrolled | StudentReinstated
        & courseId = c1
        """
    ),
    after
);
```

is already semantically complete.

KeQL answers only:

> **Which events are relevant?**

---

# 26. KeQL does not represent Namespace

`Namespace` belongs to Event Store addressing.

Therefore this is intentionally impossible:

```text
namespace = school1
& type = StudentEnrolled
```

unless `namespace` happens to be an ordinary event tag.

The actual namespace remains:

```java
store.events(
    namespace,
    filter,
    after
);
```

KeQL must not duplicate it.

---

# 27. KeQL does not represent Position

Similarly, KeQL does not express:

```text
position > 100
after = 100
```

Observation boundaries belong to `EventStore` and `StoredEvents`.

They are not filter predicates.

---

# 28. KeQL does not represent limit or follow

KeQL must not grow into constructs such as:

```text
LIMIT 100
FOLLOW
NEXT 100
```

These semantics already belong to:

```text
StoredEvents
Subscription
next(count)
```

The language remains exclusively an `EventFilter` representation.

---

# 29. KeQL cannot outrun EventFilter

KeQL v1 does not support:

```text
amount > 100
status != deleted
name ~= "foo.*"
payload.age > 18
date >= 2026-01-01
```

nor:

```text
contains(...)
startsWith(...)
matches(...)
lower(...)
```

unless corresponding semantic filters are first added to Kern deliberately.

The evolution order is:

```text
semantic requirement
      ↓
EventFilter abstraction
      ↓
implementation semantics
      ↓
KeQL syntax
```

Never:

```text
DSL feature
      ↓
force domain to catch up
```

---

# 30. Why this restriction matters

If KeQL could express capabilities that `EventFilter` cannot, Kern would acquire two semantic models:

```text
Object EventFilter model
          ↓
       storage

KeQL model
          ↓
       storage
```

That would undermine the architecture.

The intended model is:

```text
Object construction ─┐
                     │
KeQL construction ───┼──► EventFilter
                     │
wire decoding ───────┘
                          ↓
                   EventSelection<T>
                          ↓
                   implementation
```

There is exactly one semantic filter algebra.

---

# 31. Parser technology

KeQL v1 uses:

> **Google Mug Dot Parse**

The language is:

```text
small
expression-oriented
parse-once
Java-native
directly mapped to Java objects
```

A parser-combinator approach is therefore preferred.

---

# 32. Why Mug Dot Parse

Parser combinators allow grammar productions to construct semantic objects directly.

Conceptually:

```text
type condition
      ↓
TypedBy

tag condition
      ↓
TaggedAs

AND
      ↓
AllEvents

OR
      ↓
AnyEvents
```

The parser result can therefore already be:

```java
EventFilter
```

rather than a generic syntax tree requiring a separate compiler.

This is well aligned with Kern's object-oriented design.

---

# 33. Parsing attribute inheritance

The same-attribute shorthand requires the parser to retain a small amount of local parsing context.

Given:

```text
type = A | B | C
```

the parser recognizes:

```text
attribute = type
first value = A
alternative = B
alternative = C
```

and creates:

```java
new AnyEvents(
    new TypedBy("A"),
    new TypedBy("B"),
    new TypedBy("C")
)
```

For:

```text
region = eu | us
```

it analogously produces `TaggedAs` filters.

This context exists only during parsing.

It is not represented in the resulting domain model.

---

# 34. Why not ANTLR

ANTLR was considered.

It provides excellent tooling for large languages, but KeQL v1 is small enough that ANTLR would introduce unnecessary ceremony:

```text
.g4 grammar
    ↓
code generation
    ↓
generated lexer/parser
    ↓
semantic conversion
```

Dot Parse allows the grammar and semantic mapping to remain in ordinary Java.

ANTLR remains a valid option if KeQL grows substantially.

---

# 35. Why not Tree-sitter

Tree-sitter was also considered.

Its greatest strengths include:

```text
incremental parsing
editing incomplete source
syntax highlighting
IDE integration
incremental syntax trees
```

Those are extremely valuable for editor-oriented languages.

KeQL's primary use case is instead:

```text
receive expression
      ↓
parse once
      ↓
construct EventFilter
```

Tree-sitter is therefore more infrastructure than Kern requires for v1.

It remains a possible future choice for dedicated KeQL tooling.

---

# 36. Tree-sitter may still complement KeQL later

Choosing Dot Parse for runtime parsing does not prevent Tree-sitter from being introduced later.

For example:

```text
KeQL runtime parser
    Mug Dot Parse

KeQL IDE grammar
    Tree-sitter
```

could coexist.

The two tools solve different problems.

A future editor, language server or syntax-highlighting extension may make Tree-sitter desirable without changing Kern runtime semantics.

---

# 37. Module

KeQL belongs to an optional module:

```text
kern-keql
```

with dependency:

```text
kern-keql
    ↓
kern-api
```

Never:

```text
kern-api
    ↓
kern-keql
```

The semantic core must remain independent of textual syntax.

---

# 38. Public API surface

The desired public surface is intentionally tiny:

```java
public final class KeqlEventFilter
    implements EventFilter {

    public KeqlEventFilter(String expression);

    @Override
    public <T> T describe(
        EventSelection<T> selection
    );
}
```

Initially there is no public:

```text
KeqlParser
KeqlLexer
KeqlAst
KeqlCompiler
KeqlNode
```

These are implementation concerns.

---

# 39. Eager parsing

`KeqlEventFilter` parses during construction.

Therefore:

```java
new KeqlEventFilter(expression)
```

either creates a valid immutable filter or fails.

Parsing is not deferred until:

```java
describe(...)
```

This provides deterministic validation and prevents repeated parsing.

---

# 40. Conceptual implementation

A possible implementation is:

```java
public final class KeqlEventFilter
    implements EventFilter {

    private final EventFilter origin;

    public KeqlEventFilter(final String expression) {
        this.origin =
            new ParsedKeql(expression).filter();
    }

    @Override
    public <T> T describe(
        final EventSelection<T> selection
    ) {
        return this.origin.describe(selection);
    }
}
```

The precise helper classes are not prescribed.

Only the semantic structure matters.

---

# 41. Flattened Composites

The parser should preferably flatten repeated boolean operations.

Thus:

```text
a = 1 & b = 2 & c = 3
```

should produce:

```java
new AllEvents(
    new TaggedAs("a", "1"),
    new TaggedAs("b", "2"),
    new TaggedAs("c", "3")
)
```

instead of nested `AllEvents`.

Likewise:

```text
type = A | B | C
```

should preferably produce:

```java
new AnyEvents(
    new TypedBy("A"),
    new TypedBy("B"),
    new TypedBy("C")
)
```

This is not required for semantic correctness, but gives cleaner object graphs.

---

# 42. Syntax error model

Invalid syntax should result in a KeQL-oriented exception such as:

```java
InvalidKeqlExpression
```

The public API should not expose parser-library exceptions.

Useful diagnostic information includes:

```text
line
column
offset
expected token or construct
nearby source fragment
```

For example:

```text
type = A |
```

may result in:

```text
Invalid KeQL expression at line 1, column 11

type = A |
          ^

Expected an alternative value or condition
```

---

# 43. Invalid shorthand errors

Expressions such as:

```text
type = A & B
```

should produce a specific syntax/semantic error:

```text
Expected a condition after '&'
```

rather than silently inheriting `type`.

This reinforces the rule:

> implicit attribute inheritance exists only across `|`.

---

# 44. Domain validation is reused

KeQL parsing should not duplicate validation implemented by Kern value objects.

For example:

```text
type = ""
```

may parse as a quoted string but still fail construction of a valid `EventType`.

Similarly:

```text
tagName
tagValue
```

must follow the same validation rules used by programmatic filters.

KeQL syntax validation and Kern semantic validation remain distinct.

---

# 45. Programmatic equivalence as test oracle

The most important tests compare KeQL with programmatic construction.

For example:

```text
type = CourseCreated | StudentEnrolled
& courseId = c1
& studentId = s1
```

must be semantically equivalent to:

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

The comparison should preferably happen by interpreting both through the same test `EventSelection<T>`.

It should not depend on concrete filter `equals()` implementations.

---

# 46. Shorthand equivalence tests

The following pairs must be semantically equivalent:

```text
type = A | B
```

and:

```text
type = A | type = B
```

Likewise:

```text
region = eu | us
```

and:

```text
region = eu | region = us
```

And:

```text
type = A | B | C
```

must be equivalent to:

```text
type = A
| type = B
| type = C
```

These equivalences are central language invariants.

---

# 47. Precedence tests

The expression:

```text
type = A
| type = B
& region = eu
```

must mean:

```text
type = A
|
(
    type = B
    & region = eu
)
```

While:

```text
(type = A | B)
& region = eu
```

must mean:

```text
(
    type = A
    | type = B
)
& region = eu
```

Operator precedence is part of the KeQL contract.

---

# 48. Lexical tests

The test suite should cover:

```text
whitespace
newlines
no whitespace
quoted values
escaped quotes
escaped backslashes
operators inside quoted values
invalid identifiers
missing values
missing operators
unexpected parentheses
unbalanced parentheses
trailing |
leading |
repeated &&
repeated ||
invalid shorthand after &
```

---

# 49. KeQL must be invisible to RocksDB

The RocksDB path remains:

```text
EventFilter
     ↓
describe(...)
     ↓
RocksEventSelection
     ↓
Index
     ↓
Iterable<Position>
```

There is no:

```text
KeQL
 ↓
RocksDB parser
```

By the time the filter reaches RocksDB, its textual origin is irrelevant.

---

# 50. KeQL must be invisible to subscriptions

The same is true for:

```text
Tail
Subscription
StoredEvents
```

A filter created through:

```java
new KeqlEventFilter(...)
```

has exactly the same semantics as a programmatic Composite.

The asynchronous waiting model therefore requires no changes.

---

# 51. KeQL and the wire protocol

A Java remote client does not necessarily send the KeQL string over the wire.

The preferred path is:

```text
KeQL
 ↓
EventFilter
 ↓
semantic protocol representation
 ↓
EventFilter
 ↓
server
```

This prevents textual grammar evolution from automatically becoming protocol evolution.

---

# 52. HTTP adapters may accept KeQL directly

An HTTP-facing adapter may nevertheless accept a textual filter:

```text
/events?filter=...
```

and construct:

```java
new KeqlEventFilter(expression)
```

This is a transport convenience.

It does not make KeQL the authoritative wire model.

---

# 53. Compatibility

KeQL expressions may eventually be persisted outside Kern in:

```text
configuration
scripts
saved queries
subscription definitions
external systems
```

Therefore grammar compatibility matters.

Whenever reasonably possible:

> valid expressions must preserve their meaning across compatible releases.

---

# 54. Minimal keyword policy

KeQL deliberately uses almost no keywords.

The symbolic syntax is:

```text
=
&
|
()
```

and only:

```text
type
```

has reserved semantic meaning.

This minimizes future compatibility problems with tag names.

---

# 55. No explicit grammar version in v1

Expressions do not include:

```text
keql:1:
```

or similar prefixes.

If incompatible grammar evolution eventually becomes necessary, versioning should preferably live in the surrounding protocol or configuration.

---

# 56. Canonical printing is deferred

KeQL v1 requires:

```text
KeQL
 ↓
EventFilter
```

but not yet:

```text
EventFilter
 ↓
KeQL
```

A future interpretation:

```text
EventSelection<String>
```

may produce canonical KeQL.

This would allow:

```text
programmatic EventFilter
      ↓
canonical KeQL
```

without exposing filter internals.

---

# 57. Potential round-trip property

If a KeQL printer is added later:

```text
EventFilter
   ↓ print
KeQL
   ↓ parse
EventFilter'
```

must preserve semantic equivalence:

```text
EventFilter ≡ EventFilter'
```

Original whitespace or shorthand formatting need not be preserved.

---

# 58. Security

KeQL can only construct known Kern filter objects.

It must never produce:

```text
SQL
Java code
reflection expressions
raw RocksDB queries
arbitrary functions
```

The expressive power of the language is bounded by the `EventFilter` algebra.

---

# 59. Complexity limits

Remote or untrusted KeQL input must be bounded.

Operational limits may include:

```text
maximum expression length
maximum nesting depth
maximum conditions
maximum OR alternatives
```

These should align with the general `EventFilter` complexity limits in the Kern runtime.

---

# 60. Consequences

KeQL gives Kern multiple representations of the same semantic filter:

```text
                     EventFilter
                    /     |      \
                   /      |       \
          Object API     KeQL    Wire model
```

These are not independent query systems.

They are different construction/representation mechanisms for one semantic model.

This preserves:

```text
one filter algebra
one storage interpretation path
one consistency model
one subscription model
```

while improving ergonomics.

---

# Final Decision

Kern adopts **KeQL — Kern Event Query Language** as an optional textual DSL for constructing `EventFilter`.

Its Java representation is:

```java
KeqlEventFilter implements EventFilter
```

and belongs to:

```text
kern-keql
```

KeQL v1 uses only:

```text
=     condition
&     boolean AND
|     boolean OR
()    grouping
```

The attribute:

```text
type
```

maps to:

```java
TypedBy
```

all other identifiers map to:

```java
TaggedAs
```

KeQL supports same-attribute OR shorthand:

```text
type = A | B | C
```

which is equivalent to:

```text
type = A
| type = B
| type = C
```

Attribute inheritance is permitted only across `|`, never across `&`.

Therefore:

```text
type = A & B
```

is invalid shorthand.

The parser produces the same `EventFilter` Composite that would be constructed programmatically.

The initial parsing engine is **Mug Dot Parse**.

ANTLR and Tree-sitter remain considered alternatives for future language growth or editor-oriented tooling, but are unnecessary for runtime parsing in v1.

No KeQL-specific query engine, storage layer, runtime path or semantic AST is introduced.

The architectural rule remains:

> **KeQL is a more concise way to construct an EventFilter, never a second way to define what filtering means.**