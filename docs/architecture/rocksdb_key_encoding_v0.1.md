# RocksDB Key Encoding v0.1

Kern RocksDB keys are binary, deterministic, and scoped by namespace unless the
key belongs to the system column family.

## Component Encoding

- Text components are UTF-8 bytes prefixed by a 4-byte big-endian length.
- Position and revision components are non-negative 8-byte big-endian integers.
- Event ids are encoded as the 16 raw UUID bytes, most significant bits first.
- Key kind markers are single bytes after the namespace component.
- Keys never use textual separators.

## Key Shapes

Each key shape is represented by a concrete `BinaryKey` object in
`it.riccisi.kern.rocksdb.key`. The object owns the binary encoding for the
storage coordinate it represents.

| Key | Shape |
| --- | --- |
| Namespace prefix | `text(namespace)` |
| Event | `text(namespace), 0x01, position` |
| Subject revision | `text(namespace), 0x02, text(subject), revision` |
| Event id | `text(namespace), 0x03, uuid(eventId)` |
| Type index | `text(namespace), 0x04, text(type), position` |
| Tag index | `text(namespace), 0x05, text(tagName), text(tagValue), position` |
| Subject head | `text(namespace), 0x06, text(subject)` |
| Consistency | `text(namespace), 0x07, text(consistencyKey)` |
| Idempotency | `text(namespace), 0x08, text(idempotencyKey)` |
| System | `0x00, systemKeyCode` |

The namespace prefix shape is intentionally a real key prefix, so namespace
range scans can stay isolated without parsing string separators.
