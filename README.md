# Kern

**Kern — Observable Event Database**

The immutable core of your system.

Kern is a standalone event database designed to support traditional Event Sourcing and key-based Dynamic Consistency Boundaries.

## Coordinates

- Maven groupId: `it.riccisi.kern`
- Java root package: `it.riccisi.kern`
- Initial version: `0.1.0-SNAPSHOT`

## Development Environment

Kern targets Java 25. The repository uses `direnv` to select the project JDK
without changing the system Java version.

On macOS with Homebrew:

```bash
brew install openjdk@25 direnv
echo 'eval "$(direnv hook zsh)"' >> ~/.zshrc
source ~/.zshrc
direnv allow .
mvn clean verify
```

## Modules

- `kern-api`: public domain model and storage-neutral client contracts
- `kern-core`: append coordination, consistency, subscriptions, and storage SPI
- `kern-rocksdb`: RocksDB storage implementation
- `kern-protocol`: versioned Protobuf and gRPC wire contracts
- `kern-armeria`: Armeria data-plane adapter
- `kern-server`: executable server, application assembly, and control plane
- `kern-client`: remote Java client
- `kern-integration-tests`: end-to-end and failure tests

The authoritative module boundaries, dependency direction, and Java package
conventions are defined in
[ADR-0001](docs/architecture/adr/0001-module-boundaries-and-package-rules.md).
