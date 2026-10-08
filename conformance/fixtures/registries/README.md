# Test registries

*Part of the [fixture manifest](../README.md).*

Registries written for the manifest. They describe no published model. `cardinality.json` is a core
registry the case `../model/max-cardinality.esj.json` is validated with; `extension.json` is an
extension a loader takes; every other file breaks one rule `SPEC.md` section 10 holds a registry
or a combination of registries to, and the `registryChecks` section of the manifest records,
alone and combined with `model/en16931/2017.json`, whether a loader takes it.

| File | Content |
|---|---|
| `cardinality.json` | a core registry of three terms and a group, BT-3 and BG-1 declared `0..2`; the one way to reach `ESJ-L3-MAX-CARDINALITY` |
| `extension.json` | a well-formed extension: one term of the namespace `ZZZ` in the invoice line, importing the 2017 edition |
| `binary-without-components.json` | a `BinaryObject` term that declares no components |
| `scheme-version-without-scheme.json` | an identifier term that declares `schemeVersion` and no `scheme` |
| `mandatory-version-beside-optional-scheme.json` | an identifier term whose `schemeVersion` is mandatory beside an optional `scheme` |
| `duplicate-identifier.json` | BT-ZZZ-1 defined twice in one file |
| `identifier-of-another-extension.json` | BT-DEX-001, which `model/xrechnung/3.0.2.json` defines as well: refused when the two are combined |
| `no-imports.json` | a term placed in BG-25, a group it does not define, without `imports` |
| `imports-another-model.json` | an extension that imports another model, combined with the 2017 edition |
| `identifier-without-namespace.json` | an extension that defines BT-999, an identifier without a namespace |
| `two-namespaces.json` | an extension whose identifiers carry two namespaces |
| `redefines-core-term.json` | an extension that defines BT-1 again |
