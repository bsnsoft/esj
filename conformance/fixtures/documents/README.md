# Documents

*Part of the [fixture manifest](../README.md).*

Documents a reader reads, for their digests and for what a validator says of them: an edition no
registry describes (`ESJ-L2-EDITION-UNKNOWN`, `SPEC.md` section 9.2), terms no loaded registry
carries (`ESJ-L2-NOT-CHECKED`, section 5.6), and line endings (section 6.8). The manifest records
each in its `documents` section; `line-endings.canonical.esj.json` holds the canonical bytes of the
one twin.

Each file is `../../../examples/minimal.esj.json` with the change named below.

| File | Content |
|---|---|
| `unknown-edition.esj.json` | minimal.esj.json naming the edition `EN16931-1:2099`, for which no registry exists |
| `not-checked-unloaded-namespace.esj.json` | a term of the namespace `ZZZ`, for which no registry is loaded |
| `not-checked-in-loaded-namespace.esj.json` | BT-DEX-999, an identifier the loaded registry of the namespace `DEX` does not carry |
| `line-endings.esj.json` | CR LF, a lone CR and CR CR LF in strings of `values`, a component included, CR LF in `source` and `extensions`, and a decomposed `é` |
