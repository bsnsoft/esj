# Format version 0.1

*Part of the [fixture manifest](../README.md).*

Documents of format version 0.1, the release candidate of 1.0. A reader of format 1.0 reads them
by the rules of `SPEC.md` 1.0 and writes them in the version they carry (`SPEC.md` sections 4.3
and 7.3), so their canonical bytes, their semantic digests and their document digests are the
ones they had while the format was 0.1. Each one has its canonical bytes beside it as
`*.canonical.esj.json`, written by the release that wrote format 0.1; they are not regenerated.
The manifest records them in its `earlierVersions` section, the 2026 document in the part of its
edition, and `scrambled.esj.json` in its `canonicalOrder` section.

| File | Content |
|---|---|
| `minimal.esj.json` | `../../../examples/minimal.esj.json` as it was in version 0.1 |
| `extended.esj.json` | `../../../examples/extended.esj.json` as it was in version 0.1: `extensions` and `source` |
| `edition-2026.esj.json` | `../../../examples/edition-2026.esj.json` as it was in version 0.1 |
| `01.01a-INVOICE_ubl.xml.esj.json` | `../../esj/business-cases/standard/01.01a-INVOICE_ubl.xml.esj.json` as the streaming reader wrote it in version 0.1 |
| `scrambled.esj.json` | the members of `extended.esj.json` in the wrong order; its canonical bytes are `extended.canonical.esj.json` |

The same values written as a 1.0 document have the same semantic digest and a different document
digest, because the document digest covers `version` (`SPEC.md` section 8.3).
