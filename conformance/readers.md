# Two readers, one corpus

*Part of [EN16931 Semantic JSON](../README.md).*

There are two ways into the semantic model in this repository, and they share no code.

The **streaming reader** (`esj-bindings`) pulls a document one element at a time and matches
each element against the binding tables of `model/bindings/`. It holds one element and the
values it has produced, so eighty megabytes cost seconds; what "one element" is bounded by is
at the end of this page. It is the default of the command line tool and the reader that writes
the ESJ documents under `esj/`.

The **XSLT path** (`esj-xr`) hands a document to the vendored XRechnung visualization
stylesheets and maps the business term identifiers of the XR tree they produce to semantic
paths. It does not scale — source tree, XR tree and mapping in memory, so eighty megabytes cost
minutes and gigabytes (`../docs/deployment-measurements.md`) — and it stays in the product as
the oracle; `esj convert --importer xslt` reads with it.

This page is the record of where the two disagree: every instance of the corpus read both ways,
every differing semantic path traced to a cause, every cause classified. The corpus is 41 cross
industry invoices and 45 UBL **Invoices**, so it measures `model/bindings/cii.json` and
`model/bindings/ubl-invoice.json`; the credit note table is measured by the hand-written
document of `creditnote/`, below. `readers.json` carries the same as data, and
`ReaderCorpusTest` recomputes it and fails if a difference is not one this page explains.

## Which registry each door starts from

`ReaderOptions` — the library door — starts from the core registry **with** the XRechnung
extension. `esj convert` and the other commands start from the **core** registry alone and take
`--extension xrechnung` to add it: the tool is the process boundary, where the smaller model is
the conservative default. Everything measured below, and every document under `esj/`, is the
registry with the extension; reading the four extension instances without it turns the sub
invoice lines into ten `UNKNOWN_TERM` observations. So:

```text
esj convert --extension xrechnung --to esj <instance>
```

## Summary

| Measure | Instances |
|---|---:|
| `identical` — the two readers produce the same canonical bytes | 74 |
| `differing` — at least one semantic path differs | 12 |
| `total` — instances in the corpus | 86 |

Thirteen semantic paths differ over the whole corpus, in twelve instances.

## The causes

| Cause | Kind | Instances | Paths |
|---|---|---:|---:|
| `line-object-identifier-scheme` | overcome | 6 | 6 |
| `object-identifier-as-supporting-document` | overcome | 6 | 6 |
| `tax-point-date-code-list` | overcome | 1 | 1 |

**Kind** says who is right. *Overcome* is a limitation of the XSLT path that the streaming
reader does not have. *Open* would be a difference neither reader can settle, its cause a fact
missing or wrong in the binding tables; the corpus reaches none in this release, and the two
that stood here until this release are corrections of the tables now. *Constrained* is a class of document the streaming reader
refuses and the XSLT path reads; the corpus reaches none of that either, and one is known, the
resource bound in "Where the streaming reader gives way". The corpus alone would read as though
the streaming reader were strictly the better of the two, and it is not: the differences a
broken document reaches go the other way.

### `line-object-identifier-scheme`

*Overcome.* Six paths, one per CII instance that carries an invoice line object identifier,
all of them `/BG-25/*/BT-128`.

In CII the scheme of that identifier is the sibling element `ram:ReferenceTypeCode` and not an
attribute. The stylesheet writes a scheme only where the matched element carries a `schemeID`
of its own, which `ram:IssuerAssignedID` never does, so the scheme is dropped before the mapper
sees it. The table states the fact — the component of BT-128 is `../ram:ReferenceTypeCode`,
flagged `scheme-as-sibling-element` — so **the streaming reader is right here** and its BT-128
carries the scheme `ABZ`. Six documents under `esj/` gained a `scheme` member, and
`ledger/pairs.md` lost the limitation `identifier-with-scheme`.

### `object-identifier-as-supporting-document`

*Overcome.* Six paths, one per UBL instance that carries an invoiced object identifier, all
of them `/BG-24/*/BT-122` and all of them read by the XSLT path alone.

UBL writes BT-18 into `cac:AdditionalDocumentReference` with the document type code `130`, the
element BG-24 stands on. The source model states that condition on BT-18 and leaves it off
BG-24 and its four terms, and the stylesheet has the same gap, so a document that states an
invoiced object identifier gains a supporting document group whose only value is that
identifier read a second time as BT-122. `model/bindings/ubl-invoice.json` and
`ubl-creditnote.json` carry the exclusion as a correction, with the CEN validation artefact of
`packs/` as its reason, so **the streaming reader is right here** and the six documents under
`esj/` lost one BT-122 each; `bindings/crosscheck.md` names the difference against the
stylesheet.

### `tax-point-date-code-list`

*Overcome.* One path, `/BT-8`, in the one cross industry invoice of the corpus that states a
value added tax point date code.

EN 16931-1 gives BT-8 a restriction of UNTDID 2005 and the cross industry invoice writes
`ram:DueDateTypeCode` in UNTDID 2475: the artefacts of `packs/` admit `3`, `35` and `432` on
the UBL side and `5`, `29` and `72` on the CII side for the same three events. The table marks
the element `code-list-2475` and the streaming reader translates it, so BT-8 reaches the model
as the code of the standard whichever syntax the invoice arrived in; the XSLT path takes the
content as it stands. `ledger/pairs.md` records the same change from the other side.

## What the reader reports that the XSLT path does not

The XSLT path reports 89 dropped supplementary components over this corpus — schemes the source
syntax carries at terms the standard gives no scheme — and nothing else. The streaming reader
reports none of them: it reads a component only where the table names one, and the tables name
one only where the standard has it.

The streaming reader reports 34 observations of its own, **occurrences** and not instances: an
invoice that repeats the payment means three times reports twice. `readers.json` splits them by
syntax and by instance, and `ReaderCorpusTest` pins every cell:

| Kind | Where | Occurrences | Instances | CII | UBL |
|---|---|---:|---:|---|---|
| `DUPLICATE_PATH` | `/BG-16/BT-81` | 30 | 22 | 15 in 11 | 15 in 11 |
| `DUPLICATE_PATH` | `/BG-16/BT-83` | 4 | 4 | — | 4 in 4 |

Both are the same situation. A syntax lets the payment means and the payment reference repeat;
the semantic model gives an invoice one BG-16 with one BT-81 and one BT-83 in it. The first
element is the value, the second has nowhere to go and is reported. The XSLT path arrives at
the same document and says nothing: the stylesheet merges the repeated elements first.

## The same gap in the cross industry invoice

The cross industry invoice writes four references into `ram:AdditionalReferencedDocument` and
tells them apart by `ram:TypeCode`: `50` is BT-17, `130` is BT-18, `916` is BG-24. The source
model states that condition for BT-17, BT-18 and BT-122 and leaves it off BG-24, BT-123,
BT-124 and BT-125; the stylesheet has the same gap. `model/bindings/cii.json` carries the
condition as a correction, so the two readers part here as well. Every instance of this corpus
writes its supporting document before any other reference, so nothing above shows it; a
document that writes a tender reference first has the supporting document at `/BG-24/0/…` for
the streaming reader, where the standard puts it, and at `/BG-24/1/…` for the XSLT path. An
occurrence index is part of a semantic path, so that is a wrong document and not a spare
group. `StreamingReaderTest` holds the corrected behaviour.

## One difference beyond the corpus

UBL requires `cac:OrderReference/cbc:ID` as soon as the sales order reference BT-14 is
written, and the `conventions` member of `model/bindings/ubl-invoice.json` says what stands
there where a document states no purchase order reference. The streaming reader knows that
value and leaves BT-13 out, with a `CONVENTION_NOT_READ` note; the vendored stylesheets read
it as BT-13, which is a reference nobody made.

| Cause | Kind | Paths |
|---|---|---:|
| `order-reference-convention` | overcome | 1 |

No instance of the corpus carries it — the suite writes a placeholder of its own at that
element — so `readers.json` records it under `beyondTheCorpus` and `ReaderCorpusTest` runs both
readers over a document written for it. `../writers/ubl-roundtrip.md` says what the writer
writes there.

## What a broken document reaches

No instance of this corpus reaches one of these. Each is a term the source carries and one
reader does not; both readers hold to the rule that nothing the source carries is dropped in
silence.

An **identifier without the identification scheme its term requires** — BT-34, BT-49, BT-157,
BT-158 — is kept by both readers, which report `COMPONENT_MISSING` at its path. The model layer
reports `ESJ-L2-COMPONENT-MISSING` there and `BR-62` to `BR-65` fire over the document either
reader builds. Until this release the streaming reader dropped the value instead, and
`rules/ledger.md` recorded the four rules as differences against the official artefacts; it
records them as agreeing now.

A **term the binding selects by the scheme its identifier states** is the reverse: a CII tax
registration is BT-31 or BT-32 by the scheme on it, and one that states neither is neither.
Both readers report it — from the binding table in the streaming reader, from `XrCoverage` in
the XSLT path — and write no value, because no term of the model would hold one. The CII
global identifier of a party goes the other way: a document that states one without a scheme
is conformant, so `model/bindings/cii.json` binds it without the condition and the streaming
reader keeps it as BT-29, BT-46, BT-60 or BT-71. The stylesheet keeps the condition, so **the
two readers part here**: the XSLT path loses the identifier, `XrCoverage` writes an
`UNPLACEABLE` note, and `BR-CO-26` faults a seller that is identified by such an identifier
alone.

A **CII tax representative's tax registration** is bound by the VAT scheme, as the CEN syntax
binding binds it, so the streaming reader reads BT-63 from that registration and reports any
other. The stylesheet reads the registration whatever its scheme, so the XSLT path states a
BT-63 that is not a value added tax identifier and `BR-56` cannot be raised on it. This one
cannot be corrected here: the choice is made inside the vendored, unmodified stylesheet.

A **CII date whose format qualifier is not the eight-digit one** the binding names reaches
neither reader, and the streaming reader reports it while the XSLT path does not.

One difference here is between the two **syntaxes**. CII binds BT-31 and BT-32 by the scheme
each states, so a registration under a third scheme is neither and is reported; UBL binds
BT-31 by the value added tax scheme and BT-32 by the negation of it, as the source model and
the CEN binding state, so the same registration is BT-32 there. Both readers agree within each
syntax. `rules/mutations/mutations.json` measures the shape as
`near-tax-registration-without-scheme-{cii,ubl}`.

An **amount whose source notation carries more fraction digits than its semantic data type**
— `319.860` against a type of two decimals — is reported by the XSLT path as `SCALE_REDUCED`
and by the streaming reader not at all. Both hold the same number; the second no longer
records that the sender wrote a third digit. It is the one loss still open against the
streaming reader.

## The credit note

The corpus holds no UBL credit note, so `model/bindings/ubl-creditnote.json` — 195 bindings —
would otherwise be exercised by nothing here. `creditnote/credit-note_ubl.xml` is written for
it, `creditnote/README.md` says what is in it, and `CreditNoteReaderTest` reads it both ways as
this page does the corpus. Four defects of that table were found the moment the document
existed, all four in its `corrections` member now: BT-11 bound to every additional document
reference, BT-18 bound with a document type code that is not in the code list of the element,
the buyer and tax representative VAT identifiers bound without the condition that tells value
added tax from another tax, and BG-24 bound to every additional document reference.

One difference remains between the two readers on that document, the credit note's share of the
cause above:

| Cause | Kind | Paths |
|---|---|---:|
| `supporting-document-type-130` | overcome | 4 |

The table keeps BG-24 off the `cac:AdditionalDocumentReference` whose document type code is
130 or 50, because those two carry BT-18 and BT-11; the stylesheet leaves out only the one
whose code is 50. The stylesheet therefore reads the invoiced object identifier as a
supporting document as well, and the index of the real one moves by one.
`creditnote/README.md` prints the four paths.

## Where the streaming reader gives way

One class of document this reader refuses and the XSLT path reads; no instance of the corpus
is of that class.

An element the reader holds whole to decide a condition over it is held within
`--max-buffered-bytes` and `--max-buffered-elements`, and both refuse the **document** rather
than one term. The embedded attachment BT-125 sits inside such an element, so the byte bound
leaves room for a binary value of the size the limits admit: 33 MiB against the 32 MiB the
default limits give one binary value. The element bound has no equivalent in the XSLT path: a
document reference with more than a hundred thousand children is refused here and read there,
because those elements cost heap without costing a byte of content. `StreamingReaderTest`
holds both bounds.

## Reproducing this

```text
mvn -B -q -pl esj-bindings test
```

`ReaderCorpusTest` reads all 86 instances both ways on every build, so this page cannot drift
away from the code without the build saying so.
