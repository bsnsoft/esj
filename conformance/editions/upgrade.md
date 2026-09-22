# Upgrading between editions, measured

*Part of [EN16931 Semantic JSON](../../README.md).*

Every document of the conformance corpus and every example of the repository, written as
EN 16931-1:2026 and written back. The figures beside this page in `upgrade.json` are
recomputed on every build by `UpgradeCorpusTest` in `esj-core`, which fails where one of
them has moved in either direction.

## How it was taken

| | |
|---|---|
| Documents | the 86 instances of `conformance/esj/` and the 11 examples of `examples/` that name the 2017 edition |
| Moved by | `EditionUpgrade`, driven by `model/en16931/upgrade-2017-2026.json` |
| Checked by | `StructuralValidator` against `model/en16931/2026.json`, layers L2 and L3 |
| Compared by | the canonical bytes of the specification, section 7 |
| Provenance | not recorded in this run; see *What the round trip compares* |

## Up

| Measure | Documents |
|---|---:|
| Documents | 97 |
| `upgraded` | 97 |
| `refused` | 0 |
| Paths moved | 408 |
| Documents with at least one moved path | 93 |

The six paths that move are the ones the mapping lists; `/BT-20` into the new payment
terms group, the invoice line period under the new line delivery group with its two
terms, and the two terms whose segment gained an occurrence index because the edition
made them repeatable.

## What the registry of the 2026 edition says about the results

| Measure | Documents |
|---|---:|
| No finding at all | 18 |
| Only identifiers whose scheme the 2026 edition made mandatory | 79 |
| Anything else | 0 |

The second row is the migration point of this edition, not a defect of the upgrade: the
scheme of BT-29, BT-30, BT-46, BT-47, BT-60, BT-61 and BT-71 is mandatory in 2026 and was
optional in 2017, and an identification scheme is not derivable from anything a document
carries. The run reports each one and invents none.

## What the run left to the caller

| Open point | Values |
|---|---:|
| `scheme-missing` | 155 |
| `specification-identifier` | 97 |
| `extension-carried` | 6 |

`specification-identifier` is one per document: BT-24 states which specification an
invoice claims to follow, and an upgrade does not know whether that claim still holds.
`extension-carried` is one per document that uses an extension of this repository, the
XRechnung one or the B2C one: each registry names the edition it was written against, so
its terms are carried unchanged and are not checked against the target edition.

Nothing was rounded and nothing was dropped: `decimals-out-of-bounds` and `value-dropped`
are zero over this corpus. Where the 2026 edition bounds fraction digits by the minor unit
of the currency in use, the run says that it did not evaluate that bound — it is a fact of
a versioned rule pack rather than of the registry.

## Down

| Measure | Documents |
|---|---:|
| `downgraded` | 97 |
| `byteIdentical` | 97 |
| `refused` | 0 |

## What the round trip compares

The canonical bytes of the document that went in and of the one that came back. The run is
made without provenance, which is the whole of the difference between them: `source` is
set where a caller hands over the bytes the result derives from, and the command line
always does, so a round trip through `esj upgrade` differs from its input in that one
member and in nothing else.

## What this measurement does not say

It says nothing about a document that carries 2026 content. No document of this corpus
does — they are all 2017 — so the reverse direction is measured here only on results of
the forward one. What the downgrade does with content the 2017 edition has no address for
is not a figure but a rule: it refuses and names every such path, and a caller who names
the paths that may be dropped finds each of them in the report.
