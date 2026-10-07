# The rule language

*Part of [EN16931 Semantic JSON](../README.md). [`../docs/validation.md`](../docs/validation.md)
is what `esj validate` does and what a finding means; this page is the language the semantic
rules are written in.*

A business rule of EN 16931 is a statement about business terms. `BR-CO-10` says that the sum
of the invoice line net amounts is the figure BT-106 carries — about BT-131 and BT-106, not
about `cac:InvoiceLine/cbc:LineExtensionAmount` and not about
`ram:SpecifiedTradeSettlementHeaderMonetarySummation/ram:LineTotalAmount`. The official
validation artefacts have to say it twice, once per syntax, because they check XML. Here it is
said once:

```json
{
  "id": "BR-CO-10",
  "severity": "fatal",
  "context": "/",
  "terms": ["BT-106", "BT-131"],
  "assert": { "eq": [ { "value": "/BG-22/BT-106" }, { "sum": "/BG-25/*/BT-131" } ] },
  "bind": { "lines": { "sum": "/BG-25/*/BT-131" } },
  "message": "The sum of the invoice line net amounts is {$lines}, and BT-106 at {@/BG-22/BT-106} carries {/BG-22/BT-106}.",
  "source": "EN 16931-1, 6.4.2, BR-CO-10"
}
```

One rule then serves a UBL invoice, a CII invoice and a document that was never XML, and a
finding lands on the business term the caller wrote the value at.

This directory is the project's own material, under the licence of the repository, with one
carve-out: `<pack>/<version>/codelists/` holds dated snapshots of code lists published by other
bodies. Those files are not this project's work, no publisher states terms for the data, and
the `SOURCES.md` beside them ([1.3.16](en16931/1.3.16/codelists/SOURCES.md),
[2026](en16931-2026/0.1/codelists/SOURCES.md)) records source, digest and that open question
for each. Everything else here — the rule statements, the manifests, the language — is the
project's own. It is not [`../packs/`](../packs/README.md), which holds the third-party
validation artefacts the syntax engine executes, each under the licence it came with.

A note on what this is and is not. The rule *statements* are facts of EN 16931-1, clause 6.4,
and they are written here in this project's own words: no expression and no assertion text of
the CEN artefacts is translated or copied, and no text of the norm is reproduced. The
artefacts stay the authority, a pack names the release it was verified against, and no claim
about agreement is made anywhere in this repository without the ledger that measured it.

## A rule file

```json
{
  "id": "en16931",
  "version": "1.3.16",
  "edition": "EN 16931-1:2017+A1:2019/AC:2020",
  "verifiedAgainst": "CEN/TC 434 eInvoicing EN 16931 validation artefacts, release 1.3.16",
  "description": "…",
  "codeLists": { "untdid-5305": "2026-09-19" },
  "javaRules": [{ "class": "…rules.internal.en16931.SomeRule", "oracle": "artefact" }],
  "files": ["rules/br.json", "rules/br-co.json"],
  "shares": [{ "pack": "en16931", "version": "1.3.16", "file": "rules/br.json",
               "oracle": "downgrade", "rules": ["BR-01", "BR-02"] }],
  "rules": [ … ]
}
```

| Member | What it is |
|---|---|
| `id`, `version` | the pack, which appears in every finding it produces; a released version is never edited, a newer list or a corrected rule is a new version |
| `edition` | the edition of the semantic model the rules are addresses in. It is the one member by which the engine decides whether a pack may be compiled against a registry and run over a document, and a pack of another edition is refused rather than run |
| `verifiedAgainst` | optional: the release of the official artefacts this pack's behaviour is compared against — a statement of what was measured, not a claim of equivalence. A pack of an edition no artefact release covers leaves it out and says per rule what stands behind it |
| `codeLists` | which day's snapshot of each code list this pack decides membership against ([`en16931/1.3.16/codelists/SOURCES.md`](en16931/1.3.16/codelists/SOURCES.md)). A value may be `{"day": …, "from": "en16931/1.3.16"}` instead, which reads the file of that pack: two editions naming one published list decide against the same bytes |
| `javaRules` | the rules the language cannot express, each by the name of its class and by its oracle; naming a class is not loading it, see below |
| `shares` | optional: rules of another pack this pack takes over unchanged, by pack, file and identifier, with the oracle under which it takes them. A rule the next edition leaves alone is the same statement about the same terms, and a copy of it is a second file to keep equal by hand. A rule of the named file that is not named here is not taken over, and a shared rule that addresses a path this edition moved does not compile — which is what keeps sharing from being a guess |
| `files` | the rule files the pack is made of, each an array of rules, each a path relative to the manifest. Two hundred rules in one file are unreadable and unreviewable, so they are split by family; the rules of the files and the rules the manifest writes itself are one set, and an identifier that appears twice in it is refused |
| `rules` | the rules written in this language, for a pack small enough to be one file |

[`rule.schema.json`](rule.schema.json) is the schema of the file. It checks the shape; it
cannot check anything that needs the term registry, and the compiler of `esj-rules` checks
that: whether a path names terms that exist, whether it nests them in a way the registry
records, whether an asterisk stands exactly where a repeatable term is, which semantic data
type a path has and therefore whether an arithmetic operator was handed numbers, and whether a
code list a rule names has a snapshot in the pack. A pack that fails any of it does not start,
and none of it is ever reported as a defect of an invoice.

A compiled pack belongs to the edition of the registry it was compiled against, and the engine
refuses a document that names another one: a path is an address relative to an edition
([`../SPEC.md`](../SPEC.md) section 10), so the same rule identifier would otherwise be reported
about terms of a different table. A caller that has no pack for the edition it holds reports
that the business rules were not checked, which is what the command line does.

## A rule

| Member | What it is |
|---|---|
| `id` | the identifier the standard gives the rule; it is the code of every finding, so a report can be compared with any other tool's |
| `severity` | `fatal` or `warning`. `info` is the engine's and says that a rule could not be decided |
| `oracle` | what stands behind the rule: `artefact` (measured against the official artefacts of a release over the same document), `downgrade` (unchanged against the edition such a release covers, and measured against those artefacts over the document written down to it with `esj upgrade`) or `cases` (hand-computed cases alone). None of the three is a conformance claim |
| `context` | what the rule is a statement about: `/` for the document, or a business group pattern such as `/BG-25/*`. The rule is evaluated once per instance |
| `terms` | the business terms and groups the rule reads — documentation, and the column a coverage table is built from |
| `assert` | what must hold; a finding is produced when it is false |
| `warn` | optional: a second `assert` and `message`, weighed only where the first assertion holds, whose failure is a warning and decides no verdict |
| `undecided` | optional: a `when` and a `message`, weighed before `assert`. Where `when` is true the rule is not decided at that instance — an `info` finding, `not decided:` and the message — and nothing else of it is weighed. It names a figure a document may state and the rule has no answer for, such as a price per zero units |
| `bind` | expressions the message may show under a name, evaluated only when the rule fails |
| `message` | what the finding says, in this project's own words |
| `source` | the clause of the standard, as a reference and never as a quotation |
| `note` | optional: why the rule reads the way it does where the statement admits more than one reading — a tolerance, a rounding, a place where the official artefacts settle what the norm leaves open |

A rule with a `warn` says two things: the first assertion decides the verdict, the second is
noted. It is written where the two official artefacts of a release do not ask the same
closeness of the same two figures — the rule faults from the wider reading on and warns inside
the zone only one artefact grants, naming that syntax.

### Context: what a rule is about, and how often it runs

A rule with the context `/` is a statement about the document and runs once. A rule with the
context `/BG-25/*` is a statement about an invoice line and runs once per line; inside it,
paths are written relative to that line, so `/BT-131` is *this* line's net amount.

### Paths: no occurrence index, ever

A rule cannot write `/BG-25/3/BT-131`, and the ban is not a convenience. A rule is a statement
about every invoice, and the number of lines an invoice has is unknown when the rule is
written. So a repeatable term is written with an asterisk and a term that occurs at most once
is written without one — which is the index rule of `SPEC.md` section 5.3, checked against the
registry when the pack is compiled rather than once per invoice. `/BG-22/BT-106` carries no
asterisk because there is one Document totals group; `/BG-25/*/BT-131` carries one because
there are many lines.

### Message: what the reader gets

A finding that says only that BR-CO-10 failed makes the reader open the invoice and do the
arithmetic. Four placeholders make the message say what happened instead:

| Placeholder | What it shows |
|---|---|
| `{/BG-22/BT-106}` | the value at that path, escaped as `SPEC.md` section 9.5 requires; `(absent)` where there is none |
| `{@/BG-22/BT-106}` | the path itself, so the reader can go to it |
| `{$lines}` | an expression the rule bound under that name |
| `{.}` | the business group instance the rule is looking at; `/` for the document |

A literal brace is written twice. Every path in a message is resolved when the pack is
compiled, so a message that names a term nobody has is a defect of the pack.

## Values, types and absence

The type of a value comes from the registry, which records the semantic data type of every
business term once (`SPEC.md` section 6.2). A path to BT-131 is a decimal because the registry
says Amount; a path to BT-2 is a date because it says Date; everything else is text. There are
no type declarations in a rule and there is nothing to keep in step. A literal — `{"const":
"S"}` — takes its type from what it stands beside, and a literal that is not the number or the
date it is compared against is a defect of the pack, found once.

**Absence propagates.** An invoice is a document in which most terms are optional, so an
expression that reads one asks a question that may have no answer, and the engine has to
decide what that means. Treating a missing amount as zero would invent a figure the invoice
does not carry. Treating a missing value as a failure would make one omission fail every rule
that mentions the term, and the cardinality layer (L3) has already said so once. So:

- an arithmetic operator, a comparison, `matches`, `inList`, `len`, `round`, `abs` and
  `decimals` with an absent operand are **absent**;
- `and` is false as soon as one operand is false, and absent otherwise unless every operand is
  true; `or` is the mirror; `not` of absent is absent;
- `exists`, `absent` and `count` are never absent: they answer about presence;
- a `sum` over no instance is zero, and a `min` or `max` over no instance is absent;
- **an assertion that comes out absent is a rule that could not be decided, and a rule that
  could not be decided reports nothing.** A rule author who wants a missing term to fail
  writes that, with `exists`.

**A value that does not spell its type stops its rule.** An amount that reads `1.000,00` is
not a number this engine will guess at. The structural validator reports it at layer L2 as
`ESJ-L2-DECIMAL` with its path; a rule that reads it produces one `info` finding saying which
rule was not decided and why, and no verdict. Reporting the same defect a second time as a
failed business rule would make one problem look like two.

## Decimals

Every number is an exact `BigDecimal` and no binary floating point type takes part anywhere.
Three consequences are worth stating because they are decisions and not accidents.

**No number is rounded, capped or normalised unless a rule says so.** How many fraction digits a
term admits is a fact of its edition, stated by the `BR-DEC-*` rules of its pack with `decimals`:
the 2017 edition fixes Amount at two and leaves Unit Price Amount, Quantity and Percentage
unlimited (a net price derived from a gross one needs many decimals, Annex A.2); the 2026
edition ties an amount to the minor unit of the currency in use and bounds some unit prices.

**`decimals` counts what the number needs, not what a file spells.** The CEN artefacts check an
XML lexical form and count the fraction digits written there, so `100.00` has two of them. ESJ
has no such spelling to count: the decimal form of `SPEC.md` section 6.4 has no trailing
fraction zeros at all, a value that carries them is refused at layer L2, and `100.00` and `100`
are therefore not two spellings of one value in an ESJ document — only the second is one.
`decimals` asks how many fraction digits the number needs, which on any document this engine
will be handed is the same question. The difference shows only where one invoice reached the
artefacts as XML carrying `100.00` and reached this engine as the value `100`: the artefact
counts two digits and this engine counts none. It follows from the format rather than from a
choice made here, and it belongs in the ledger next to the rule.

**Division names a working precision.** Exact arithmetic is the rule and division is the one
operation that cannot always keep it: one divided by three has no decimal expansion. A
division whose exact quotient does not terminate is computed to 34 fraction digits, half up —
far beyond any figure an invoice carries. A cut quotient can still decide a later rounding that
lands on a half, so a rule divides once and last: `(BT-146 × BT-129) ÷ BT-149`, not the price per
unit times the quantity. Division by zero is absent; a rule whose divisor a document may state
as zero names that case in `undecided`.

`round` is always half up, which is what EN 16931-1, 6.5.13 asks for, and it is the only
operator that rounds.

## The operators

An expression is a JSON object with exactly one member, whose name is the operator. There are
thirty-four of them and there is no thirty-fifth: the set is closed, and growing it means changing
the schema and the compiler together. That is what keeps a rule file data rather than code, and
it is what lets the same file be read by an implementation in another language — the reason the
rules of this project are JSON and not a script.

Below, every operator with an example that would stand in a real rule.

### Reading the document

| Operator | Example | What it gives |
|---|---|---|
| `value` | `{"value": "/BG-22/BT-112"}` | the value at one path, typed by the registry |
| `const` | `{"const": "S"}`, `{"const": 2}`, `{"const": true}` | a literal: text, a whole number, a truth value |
| `exists` | `{"exists": "/BG-25/*/BG-27/*"}` | whether the document carries anything there |
| `absent` | `{"absent": "/BG-22/BT-114"}` | whether it carries nothing there |
| `atRoot` | `{"atRoot": {"value": "/BT-5"}}` | an expression weighed at the document rather than at the business group instance the rule runs in |
| `minorUnit` | `{"minorUnit": [{"value": "/BT-5"}, "iso-4217"]}` | the number of fraction digits the publisher of a code list gives a code |
| `unit` | `{"unit": {"const": 2}}` | the value of one unit at that many fraction digits, here `0.01` |

`value` addresses exactly one value and refuses a pattern with an asterisk: what to do with
many values is the aggregates' business. `exists` and `absent` take either, and either a
business term or a business group. A path is relative to the context of its rule; only `atRoot`
reaches past it, to the document — a rule about a line that needs the invoice currency. `minorUnit`
and `unit` say how many fraction digits an amount carries and how large one unit of the currency
is; the second operand of `round` and `decimals` is a whole number or an expression yielding one.

### Comparing

| Operator | Example | What it gives |
|---|---|---|
| `eq` | `{"eq": [ {"value": "/BG-23/BT-118"}, {"const": "S"} ]}` | whether the two are the same |
| `ne` | `{"ne": [ {"value": "/BG-22/BT-115"}, {"const": "0"} ]}` | whether they differ |
| `lt` | `{"lt": [ {"value": "/BT-2"}, {"value": "/BT-9"} ]}` | whether the first is smaller, or earlier |
| `le` | `{"le": [ {"value": "/BG-25/*"}, … ]}` — see note | at most |
| `gt` | `{"gt": [ {"value": "/BG-22/BT-115"}, {"const": "0"} ]}` | greater |
| `ge` | `{"ge": [ {"value": "/BG-23/BT-119"}, {"const": "0"} ]}` | at least |

Numbers compare as numbers, dates as dates, text by code point, and a truth value only with
`eq` and `ne`. The comparison is decided by the registry's data type and not by what the two
values look like, so `"10"` is never smaller than `"9"` at a decimal term. (The `le` row is an
illustration of the operator, not of a path: an aggregate or a `value` stands on each side.)

### Arithmetic

| Operator | Example | What it gives |
|---|---|---|
| `add` | `{"add": [ {"value": "/BG-22/BT-109"}, {"value": "/BG-22/BT-110"} ]}` | the sum of two or more |
| `sub` | `{"sub": [ {"value": "/BG-22/BT-112"}, {"value": "/BG-22/BT-113"} ]}` | the first minus the second |
| `mul` | `{"mul": [ {"value": "/BG-25/*/BT-129"}, {"value": "/BG-25/*/BG-29/BT-146"} ]}` | the product of two or more |
| `div` | `{"div": [ {"value": "/BG-23/BT-116"}, {"const": 100} ]}` | the first divided by the second |
| `abs` | `{"abs": {"value": "/BG-22/BT-114"}}` | without its sign |
| `round` | `{"round": [ {"div": [ … ]}, 2 ]}` | half up to that many fraction digits |

BR-CO-17 — the VAT category tax amount is the taxable amount times the rate over a hundred,
rounded to two decimals — is exactly:

```json
{ "eq": [
  { "value": "/BT-117" },
  { "round": [ { "div": [ { "mul": [ {"value": "/BT-116"}, {"value": "/BT-119"} ] },
                          { "const": 100 } ] }, 2 ] } ] }
```

in a rule whose context is `/BG-23/*`.

### Aggregates

| Operator | Example | What it gives |
|---|---|---|
| `sum` | `{"sum": "/BG-25/*/BT-131"}` | the total over every match; zero over none |
| `min` | `{"min": "/BG-25/*/BT-131"}` | the smallest; absent over none |
| `max` | `{"max": [ {"value": "/BG-22/BT-113"}, {"const": "0"} ]}` | the largest of a list of expressions |
| `count` | `{"count": "/BG-25/*"}` | how many values or business group instances there are |

`sum`, `min` and `max` take either a pattern or a list of expressions; `count` takes a pattern.
An aggregate over the whole document is computed once per document for the whole run, however
many rules ask for it.

### Text and codes

| Operator | Example | What it gives |
|---|---|---|
| `inList` | `{"inList": [ {"value": "/BG-23/BT-118"}, "untdid-5305" ]}` | whether the code is on the snapshot the pack names |
| `matches` | `{"matches": [ {"value": "/BT-1"}, "[A-Z]{2}[0-9]+" ]}` | whether the text is the pattern, anchored at both ends |
| `len` | `{"len": {"value": "/BT-20"}}` | how many code points the text has |

`matches` is anchored: the pattern is matched against the whole value, so no `^` or `$` is
needed and adding them changes nothing.

### Logic

| Operator | Example | What it gives |
|---|---|---|
| `and` | `{"and": [ {"exists": "/BT-9"}, {"exists": "/BT-20"} ]}` | true when every operand is |
| `or` | `{"or": [ {"exists": "/BT-9"}, {"exists": "/BT-20"} ]}` | true when one is |
| `not` | `{"not": {"exists": "/BG-22/BT-114"}}` | the opposite |
| `if` | see below | one of two, by a condition |

`if` is how a condition rule of clause 6.4.2 is written. BR-CO-25 — where the amount due for
payment is positive, either the payment due date or the payment terms must be there:

```json
{ "if": {
    "condition": { "gt": [ {"value": "/BG-22/BT-115"}, {"const": "0"} ] },
    "then":      { "or": [ {"exists": "/BT-9"}, {"exists": "/BT-20"} ] } } }
```

`else` may be left out, in which case it is `true`: the rule says nothing about an invoice its
condition does not apply to. A conditional that leaves `else` out therefore has a truth value
in `then`.

### Over the instances of a group

| Operator | Example | What it gives |
|---|---|---|
| `forEach` | `{"forEach": {"group": "/BG-25/*", "assert": … }}` | true when it holds in every instance; the failing instance is named in the finding |
| `all` | `{"all": {"group": "/BG-23/*", "assert": … }}` | the same truth value, without naming an instance |
| `any` | `{"any": {"group": "/BG-23/*", "assert": {"eq": [ {"value": "/BT-118"}, {"const": "S"} ]}}}` | true when it holds in at least one |

The three differ in two ways that matter. `forEach` and `all` are the same statement — every
instance — and `forEach` additionally puts the path of the first failing instance into the
finding, so a reader is told which line was wrong; `all` is the quantifier to use inside a
larger expression, where there is no failing instance to name. `any` is the existential one and
is false over no instances at all, where `forEach` and `all` are true.

Inside a quantifier the paths are relative to the instance: within
`{"group": "/BG-25/*"}` the path `/BT-131` is that line's net amount and
`/BG-27/*/BT-136` is that line's allowances.

## Rules written in Java

A few rules do not fit a closed operator set, and a language large enough for them would be a
programming language in a data file. Those are `JavaRule` implementations under the same
identifier, in the same pack, producing the same finding — a test in `esj-rules` writes one rule
both ways and asserts the two findings equal, member for member. A Java rule reads the invoice
through `RuleContext`, which records every read in order — the paths the finding reports — and
resolves every path against the registry as a rule file's paths are resolved.

**The manifest names the classes; it does not load them.** A file that could name a class the
engine then instantiates would be a file that decides what code runs, and a pack may arrive
from a directory a caller was handed. So the caller passes the instances to
`RuleEngine.compile`, which checks that exactly the named classes arrived: one missing is a
pack that would silently check less than it claims, and one too many is a rule nobody declared.

## Code lists

A rule asks `inList` about a list identifier; the pack manifest says which day's snapshot of
that list this pack decides against. Two runs of the same pack version over the same document
therefore give the same verdict however far apart they are, which is the only way a validation
report keeps its meaning in an archive.
[`en16931/1.3.16/codelists/SOURCES.md`](en16931/1.3.16/codelists/SOURCES.md) is where the
snapshots come from, what the publisher says about reuse, and which lists this build does not
yet carry. A pack that names a list it has no snapshot for does not compile: a membership test
against a list nobody loaded would pass every code, and a rule that silently passes everything
is worse than a pack that will not start.

Two things in that page a reader of a rule should know. Several of the snapshots are the
European Commission's listing of which codes of a list this standard admits rather than the whole
list of its originating body, which is what a rule of the standard asks about — the tax point
date code has three values there and not the hundreds UNTDID 2005 carries. And the pages of UNECE
and of the Library of Congress refuse automated retrieval, which is recorded rather than worked
around; where a snapshot could only be taken from a second publication, its row says so.

## What it costs

A pack of five rules — one summing over every line, one evaluated once per line, one walking a
group inside a line, one counting, and one asking the same document-wide sum as the first — over
a synthetic invoice of the shape `conformance/scale` generates, on a JDK 21 with a four gibibyte
heap:

| Invoice lines | One evaluation | Cost per line, against the ten-thousand-line run |
|---|---|---|
| 10 000 | 53 ms | 1.0 |
| 100 000 | 423 ms | 0.8 |
| 300 000 | 1 271 ms | 0.8 |

Thirty times the lines cost twenty-four times the time: the cost per line does not grow. The
measurement is a test of `esj-rules`, taken on every build, so a linear pass turned quadratic
fails it. **How large the largest measured document is, is the decision of whoever runs the
build**: the default is a hundred thousand lines, which fits the two gibibytes the module asks
for; the three-hundred-thousand-line run needs a larger heap:

```console
$ mvn -pl esj-rules test -Desj.rules.linearity.maxLines=300000 -DargLine=-Xmx4g
```

Three things make it so. The business group instances of a rule's context come from **one
indexed pass** over the document. An aggregate over the whole document is **computed once for
the whole run**, so ten rules that mention the sum of the line net amounts walk the lines once.
A pattern inside a line is answered from **one range of the sorted map**, so it costs the size
of the line, not of the invoice.

## The packs this repository carries

| Pack | Edition | Rules | Artefacts | Coverage |
|---|---|---|---|---|
| [`en16931/1.3.16/`](en16931/1.3.16/pack.json) | EN 16931-1:2017+A1:2019/AC:2020 | 217, of which 189 in this language and 28 in Java | release 1.3.16 | [`conformance/rules/coverage.md`](../conformance/rules/coverage.md) |
| [`en16931-2026/0.1/`](en16931-2026/0.1/pack.json) | EN 16931-1:2026 | 252, of which 104 taken over from the pack above and 35 in Java | none is published | [`conformance/rules-2026/coverage.md`](../conformance/rules-2026/coverage.md) |

Every rule of the first pack has a case in `esj-rules`: a document the rule is silent on and the
same document with one thing changed, which it speaks on. Over the 86 instances of the
conformance corpus the pack and the official EN 16931 Schematron of the same release report on
the same four ([`corpus.md`](../conformance/rules/corpus.md)). Over 448 mutations broken on
purpose, of the 217 rules the two report the same identifiers on every shape measured for 166,
agree on one shape and differ on another for 33, differ outright for 16 and cannot be compared
for 2 — each of the 51 named in [`ledger.md`](../conformance/rules/ledger.md), none open;
[`not-applicable.md`](../conformance/rules/not-applicable.md) lists the rules it does not carry.

**No official validation artefact is published for EN 16931-1:2026**, so nothing in the second
pack is corroborated by one and a document of that edition never reaches `VALID`. Every rule
names in `oracle` what stands behind it, and
[`conformance/rules-2026/ledger.md`](../conformance/rules-2026/ledger.md) measures both: the 108
rules the edition leaves unchanged go through the artefacts of release 1.3.16 over the document
written back, the 144 it changed or adds through cases of this project.

## Adding a rule

1. Read the statement in EN 16931-1, clause 6.4: the identifier, the terms, the condition.
   Write it in this language, in this project's words. Do not open the CEN artefacts' XPath;
   their behaviour is the oracle, not the source.
2. Give it the identifier the standard gives it, its clause in `source`, and the terms it reads
   in `terms`.
3. Add a positive case and a negative fixture — a corpus instance, a location in it and one
   change there, as data, so that no broken invoice is checked in.
4. Where the edition has artefacts, run the rule beside them over the same bytes and record
   every deviation in the ledger; where it has none, say so in `oracle`. Only a measurement
   licenses a claim.

`CONTRIBUTING.md`, under **Adding or changing a semantic rule**, is the same four steps with the
files each touches and the tests that hold them together.
