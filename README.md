# Kern

**Kern — Observable Event Database**

The immutable core of your system.

Kern is a standalone event database designed to support traditional Event Sourcing and key-based Dynamic Consistency Boundaries.

## Coordinates

- Maven groupId: `it.riccisi.kern`
- Java root package: `it.riccisi.kern`
- Initial version: `0.1.0-SNAPSHOT`

## Modules

- `kern-api`: public domain model and storage-neutral contracts
- `kern-core`: append coordination, consistency, subscriptions and services
- `kern-rocksdb`: RocksDB storage implementation
- `kern-protocol`: Protobuf and gRPC contracts
- `kern-armeria`: Armeria data-plane services
- `kern-spring`: application bootstrap, configuration and control plane
- `kern-client`: Java client
- `kern-integration-tests`: end-to-end and failure tests
