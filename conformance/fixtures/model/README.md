# Model fixtures

*Part of the [fixture manifest](../README.md).*

Documents layers L2 and L3 refuse, each for the whole list of findings it draws: the checks of one
path at layer L2 run independently and in a fixed order (`SPEC.md` section 9.2), and a path that
failed layer L2 is not counted at layer L3 (section 9.3). The manifest records the list, in its
`invalid` section.

Each file is `../../../examples/minimal.esj.json` with the members named below added or put in
place of others, except `max-cardinality.esj.json`, a document of the test registry
`../registries/cardinality.json`: no published edition declares a maximum cardinality above one
for a repeatable term, so `ESJ-L3-MAX-CARDINALITY` is reached through a registry written for it.
That case names the registry in the manifest, and the runner passes it with the request.

| File | Defect |
|---|---|
| `parent-chain-and-date.esj.json` | `/BG-25/0/BT-2` carrying `2026-02-30` (SPEC.md section 9.2) |
| `index-forbidden-and-date.esj.json` | `/BT-2/0` carrying `2026-02-30` in place of `/BT-2` |
| `index-forbidden-twice.esj.json` | `/BT-1/0` and `/BT-1/1` in place of `/BT-1` (SPEC.md section 9.3) |
| `index-required-and-unknown-term.esj.json` | `/BG-25/BG-999/BT-1` (SPEC.md section 9.2) |
| `unknown-term-two-segments.esj.json` | `/BG-998/BT-999`: two segments no registry carries |
| `unknown-core-segment-beside-extension-segment.esj.json` | `/BG-999/BT-ZZZ-1`: an unknown core group above a term of a namespace no registry is loaded for |
| `index-required-above-extension-segment.esj.json` | `/BG-25/BG-ZZZ-1/BT-1`: BG-25 without its index, above a group of an unloaded namespace |
| `binary-as-string.esj.json` | BT-125 written as a string, with neither `mimeCode` nor `filename` |
| `binary-with-scheme.esj.json` | BT-125 carrying `scheme` and neither of its two components |
| `max-cardinality.esj.json` | three occurrences of BT-3 and of BG-1, each declared `0..2` by `../registries/cardinality.json` |
