# Litmus test: the same invoice in both syntaxes

The KoSIT test suite carries 40 business cases in two syntaxes each: the same invoice as a
UBL instance and as a UN/CEFACT CII instance, distinguished only by the file name suffix
`_ubl` and `_uncefact`. EN 16931-1 says that both carry the same semantic content. ESJ can
check that claim, because it gives that content one byte sequence and one digest: import
both files, compare the semantic digest of the specification, section 8.2, and a pair that
agrees agrees exactly.

This is the sharpest test the corpus affords. A converter can be wrong in both directions
at once and still round-trip; it cannot be wrong in one syntax only and still meet the
other one on the same digest. Where the digests differ, the difference is the set of
semantic paths at which the two documents disagree: a value that differs, or a value one
side has and the other has not. Every one of them is listed below, with both contents, and
traced to a cause.

The two documents compared are the ones checked in under `../esj/`, which is what this
repository says each instance of the corpus is as a semantic document. The reader that
writes them is held against them on every build, and so is the second reader of the
repository, so a change in either one reaches this ledger through those files rather than
beside them.

Three kinds of cause appear. A *fixture* difference is a difference between the two KoSIT
files: they were written by hand and they do not always say the same thing. A *limitation*
is a place where the way in — the syntax bindings, and the implementation of them that
built these documents — does not carry into the semantic model something the source
document holds, or carries something it should not. A *defect* of the reader would be the
third, and this run found none; had it found one, the fix would be in the code and this
ledger would show the pair after the fix.

The classification is data rather than narration. `pairs.json` beside this file records
every differing path with its cause and the kind of that cause, `ConformancePairsTest`
recomputes the paths from the checked-in documents and fails if the set has moved in either
direction,
and it recomputes both tables below from `pairs.json` and fails if a number here has
drifted from what the data says. A pair that becomes identical fails this test as loudly
as a pair that starts to differ, which is the point: an improvement has to be recorded
here before it counts.

## Summary

| Measure | Pairs |
|---|---:|
| `identical` — the two syntaxes arrive at the same semantic digest | 18 |
| `fixture` — every difference is a difference between the two KoSIT files | 22 |
| `limitation` — at least one difference is a limitation of the import path | 0 |
| `defect` — at least one difference is a defect of the mapper (none found) | 0 |
| `total` — business cases the suite carries in both syntaxes | 40 |

## The causes, by size

| Cause | Kind | Pairs | Differing paths |
|---|---|---:|---:|
| `zero-total` | fixture | 13 | 15 |
| `item-attribute-order` | fixture | 1 | 12 |
| `seller-identifiers` | fixture | 3 | 9 |
| `buyer-name-and-trading-name` | fixture | 4 | 8 |
| `price-base-quantity` | fixture | 4 | 8 |
| `order-reference-placeholder` | fixture | 7 | 7 |
| `payee-legal-registration` | fixture | 6 | 6 |
| `address-city-postcode-swapped` | fixture | 1 | 4 |
| `address-placeholder-wording` | fixture | 1 | 2 |
| `preceding-invoice-references` | fixture | 1 | 2 |
| `seller-contact-point` | fixture | 2 | 2 |
| `buyer-trading-name-only-ubl` | fixture | 1 | 1 |
| `item-description-typo` | fixture | 1 | 1 |
| `payment-means-text` | fixture | 1 | 1 |
| `seller-vat-identifier` | fixture | 1 | 1 |
| `vat-exemption-reason` | fixture | 1 | 1 |

No limitation remains: every one of the 80 differing paths is a difference between the two
KoSIT files. The two causes that stood here until this release are at the end of the
sections below.

## The note subject code, and why it is no longer a cause

Until this release the largest cause of this ledger by a wide margin was
`note-subject-code`: 28 pairs and 76 differing paths, and the one place in the corpus where
a value ESJ stored was **wrong** rather than merely absent. It is now corrected, so it
appears in neither table above. The record of what it was and what was done is kept here,
because the fix is a rule that reads content and a reader of this ledger is entitled to see
its justification.

**What the syntaxes do.** EN 16931-1 binds the note subject code BT-21 and the note BT-22
to one `cbc:Note` element in the UBL syntax, with the code as a prefix between two number
signs: `<cbc:Note>#AAC#Invoice Note Description</cbc:Note>`. That this is the binding is not
a habit of this test suite. The CEN validation artefacts for UBL (version 1.3.16) read the
code out of exactly that prefix: in `ubl/schematron/UBL/EN16931-UBL-model.sch` the parameter
`BR-CL-08` takes `substring-before(substring-after(., '#'), '#')`, requires it to be three
characters long and tests it against the UNTDID 4451 entries the standard admits, and the
rule that asserts it runs on the context `/ubl:Invoice/cbc:Note | /cn:CreditNote/cbc:Note`
(`abstract/EN16931-model.sch`, the rule over the `Note` context, whose definition is in
`EN16931-UBL-model.sch`). In CII the two are separate elements, `ram:SubjectCode` and
`ram:Content`, and nothing has to be read out of a text.

**What the stylesheet does.** The vendored UBL stylesheet implements a different spelling:
it recognizes a code only where it stands in a `cbc:Note` element of its own, made of
exactly three capital letters, immediately before the note it belongs to
(`ubl-invoice-xr.xsl`, the grouping key of the `BG-1` template; `ubl-creditnote-xr.xsl` has
the same template). It does not split the prefix form, and nothing in this corpus writes the
form it does recognize. The consequence, measured over the whole corpus before the fix, was
that not one of the 45 UBL instances yielded a single BT-21 value while 28 of the 41 CII
instances yielded 38, and that 39 of the 43 UBL BT-22 values, spread over 29 instances,
began with a `#XXX#` prefix: the stored text was the note with a syntax artefact glued in
front of it, not the note. Validation layer L2 cannot catch that and does not claim to —
BT-22 is free text, and a text value that starts with a number sign is a perfectly well
formed text value.

**What is done now.** `esj-xr` applies the named normalization
`XrNormalization.UBL_NOTE_SUBJECT_CODE` to a document it read as UBL: where a document level
invoice note matches `^#([A-Z]{3})#(.*)$` and the remainder is not empty, BT-21 becomes the
three characters and BT-22 the remainder. It is scoped exactly as the rule it follows is
scoped, and the scope is worth stating precisely:

- **Document level only.** BR-CL-08 runs on `/ubl:Invoice/cbc:Note` and
  `/cn:CreditNote/cbc:Note` and on no other context, and the semantic model gives the
  invoice line note BT-127 no subject code to split off in the first place. A line note is
  therefore left alone.
- **UBL only.** The prefix is a property of the UBL syntax binding. A document read as CII
  carries the code in an element of its own, and a document handed to `fromXr` is not
  attributed to a syntax at all, so neither is normalized.
- **The shape, not the code list.** The three characters are taken as the code without being
  tested against UNTDID 4451. Code list membership is a business rule, which the
  specification, section 9.4 places in a separate layer rather than in L1 to L3, and the
  registry records the name of the list for BT-21 rather than its entries; testing it here
  would put one business rule inside the importer and leave the other twenty-one outside.
  This reads the document the way BR-CL-08 reads it — that rule also takes whatever stands
  between the first two number signs as the code and only then asks whether it is a valid
  one. The three codes this corpus produces, `AAC`, `ADU` and `REG`, are all entries
  BR-CL-08 admits.
- **Never at the price of a value.** A note that is nothing but a prefix would leave BT-22
  empty, which the specification, section 6.1 cannot represent, so it is stored whole. A
  BG-1 instance in which the stylesheet already found a BT-21 keeps that code and its text
  unchanged. And a note that would not fit the limits the importer writes within is stored
  whole rather than stripped of a code that then goes missing.

The normalization is switchable, and an importer constructed without it reproduces exactly
the numbers of the paragraph above.

## The supporting document type code, and why it is no longer a cause

*Was a limitation.* Six pairs and six differing paths, all of them `/BG-24/*/BT-122`.

An invoiced object identifier is written in UBL as a `cac:AdditionalDocumentReference` whose
`cbc:DocumentTypeCode` is `130`, and in CII as a `ram:AdditionalReferencedDocument` whose
`ram:TypeCode` is `130`. On the CII side that element was already kept out of BG-24, the
group of supporting documents, because the table binds BT-122 only where the type code is
`916`; on the UBL side BT-122 was bound to the `cbc:ID` of every
`cac:AdditionalDocumentReference` and nothing excluded it, so the UBL side grew one
supporting document more than the CII side, carrying the invoiced object identifier a second
time.

Since this release the two UBL tables carry the exclusion as a correction, with the CEN
validation artefact of `packs/` as its reason: that artefact reads the supporting document
group from `cac:AdditionalDocumentReference[cbc:DocumentTypeCode != '130' or
not(cbc:DocumentTypeCode)]`. The asymmetry between the two syntaxes is gone, and
`../readers.md` records the same change from the other side.

## The invoice line object identifier scheme, and why it is no longer a cause

Until this release a second limitation stood beside the one above: `identifier-with-scheme`,
six pairs and six differing paths, all of them `/BG-25/*/BT-128`.

In CII the scheme of the invoice line object identifier is a sibling element,
`ram:ReferenceTypeCode`, and the vendored stylesheet passes it into the shared template
`identifier-with-scheme`. That template writes the scheme only inside
`<xsl:if test="@schemeID">` (`common-xr.xsl`), that is, only when the matched element
carries a `schemeID` attribute of its own — which `ram:IssuerAssignedID` never does. The
scheme was dropped before the mapper ever saw it, while on the UBL side the scheme is an
attribute and survived, so the two syntaxes of six pairs met with one value differing.

The binding table of CII states the fact plainly — the scheme of BT-128 is
`../ram:ReferenceTypeCode`, flagged `scheme-as-sibling-element` — and the streaming reader
of `esj-bindings` reads it. That reader writes the documents under `conformance/esj/` since
this release, so the CII side of those six pairs now carries the scheme `ABZ` the document
holds and the difference is gone. `../readers.md` records the same change from the other
side. The XSLT path still drops it, which is why it is a difference between the two readers
and no longer a difference between the two syntaxes.

### Fixture causes

Each of the following is a difference between the two KoSIT files. The values are quoted
in the pair tables below.

- **order-reference-placeholder** — UBL requires `cbc:ID` inside `cac:OrderReference`, so
  an instance that wants to carry only the sales order reference BT-14 must write
  something as BT-13; the suite writes `Dummywert`. The CII instances carry
  `ram:SellerOrderReferencedDocument` alone and no BT-13.
- **zero-total** — one side writes a document total explicitly as zero and the other side
  leaves the element out: `ram:RoundingAmount`, `ram:AllowanceTotalAmount`,
  `ram:ChargeTotalAmount` and `ram:TotalPrepaidAmount` in the CII files against an absent
  `cbc:PayableRoundingAmount`, `cbc:AllowanceTotalAmount`, `cbc:ChargeTotalAmount` and
  `cbc:PrepaidAmount` in the UBL files, and once the other way round for BT-110, where the
  UBL file writes `cbc:TaxAmount` as `0` and the CII file writes no `ram:TaxTotalAmount`.
- **seller-identifiers** — the CII files give the seller three identifiers (two
  `ram:ID` and one `ram:GlobalID` with scheme `0088`), the UBL files one
  `cac:PartyIdentification/cbc:ID` with scheme `0088`. Because the occurrence index is
  positional, the single UBL identifier meets the first CII one and the other two have no
  counterpart.
- **payee-legal-registration** — the CII files carry
  `ram:PayeeTradeParty/ram:SpecifiedLegalOrganization/ram:ID` with scheme `0204`; the UBL
  files give the payee no `cac:PartyLegalEntity`.
- **buyer-name-and-trading-name** — the UBL binding reads BT-44 from
  `cac:PartyLegalEntity/cbc:RegistrationName` and BT-45 from `cac:PartyName/cbc:Name`, the
  CII binding reads BT-44 from `ram:Name` and BT-45 from `ram:TradingBusinessName`. The two
  files fill those slots with the placeholders the other way round, so the two terms trade
  places.
- **buyer-trading-name-only-ubl** — the UBL file repeats `[Buyer name]` in both
  `cac:PartyName/cbc:Name` and `cac:PartyLegalEntity/cbc:RegistrationName`, so BT-45
  exists; the CII file carries `ram:Name` alone.
- **seller-contact-point** — BT-41 comes from `cac:Contact/cbc:Name` in UBL and from either
  `ram:PersonName` or `ram:DepartmentName` in CII. The UBL file names a person, the CII
  file names a department.
- **price-base-quantity** — the UBL file carries `cbc:BaseQuantity` with its unit code on a
  line; the CII file gives that line a `ram:NetPriceProductTradePrice` without
  `ram:BasisQuantity`.
- **preceding-invoice-references** — the UBL file carries two `cac:BillingReference`, the
  CII file one `ram:InvoiceReferencedDocument`.
- **seller-vat-identifier**, **payment-means-text**, **vat-exemption-reason** — three
  places where the two files of one business case simply carry different content.
- **address-placeholder-wording** — the UBL file writes `[Seller street]` and
  `[Seller city]` where the CII file writes `[Street]` and `[City]`.
- **address-city-postcode-swapped** — the UBL file puts the postcode in `cbc:CityName` and
  the town in `cbc:PostalZone`, for the seller and for the buyer; the CII file has them the
  right way round.
- **item-description-typo** — `Gruppe von Fahrzeug XY der Klasse Z` in the UBL file against
  `Gruppe von Fahrzeugde XY der Klasse Z` in the CII file.
- **item-attribute-order** — both files carry the same six item attributes, but the UBL
  file lists the one named `cva` first and the CII file lists it last. The occurrence index
  of ESJ is positional and the standard gives the instances of BG-32 no order, so a
  rotation shows up as a difference at every index.

## The value added tax point date code, and why it is no longer a cause

*Was a fixture difference.* One pair, one path, `/BT-8`.

`cbc:DescriptionCode` is `3` in the UBL file and `ram:DueDateTypeCode` is `5` in the CII
file, and both are right: EN 16931-1 gives BT-8 a restriction of UNTDID 2005 and the cross
industry invoice states the same three events in UNTDID 2475, which the validation artefacts
of `packs/` spell out — `3`, `35` and `432` on the one side, `5`, `29` and `72` on the other.
It was recorded as a difference between the two files because the readers took the content
as it stood. Since this release the binding table marks the element with the flag
`code-list-2475` and the streaming reader translates it, so both syntaxes arrive at the code
of the standard and the writers translate back.

## Limitations the pairs do not show

A difference between two files is only visible where the two files differ. These four
properties are recorded here because no pair of this corpus makes them visible, and a
reader of the ledger would otherwise take the absence of a difference for the absence of a
question.

Three of the four belong to the XSLT path of `esj-xr` rather than to the documents this
ledger compares, which the streaming reader of `esj-bindings` writes. They are kept because
the XSLT path is the oracle this repository checks that reader against, and because the
first of them explains why two sources that differ meet on the same ESJ value. Where the
two readers behave differently, the sentence says so; `../readers.md` measures the
difference over the whole corpus.

**A supplementary component the standard does not give a term is dropped.** This is the
only one of the four that actually occurs in this corpus, 89 times in the XSLT path, and it
is invisible in the pairs because the two syntaxes drop at disjoint terms: CII at BT-31 (39 times), BT-32
(18), BT-48 (16) and BT-63 (11), UBL at BT-90 (5). Every drop inside a pair is therefore
one-sided — 87 of them across 39 of the 40 pairs, `technical-cases/cius/01.05_minimal_test`
being the only untouched one — and every one of the 18 identical pairs is among them. In
`business-cases/standard/02.05a-INVOICE` the CII source writes
`<ram:ID schemeID="VA">ATU123456789` and the UBL source
`<cbc:CompanyID>ATU123456789</cbc:CompanyID>`; the two meet on the same ESJ value because
the only thing that differed at that path was discarded before the comparison. On the
merits the drop is harmless: the 89 codes are 66 × `VA`, 18 × `FC` and 5 × `SEPA`, each
redundant with the term it sits on, and EN 16931-1 gives those terms no scheme component to
hold them. These are the only notes the XSLT path produces over the whole corpus;
`conformance/README.md` item 6 carries the same counts. The streaming reader reaches the
same values without the detour: it reads a component only where the binding table names
one, and a table names one only where the standard has it, so it reports none of these
drops and loses nothing by it.

**BT-149 and BT-150 come from the gross price when a line carries both.** In CII a line
may carry a basis quantity under `ram:NetPriceProductTradePrice` and another under
`ram:GrossPriceProductTradePrice`. Where both are there, `cii-xr.xsl` reads BT-149 and
BT-150 from the gross one; where only one is there, it reads that one. The binding table of
CII names the gross location first and the net one as its alternative, so the streaming
reader answers the same way, and a document that writes the quantity under both prices
produces one value rather than two. In UBL there is one
location, `cac:Price/cbc:BaseQuantity`, so the two syntaxes do not even answer the same
question. Over the corpus: two lines of `business-cases/standard/01.20a-INVOICE_uncefact.xml`
carry both, and the two agree there (`1.0000 C62` and `1.0000 TNE`), so no value checked in
here is wrong; six instances carry a gross basis quantity and no net one and are therefore
read from the gross price — `business-cases/standard/03.07a`, `technical-cases/cius/01.01`
to `01.04` and `technical-cases/cvd/02.01a`, all `_uncefact.xml`, each one value at
`/BG-25/0/BG-29/BT-149` and `/BG-25/0/BG-29/BT-150`. Whether a later release should prefer
the net basis quantity, which is the one the price of the line is stated in, is open.

**A date that is no day of the calendar costs the whole document.** The `date` template of
`common-xr.xsl` checks that the month is 1 to 12 and the day 1 to 31 and then builds the
date. Eight digits that pass those checks and still name no day — `20160230` — make the
stylesheet fail with a dynamic error, which `XrTransformer` turns into an
`XrFormatException`. The caller gets no document at all rather than the rest of the invoice
and one note about the one value. Every date of this corpus is a real one, so nothing here
shows it.

**A CII date with another format qualifier disappears silently.** The `cii-xr.xsl`
templates match `udt:DateTimeString[@format = '102']`, the eight-digit calendar date. A
date written with any other qualifier — `610` for a month, say — produces no XR element at
all, so the mapper never sees it and the import report says nothing about it: the document
simply loses that date. All 223 date qualifiers of this corpus are `102`: 214 on
`udt:DateTimeString` and 9 on `udt:DateString`, counted as attributes of the parsed
documents rather than as occurrences of the string, one of which sits inside an XML comment
in `business-cases/extension/04.05a-INVOICE_uncefact.xml` and is no attribute at all.

## The pairs

### business-cases/standard/01.01a-INVOICE

The semantic digests are equal: every value the importer read from the UBL
instance it read from the CII instance as well, at the same path and with the same
content.

### business-cases/standard/01.02a-INVOICE

| Path | UBL | CII | Cause |
|---|---|---|---|
| `/BT-13` | documentReference `Dummywert` | *absent* | [fixture: order-reference-placeholder](#fixture-causes) |

### business-cases/standard/01.03a-INVOICE

The semantic digests are equal: every value the importer read from the UBL
instance it read from the CII instance as well, at the same path and with the same
content.

### business-cases/standard/01.04a-INVOICE

The semantic digests are equal: every value the importer read from the UBL
instance it read from the CII instance as well, at the same path and with the same
content.

### business-cases/standard/01.05a-INVOICE

The semantic digests are equal: every value the importer read from the UBL
instance it read from the CII instance as well, at the same path and with the same
content.

### business-cases/standard/01.06a-INVOICE

The semantic digests are equal: every value the importer read from the UBL
instance it read from the CII instance as well, at the same path and with the same
content.

### business-cases/standard/01.07a-INVOICE

The semantic digests are equal: every value the importer read from the UBL
instance it read from the CII instance as well, at the same path and with the same
content.

### business-cases/standard/01.08a-INVOICE

| Path | UBL | CII | Cause |
|---|---|---|---|
| `/BT-13` | documentReference `Dummywert` | *absent* | [fixture: order-reference-placeholder](#fixture-causes) |

### business-cases/standard/01.09a-INVOICE

| Path | UBL | CII | Cause |
|---|---|---|---|
| `/BT-13` | documentReference `Dummywert` | *absent* | [fixture: order-reference-placeholder](#fixture-causes) |

### business-cases/standard/01.10a-INVOICE

| Path | UBL | CII | Cause |
|---|---|---|---|
| `/BT-13` | documentReference `Dummywert` | *absent* | [fixture: order-reference-placeholder](#fixture-causes) |

### business-cases/standard/01.11a-INVOICE

| Path | UBL | CII | Cause |
|---|---|---|---|
| `/BT-13` | documentReference `Dummywert` | *absent* | [fixture: order-reference-placeholder](#fixture-causes) |

### business-cases/standard/01.12a-INVOICE

| Path | UBL | CII | Cause |
|---|---|---|---|
| `/BT-13` | documentReference `Dummywert` | *absent* | [fixture: order-reference-placeholder](#fixture-causes) |

### business-cases/standard/01.13a-INVOICE

| Path | UBL | CII | Cause |
|---|---|---|---|
| `/BT-13` | documentReference `Dummywert` | *absent* | [fixture: order-reference-placeholder](#fixture-causes) |

### business-cases/standard/01.14a-INVOICE

The semantic digests are equal: every value the importer read from the UBL
instance it read from the CII instance as well, at the same path and with the same
content.

### business-cases/standard/01.15a-INVOICE

The semantic digests are equal: every value the importer read from the UBL
instance it read from the CII instance as well, at the same path and with the same
content.

### business-cases/standard/01.17a-INVOICE

The semantic digests are equal: every value the importer read from the UBL
instance it read from the CII instance as well, at the same path and with the same
content.

### business-cases/standard/01.18a-INVOICE

The semantic digests are equal: every value the importer read from the UBL
instance it read from the CII instance as well, at the same path and with the same
content.

### business-cases/standard/01.19a-INVOICE

The semantic digests are equal: every value the importer read from the UBL
instance it read from the CII instance as well, at the same path and with the same
content.

### business-cases/standard/01.20a-INVOICE

| Path | UBL | CII | Cause |
|---|---|---|---|
| `/BG-4/BG-5/BT-35` | text `[Seller street]` | text `[Street]` | [fixture: address-placeholder-wording](#fixture-causes) |
| `/BG-4/BG-5/BT-37` | text `[Seller city]` | text `[City]` | [fixture: address-placeholder-wording](#fixture-causes) |
| `/BG-22/BT-107` | *absent* | amount `0` | [fixture: zero-total](#fixture-causes) |
| `/BG-22/BT-108` | *absent* | amount `0` | [fixture: zero-total](#fixture-causes) |
| `/BG-22/BT-113` | *absent* | amount `0` | [fixture: zero-total](#fixture-causes) |

### business-cases/standard/01.21a-INVOICE

| Path | UBL | CII | Cause |
|---|---|---|---|
| `/BG-4/BT-31` | identifier `DE 123456789` | identifier `DE152338654` | [fixture: seller-vat-identifier](#fixture-causes) |
| `/BG-16/BT-82` | text `Information` | text `Rechnung` | [fixture: payment-means-text](#fixture-causes) |
| `/BG-22/BT-107` | *absent* | amount `0` | [fixture: zero-total](#fixture-causes) |
| `/BG-23/0/BT-120` | text `Umkehrung der Steuerschuldnerschaft` | text `als gemeinnützig anerkannt` | [fixture: vat-exemption-reason](#fixture-causes) |

### business-cases/standard/02.01a-INVOICE

| Path | UBL | CII | Cause |
|---|---|---|---|
| `/BG-22/BT-114` | *absent* | amount `0` | [fixture: zero-total](#fixture-causes) |

### business-cases/standard/02.02a-INVOICE

| Path | UBL | CII | Cause |
|---|---|---|---|
| `/BG-22/BT-114` | *absent* | amount `0` | [fixture: zero-total](#fixture-causes) |

### business-cases/standard/02.03a-INVOICE

| Path | UBL | CII | Cause |
|---|---|---|---|
| `/BG-22/BT-114` | *absent* | amount `0` | [fixture: zero-total](#fixture-causes) |

### business-cases/standard/02.04a-INVOICE

| Path | UBL | CII | Cause |
|---|---|---|---|
| `/BG-22/BT-114` | *absent* | amount `0` | [fixture: zero-total](#fixture-causes) |

### business-cases/standard/02.05a-INVOICE

The semantic digests are equal: every value the importer read from the UBL
instance it read from the CII instance as well, at the same path and with the same
content.

### business-cases/standard/02.06a-INVOICE

The semantic digests are equal: every value the importer read from the UBL
instance it read from the CII instance as well, at the same path and with the same
content.

### business-cases/standard/03.01a-INVOICE

| Path | UBL | CII | Cause |
|---|---|---|---|
| `/BG-7/BT-45` | text `[Buyer name]` | *absent* | [fixture: buyer-trading-name-only-ubl](#fixture-causes) |

### business-cases/standard/03.02a-INVOICE

The semantic digests are equal: every value the importer read from the UBL
instance it read from the CII instance as well, at the same path and with the same
content.

### business-cases/standard/03.03a-INVOICE

The semantic digests are equal: every value the importer read from the UBL
instance it read from the CII instance as well, at the same path and with the same
content.

### business-cases/standard/03.04a-INVOICE

The semantic digests are equal: every value the importer read from the UBL
instance it read from the CII instance as well, at the same path and with the same
content.

### business-cases/standard/03.05a-INVOICE

The semantic digests are equal: every value the importer read from the UBL
instance it read from the CII instance as well, at the same path and with the same
content.

### business-cases/standard/03.06a-INVOICE

| Path | UBL | CII | Cause |
|---|---|---|---|
| `/BG-4/BG-5/BT-37` | text `12345` | text `Testhausen` | [fixture: address-city-postcode-swapped](#fixture-causes) |
| `/BG-4/BG-5/BT-38` | text `Testhausen` | text `12345` | [fixture: address-city-postcode-swapped](#fixture-causes) |
| `/BG-7/BG-8/BT-52` | text `12345` | text `Testhausen` | [fixture: address-city-postcode-swapped](#fixture-causes) |
| `/BG-7/BG-8/BT-53` | text `Testhausen` | text `12345` | [fixture: address-city-postcode-swapped](#fixture-causes) |

### business-cases/standard/03.07a-INVOICE

| Path | UBL | CII | Cause |
|---|---|---|---|
| `/BG-4/BT-29/0` | identifier `987654321`, scheme `0088` | identifier `9876543217894897438` | [fixture: seller-identifiers](#fixture-causes) |
| `/BG-4/BT-29/1` | *absent* | identifier `748387437438` | [fixture: seller-identifiers](#fixture-causes) |
| `/BG-4/BT-29/2` | *absent* | identifier `987654321`, scheme `0088` | [fixture: seller-identifiers](#fixture-causes) |
| `/BG-10/BT-61` | *absent* | identifier `90000000-03083-72`, scheme `0204` | [fixture: payee-legal-registration](#fixture-causes) |
| `/BG-22/BT-114` | *absent* | amount `0` | [fixture: zero-total](#fixture-causes) |

### technical-cases/cius/01.01_comprehensive_test

| Path | UBL | CII | Cause |
|---|---|---|---|
| `/BG-3/1/BT-25` | documentReference `PIR0987654321` | *absent* | [fixture: preceding-invoice-references](#fixture-causes) |
| `/BG-3/1/BT-26` | date `2018-03-05` | *absent* | [fixture: preceding-invoice-references](#fixture-causes) |
| `/BG-4/BT-29/0` | identifier `987654321`, scheme `0088` | identifier `9876543217894897438` | [fixture: seller-identifiers](#fixture-causes) |
| `/BG-4/BT-29/1` | *absent* | identifier `748387437438` | [fixture: seller-identifiers](#fixture-causes) |
| `/BG-4/BT-29/2` | *absent* | identifier `987654321`, scheme `0088` | [fixture: seller-identifiers](#fixture-causes) |
| `/BG-7/BT-44` | text `[Buyer trading name]` | text `[Buyer name]` | [fixture: buyer-name-and-trading-name](#fixture-causes) |
| `/BG-7/BT-45` | text `[Buyer name]` | text `[Buyer trading name]` | [fixture: buyer-name-and-trading-name](#fixture-causes) |
| `/BG-10/BT-61` | *absent* | identifier `90000000-03083-72`, scheme `0204` | [fixture: payee-legal-registration](#fixture-causes) |
| `/BG-22/BT-114` | *absent* | amount `0` | [fixture: zero-total](#fixture-causes) |
| `/BG-25/1/BG-29/BT-149` | quantity `1` | *absent* | [fixture: price-base-quantity](#fixture-causes) |
| `/BG-25/1/BG-29/BT-150` | code `XPP` | *absent* | [fixture: price-base-quantity](#fixture-causes) |

### technical-cases/cius/01.02_comprehensive_test

| Path | UBL | CII | Cause |
|---|---|---|---|
| `/BG-7/BT-44` | text `[Buyer trading name]` | text `[Buyer name]` | [fixture: buyer-name-and-trading-name](#fixture-causes) |
| `/BG-7/BT-45` | text `[Buyer name]` | text `[Buyer trading name]` | [fixture: buyer-name-and-trading-name](#fixture-causes) |
| `/BG-10/BT-61` | *absent* | identifier `90000000-03083-72`, scheme `0204` | [fixture: payee-legal-registration](#fixture-causes) |
| `/BG-22/BT-114` | *absent* | amount `0` | [fixture: zero-total](#fixture-causes) |
| `/BG-25/1/BG-29/BT-149` | quantity `1` | *absent* | [fixture: price-base-quantity](#fixture-causes) |
| `/BG-25/1/BG-29/BT-150` | code `XPP` | *absent* | [fixture: price-base-quantity](#fixture-causes) |

### technical-cases/cius/01.03_comprehensive_test

| Path | UBL | CII | Cause |
|---|---|---|---|
| `/BG-4/BG-6/BT-41` | text `Tim Tester` | text `Testorganisation` | [fixture: seller-contact-point](#fixture-causes) |
| `/BG-7/BT-44` | text `[Buyer trading name]` | text `[Buyer name]` | [fixture: buyer-name-and-trading-name](#fixture-causes) |
| `/BG-7/BT-45` | text `[Buyer name]` | text `[Buyer trading name]` | [fixture: buyer-name-and-trading-name](#fixture-causes) |
| `/BG-10/BT-61` | *absent* | identifier `90000000-03083-72`, scheme `0204` | [fixture: payee-legal-registration](#fixture-causes) |
| `/BG-22/BT-114` | *absent* | amount `0` | [fixture: zero-total](#fixture-causes) |
| `/BG-25/1/BG-29/BT-149` | quantity `1` | *absent* | [fixture: price-base-quantity](#fixture-causes) |
| `/BG-25/1/BG-29/BT-150` | code `XPP` | *absent* | [fixture: price-base-quantity](#fixture-causes) |

### technical-cases/cius/01.04_comprehensive_test

| Path | UBL | CII | Cause |
|---|---|---|---|
| `/BG-4/BG-6/BT-41` | text `Tim Tester` | text `Testorganisation` | [fixture: seller-contact-point](#fixture-causes) |
| `/BG-7/BT-44` | text `[Buyer trading name]` | text `[Buyer name]` | [fixture: buyer-name-and-trading-name](#fixture-causes) |
| `/BG-7/BT-45` | text `[Buyer name]` | text `[Buyer trading name]` | [fixture: buyer-name-and-trading-name](#fixture-causes) |
| `/BG-10/BT-61` | *absent* | identifier `90000000-03083-72`, scheme `0204` | [fixture: payee-legal-registration](#fixture-causes) |
| `/BG-22/BT-114` | *absent* | amount `0` | [fixture: zero-total](#fixture-causes) |
| `/BG-25/1/BG-29/BT-149` | quantity `1` | *absent* | [fixture: price-base-quantity](#fixture-causes) |
| `/BG-25/1/BG-29/BT-150` | code `XPP` | *absent* | [fixture: price-base-quantity](#fixture-causes) |

### technical-cases/cius/01.05_minimal_test

| Path | UBL | CII | Cause |
|---|---|---|---|
| `/BG-22/BT-110` | amount `0` | *absent* | [fixture: zero-total](#fixture-causes) |

### technical-cases/cius/01.06_minimal_test

The semantic digests are equal: every value the importer read from the UBL
instance it read from the CII instance as well, at the same path and with the same
content.

### technical-cases/cvd/02.01a-cvd_INVOICE

| Path | UBL | CII | Cause |
|---|---|---|---|
| `/BG-4/BT-29/0` | identifier `987654321`, scheme `0088` | identifier `9876543217894897438` | [fixture: seller-identifiers](#fixture-causes) |
| `/BG-4/BT-29/1` | *absent* | identifier `748387437438` | [fixture: seller-identifiers](#fixture-causes) |
| `/BG-4/BT-29/2` | *absent* | identifier `987654321`, scheme `0088` | [fixture: seller-identifiers](#fixture-causes) |
| `/BG-10/BT-61` | *absent* | identifier `90000000-03083-72`, scheme `0204` | [fixture: payee-legal-registration](#fixture-causes) |
| `/BG-22/BT-114` | *absent* | amount `0` | [fixture: zero-total](#fixture-causes) |
| `/BG-25/0/BG-31/BT-154` | text `Gruppe von Fahrzeug XY der Klasse Z` | text `Gruppe von Fahrzeugde XY der Klasse Z` | [fixture: item-description-typo](#fixture-causes) |
| `/BG-25/0/BG-31/BG-32/0/BT-160` | text `cva` | text `Leistung` | [fixture: item-attribute-order](#fixture-causes) |
| `/BG-25/0/BG-31/BG-32/0/BT-161` | text `clean` | text `[Leistung Value]` | [fixture: item-attribute-order](#fixture-causes) |
| `/BG-25/0/BG-31/BG-32/1/BT-160` | text `Leistung` | text `Länge` | [fixture: item-attribute-order](#fixture-causes) |
| `/BG-25/0/BG-31/BG-32/1/BT-161` | text `[Leistung Value]` | text `[Länge Value]` | [fixture: item-attribute-order](#fixture-causes) |
| `/BG-25/0/BG-31/BG-32/2/BT-160` | text `Länge` | text `Starthöchstmasse` | [fixture: item-attribute-order](#fixture-causes) |
| `/BG-25/0/BG-31/BG-32/2/BT-161` | text `[Länge Value]` | text `[Starthöchstmasse Value]` | [fixture: item-attribute-order](#fixture-causes) |
| `/BG-25/0/BG-31/BG-32/3/BT-160` | text `Starthöchstmasse` | text `Laufleistung` | [fixture: item-attribute-order](#fixture-causes) |
| `/BG-25/0/BG-31/BG-32/3/BT-161` | text `[Starthöchstmasse Value]` | text `[Laufleistung Value]` | [fixture: item-attribute-order](#fixture-causes) |
| `/BG-25/0/BG-31/BG-32/4/BT-160` | text `Laufleistung` | text `Zeitpunkt Inbetriebnahme` | [fixture: item-attribute-order](#fixture-causes) |
| `/BG-25/0/BG-31/BG-32/4/BT-161` | text `[Laufleistung Value]` | text `[Zeitpunkt Inbetriebnahme Value]` | [fixture: item-attribute-order](#fixture-causes) |
| `/BG-25/0/BG-31/BG-32/5/BT-160` | text `Zeitpunkt Inbetriebnahme` | text `cva` | [fixture: item-attribute-order](#fixture-causes) |
| `/BG-25/0/BG-31/BG-32/5/BT-161` | text `[Zeitpunkt Inbetriebnahme Value]` | text `clean` | [fixture: item-attribute-order](#fixture-causes) |
