# Vendored KoSIT semantic model schema

`xrechnung-semantic-model.xsd` is a copy, byte for byte unmodified, of the XML schema
that describes the XR representation: which element name stands for which business term
or business group of EN 16931-1, in which group it sits and in which order.

| Item | Value |
|---|---|
| Repository | <https://github.com/itplr-kosit/xrechnung-visualization> |
| Tag | `v2026-08-31` |
| Path in that repository | `src/xsd/` |
| License | Apache License, Version 2.0 (see the `LICENSE` file beside this one) |
| Publisher | Koordinierungsstelle für IT-Standards (KoSIT) / XStandards Einkauf |
| Modified | no |

## Files and digests

SHA-256 over the bytes of each file, lowercase hexadecimal:

| File | SHA-256 |
|---|---|
| `xrechnung-semantic-model.xsd` | `dc2193877adf0f5b31dcf0e86a24f38773bb748b0f0ec001341e4a2892121aef` |
| `LICENSE` | `e0d7665e91531aebb79e4feaa415796f076b0f93e9eddd6d1d05efe9d93808ac` |

`XrElementsTest` recomputes both digests on every build, so a change to either file is
a failing build rather than a quiet divergence from this table.

## Why it is a test resource and not a shipped one

The schema is read once, at generation time, and never at run time. What the exporter
`XrExporter` reads is
`esj-xr/src/main/resources/de/bsnsoft/esj/xr/xr-elements.tsv`, a table of
element names and element order derived from this schema and checked in beside the code.
`XrElementsTest` derives that table again from this file on every build and compares
it byte for byte with the checked-in one, so the table cannot drift away from the schema
it claims to come from, and no XML schema is parsed while an invoice is being written.

The schema is therefore not on the run-time classpath, is not part of any published
artefact of this project, and lies here — beside the test that reads it — rather than in
`src/main/resources`.

## What is taken from it

Facts: element names, the identifier in the `xs:appinfo` of each element, the type name
of each element, and the order of the elements inside each `xs:sequence`. The German
documentation of the informational elements is not read and is not reproduced anywhere in
this repository.
