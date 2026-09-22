# `model/` — the ESJ term registry

This directory holds the machine-readable description of the semantic model that
EN16931 Semantic JSON (ESJ) encodes. Everything in ESJ that depends on *which*
business terms exist — path validation, cardinality checking, the generated typed
API — reads these files and nothing else.

| File | What it is |
|---|---|
| `en16931/2017.json` | The 196 core terms of EN 16931-1:2017+A1:2019/AC:2020: 164 business terms (BT) and 32 business groups (BG), in the order of Table 2. |
| `en16931/2026.json` | The 255 core terms of EN 16931-1:2026: 216 BT and 39 BG. Separable; see below. |
| `en16931/upgrade-2017-2026.json` | How the paths and the structural facts of the two editions relate, as data: `upgrade.schema.json` gives its format. |
| `xrechnung/3.0.2.json` | The 12 terms of the KoSIT XRechnung 3.0.2 extension (the `SUB INVOICE LINE` tree and `THIRD PARTY PAYMENT`). These identifiers are **not** part of EN 16931-1. |
| `b2c/0.1.json` | The 4 terms of the B2C extension of this project: the gross figures a consumer was shown or agreed to. Section **The B2C extension** below. |
| `registry.schema.json` | JSON Schema 2020-12 for the registry file format. Every registry file validates against it, and the `esj-core` test suite checks that they do. |
| `bindings/` | Where each term of the 2017 registries lives in UBL Invoice, UBL Credit Note and CII: one table per syntax, generated from the KoSIT SeMoX model. `bindings/README.md` describes them. A registry says which terms exist; a binding table says where one of them sits in one syntax. |
| `derivable-terms.json` | The terms and groups a derivation policy of this SDK writes. They are not steps of the constrained builder of `esj-typed`, which asks for what a caller has to state. |
| `profiles/` | One overlay per profile the constrained builder is generated for: the cardinalities the profile narrows and the values it fixes. |
| `enums.json` | Which code list snapshots of which rule pack become which enum of `esj-invoice`, whether a constant is named after the code or after the publisher's name, and the constant names the file decides rather than derives. |

## One file per edition

A registry file describes exactly one edition of one model: `<model>/<edition key>.json`.
The `edition` member carries the full edition string in the spelling the standards body uses
— `EN 16931-1:2017+A1:2019/AC:2020` — and the file name carries the short key. A document
writes that string with every space removed in its `semanticModel` member; `SPEC.md`
sections 4.4 and 10 give the mapping. A later edition arrives as a new file beside the older
one and never as a rewrite of it, and a file is self-contained: no registry imports another
core registry and neither carries a delta of the other. `Registry.editions()` lists the
editions a build carries, `Registry.forEdition(key)` reads one and
`Registry.forSemanticModel(string)` finds the one a document is measured against; 2017 stays
the edition everything writes unless a caller asks for another.

The files of the 2026 edition are **separable**: `en16931/2026.paths` lists what a
distribution leaves out when that edition is not shipped, and the Maven profile
`without-edition-2026` builds without them, with `Registry.editions()` reporting `2017`
alone. Prose that states facts of the edition, this page among it, is not separable.
`../docs/editions.md` has the rest, and what no build does for that edition.

An extension registry names what it builds on in its `imports` member:

```json
"imports": [{ "model": "EN16931-1", "edition": "EN 16931-1:2017+A1:2019/AC:2020" }]
```

The edition string is copied character for character from the registry the extension was
written against, and combining the two is refused when the strings differ: an extension's
parents, its `reusesTerms` and its cardinalities were checked against one list of terms.
`registry.schema.json` requires `imports` of every registry whose groups carry terms of
another model, and `esj-core` additionally checks that no `parent` points outside the file.

An extension registry may also declare `"transport": "none"`, with the `transportNote` that says
why: its terms are bound by no transport syntax by design, so the UBL or CII written from a
document using them is the whole invoice and `esj validate` counts the written-syntax row and
names them instead of answering `term-not-in-syntax` (`../docs/b2c.md`). `b2c/0.1.json` is the
one file of this release that declares it.

## An extension namespace is permanent

`imports` names the edition of another model, never a version of the extension's own
namespace. An identifier published in an extension namespace — `BT-DEX-001`, `BT-B2C-001` —
keeps its meaning, its semantic data type and its structural semantics in every later
registry of that namespace; an incompatible revision takes a new namespace. A namespace may
grow, and a registry file is versioned so that a validator knows which identifiers it holds,
but a version never says what one of them means. `SPEC.md` sections 5.6, 10 and 11.1 state
the rule normatively.

## The registry contains no normative prose

The registry records facts about the structure of the model — identifiers, names,
parent/child relations, cardinalities, semantic data types, supplementary components, code
list names, decimal limits — and reproduces no sentence of EN 16931-1, of the XRechnung
specification or of Peppol BIS Billing 3.0. Every `description` and every entry in `notes`
is original wording; where a statement rests on the standard, the note names the clause, the
table or the rule identifier instead of quoting it. `docs/sources.md` says how to obtain the
normative text.

## File format

A registry file is a header followed by a flat array of terms in document order.

```json
{
  "format": "EN16931-Semantic-JSON-registry",
  "version": "0.1",
  "model": "EN16931-1",
  "edition": "EN 16931-1:2017+A1:2019/AC:2020",
  "license": "Apache-2.0",
  "notice": "…attribution for the described standard…",
  "generated": "2026-09-19",
  "sourceStatement": "…how the content was derived and cross-checked…",
  "terms": [ … ]
}
```

A term looks like this:

```json
{
  "id": "BT-131",
  "kind": "BT",
  "name": "Invoice line net amount",
  "slug": "netAmount",
  "parent": "BG-25",
  "path": ["BG-25", "BT-131"],
  "depth": 1,
  "min": 1,
  "max": 1,
  "datatype": "Amount",
  "maxDecimals": 2,
  "components": [],
  "order": 159,
  "description": "Net amount of the invoice line, after line allowances and charges and excluding VAT.",
  "notes": ["EN 16931-1, 6.5.12, Table 26 allows at most two fraction digits for this amount."]
}
```

| Member | Meaning |
|---|---|
| `id` | The identifier of the model, used verbatim in ESJ semantic paths. |
| `kind` | `BT` for a term that carries a value, `BG` for a group. |
| `name` | The business term name as the model spells it. |
| `slug` | Language-neutral API name stem for code generation. An ESJ convention (see below). |
| `parent` | Enclosing group, or `null` for a term directly under the invoice root. |
| `path` | Identifiers from the root down to and including this term. |
| `depth` | `len(path) - 1`. |
| `min` / `max` | Cardinality relative to the parent group; `max` is `1` or `"n"`. |
| `datatype` | One of the semantic data types, `null` for groups. |
| `maxDecimals` | Present where the edition caps the fraction digits at a constant. |
| `maxDecimalsRule` | Present in place of `maxDecimals` where the edition derives the cap from the document: `iso4217-minor-unit` or `iso4217-minor-unit-plus-2`. |
| `reqIds` | 2026 registry only: the requirement identifiers the edition's Table 2 gives for the term. |
| `codeList` | Present on code terms; the name of the list, without a version. |
| `components` | Supplementary components of the semantic data type; see below. A `scheme` component may carry `schemeList`, the name of the list its code comes from. |
| `order` | One-based position in the document order of the model. |
| `description` | One sentence, written for this registry. |
| `notes` | Further remarks, written for this registry. |
| `reusesTerms` | Extension registries only: terms an extension group carries one level deeper. A group that lists its own identifier there is recursive — it may carry further instances of itself, to any depth; `SPEC.md` section 5.6 gives the rule. `BG-DEX-01`, the sub invoice line, is the one such group here. |

### The semantic data types

`Amount`, `UnitPriceAmount`, `Quantity`, `Percentage`, `Code`, `Identifier`,
`DocumentReference`, `Date`, `Text`, `BinaryObject`, and `Time`, which the 2026 edition
adds and gives to BT-166 alone. The datatype decides the grammar of the content and the
supplementary components; `SPEC.md` section 6 gives the rules.

### Supplementary components

`components` is non-empty only on `Identifier` and `BinaryObject` terms — 14 terms carrying
16 components in the 2017 registry, 17 carrying 19 in the 2026 one. Each component has a
`role` that names the ESJ value member it feeds:

| `role` | ESJ value member | Where |
|---|---|---|
| `scheme` | `scheme` | 13 identifier terms |
| `schemeVersion` | `schemeVersion` | BT-158 only |
| `mimeCode` | `mimeCode` | BT-125 |
| `filename` | `filename` | BT-125 |

A scheme member is allowed in an ESJ document only where the registry lists a
component for that term. Everything else an XML syntax may attach to a value
(`@listID`, `@languageID`, `@currencyID`, `@schemeName`, …) is a syntax carrier or
is forbidden by the CEN syntax rules, and has no place in ESJ.

A `scheme` component carries `schemeList` where the edition or, for 2017, the CEN validation
artefacts fix the list the scheme code comes from. All 13 scheme components of the 2017
registry do:

| `schemeList` | Terms | Rule |
|---|---|---|
| `UNTDID 1153` | BT-18, BT-128 | `BR-CL-07` |
| `ISO 6523 ICD` | BT-29, BT-46, BT-60 | `BR-CL-10` |
| `ISO 6523 ICD` | BT-30, BT-47, BT-61 | `BR-CL-11` |
| `UNTDID 7143` | BT-158 | `BR-CL-13` |
| `ISO 6523 ICD` | BT-157 | `BR-CL-21` |
| `CEF EAS` | BT-34, BT-49 | `BR-CL-25` |
| `ISO 6523 ICD` | BT-71 | `BR-CL-26` |

The rules are the code-list assertions of `EN16931-UBL-codes.sch` in the CEN/TC 434
validation artefacts 1.3.16, and each term's `notes` names the one it comes from. The 2026
registry names no rule, because that edition has no artefacts: each of its 16 assignments
rests on the usage note of the term's own Table 2 row, which is why BT-157 reads `UNTDID
7143` there and `ISO 6523 ICD` here. `schemeList` is documentation: it reaches the Javadoc
of the generated accessors so that a caller writing `identifiers().add(value, scheme)` can
see which list the scheme comes from, and nothing else. Membership of a code list is a
business rule decided by a versioned rule pack, and the structural layers never check it.
There is no `schemeList` on `schemeVersion`, `mimeCode` or `filename`, and the schema
refuses one there.

**The `BT-x-y` numbering of components is informative.** Neither edition numbers the
supplementary components; the style is the one implementers commonly use, and where it comes
from could not be verified — CEN/TS 16931-3-2 and CEN/TS 16931-3-3 were not obtained, and the
string occurs nowhere in the CEN/TC 434 validation artefacts. The notes of every affected
term say so. Treat `role` as the normative-facing member and the component id as a label.

### `maxDecimals` and `maxDecimalsRule`

How many fraction digits a term allows is a fact of the edition, and the two editions state
it differently:

| Edition | Amounts | Unit prices | Quantities | Percentages |
|---|---|---|---|---|
| 2017, Table 26 | `"maxDecimals": 2` on all 21 | unlimited | unlimited | unlimited |
| 2026, Table 28 | `"maxDecimalsRule": "iso4217-minor-unit"` | `"iso4217-minor-unit-plus-2"` | BT-129 4, BT-149 2 | 2, and BT-167 6 |

A term states a number or a rule, never both. The limit is a business rule, not a structural
one: an ESJ reader accepts a value with more decimals and a rule pack rejects it (`rules/`, the
`BR-DEC-*` rules).

### `codeList`

All 22 `Code` terms of the 2017 registry carry a `codeList`; of the 37 in the 2026 registry,
33 do and four do not, because their usage note spells the permitted values out instead of
naming a published list. The value is the list name without a version: the applicable
version is the latest published at the time of the syntax binding. The 2017 assignments:

| Code list | Terms |
|---|---|
| `UNTDID 1001` | BT-3 |
| `ISO 4217 alpha-3` | BT-5, BT-6 |
| `UNTDID 2005` | BT-8 |
| `UNTDID 4451` | BT-21 |
| `ISO 3166-1 alpha-2` | BT-40, BT-55, BT-69, BT-80, BT-159 |
| `UNTDID 4461` | BT-81 |
| `UNTDID 5305` | BT-95, BT-102, BT-118, BT-151 |
| `UNTDID 5189` | BT-98, BT-140 |
| `UNTDID 7161` | BT-105, BT-145 |
| `VATEX` | BT-121 |
| `UN/ECE Recommendation 20 with Rec 21 extension` | BT-130, BT-150 |

Every one of those assignments is justified in the term's own `notes`, by a usage note of
Table 2, by a `BR-CL-*` rule of the CEN validation artefacts, or by both. One oddity is
recorded there rather than smoothed over: the UBL artefacts phrase the tax point date code
rule (BT-8) against UNTDID 2005 while the CII artefacts phrase it against UNTDID 2475. The
allowance and charge reason rules are separate and unambiguous: `BR-CL-19` applies to the
allowance reason code (UNCL 5189, BT-98 and BT-140) and `BR-CL-20` to the charge reason code
(UNCL 7161, BT-105 and BT-145).

The 2026 registry adds `UNTDID 5153` (BT-177, BT-193), `UNTDID 6313` (BT-211) and further
terms to the lists above, all of them on the strength of a Table 2 usage note.

**Membership belongs to a versioned rule pack, never to the registry.** A code list gains
and loses codes on its own schedule while a structural verdict must not move, so the
registry says which list a value is drawn from — a fact of the semantic model — and a dated
rule pack says which codes that list held. A membership check is therefore a business rule
(`SPEC.md` section 9.4), and its answer is always relative to a named rule pack;
`rules/en16931/1.3.16` is one.

### `slug`

`slug` is an ESJ convention with no standing in any standard. It exists so that a
generated API can read `invoice.seller().postalAddress().countryCode()` instead of
`invoice.get("BT-40")`. The rules:

1. A lowerCamelCase ASCII stem matching `^[a-z][A-Za-z0-9]*$`, language-neutral: a generator
   applies its target's naming convention and escapes that language's keywords itself.
2. Unique among the children of one parent, and the same stem repeats under different
   parents: `name` appears under BG-4, BG-6, BG-7, BG-9, BG-10, BG-11, BG-31 and BG-32.
3. Derived from the term name with the enclosing group's name removed where it repeats it:
   BT-131 "Invoice line net amount" under BG-25 is `netAmount`.
4. Plural where the accessor returns a list: BG-25 `invoiceLines`, BT-29 `identifiers`. Two
   non-repeating groups read plural because their own name does, BG-16 and BG-22.
5. Curated where the mechanical derivation reads badly: BT-41 "Seller contact point" under
   BG-6 is `name`, not `point`.
6. A term that exists in more than one edition keeps the stem of the oldest one, byte for
   byte, even where the newer edition renamed it: BT-20 is `paymentTerms` in both, although
   the 2026 edition calls it "Payment term text". `SPEC.md` section 10 states rules 1, 2 and
   the editorial part of 3 normatively.

Slugs are stable API surface: changing one is a breaking change for `esj-typed`, adding one
is not.

## `derivable-terms.json`, `profiles/` and `enums.json`

None of these states a fact the registry carries, and none takes part in validation.
`derivable-terms.json` lists what `Totals`, the derivation policy of `esj-typed`, writes:
BT-131, BG-22 with its sums and BG-23 with the VAT breakdown. The constrained builder leaves
them to `derive()` rather than asking a caller for them; they stay settable by hand.

A profile overlay carries `constant` and `typeSuffix` for the generated `Profile` and the
types of its chain, `narrows` — `id`, the registry cardinality and the profile's — and
`fixed`, the values it writes into every invoice it opens. `xrechnung-3.0.2.json` holds the
nineteen narrowings of the cross-check below; adding a profile is adding a file.

`enums.json` names one enum per list the domain API offers a word for — nine of the eleven
code lists the registry names (UNTDID 2005 of BT-8 and UNTDID 4451 of BT-21 are left out) and
the scheme list EAS as the tenth — the snapshots of the rule pack `en16931` `1.3.16` it is generated from, and the naming rule in
words beside the data it applies to. An enum names the list as the registry spells it, and
the generator refuses a list no term names.

## The B2C extension

`b2c/0.1.json`, namespace `B2C`, four terms:

| Path | Term | Datatype | Cardinality |
|---|---|---|---|
| `/BG-25/<n>/BT-B2C-001` | Displayed gross unit price | `UnitPriceAmount` | 0..1 |
| `/BG-25/<n>/BT-B2C-002` | Displayed gross line total | `Amount` | 0..1 |
| `/BG-25/<n>/BT-B2C-003` | Displayed line VAT amount | `Amount` | 0..1 |
| `/BT-B2C-010` | Displayed invoice gross total | `Amount` | 0..1 |

Each term records a figure the customer was shown or agreed to; absence means nothing was
shown at that level.

The registry states **no arithmetic relation** — neither among its own terms nor between one
of them and a term of EN 16931-1 — so BT-114 stays the invoice rounding amount of the
standard and the core terms keep their meaning. Deriving one figure from another is a policy
of the SDK, which names its preconditions and reports what it did (`../docs/b2c.md`).

`examples/b2c-gross.esj.json` is a three-line consumer invoice carrying all four.

## How the registry was derived

The 2026 registry was built from the normative text of **EN 16931-1:2026**, Table 2 of
clause 6.3 and clause 6.5, and from nothing else. That edition has no validation artefacts,
so no `codeList` or `schemeList` of the 2017 registry was carried forward on the strength of
a rule written for the older one. The rest of this section is about the 2017 registry.

The core registry was built from the **normative text of EN 16931-1:2017+A1:2019 with the
corrigendum AC:2020 applied**, principally Table 2 (the semantic data model) and clause 6.5
(semantic data types, decimals and rounding). The corrigendum changes the semantic data type
of BT-21 from Text to Code, which the uncorrected print of Table 2 still shows as Text; that
correction is applied here and recorded in BT-21's notes.

Code list assignments were taken from the `BR-CL-*` rules of the CEN/TC 434 validation
artefacts (release 1.3.16, EUPL-1.2) and from the Table 2 usage notes, each checked against
both before being written.

The result was then cross-checked, term by term, against three independent
implementations of the same model, facts only and no prose:

- the **KoSIT XRechnung semantic-model XSD** (Apache-2.0). It declares INVOICING PERIOD at
  the invoice root where EN 16931-1 Table 2 places it inside DELIVERY INFORMATION (BG-13);
  the divergence is recorded in BG-14's notes;
- the **KoSIT SeMoX models of XRechnung**, over all 196 core terms: no difference in
  identifier, semantic data type or parent, and every cardinality difference is XRechnung
  narrowing an optional element to a mandatory one. The older of the two files is licensed
  MIT, Copyright 2022-2024 Coordination Office for IT Standards (CoSIT); and
- the **Peppol BIS Billing 3.0 structure files**, which bind several business
  terms to one syntax element in places and therefore label elements differently.

Differences were resolved in favour of EN 16931-1. National restrictions are notes and never
applied to `min`, `max` or `datatype`; nothing from Peppol BIS is reproduced here.

The XRechnung extension registry was built the same way from the semantic model column of
table 1.1 of the XRechnung 3.0.2 UBL syntax-binding extension, cross-checked against the KoSIT
schema. `b2c/0.1.json` was derived from nothing outside this repository.

## Regenerating and validating

The registry files are checked in: they are the input to code generation, not its output.

**After an edit, regenerate** the artefacts derived from the registries — the sources of
`esj-typed`, its constrained builder, the code list enums of `esj-invoice` and the model schema
of each edition under `schema/` — from the repository root:

```sh
mvn -B -Pgenerate -pl esj-generator -am process-classes
```

The build in `.github/workflows/ci.yml` runs the same command and fails when the checked-in
files differ from what it produces.

The `esj-core` test suite validates every registry file against `registry.schema.json`. To
run the same check by hand, with either engine:

```sh
# Node
npx --yes ajv-cli@5 validate --spec=draft2020 \
  -s registry.schema.json \
  -d en16931/2017.json -d en16931/2026.json -d xrechnung/3.0.2.json -d b2c/0.1.json

# Python
pip install jsonschema
python3 -c "
import json
from jsonschema import Draft202012Validator
s = json.load(open('registry.schema.json'))
Draft202012Validator.check_schema(s)
for f in ('en16931/2017.json', 'en16931/2026.json', 'xrechnung/3.0.2.json', 'b2c/0.1.json'):
    Draft202012Validator(s).validate(json.load(open(f)))
    print(f, 'valid')
"
```

JSON Schema cannot express the cross-term invariants. Those are checked by the
`esj-core` test suite and must hold after any edit:

- exactly 196 terms in the 2017 core registry, 255 in the 2026 one, 12 in
  `xrechnung/3.0.2.json`, 4 in `b2c/0.1.json`;
- every `parent` resolves to a term of the same file, except `BG-25` in the
  extension, which resolves into the core registry named by its `imports`;
- `path` equals the parent's `path` plus the term's own id, and `depth` equals
  `len(path) - 1`;
- `order` is a dense 1..n sequence and the array is stored in that order;
- `slug` is unique among the children of one parent and matches `^[a-z][A-Za-z0-9]*$`;
  whether the stem collides with a keyword is the generator's problem, not the registry's;
- `datatype` is one of the datatype tokens for a BT and `null` for a BG;
- `components` is non-empty only on `Identifier` and `BinaryObject` terms, and only a
  `scheme` component carries `schemeList`;
- every `Code` term has a `codeList` whose justification appears in its `notes`;
- every `Amount` term has `"maxDecimals": 2` in the 2017 registry, and `maxDecimals` or
  `maxDecimalsRule` and never both in the 2026 one;
- every term of the 2017 registry is a term of the 2026 registry with the same stem, and
  `upgrade-2017-2026.json` says exactly what the two registries say about their difference.

**Changing a slug** means editing the `slug` member and re-running code
generation; the rules of `SPEC.md` section 10 — unique among siblings, one stem
per concept, a plural for a repeatable group — are enforced by the test suite.

## Attribution

`en16931/2026.json` is a derived application of EN 16931-1:2026. That edition is not free of
charge, was bought from a national standards body, is not redistributed here, and no prose
of it appears in these files. The agreement below is documented for the 2017 version, so no
CEN permission is claimed for this edition and the publication question is answered before
the files listed in `en16931/2026.paths` are published. Where this project and the standard
disagree, the standard governs.

This registry is a derived application of EN 16931-1:2017+A1:2019 with the
corrigendum AC:2020: it implements that European Standard. The standard text was
obtained free of charge under the European Commission / CEN Licence Agreement of
18 December 2018 as published by the Slovak Office of Standards, Metrology and
Testing (ÚNMS SR). Reproduction of the standard is with the consent of CEN and
ÚNMS SR. The standard itself is not redistributed here, and no normative prose
from it appears in these files.

`b2c/0.1.json` describes no standard at all: its terms are defined by this project and
published under Apache-2.0 with the rest of the repository.

`xrechnung/3.0.2.json` describes an extension published by KoSIT
(Koordinierungsstelle für IT-Standards). Its specification text is copyright CEN
and distributed under the same European Commission / CEN agreement; the KoSIT
schema material used for the cross-check is licensed Apache-2.0. ESJ is not an
XRechnung format and this registry is not a KoSIT deliverable.

The `BR-CL-*` rules cited in the notes come from the CEN/TC 434 validation
artefacts, licensed EUPL-1.2. No rule text is reproduced; the notes cite rule
identifiers and restate their effect in the author's own words.

The files in this directory are licensed Apache-2.0, like the rest of the
repository.

Author: Christian Bürckert. Publisher: BSNSoft Solutions GmbH.
