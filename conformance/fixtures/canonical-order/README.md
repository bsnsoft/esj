# Canonical order

*Part of the [fixture manifest](../README.md).*

Documents not in canonical form, with the bytes a canonicalizer has to produce from each beside
them as `*.canonical.esj.json` (`SPEC.md` section 7). `scrambled.esj.json` is
`../../../examples/extended.esj.json` with its members in the wrong order, and its canonical
bytes are the twin of that example; `indices.esj.json` holds paths whose indices order
numerically. The manifest records each in its `canonicalOrder` section, with its document digest
and its number of values.

| File | Content |
|---|---|
| `scrambled.esj.json` | the members of `../../../examples/extended.esj.json` in the wrong order |
| `indices.esj.json` | indices under one group that order as numbers and not as text |
| `escaping.esj.json` | every escape of JSON in member names and values, uppercase and lowercase hexadecimal, a surrogate pair, `\/`, and CR LF inside and outside `values` |
| `corners.esj.json` | paths that meet each rule of section 7.4: indices past 2^64, a term beside an index, namespaces, leading zeros, a prefix |
| `numbers.esj.json` | numbers of `extensions` at the 64-character bound and in every spelling section 7.6 rewrites, objects inside arrays |
| `value-object-order.esj.json` | value objects and `source` with their members in reverse order |
