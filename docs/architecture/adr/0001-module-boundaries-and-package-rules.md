# ADR-0001: Module Boundaries and Package Rules

- Status: Proposed
- Date: 2026-07-29
- Issue: [KERN-001](https://github.com/riccisi/kern/issues/1)

## Context

Kern is a single-node event database whose core behavior must remain usable and
benchmarkable without Spring Boot, Armeria, Protobuf, gRPC, or RocksDB. The
initial repository already separates the public API, core behavior, storage,
wire protocol, transport, server assembly, client, and end-to-end tests.

These boundaries must prevent storage and framework types from entering the
domain contracts while keeping the append path direct enough for predictable
latency and memory use.

## Decision

Kern uses the following Maven modules:

| Module | Responsibility | Must not contain |
| --- | --- | --- |
| `kern-api` | Public domain values and capabilities used by embedded and remote clients | Storage SPI, wire types, framework types, implementations |
| `kern-core` | Append coordination, consistency rules, reads, subscriptions, diagnostics, and storage SPI | RocksDB, Protobuf, Armeria, or Spring types |
| `kern-rocksdb` | RocksDB implementation of the core storage SPI, including binary codecs and lifecycle | Transport, application assembly, or public API policy |
| `kern-protocol` | Versioned Protobuf schemas and generated gRPC types | Domain mapping, core behavior, or dependencies on other Kern modules |
| `kern-armeria` | Data-plane adapter: authentication context, validation at the transport boundary, protocol mapping, streaming, timeout, and backpressure | Storage implementation or application bootstrap |
| `kern-server` | Executable application, object composition, configuration, lifecycle, control plane, security, and observability wiring | Reusable domain behavior or wire contracts |
| `kern-client` | Remote Java implementation of public client capabilities using the versioned protocol | Server, storage, or core implementation details |
| `kern-integration-tests` | End-to-end, recovery, failure, and compatibility tests | Production code published for reuse |

`kern-spring` is renamed to `kern-server`. Spring Boot is an implementation
detail of the server control plane, while the artifact represents the
deployable Kern process. There is no separate Spring integration or starter
module in v0.1.

### Dependency direction

Production dependencies form an acyclic graph:

```text
kern-client  -> kern-api
             -> kern-protocol

kern-server  -> kern-armeria
             -> kern-rocksdb

kern-armeria -> kern-api
             -> kern-core
             -> kern-protocol

kern-rocksdb -> kern-api
             -> kern-core

kern-core    -> kern-api

kern-protocol
kern-api
```

An arrow means that the dependency is allowed, not required. A module declares
a direct Maven dependency only when its own source code imports types from that
module; it must not rely on a transitive dependency for compiled types.
`kern-server`, as the composition root, may add direct dependencies on any
production module that it explicitly wires.

`kern-integration-tests` may depend on any production module, but internal Kern
dependencies in that module use `test` scope. No production module may depend
on `kern-server`, `kern-client`, or `kern-integration-tests`.

Third-party technology stays at its owning boundary:

- RocksJava is confined to `kern-rocksdb`.
- Protobuf and gRPC generated types are confined to `kern-protocol`,
  `kern-armeria`, and `kern-client`.
- Armeria is confined to `kern-armeria`, `kern-client`, and server wiring.
- Spring Boot, Spring Security, Actuator, and concrete telemetry exporters are
  confined to `kern-server`.

Protocol mapping occurs in `kern-armeria` and `kern-client`. RocksDB types are
adapted behind contracts owned by `kern-core`. Neither kind of technical type
is part of `kern-api`.

### Java packages

Each module owns one package root:

| Module | Package root |
| --- | --- |
| `kern-api` | `it.riccisi.kern.api` |
| `kern-core` | `it.riccisi.kern.core` |
| `kern-rocksdb` | `it.riccisi.kern.rocksdb` |
| `kern-protocol` | `it.riccisi.kern.protocol.v1` |
| `kern-armeria` | `it.riccisi.kern.armeria` |
| `kern-server` | `it.riccisi.kern.server` |
| `kern-client` | `it.riccisi.kern.client` |
| `kern-integration-tests` | `it.riccisi.kern.integration` |

No production type is declared directly in the shared `it.riccisi.kern`
package.

Subpackages are introduced around cohesive capabilities such as `append`,
`read`, `subscription`, or `diagnostics` only when the package contains enough
types to justify the grouping. Generic packages such as `util`, `common`,
`model`, `dto`, `service`, and `impl` are not used as catch-all layers.

The storage SPI is owned by `it.riccisi.kern.core.storage`. RocksDB-specific
code remains under `it.riccisi.kern.rocksdb`. Protobuf declarations use package
`kern.v1`, generate Java into `it.riccisi.kern.protocol.v1`, and reside under
`kern-protocol/src/main/proto/kern/v1`.

Tests mirror the package of the behavior they verify. Cross-module tests use
`it.riccisi.kern.integration`.

## Consequences

- Core behavior can be tested and benchmarked without loading server,
  transport, serialization, or storage frameworks.
- Storage and protocol implementations depend on contracts toward the center;
  their concrete types cannot become public domain types by convenience.
- The protocol can evolve and be compatibility-tested independently from the
  Java API.
- The server remains free to replace its control-plane framework without
  changing the deployable artifact coordinate.
- A future embedded integration, Spring starter, test kit, observability
  library, or benchmark module requires a concrete use case and a separate ADR.

The Maven reactor is the initial boundary check. Automated package and
dependency enforcement will be added when production packages exist and the
rule can verify real code without placeholder configuration.

## Alternatives Considered

### Keep `kern-spring` as the deployable module

Rejected because the name exposes an internal framework choice and differs from
the `kern-server` assembly boundary already described by the specification.

### Split `kern-server` and a reusable `kern-spring` module now

Rejected because v0.1 has no embedded Spring integration use case. The split
would add a module and public surface without separating a real responsibility.

### Let `kern-protocol` depend on `kern-api`

Rejected because schemas and generated wire types are independent contracts.
Their conversion to domain objects belongs in transport and client adapters.

### Add observability, test-kit, and benchmark modules immediately

Rejected for the foundation milestone. These modules are useful only when
their code and consumers establish a stable boundary.

### Use the longer `kern-event-store-*` artifact names from the specification

Rejected because the repository, product, Maven group, and parent artifact
already identify Kern. The shorter `kern-*` names preserve the same boundaries
without repeating the product category in every coordinate.
