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
carve-out: `en16931/<version>/codelists/` holds dated snapshots of code lists published by other
bodies. Those files are not this project's work, none of the four publishers states terms for
the data, and [`en16931/1.3.16/codelists/SOURCES.md`](en16931/1.3.16/codelists/SOURCES.md)
records the source, the digest and that open question for each. Everything else here — the rule
statements, the manifests, the language — is the project's own. It is not
[`../packs/`](../packs/README.md), which holds the third-party validation artefacts the syntax
engine executes, each under the licence it came with; the two are kept apart for that reason.

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
  "verifiedAgainst": "CEN/TC 434 eInvoicing EN 16931 validation artefacts, release 1.3.16",
  "description": "…",
  "codeLists": { "untdid-5305": "2026-09-19" },
  "javaRules": ["de.bsnsoft.esj.rules.en16931.SomeRule"],
  "files": ["rules/br.json", "rules/br-co.json"],
  "rules": [ … ]
}
```

| Member | What it is |
|---|---|
| `id`, `version` | the pack, which appears in every finding it produces; a released version is never edited, a newer list or a corrected rule is a new version |
| `verifiedAgainst` | the release of the official artefacts this pack's behaviour is compared against — a statement of what was measured, not a claim of equivalence |
| `codeLists` | which day's snapshot of each code list this pack decides membership against ([`en16931/1.3.16/codelists/SOURCES.md`](en16931/1.3.16/codelists/SOURCES.md)) |
| `javaRules` | the classes of the rules the language cannot express; naming a class is not loading it, see below |
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
| `context` | what the rule is a statement about: `/` for the document, or a business group pattern such as `/BG-25/*`. The rule is evaluated once per instance |
| `terms` | the business terms and groups the rule reads — documentation, and the column a coverage table is built from |
| `assert` | what must hold; a finding is produced when it is false |
| `warn` | optional: a second `assert` and `message`, weighed only where the first assertion holds, whose failure is a warning and decides no verdict |
| `bind` | expressions the message may show under a name, evaluated only when the rule fails |
| `message` | what the finding says, in this project's own words |
| `source` | the clause of the standard, as a reference and never as a quotation |
| `note` | optional: why the rule reads the way it does where the statement admits more than one reading — a tolerance, a rounding, a place where the official artefacts settle what the norm leaves open |

A rule with a `warn` says two things: the first assertion decides the verdict, the second is
noted. It is written where the two official artefacts of a release do not ask the same closeness
of the same two figures — the rule faults from the wider reading on and warns inside the zone
only one artefact grants, naming that syntax (`BR-CO-17`; the `*-08` family says it in Java, by
answering `warn(context)` beside `check(context)`).

### Context: what a rule is about, and how often it runs

A rule with the context `/` is a statement about the document and runs once. A rule with the
context `/BG-25/*` is a statement about an invoice line and runs once per line; inside it,
paths are written relative to that line, so `/BT-131` is *this* line's net amount. An invoice
with three hundred thousand lines runs such a rule three hundred thousand times, and the
engine is built so that this costs three hundred thousand times one line rather than three
hundred thousand times the invoice.

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

**Unit prices, quantities and percentages are never rounded, capped or normalised.** EN 16931
fixes Amount at two fraction digits and leaves Unit Price Amount, Quantity and Percentage
unlimited, for the reason Annex A.2 gives: a net price derived from a gross price needs many
decimals. So `decimals` — the operator the `BR-DEC-*` rules are written with — applies to
Amount alone, and a rule that capped the scale of BT-146, BT-129 or BT-119 does not compile.

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
far beyond any figure an invoice carries, and every amount a rule compares is rounded to two
decimals by the rule that computes it, so the choice can change nothing a rule decides. It
exists so that a rule dividing by a base quantity of three has a number to go on rather than
an exception. Division by zero is absent.

`round` is always half up, which is what EN 16931-1, 6.5.13 asks for, and it is the only
operator that rounds.

## The operators

An expression is a JSON object with exactly one member, whose name is the operator. There are
thirty-one of them and there is no thirty-second: the set is closed, and growing it means changing
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

`value` addresses exactly one value and refuses a pattern with an asterisk: what to do with
many values is the aggregates' business. `exists` and `absent` take either, and either a
business term or a business group.

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

A few rules of a real pack do not fit a closed operator set, and a language large enough for
them would be a programming language embedded in a data file. Those are written in Java, as
`JavaRule` implementations, under the same rule identifier, in the same pack, producing the same
finding: which side of that line a rule fell on is an implementation detail and not a fact about
the invoice. A test in `esj-rules` writes one rule both ways and asserts that the two findings
are equal, member for member. A Java rule reads the invoice through `RuleContext`, which records
every read in the order it was made — the record the finding reports as the paths the rule
looked at — and resolves every path against the registry as a rule file's paths are resolved.

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

Thirty times the lines cost twenty-four times the time: the cost per line does not grow.

The measurement is a test of `esj-rules`, so it is taken on every build and a change that
turned a linear pass into a quadratic one would fail it rather than be noticed in the field.
**How large the largest measured document is, is the decision of whoever runs the build**: the
default is a hundred thousand lines, which fits the two gibibytes the module asks for and which
an ordinary continuous integration machine has. The three-hundred-thousand-line run of the table
above needs a larger heap and is one command away:

```console
$ mvn -pl esj-rules test -Desj.rules.linearity.maxLines=300000 -DargLine=-Xmx4g
```

Three things in the engine are what make it so, and each is the answer to a way of getting it
wrong. The business group instances of a rule's context come from **one indexed pass** over the
document, not from a scan per rule. An aggregate over the whole document is **computed once for
the whole run**, so a pack in which ten rules mention the sum of the line net amounts walks the
lines once and not ten times. A pattern evaluated inside a line — that line's allowances — is
answered from **one range of the sorted map**, so it costs the size of the line and not the size
of the invoice.

## The pack this repository carries

[`en16931/1.3.16/`](en16931/1.3.16/pack.json) is the EN 16931 pack: 217 rules over the business
terms, 189 in this language and 28 in Java, with seventeen code list snapshots. What it covers,
rule by rule, is [`conformance/rules/coverage.md`](../conformance/rules/coverage.md); what it says
about documents that are known to be good is
[`conformance/rules/corpus.md`](../conformance/rules/corpus.md); the rules it does not carry and
why are [`conformance/rules/not-applicable.md`](../conformance/rules/not-applicable.md); and what
happens when the pack and the official artefacts are run over the same bytes, including bytes
broken on purpose, is [`conformance/rules/ledger.md`](../conformance/rules/ledger.md).

Two claims are worth making here. Every rule has a case in `esj-rules`: a document the rule is
silent on and the same document with one thing changed, which it speaks on. And over the 86
instances of the conformance corpus — invoices the official validator accepts — this pack and
the official EN 16931 Schematron of the same release report on the same four, with the same
rule identifiers, and are silent on the other 82.

Agreement with the official artefacts beyond that is measured rather than claimed: both engines
are run over the same bytes, including 448 mutations of the corpus broken on purpose, and the
identifiers are counted rule by rule. Of the 217 rules, the two report the same identifiers on
every shape measured for 166, agree on one shape of a defect and differ on another for 33,
differ outright for 16 and cannot be compared at all for 2 — each of the 51 named in
[`conformance/rules/ledger.md`](../conformance/rules/ledger.md) with the reason for it, and
none of them open. That page is what licenses a claim, and no page of this repository says more
than the figures in it.

## Adding a rule

1. Read the statement in EN 16931-1, clause 6.4: the identifier, the terms, the condition.
   Write it in this language, in this project's words. Do not open the CEN artefacts' XPath for
   it; their behaviour is the oracle, not the source.
2. Give it the identifier the standard gives it, its clause in `source`, and the terms it reads
   in `terms`.
3. Add a positive case and at least one negative fixture — an instance of the conformance
   corpus, a location in it and one change to make there, kept as data, so that no broken
   invoice is checked in.
4. Run it beside the official artefacts over the same bytes and record the agreement and every
   deviation in the ledger. Only the ledger licenses a claim, and the claim never goes further
   than the figures in it.

`CONTRIBUTING.md`, under **Adding or changing a semantic rule**, is the same four steps with
the files each one touches and the tests that hold them together.
