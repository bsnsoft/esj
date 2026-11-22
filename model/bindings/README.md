# `model/bindings/` — where each business term lives in each XML syntax

The registry in `model/` says *which* business terms exist. A binding table says *where*
one of them sits in one XML syntax, and nothing else: the path to its element, the paths of
its supplementary components, and the flags that warn a reader or a writer where the two do
not line up cleanly. A table is data. The streaming reader and the CII and UBL writers of
`esj-bindings` run on these tables; `esj-xr` imports through the KoSIT stylesheets.

| File | Syntax |
|---|---|
| `ubl-invoice.json` | OASIS UBL 2.1 Invoice |
| `ubl-creditnote.json` | OASIS UBL 2.1 Credit Note |
| `cii.json` | UN/CEFACT Cross Industry Invoice D16B |
| `binding.schema.json` | JSON Schema 2020-12 for the three files above |
| `tools/semox_to_bindings.py` | Writes the three files from a SeMoX model |
| `tools/crosscheck_bindings.py` | Writes `conformance/bindings/crosscheck.md` |

## What a binding is

An entry binds one term of one registry to one place in one syntax:

```json
{
  "id": "BT-131",
  "kind": "BT",
  "bound": true,
  "xpath": "/Invoice/cac:InvoiceLine/cbc:LineExtensionAmount"
}
```

| Member | Meaning |
|---|---|
| `id` | The term identifier, exactly as the registry writes it. |
| `kind` | `BT` for a term that carries a value, `BG` for a group. |
| `bound` | Whether this syntax has a place for the term at all. |
| `xpath` | Absolute path from the document element, with prefixes of `namespaces` and with the predicates the source states. Absent when `bound` is false. |
| `alternatives` | Further paths the term may take. A document uses one of them. |
| `instanceElement` | Groups only: the element that one instance of the group is, without its predicate. |
| `instancePredicates` | Groups only: the conditions that tell this group's instances from another group's in the same element — the allowance/charge indicator, for example. They hold together. |
| `components` | The supplementary components, each with `role`, a path relative to `anchor`, and the `anchor` it is relative to. |
| `flags` | Everything a consumer has to know beyond the path. |

A component's path is relative because a reader uses it that way: standing on the value
element, it evaluates `@schemeID` there. Where the syntax does not put the component on the
value, the relative path steps out. CII, for example, carries the scheme of BT-18 and BT-128
in a sibling element. Such an entry also carries the flag `scheme-as-sibling-element`, so you
need not spot the `..` to find out.

The `anchor` matters where a term has alternatives. The scheme of BT-29 in CII belongs to the
`ram:GlobalID` alternative and not to the `ram:ID` one, and `anchor` says which.

The file header carries the namespace map, the source the facts came from, the counts you can
check what you read against, and one line per flag the file uses. The empty key of
`namespaces` is the default namespace, the one an unprefixed step is in. XPath 1.0 cannot say
that, so a consumer of these tables arranges it itself.

The tables record no cardinality of the syntax, because the source states none in its binding
section. The registry already holds the cardinality in the *semantic* model. What the XSD of a
syntax permits is a fact of that XSD; where the two differ, the source marks it with a `CAR-*`
flag.

## The flags

A flag in upper case is a note code of the source model, carried through unchanged. A flag
in lower case is one this project derives from the bound paths.

**The source does not define the upper case codes.** The SeMoX model writes them without
saying what they mean. Their normative definitions belong to the CEN syntax binding technical
specifications, which this project does not hold and therefore does not restate. The line
each code gets below says what its entries have in common in the release the tables were
generated from; `conformance/bindings/crosscheck.md` gives the counts. Treat a code as a
warning to look at the entry, not as a statement of the standard.

| Flag | What it means here | UBL Inv | UBL CN | CII |
|---|---|---:|---:|---:|
| `CAR-2` | The semantic model requires the term where the syntax leaves the element optional. | 36 | 32 | 37 |
| `CAR-3` | The syntax lets the element repeat where the semantic model allows the term only once. | 57 | 57 | 46 |
| `CAR-4` | The syntax bounds the element more narrowly than the semantic model bounds the term. | 0 | 0 | 1 |
| `SEM-2` | The syntax element is semantically wider than the term; a predicate or a convention picks out the occurrence that carries it. | 23 | 23 | 1 |
| `SEM-3` | The syntax element means something else in its own syntax, so the binding is a convention rather than a like-for-like match. | 1 | 1 | 0 |
| `STR-2` | The syntax nests the element inside a group that the semantic model places it outside of. | 0 | 0 | 2 |
| `STR-3` | No element of the syntax corresponds one to one to the term; it is reached through an element that stands for something else, or through more than one. | 0 | 0 | 7 |
| `STR-4` | One syntax element serves several terms or groups, and an indicator element or an attribute value tells them apart. | 0 | 0 | 6 |
| `STR-5` | A series of like terms of the semantic model is spread over differently named syntax elements. | 8 | 8 | 2 |
| `SYN-1` | The syntax offers two elements for the value, and which one carries it depends on whether an identification scheme can be given. | 0 | 0 | 4 |
| `SYN-2` | The lexical form in the syntax is not the semantic value written plainly: it is prefixed, formatted or otherwise encoded. | 5 | 3 | 3 |
| `code-list-2475` | The bound element states the code in UNTDID 2475, the list this syntax gives it, while the term's code list is UNTDID 2005; a reader and a writer translate between the two. | 0 | 0 | 1 |
| `date-format-102` | The bound element is a CII date string whose format attribute is 102, so the value is written as ccyymmdd rather than as an ISO date. | 0 | 0 | 9 |
| `extension-not-bound` | The extension that defines this term states a binding for other syntaxes only, so this table has no XPath for it. | 0 | 4 | 48 |
| `not-represented` | The source model states that this syntax has no representation for the term, so the entry carries no XPath. | 2 | 2 | 0 |
| `scheme-as-sibling-element` | The identification scheme is carried by a sibling element of the value rather than by an attribute of it. | 0 | 0 | 2 |
| `subject-code-prefix` | The subject code is written into the text value as a `#CODE#` prefix instead of into an element of its own. | 1 | 1 | 0 |

The counts cover the core terms and the extension terms of one table together. A test of
`esj-core` checks that this table and the files still agree.

Three flags decide whether a round trip works at all:

- **`subject-code-prefix`**: UBL has one `cbc:Note` per invoice note and no element for the
  note subject code. BT-21 and BT-22 share it, and the code is written as `#AAI#the text`.
  CII has `ram:SubjectCode` beside `ram:Content` and needs no such trick, so only the UBL
  tables carry the flag.
- **`date-format-102`**: every CII date is an element whose `@format` says how to read its
  content, and the core model only ever uses `102`, the eight digit form. A reader that takes
  the text as an ISO date gets a wrong date, silently.
- **`not-represented`**: the UBL bindings state that BG-1 (INVOICE NOTE) and BG-2 (PROCESS
  CONTROL) have no representation of their own. The terms inside them are bound, the groups
  are not. CII binds both. A writer reads the entry, sees `"bound": false` and writes the
  children where their own entries say.

## Corrections

Where the source states something about a syntax that the syntax does not do, the generator
departs from it on purpose. It writes what it changed, what it changed it from and why into
the `corrections` member of the table. A later release of the source shows up as a diff there.

Thirty-seven corrections stand today, and all but nine of them are one shape: a term or a
group bound to an element that the syntax also uses for something else, without the condition
that tells the two apart. Such a binding stores a **wrong** value instead of losing one. Which
value it stores depends on the order in which the document writes its elements.

- **`ubl-invoice.json` and `ubl-creditnote.json`, 2 each**: the source binds the bank
  assigned creditor identifier BT-90 to a party identifier of the supplier party and of the
  payee party, with the predicate `@schemeID = 'SEPA'`. It binds the party identifier of the
  same party (BT-29 for the seller, BT-60 for the payee) to that very element without a
  predicate. One element then carries two terms, and a consumer that follows the table stores
  the creditor identifier a second time as a party identifier. The added condition
  `[not(@schemeID = 'SEPA')]` leaves that occurrence to BT-90 and takes every other one.
- **`ubl-invoice.json`, 5, and `ubl-creditnote.json`, 4**: the source binds these terms
  without a condition on the tax scheme: the buyer VAT identifier BT-48, the tax
  representative's BT-63, the VAT category code of a tax breakdown BT-118 and of a line item
  BT-151, and, in the invoice table, the copy of BT-151 that the sub invoice line of the
  extension reuses. It binds the seller's BT-31 and BT-32 in the same table with one. A German
  buyer that states both a VAT identifier and a tax number is an ordinary document; without the
  condition, the element the document writes first wins. The CEN validation artefact of
  `packs/` and the KoSIT visualization stylesheet both write the added condition
  `[cac:TaxScheme/cbc:ID = 'VAT']` on these elements.
- **`ubl-creditnote.json`, BT-11**: UBL 2.1 gives the credit note no `cac:ProjectReference`.
  The source binds the project reference to `cac:AdditionalDocumentReference/cbc:ID` without a
  condition, the very element it also binds the supporting document reference BT-122 to. Every
  supporting document of a credit note was therefore read as a project reference the document
  does not state. The KoSIT stylesheet reads this term with the added condition
  `[cbc:DocumentTypeCode = '50']`.
- **`ubl-invoice.json`, 5, and `ubl-creditnote.json`, 5**: UBL writes the invoiced object
  identifier BT-18 into `cac:AdditionalDocumentReference` with the document type code `130`.
  The credit note, which UBL 2.1 gives no `cac:ProjectReference`, writes the project reference
  BT-11 into the same element with the code `50`. The source states those conditions on those
  two terms and leaves the supporting document group BG-24 and its BT-122, BT-123, BT-124 and
  BT-125 without one. Every additional document reference of any kind then opens a supporting
  document group, and a reference standing before the supporting document moves that
  document's occurrence index. The CEN validation artefact of `packs/` reads this element with
  the document type code `130` excluded, and the KoSIT stylesheet excludes `50` from the
  credit note. The two conditions of the credit note hold together.
- **`ubl-creditnote.json`, BT-18**: the source discriminates the invoiced object identifier by
  the document type code `ATS`, which is not a code of UNTDID 1001. No conformant credit note
  matches the XPath, and the term is lost without a word. The UBL Invoice and CII bindings of
  the same source, the stylesheet and the CEN artefact all use `130`, and the correction writes
  it.
- **`ubl-creditnote.json`, the sub credit note line, 2**: the tailoring model binds the sub
  invoice line of the extension for UBL Invoice only, so a credit note that declares the
  extension lost every sub line. The XRechnung Schematron of `packs/` recognises the extension
  on `cn:CreditNote`, and UBL 2.1 gives `cac:CreditNoteLine` a repeatable
  `cac:SubCreditNoteLine` of its own type. The first correction (member `extension`) takes the
  sub line entries of `ubl-invoice.json` with the prefix
  `/CreditNote/cac:CreditNoteLine//cac:SubCreditNoteLine`. The second moves BT-129 and BT-130
  to `cbc:CreditedQuantity`, the quantity element of a credit note line. BG-DEX-09 and
  BT-DEX-001 to BT-DEX-003 stay unbound, because UBL 2.1 gives the credit note no
  `cac:PrepaidPayment`.
- **`ubl-creditnote.json`, namespace**: the credit note binding of the source declares the
  UBL Invoice namespace as its default namespace. Yet every XPath in it is rooted at the
  unprefixed step `CreditNote`, whose namespace in UBL 2.1 is the CreditNote namespace.
- **`cii.json`, BG-24 and 3 of its terms**: the cross industry invoice writes 4 different
  references into `ram:AdditionalReferencedDocument` and tells them apart by `ram:TypeCode`.
  `50` is the tender or lot reference BT-17, `130` the invoiced object identifier BT-18, `916`
  the supporting document. The source states that condition for BT-17, BT-18 and BT-122 and
  leaves the group BG-24 and its BT-123, BT-124 and BT-125 without one. Every reference of any
  kind then opens a supporting document group. A reference standing before the supporting
  document in the file moves that document's occurrence index, which is part of its semantic
  path. The CEN validation artefact of `packs/` writes the added condition
  `[ram:TypeCode = '916']` on this element for BT-123 and BT-125. The vendored stylesheet has
  the same gap as the source, so the two readers part here; `conformance/readers.md` says
  where.
- **`cii.json`, BT-29, BT-46, BT-60 and BT-71**: the source binds the global identifier of
  the seller, the buyer, the payee and the ship-to party with the predicate `[@schemeID]`.
  That attribute is optional in the schema of the syntax. A document that states a global
  identifier and no scheme is conformant, and the condition loses the identifier, not only its
  scheme. `BR-CO-26` then faults an invoice that does identify its seller. The CEN validation
  artefact of `packs/` reads `(ram:ID) or (ram:GlobalID)` without a condition, and the scheme
  stays bound as the supplementary component it is. The stylesheet keeps the condition, so
  the two readers part here; `conformance/readers.md` says where.
- **`cii.json`**: the second XPath the source gives the unit of the price base quantity ends
  in `@UnitCode`. The quantity type of UN/CEFACT CII D16B declares `unitCode` and no attribute
  differing from it in case. Every other unit of measure the source binds, in this syntax and
  in the two UBL ones, is bound to the lower case spelling.

A correction carries a `term` where the source binds two terms to the very same XPath; then
only the one named is rewritten. A correction with the member `extension` moves a prefix of
the extension entries instead of one XPath. It takes an entry the source leaves unbound from
the invoice table where every path of that entry lies below `from`.

## Conventions

A correction departs from the source about a business term. A **convention** is a value
written at an element that the syntax requires and no business term of the semantic model
carries. The `conventions` member of a table carries one entry per such element: `element`,
`condition`, `value`, `requiredBy`, `source`, and the optional `option`, `term` and
`notReadBeside`. `binding.schema.json` defines each of them. The two UBL tables carry 3,
`cii.json` none.

| Element | Value | Required by | Source |
|---|---|---|---|
| `cac:AccountingSupplierParty/cac:Party/cac:PartyTaxScheme/cac:TaxScheme/cbc:ID` | `FC` | `UBL-SR-53` | the UNTDID 1153 code the CII binding of the same source model fixes for BT-32 |
| `cac:OrderReference/cbc:ID` | `NA` | schema | Peppol BIS Billing 3.0 |
| `cac:PaymentMeans/cac:CardAccount/cbc:NetworkID` | `NA` | schema | Peppol BIS Billing 3.0 |

A writer applies a convention where the element above it stands in the document and no
business term states the element itself. It names every convention it applied in its report.
`option` names a writer setting that overrides the value. `term` and `notReadBeside` serve the
reader. The purchase order reference is written at an element BT-13 is bound to, so a reader
that met the conventional value beside a `cbc:SalesOrderID` would read a reference nobody
made.

## Extension terms

A table carries the 196 core terms in `terms` and the 12 XRechnung extension terms in
`extension.terms`, in the order of `model/xrechnung/3.0.2.json`. The core terms are the
European standard, the extension terms are not; if you only want the core, read one member.
The extension binds its terms for UBL Invoice only. `ubl-creditnote.json` takes the 8 sub line
entries from it by a correction (see "Corrections") and carries the third party payment group
and its 3 terms unbound, with `extension-not-bound`. `cii.json` carries all 12 unbound.

A third member, `extension.reusedTerms`, carries the 36 core terms that the groups of the
extension registry state in their `reusesTerms` member. A sub invoice line carries the terms of
an invoice line, and the source binds each of them in one expression, both where the standard
puts it and where it sits inside the group. The core entry takes the first place and this
member the second. A consumer that does not load the extension registry reads `terms` and is
unaffected. `ubl-creditnote.json` binds the 36 inside the sub credit note line. `cii.json`
binds no group of the extension and carries them unbound.

## Where the facts come from

The source is the SeMoX model of the XRechnung CIUS published by KoSIT
(Koordinierungsstelle für IT-Standards), release 2026-08-31, and its tailoring model for the
extension. From the `syntax-bindings` section of the first and the `syntax-binding` elements
of the second, this project takes the term identifier, the XPath, the XPaths of the
supplementary components and the note codes. It takes no description, no note text, no rule
and no example. The prose of that model is standard text under the CEN and DIN regime; every
sentence in these files and in this directory is written for this repository.

KoSIT publishes these models as a data basis for further application scenarios
(<https://xeinkauf.de/xrechnung>, section *Komponenten*). The SeMoX modelling framework is
published under the MIT licence, Copyright Coordination Office for IT Standards (CoSIT).
The model repository itself carries no licence file, and this project claims none for it. It
takes only facts from it, no descriptions and no rule text. The cross-checks read the KoSIT
visualization stylesheets (Apache-2.0) already vendored in `esj-xr` and the CEN validation
artefacts of `packs/` (EUPL-1.2), both for facts only.

ESJ is not an XRechnung format and these tables are not a KoSIT deliverable. The files in
this directory are licensed Apache-2.0, like the rest of the repository.

## Regenerating

The tables are checked in and generated, never edited by hand: an edit belongs in the
generator, not in the JSON. The model file is not part of this repository, so you give its
path on the command line.

```sh
python3 model/bindings/tools/semox_to_bindings.py \
    <path>/xrechnung-cius-model.xml \
    --tailoring <path>/xrechnung-extension-model-tailoring.xml \
    --release 2026-08-31
```

`--check` compares with the files on disk instead of writing them, and exits non-zero on a
difference. Then regenerate the cross-check report, which needs the older model as well:

```sh
python3 model/bindings/tools/crosscheck_bindings.py \
    --older-model <path>/xrechnung-model.xml \
    --date 2026-09-19
```

The report is `conformance/bindings/crosscheck.md`. The script computes its machine part.
Its explanation column lives in the `EXPLANATIONS` table of the same script, and the script
prints a difference without an entry there as unexplained.

## What the tests check

`esj-core` reads the three tables from the class path and checks that

- each validates against `binding.schema.json`;
- each carries exactly the terms of `model/en16931/2017.json`, in that order, exactly
  the terms of `model/xrechnung/3.0.2.json` in `extension.terms`, in that order, and
  exactly the terms those groups reuse in `extension.reusedTerms`, in that order;
- the `counts` of a file describe that file;
- every flag a file uses is defined in its own `flagDefinitions`, and every definition is
  used;
- the flag table of this README lists every flag of every file with the count that file
  has, and no flag the files do not use;
- every prefix a path uses is declared in `namespaces`, and every path of a bound entry
  starts at `rootElement`;
- a component's `anchor` is a path the entry itself binds;
- every convention names an element the schema of its syntax declares, with a condition of
  the closed vocabulary and a source;
- the cross-check report leaves no difference unexplained.

## Regenerating is not the same as re-deciding

A later release of the source model may move a path. Then the tables change and the report
changes with them, and you judge the change by the diff of the report. That is why the report
stays in the repository instead of the check running in the build alone.

Author: Christian Bürckert.
