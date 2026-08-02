# RocksDB Event Record Format v0.1

> Status: Superseded by `kern_event_store_specification_v0.2.pdf`.
> This document is historical and must not be used as the current storage
> contract for new RocksDB code.

Kern stores authoritative events as explicit binary records. Event records
implement the same Cactoos `Bytes` contract used by RocksDB keys: callers ask
the object for its binary representation and materialize `byte[]` only at the
RocksDB boundary. The format is independent from Java serialization and from the
public Protobuf protocol.

## Version 1

All numeric fields are big-endian. Text fields are UTF-8 bytes prefixed by a
4-byte signed length. Byte fields are prefixed by a 4-byte signed length.
Negative lengths and truncated fields are invalid.

| Field | Type | Notes |
| --- | --- | --- |
| magic | int | `0x4B45524E` |
| formatVersion | int | `1` |
| position | long | Global committed position |
| subjectRevision | long | Revision assigned inside the subject |
| timestampEpochMicros | long | Commit timestamp with microsecond precision |
| eventIdMostSignificantBits | long | UUID most significant bits |
| eventIdLeastSignificantBits | long | UUID least significant bits |
| namespace | text | Stored redundantly for independent verification |
| type | text | Event type |
| subject | text | Subject/stream identifier |
| contentType | text | Payload content type |
| schema | text | Opaque schema reference |
| tagCount | int | Number of exact-match tags |
| tags | repeated text,text | Tag names are encoded in lexicographic order |
| metadata | bytes | Opaque client metadata |
| payload | bytes | Opaque client payload |
| checksum | int | CRC32C of every preceding byte |

The checksum is part of the application record rather than RocksDB metadata.
Checksum append is modeled as a `Bytes` decorator, while verification decorates
the binary input consumed during decoding. Offline verification and index
rebuilds can therefore detect corrupted values without depending only on the
storage engine checks.

Record encoding is declarative composition of byte-producing objects. Record
parts such as format, timestamp, tags, UUIDs, text fields, and binary fields are
`Bytes` collaborators instead of procedural writer methods. `BinaryFieldBytes`
writes a length-prefixed byte field, while Cactoos `BytesOf` represents byte
arrays that are already encoded.

## Compatibility

`kern-rocksdb` commits a v1 fixture under
`src/test/resources/it/riccisi/kern/rocksdb/record/event-record-v1.hex`.
Changing the binary layout requires a new format version and a deliberate
compatibility test.
