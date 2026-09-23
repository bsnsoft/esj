# The esj command line

*Part of [EN16931 Semantic JSON](../README.md).*

`esj-cli` is the same libraries behind a Unix tool, and this page is its complete reference. It
reads bytes, recognizes which of the three syntaxes they are in and writes what the libraries
return: the document to the standard output, diagnostics to the standard error stream, and an
exit code. Every fenced `console` block here is run by
`esj-cli/src/test/java/de/bsnsoft/esj/cli/ReadmeCliExamplesTest.java`; a line of
three dots stands for output left out.

## Building and running it

```text
mvn -B verify
```

writes the self-contained `esj-cli/target/esj.jar`. `bin/esj` runs it, and `bin\esj.cmd` does
the same on Windows:

```text
bin/esj --help
```

The wrapper finds the jar relative to itself, so a checkout works without installing anything.
It runs the virtual machine with `-Xms32m -Xmx512m -XX:+ExitOnOutOfMemoryError`, the command line
[`deployment.md`](deployment.md) prescribes; `ESJ_JAVA_OPTS` replaces that list, so a larger heap
is `ESJ_JAVA_OPTS='-Xmx2g -XX:+ExitOnOutOfMemoryError' bin/esj …`. A machine without a JDK runs
one of the artefacts of [`install.md`](install.md), which answer as this jar does. The examples
below are written as `esj` and name files of this repository, so they can be pasted from its root.

| Command | What it does |
|---|---|
| `convert` | read UBL, CII or ESJ and write ESJ, in pretty form or `--canonical` |
| `validate` | run the official artefacts of the profile, the structural layers L1 to L3 and the business rules |
| `render` | write the invoice as a PDF, or with `--html` as one self-contained page |
| `embed` | write the invoice into a PDF/A-3 file, so that pages and invoice are one file |
| `inspect` | one page: what the document is, who it is between, what it comes to |
| `extract` | write the electronic invoice of a PDF out, or list what the PDF carries |
| `get` | the value at one semantic path |
| `list` | every path with its type and its value, one per line |
| `diff` | the paths at which two documents differ, across syntaxes |
| `canonicalize` | the canonical bytes, or the two digests taken over them |
| `upgrade` | write an ESJ document as a document of another edition of the semantic model |

[`editions.md`](editions.md) is the reference for `upgrade` and for editions: its option table,
what every other command does with a document of a non-default edition, and the refusals. A
component written for one edition refuses a document of another with exit code 4, naming both.

Every command reads a file name, or `-` for the standard input — `diff` reads two — and takes
`--from` to name the syntax instead of recognizing it, `--importer` to choose the reader,
`--extension xrechnung`, `--extension b2c` or both separated by a comma to load an extension
registry, `--verbose` and `--debug`, and the limit switches of [Limits](#limits) below. An input may be an ESJ document, a UBL invoice or credit
note, a cross industry invoice, or a PDF carrying one of the three — see
[Hybrid invoices (PDF)](#hybrid-invoices-pdf). `--attachment <name>` names the attachment of
such a PDF to read, `--attachment-index <position>` names it by the position the tool prints
where two attachments carry one name, and `--strict` refuses a document whose bytes are not
written in the encoding it declares instead of recoding them.

`esj --version` prints the version of the tool, of the ESJ format, of the semantic model it writes
by default and of the registries it carries; `esj --list-packs` prints the validation packs, their
components, documents, licence and the number of rules each profile levels. Neither takes an input.

## convert

A UBL invoice in, an ESJ document out, in the pretty form of `SPEC.md` section 7.7:

```console
$ esj convert conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml
{
  "format": "EN16931-Semantic-JSON",
  "version": "0.1",
  "semanticModel": "EN16931-1:2017+A1:2019/AC:2020",
  "values": {
    "/BT-1": "123456XX",
    "/BT-2": "2016-04-04",
    "/BT-3": "380",
    "/BT-5": "EUR",
...
```

`-` reads the standard input instead. At the end of the document is the provenance: `source`
carries the syntax the bytes were written in and the SHA-256 of those bytes, which is not part
of the semantic identity (`SPEC.md` section 4.7).

```console
$ cat conformance/kosit/business-cases/standard/01.01a-INVOICE_uncefact.xml | esj convert -
...
  "source": {
    "syntax": "CII",
    "sha256": "727b51982a84c9b406599a7384783570910440ed405c9bb6d918b22442636886"
  }
}
```

`--canonical` writes the canonical bytes of `SPEC.md` section 7 instead: no insignificant
whitespace, no trailing line feed, and the same bytes from any conformant implementation.
`--pretty` is the default and asking for both at once is refused.

### Writing UBL and CII

`--to cii` writes a UN/CEFACT CII D16B invoice instead, from the same binding tables the reader
matches documents against. The input may be UBL, CII or ESJ, so a UBL invoice goes over to CII
in one command, through the semantic model.

```console
$ esj convert --to cii --out invoice.cii.xml \
    conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml
$ esj validate invoice.cii.xml
...
VALID
$ echo $?
0
```

`--out` writes the result to a file; without it the invoice goes to the standard output.

A syntax has no place for everything a semantic document may hold, and every value the writer
could not place becomes a warning on the error stream, collapsed one line per distinct sentence
with a count, and every one of them under `--verbose`:

```console
$ esj convert --to cii --extension xrechnung --out extension.cii.xml \
    conformance/kosit/business-cases/extension/04.01a-INVOICE_ubl.xml
info: the profile this document names levels BR-CO-16 more strictly for a cross industry invoice than for a UBL invoice, so the file written here can be refused where the source was not; esj validate of it says whether this invoice trips it
warning: 130 values of the document have no place in this syntax and were not written
  TERM_NOT_BOUND: a business group above BT-126 has no place in this syntax, so neither has the term (13 times)
...
```

Those are the sub invoice lines of the XRechnung extension, which it binds for UBL Invoice alone.
The `info:` line is the other half of a conversion: a core invoice usage specification states its
levels per scenario, and a scenario is a profile *and* a syntax, so the file written here will be
judged by the target syntax's table ([`validation.md`](validation.md#a-native-finding-is-levelled-by-the-profile-as-an-artefacts-is)).

A syntax may also require an element that no business term of the document states, and the
binding table says what is written there. Every such value is an `info:` line of its own on the
error stream, collapsed the same way; nothing was lost, so it is not a warning and the exit code
stays 0. [`bindings.md`](bindings.md) names the three of this release.

`--output json` carries the same list as data. The report goes to the standard output, so the
invoice needs somewhere else to go and `--out` becomes required:

```console
$ esj convert --to cii --out invoice.cii.xml --output json \
    conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml
{
  "input": "conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml",
  "detected": "ubl",
  "importer": "streaming",
  "to": "cii",
  "wrote": "a cross industry invoice",
  "out": "invoice.cii.xml",
  "bytes": 8209,
  "values": 64,
  "complete": true,
  "dropped": 0,
  "notPlaced": []
}
```

[`cii-roundtrip.md`](../conformance/writers/cii-roundtrip.md) and
[`ubl-roundtrip.md`](../conformance/writers/ubl-roundtrip.md) measure the two writers.

| Option | What it does |
|---|---|
| `--to cii\|ubl` | write a cross industry invoice, or an OASIS UBL 2.1 Invoice or Credit Note |
| `--ubl-document invoice\|creditnote\|auto` | which UBL document to write; `auto` is the default and takes it from the invoice type code BT-3, and the run says which it wrote |

```console
$ esj convert examples/credit-note.esj.json --to ubl --out credit-note.xml --verbose
...
info: converted examples/credit-note.esj.json (ESJ, 46 values) to a UBL credit note
...
```

## Which reader reads the XML

This tool ships two readers, and `--importer` chooses between them for every command that takes
an XML input.

| Token | What it is |
|---|---|
| `streaming` | **The default.** The document is matched against the binding tables of `model/bindings/` one element at a time. The reader holds one element and the values it has produced, so an invoice of tens of megabytes costs seconds. The one element it holds is held within `--max-buffered-bytes` and `--max-buffered-elements`; `conformance/readers.md` names the one class of document that reaches them and the other reader does not. |
| `xslt` | The vendored XRechnung visualization stylesheets, whose output is mapped to semantic paths. It builds three trees of the document; `docs/deployment-measurements.md` has what that costs at size. |

They share no code: the second is the oracle the first is measured against on every build. Over
the 86 instances of the conformance corpus they produce the same document for 74 and differ for
the other 12 at three business terms, all three limitations of the XSLT path, which
`conformance/readers.md` records with the counts. An ESJ input is read by neither, and
`esj inspect` says so on its `Read with` line.

## validate

`esj validate` runs three checks over one input and gives one verdict.

The **syntax engine** runs the official artefacts of the profile BT-24 names — the XML Schema
modules of its syntax, the EN 16931 Schematron of CEN/TC 434 and the Schematron of the core
invoice usage specification — over the XML exactly as it arrived; they are carried under `packs/`
unmodified and executed as data (`esj --list-packs`, `packs/SOURCES.md`). The **semantic engine**
runs the structural layers of SPEC section 9 — L1 for an ESJ input, L2 and L3 for both — and then
the business rules of EN 16931-1, clause 6.4 over the business terms. What each finding category
means and what their answers were measured against is [`validation.md`](validation.md).

Both blocks are printed, both name what they did not run, and the last line is the verdict:

```console
$ esj validate conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml
Input:            conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml
Detected syntax:  UBL Invoice
Semantic model:   EN16931-1:2017+A1:2019/AC:2020
Profile:          urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0 (pack xrechnung/3.0.2/2026-08-31)

Syntax
  XML:                       OK
  UBL 2.1 XSD:               OK
  EN 16931 UBL Schematron:   OK
...
Semantic
  ESJ format (L1):           not checked
  Model (L2):                OK
  Cardinality (L3):          OK
  EN 16931 business rules (native, pack en16931/1.3.16): OK

VALID
$ echo $?
0
```

The `Profile` line names what the document says about itself in BT-24 and the pack chosen for
that claim.

The rows of the syntax block are the components of that pack, in the order its manifest lists
them, under the pack's own names — `en16931-ubl-schematron`. The components of the other syntax
go unused and are printed under `--verbose` and in the JSON report.

A severity in the syntax block is the artefact's flag or the level the document's profile gives
that rule, never a judgement of this tool; the line names both where they differ
([`validation.md`](validation.md#two-publishers-two-levels)):

```text
BR-CL-13 [information, flagged fatal by the artefact] …
```

The levels are data of the pack, from the configuration the specification publishes with its
artefacts ([`packs/SOURCES.md`](../packs/SOURCES.md), and
[`conformance/syntax/ledger.md`](../conformance/syntax/ledger.md) for what they come to).

An ESJ document was never XML, so no artefact will look at it as it stands. It is therefore
written into a syntax binding in memory — a cross industry invoice, or a UBL document with
`--via ubl` — and the artefacts of its profile run over the result. Nothing is written to disk
and the document under test stays the ESJ one; the row names the binding it was read through:

```console
$ esj validate examples/standard-invoice.esj.json
Input:            examples/standard-invoice.esj.json
Detected syntax:  ESJ
Semantic model:   EN16931-1:2017+A1:2019/AC:2020

Syntax
  not applicable (no XML)
  official artefacts over the written CII:
  CII D16B XSD:              OK
  EN 16931 CII Schematron:   OK
...
VALID
$ echo $?
0
```

Where the writer had to leave something out, the XML is not the document that was handed over: the
row is not applicable, the cause is `term-not-in-syntax`, `term-not-stated` or
`element-not-in-model`, and the run ends `INDETERMINATE`. The row runs where the binding table states
a value for the element, and where everything left out belongs to an extension registry declaring
`"transport": "none"`, which the row names and `written.byDesign` carries ([b2c.md](b2c.md)).

A document the registry rejects leaves with exit code 1, the layer below the failure reported
as not checked rather than as passed:

```console
$ esj validate examples/invalid/unknown-term.esj.json
Input:            examples/invalid/unknown-term.esj.json
Detected syntax:  ESJ
Semantic model:   EN16931-1:2017+A1:2019/AC:2020

Syntax
  not applicable (no XML)
  official artefacts over the written CII:
    not run (a structural layer rejected the document)

Semantic
  ESJ format (L1):           OK
  Model (L2):                1 error
    ESJ-L2-UNKNOWN-TERM [error] /BT-999: the registry of EN 16931-1:2017+A1:2019/AC:2020 does not contain BT-999
  Cardinality (L3):          not checked
  EN 16931 business rules (native, pack en16931/1.3.16): not run (a structural layer rejected the document)

INVALID
$ echo $?
1
```

### The business rules, checked here

The third engine runs the business rules of EN 16931-1, clause 6.4 — `BR-*`, `BR-CO-*`, `BR-DEC-*`
and the code list rules `BR-CL-*` — over the business terms of the semantic model rather than over
the XPath of a syntax, so one rule decides a UBL invoice, a CII invoice and a document that was
never XML alike. [`../rules/README.md`](../rules/README.md) is the engine and the rule language,
[`../conformance/rules/coverage.md`](../conformance/rules/coverage.md) what the pack covers, and
[`../conformance/rules/ledger.md`](../conformance/rules/ledger.md) what it was measured against.

**For an ESJ document this is the only engine that checks the business rules**: there is no XML and no artefact; `examples/invalid/arithmetic-mismatch.esj.json` shows it, with exit code 1.

| Option | What it does |
|---|---|
| `--rules en16931` | check the EN 16931 pack this build carries — the default |
| `--rules none` | leave the business rules out |

`--rules none --no-syntax` is the fast path for a very large document: the structural layers
alone; `--no-syntax` alone adds the business rules, linear in the number of invoice lines.

Over an XML input both engines check those rules, so one rule identifier can appear twice in one
report — once against the XML tree, once against the imported document. Neither is merged into
the other: the text form names the overlap under the native findings, and `--output json` keeps
`syntax.findings` and `rules.findings` apart. A native finding of a rule the core invoice usage
specification levels down is levelled the same way: the line reads `info, fatal by the standard,
levelled by the profile <identifier>`, the JSON report carries both, and the pack itself knows no
profile ([`validation.md`](validation.md#a-native-finding-is-levelled-by-the-profile-as-an-artefacts-is)).

### Choosing, replacing and leaving out the pack

| Option | What it does |
|---|---|
| `--pack <directory>` | run a pack from a directory holding a `pack.json` — the way a release newer than this build reaches the engine |
| `--pack <id>` | run one of the bundled packs by its identity, `id/version/release` |
| `--no-syntax` | leave the official artefacts out and run the structural layers alone |
| `--via cii\|ubl` | which syntax an ESJ input is written to for the artefacts to read; `cii` is the default |
| `--max-runtime <duration>` | how long the whole command may take over the document: `500ms`, `90s`, `5m`, or a bare number of seconds — a global switch, described under [Limits](#limits) |

A manifest writes its own identity, so a directory can call itself the release this build carries:
the `Profile:` line then reads `supplied with --pack` and `syntax.pack.source` says `supplied`.
Nothing is fetched at run time. `--no-syntax` leaves a component of the complete check out, so the
run reaches no verdict: exit code 9 and `syntax-binding (skipped-by-caller)` in the last line and
in `reasons` ([`validation.md`](validation.md#--no-syntax-and---rules)).

`--max-runtime` is one number over the whole command: the clock starts before anything is
opened, and each step runs inside what is left. A run that reaches it while it is still deciding
leaves with **exit code 7 and no report at all** — never with a verdict of invalid: a document
the tool gave up on is not a document the tool rejected. The message names the step the time ran
out in, and where that step is the official artefacts it names both numbers, the whole command's
and what was left of it once the document had been read. Reached while the `--report` file is
being drawn, it is the other thing: the file is given up and the verdict stands (above).

```console
$ esj validate --max-runtime 1ms conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml
...
$ echo $?
7
```

#### When a profile brings no rules with it

The pack is chosen by what the document says in BT-24, and the two outcomes a pipeline has to
tell apart are printed under the rows:

```text
  no core invoice usage specification of this pack applies to this profile: the EN 16931
  artefacts ran, no CIUS rules did
```

```text
  no rule set of this pack applies to this profile: schema validation only
```

The first is `VALID` with exit 0, the second `INDETERMINATE` with exit 9 and the cause
`no-rules-for-profile`; both set `syntax.profileRulesSkipped` and `profile-rules` in
`notChecked` ([`validation.md`](validation.md#when-a-profile-brings-no-rules-with-it)).

### The JSON report

`--output json` writes the same report as an object for a program:

```console
$ esj validate examples/invalid/unknown-term.esj.json --output json
{
  "input": "examples/invalid/unknown-term.esj.json",
  "detected": "esj",
  "semanticModel": "EN16931-1:2017+A1:2019/AC:2020",
  "container": null,
  "invoice": {
    "checked": true,
    "ok": false,
    "reason": "layer-error"
  },
  "xml": null,
  "syntax": {
    "checked": false,
    "ok": null,
    "reason": "not applicable (no XML)",
    "customizationId": null,
    "pack": null,
    "profileNote": null,
    "profileRulesSkipped": false,
    "ran": [],
    "skipped": [],
    "findings": []
  },
  "written": {
    "required": true,
    "checked": false,
    "target": "CII",
    "reason": "not run (a structural layer rejected the document)",
...
  "layers": {
    "l1": {
      "checked": true,
      "ok": true,
      "findings": []
    },
    "l2": {
      "checked": true,
      "ok": false,
      "findings": [
        {
          "path": "/BT-999",
          "subject": null,
          "code": "ESJ-L2-UNKNOWN-TERM",
          "severity": "error",
          "message": "the registry of EN 16931-1:2017+A1:2019/AC:2020 does not contain BT-999"
        }
      ]
    },
    "l3": {
      "checked": false,
      "ok": null,
      "findings": []
    }
  },
  "rules": {
    "checked": false,
    "ok": null,
    "reason": "not run (a structural layer rejected the document)",
    "pack": null,
    "findings": []
  },
  "notChecked": [
    "cardinality-l3",
    "business-rules",
    "written-syntax"
  ],
  "reasons": [],
  "warnings": [],
  "information": [],
  "verdict": "INVALID"
}
```

`invoice` is the verdict on the document: `checked`, which says whether the rules of EN 16931
were the question asked of it, `ok`, which is `null` where they were not and the answer
otherwise, and `reason` naming which of `profile-not-en16931`, `xml-encoding`, `layer-error`,
`syntax-finding` and `rule-finding` — a business rule of EN 16931 the native pack faulted at
the level the verdict counts it at — it was where the answer is no. `ok` is `null` as well
for a run that found nothing fatal and did not perform the whole of the check, which is the
`INDETERMINATE` line; `ok: true` means completely checked, nothing fatal found. `invoice` is
separate from `container`: a container that is wrong about the invoice it carries says
nothing about the invoice. `syntax`, `written.syntax`, `rules`, `layers.l1`, `layers.l2` and
`layers.l3` each
carry `checked`, which says whether that check ran, and `ok`, which has three answers:
`true`, `false`, and `null` where the check did not run — and `null` again where a layer ran
over less than the whole document, which is the row the text form writes as `no verdict`.
That second case is the one `reasons` names rather than the layer's own findings: an
extension path no loaded registry describes leaves the finding on `l2` and leaves `l3` with
nothing to count, so both are `null` while only one says why.

Each finding of a layer carries `path`, `code`, `severity`, `message` and `subject`: the member
access the reader met the problem at, or the term or group a cardinality finding is about, and
`null` where the path says it all. A missing mandatory term is reported at the path of the
instance that lacks it, so `subject` is what names the term (specification, section 9.5).
`notChecked` lists the same thing as stable tokens: `format-l1` for an XML input, which is read
through the importer rather than as ESJ bytes; `model-l2` and `cardinality-l3` where the layer
above them ended the run before they were reached, and where they ran and could not measure what
they were asked to; `business-rules` where the native rule engine did not run — `--rules none`,
a document a structural layer rejected, or a model layer that measured nothing — which is about
that engine alone, since where `syntax.ran` names an EN 16931 Schematron component that did not
stop, those rules were checked by the artefact; `profile-rules` where a rule set of the pack was
left out because of the profile the document names ([When a profile brings no rules with
it](#when-a-profile-brings-no-rules-with-it)); `syntax-binding` for an XML input whose binding
was not checked, which is `--no-syntax` or `esj inspect`; and `written-syntax` for an ESJ input
the artefacts did not reach.

`written` is that last row as data, present whatever the input was: `required` says whether this
input needed it, `target` names the binding the document was written through, and `syntax` is
the same object as the one above it, read from different bytes.

`reasons` is the sharper list beside it and the one the verdict turns on: the components of the
**complete check** for this kind of input that did not run or did not complete, each with a
`component` token and a `cause` from the closed vocabulary of
[`validation.md`](validation.md#verdict-and-exit-code), non-empty where `verdict` is
`INDETERMINATE`. `notChecked` is the longer list — everything this run did not look at or could
not measure, including what there was nothing to look at — and says nothing about why.

Where the native rule engine ran, `rules` carries the pack that was run — `id` and `version` —
and `findings`, one entry per thing a rule of this project said:

```json
{
  "engine": "native",
  "category": "EN-BR",
  "severity": "fatal",
  "flag": "fatal",
  "code": "BR-CO-10",
  "message": "The sum of the invoice line net amounts (BT-131) is 314.86, and the sum of invoice line net amount (BT-106) at /BG-22/BT-106 carries 315.86.",
  "paths": ["/BG-22/BT-106", "/BG-25/*/BT-131"],
  "packId": "en16931",
  "packVersion": "1.3.16"
}
```

`paths` is where the rule looked, in the order it looked: a value read once appears as its own
path, and a value read as an aggregate appears as the pattern it was summed over. The message is
this project's own English, never a translation of a published assertion; `flag` is the level
the rule declares and `severity` the one the verdict is made on.

Where the syntax engine ran, `syntax` carries the customization identifier the document named,
the pack chosen for it with `source` saying whether it was `bundled` or `supplied`,
`profileNote` and `profileRulesSkipped` — the sentence for a person and the same fact for a
program — `ran` and `skipped`, every component of the pack with the reason each one did not run
and whether a component that ran `stopped` over the document, and `findings`, one entry per
thing an artefact said:

```json
{
  "engine": "schematron",
  "category": "EN-BR",
  "severity": "fatal",
  "flag": "fatal",
  "code": "BR-CO-16",
  "message": "[BR-CO-16]-Amount due for payment (BT-115) = …",
  "location": "/*:Invoice[namespace-uri()='urn:oasis:names:specification:ubl:schema:xsd:Invoice-2'][1]/*:LegalMonetaryTotal[namespace-uri()='urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2'][1]",
  "line": null,
  "column": null,
  "packId": "xrechnung",
  "packVersion": "3.0.2",
  "packRelease": "2026-08-31",
  "component": "en16931-ubl-schematron"
}
```

`severity` is the level the profile of the document gives that rule and is what the verdict is
made on; `flag` is the level the artefact itself set, which is what a comparison against another
tool running the same artefact is made on.

`message` and `location` are the artefact's own text, normalized in whitespace and otherwise
untouched, and `code` is the rule identifier its publisher gave it. The message above is shortened
on this page only, because a rule set's words travel under its own licence; a run prints them in
full. The `pack*` and `component` fields travel with every finding so that a report still means
something after the pack is replaced; `line` and `column` are `null` where the artefact reported
none.

Two codes are not a publisher's. `XSD-INVALID` stands for a schema message that named no
validity constraint, and `ARTEFACT-STOPPED` for a rule set that stopped over the document
instead of finishing — an amount no arithmetic of the artefact can carry is the case that
happens. The second is fatal: a rule set that did not finish has checked nothing, and its
component is in `syntax.ran` with `stopped` set to `true`. The native rule engine still answers
over such a document, so the two blocks can read very differently; a pack that will not
*compile* leaves with exit code 2 instead.

The sentence of a schema finding is the platform's, in the language the process runs in; the
`esj` command fixes that to English, and a program that embeds the library sets the locale of
its own process. No duration appears in any form: a report of this tool is compared, diffed and
checked in, so it says what the document decides and never what the machine was doing at the
time. What each artefact cost is on the error stream under `--verbose`.

`warnings` carries the content that could not be brought into the document, one entry per
distinct note with the number of times it was made; the distance between the source syntax and
EN 16931 itself — a supplementary component the standard does not give that term, an element
that was empty to begin with — is an information-level note and is in `information`, an array of
the same shape. The printed lines say the same in a `Conversion:` line, and no line where there
is nothing to report. Notes are counted rather than values, because one note can stand for a
whole subtree the importer skipped, and an empty note list is the absence of an observation
rather than a claim that everything reached the document.

Each layer gates the next: an L1 error ends the run and the later layers are reported as not
checked, never as passed; L2 gates L3 the same way (`--level l2` asks for the model layer
alone); and a document that fails the schema of its syntax is not one the rule sets are run
over, with `skipped` and the reason on their rows. Every check cuts its own list of findings at
twenty lines and says how many it left out; the count on the row above it is never cut, and
`--output json` carries every finding.

`--verbose` does five things on the error stream: it says what was detected and how long the run
took, in every command and not only in `esj convert`; it shows the importer's information-level
notes, which are otherwise silent; it lists every warning and every finding instead of the first
twenty of each; it prints what each artefact of the pack cost to compile and to run; and, for a
PDF, it says what the PDF library had to say about the file — the records that library writes
about a damaged object structure, which go nowhere without it (`docs/pdf-input.md`).

### The report

`--report` writes the whole run as one file to keep: what was judged with its digests, the packs
that judged it, the check table, the findings, and the invoice.

```console
$ esj validate conformance/pdf/factur-x.pdf --report report.html
...
Container:        OK
Invoice:          VALID
$ echo $?
0
$ esj validate examples/standard-invoice.esj.json --report-lang en \
    --report-time 2026-09-21T08:00:00Z --report proof.pdf
...
VALID
```

| Option | What it does |
|---|---|
| `--report <file.html\|file.pdf>` | write the report; the name says the form. A [`--max-runtime`](#limits) met while the file is being drawn gives up the file and not the verdict, with a line that says so |
| `--report -` | write it to the standard output; the lines a person reads then go to the error stream, and `--output json` is refused |
| `--report-format <html\|pdf>` | the form where no name says it, as for `--report -`; where the name says one, this must say the same or the run is refused with exit 2 — a file called `.html` never holds a PDF |
| `--report-lang <de\|en>` | the language of this report's own words and of the invoice in it; what a check, a pack or a rule of somebody else's is called travels from the run and is printed as it stands. Default: `de` |
| `--report-page <A4\|LETTER>` | the paper the PDF report is laid out for, its own pages and the invoice pages after them alike. Default: `A4` |
| `--report-time <moment>` | print this moment, exactly as written; without it the report names none |
| `--no-invoice` | leave the rendered invoice out |

The printed lines are the same with and without it; the exit code is not, where the report was
not delivered. A destination that cannot be written, and a report given up on the deadline, both
leave with exit code 6 — the output that was not written in full — unless the verdict is invalid
and keeps code 1, because code 0 says the caller received everything it asked for. The HTML form
carries the invoice in a sandboxed `iframe` written with `srcdoc`, which keeps the vendored KoSIT
style sheet and script off the report around it ([`rendering.md`](rendering.md)); printing it
cuts the invoice off at the frame, and the PDF form sets the invoice after the report as pages of
the same file instead, in the generic layout: a report is a proof, not a letter. Attachments of
the invoice travel inside either file. What it commits to, row by row:
[`validation.md`](validation.md#the-report).

The file is bounded: at most 200 findings per block — the heaviest first, so a cut never hides
an error behind warnings — and 2000 characters of any one text, each with the remainder counted.
The HTML form writes the whole rendering into one attribute, so a document whose rendering would
run past two million characters is left out of it, measured on the document before anything is
rendered: a rendering that does not fit in the page does not fit in the heap either, and a report
that lost the verdict to a heap exhaustion is worse than one without a picture. The PDF form has
pages and carries it. `--output json` carries every finding in full.

## Hybrid invoices (PDF)

A ZUGFeRD or Factur-X invoice is a PDF with the electronic invoice attached to it, and every
command that takes an input takes one. The file is recognized by its `%PDF-` header, the
attachment whose bytes spell an invoice is taken out of it, and from there the run is the run it
would have been if that XML had been the file. What the container had to say is reported beside
it and never mixed into it:

```console
$ esj validate conformance/pdf/factur-x.pdf
Input:            conformance/pdf/factur-x.pdf
Detected:         PDF (hybrid invoice container)
Embedded invoice: "factur-x.xml" (CII, 9622 bytes declared), position 1
Profile:          XRECHNUNG (the XMP packet declares "XRECHNUNG")

Container
  PDF structure              OK
  PDF/A-3 declared           yes, PDF/A-3B — declared, not validated
  Associated file (/AF)      OK (AFRelationship "Alternative")
  Factur-X XMP metadata      OK
  Embedded file params       OK

Detected syntax:  CII
Semantic model:   EN16931-1:2017+A1:2019/AC:2020
Profile:          urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0 (pack xrechnung/3.0.2/2026-08-31)

Syntax
  XML:                       OK
  CII D16B XSD:              OK
  EN 16931 CII Schematron:   OK
...

Semantic
  ESJ format (L1):           not checked
  Model (L2):                OK
  Cardinality (L3):          OK
  EN 16931 business rules (native, pack en16931/1.3.16): OK

VALID

Container:        OK
Invoice:          VALID
$ echo $?
0
```

The checks are structural: the embedded files name tree, the associated files array, the embedded
file dictionary, and the XMP packet with the Factur-X extension schema, including whether it
agrees with the attachment and with BT-24. A file that carries an ESJ document beside its invoice
gains the row `ESJ document attached`, which is the two checked against each other
([`pdf-output.md`](pdf-output.md#the-esj-document-beside-the-invoice)); a file without one has no
such row. **PDF/A conformance is none of them**; it is validated where
[`--verapdf`](#validating-the-pdfa-claim) names a validator, and nowhere else.

The two verdicts stay apart, either of them failing is exit code 1, and `--output json` carries
them as `container.ok` and `invoice.checked` with `invoice.ok`. A layer that did not run has not
failed: `invoice.reason` says which of `profile-not-en16931`, `xml-encoding`, `layer-error`,
`syntax-finding` and `rule-finding` kept it from running or made it fail.

**Nothing is read off the page.** A PDF whose invoice exists only as printed text carries no
structured invoice, and that is what is reported, with exit code 2.

| What the file is | What happens | Code |
|---|---|---|
| one attachment whose bytes spell an invoice | it is read | 0, 1, 7 or 9 |
| that invoice is a MINIMUM or BASIC WL profile | read, and not checked against EN 16931 | 1 |
| several that could be the invoice, and none named | refused, with the list and the two switches that name one | 2 |
| several that could be the invoice, one named | read, with `PDF-EMBEDDED-SEVERAL` naming the others | 0, 1, 7 or 9 |
| two attachments of one name, named with `--attachment` | refused; `--attachment-index <position>` names one | 2 |
| a name that leads to two files — a key the name tree lists twice, or a file specification holding two streams — and one of them could be the invoice, none named | refused like several candidates, both listed with the name they share | 2 |
| the same, one named with `--attachment-index` | read, with `PDF-EMBEDDED-DUPLICATE-NAME` (an error): `Container: INVALID` | 1 or 7 |
| a name that leads to two files, neither of which could be the invoice (two ESJ documents, two enclosures) | the invoice is read, with `PDF-EMBEDDED-DUPLICATE-NAME` (an error): `Container: INVALID` | 1 or 7 |
| an invoice a page refers to — a file attachment annotation, the associated files array of the page or of an annotation — beside the one the name tree lists | counted among the candidates, its place named, with `PDF-EMBEDDED-NOT-IN-TREE` | 2 |
| `--attachment` and `--attachment-index` naming two different attachments | refused | 2 |
| an XML attachment whose root element is beyond the window, declared `/Alternative`, `/Data`, `/Source` or nothing | counted among the candidates, with `PDF-EMBEDDED-UNDETERMINED` | 2 |
| the same, declared `/Supplement` or `/Unspecified`, beside exactly one invoice | left alone, with `PDF-EMBEDDED-UNDETERMINED` | 0, 1, 7 or 9 |
| the only attachment, and its root element beyond the window | refused as not established, naming the two switches that read it anyway | 2 |
| an attachment this reader cannot decode, declared `/Supplement` or `/Unspecified` | left alone, with `PDF-EMBEDDED-UNREADABLE` | 0, 1, 7 or 9 |
| an attachment this reader cannot decode, declared `/Alternative`, `/Data`, `/Source` or nothing | counted among the candidates, with `PDF-EMBEDDED-UNREADABLE` | 2 |
| an object stream or a cross-reference stream that decodes past `--max-pdf-bytes`, that this reader cannot measure, or that declares with the others more objects than the container may | refused before the library decodes it, with no verdict | 7 |
| none | `the PDF contains no structured invoice representation` | 2 |
| a ZUGFeRD 1.0 invoice, root `CrossIndustryDocument` | refused as unsupported | 4 |
| encrypted | refused wherever the parse sees the declaration; this tool never decrypts and takes no password, so a file that hides it is read as an ordinary container whose streams stay ciphertext and are reported as unreadable | 2 |
| broken, truncated, or a `/Predictor` stage declaring values this format does not define | refused as malformed, before the row length is computed | 2 |
| larger than a bound of this run | refused, naming the bound and the switch | 7 |

`--attachment <name>` names the attachment that is meant, on every command — `esj diff` applies
it to both of its inputs. Nothing in a PDF makes a name unique, so `--attachment` refuses a name
that two attachments carry — the name a file specification gives, or a key under which the name
tree lists two files — and `extract --list` ends the line of such an attachment with `one of 2 files
the name tree lists under "…"`; `--attachment-index <position>` names one by the position the listing prints,
counted from one, and is the one selector the file cannot influence. A file a page refers to rather
than the name tree is listed with its place, `not in the name tree: a file attachment annotation on
page 1` ([`pdf-input.md`](pdf-input.md#where-the-invoice-may-lie-and-what-counts-as-one)). Given both, the position
selects and the name is checked against it. Where several attachments could be the invoice and
the caller named one, `PDF-EMBEDDED-SEVERAL` names the others at warning level, and the
`Embedded invoice:` line, `container.attachmentIndex` and `"selected": true` say which one the
verdict is about. Where the *only* attachment is undetermined, the refusal says that what the
file holds was not established; either selector reads it anyway.

**MINIMUM and BASIC WL are not EN 16931 invoices**: they carry no invoice line. `esj validate`
reads such a file, reports the container, says `profile MINIMUM: not an EN 16931 invoice — EN
16931 validation not applicable`, writes `Invoice: NOT CHECKED (profile MINIMUM)` rather than
`INVALID`, and **leaves with exit code 1**, which in `--output json` is `invoice.checked: false`
and `invoice.ok: null` with `invoice.reason: "profile-not-en16931"`. BASIC, EN 16931, EXTENDED
and XRECHNUNG go down the ordinary path. ZUGFeRD 1.0 is refused rather than read: its root
element is `CrossIndustryDocument` and it is no binding of EN 16931, and exit code 4 keeps that
apart from the codes that say something about the document.

### Validating the PDF/A claim

`PDF/A-3 declared` is a declaration: it is read out of the XMP packet, and nothing in this build
checks it. `--verapdf` closes that gap with a veraPDF installation of your own — the directory it
was installed into, or its executable. It is never bundled and never downloaded: the reference
validator is under a copyleft licence and is in no artefact this project publishes.

```text
$ esj validate invoice.pdf --verapdf /opt/verapdf
  PDF/A-3 declared           yes, PDF/A-3B — veraPDF 1.30.2, "PDF/A-3B validation profile": PASS
```

The validator runs as a process of its own inside `--max-runtime`, and the row carries its
version, the profile it ran and its verdict; `--output json` carries the same under
`pdfaValidator`, beside `pdfa.validated`. A file it rejects is a container that is wrong about
itself: `Container: INVALID`, exit code 1. A validator that was asked for and could not run
leaves with 2, or with 7 where the clock ran out, and never with `VALID`. Without the switch the
row reads `declared, not validated` and has no part in the verdict. The same switch is on
[`embed`](#embed) and on `render --embed cii`, where it checks the file the invoice goes into
before anything is written into it.

### The container in `--output json`

The block of lines has an object beside it, under `container`, which is `null` for every input
that was not a PDF. `detected` still names the syntax of the invoice that was read:

```console
$ esj validate conformance/pdf/factur-x.pdf --output json
{
  "input": "conformance/pdf/factur-x.pdf",
  "detected": "cii",
  "semanticModel": "EN16931-1:2017+A1:2019/AC:2020",
  "container": {
    "ok": true,
    "attachment": "factur-x.xml",
    "attachmentIndex": 1,
    "kind": "CII",
    "profile": "XRECHNUNG",
    "en16931Invoice": true,
    "esj": null,
    "pdfa": {
      "declared": "PDF/A-3B",
      "part": 3,
      "conformance": "B",
      "validated": false
    },
    "pdfaValidator": null,
    "facturX": {
      "namespace": "urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#",
      "documentType": "INVOICE",
      "documentFileName": "factur-x.xml",
      "version": "1.0",
      "conformanceLevel": "XRECHNUNG"
    },
    "findings": [
      {
        "category": "PDF-STRUCTURE",
        "code": "PDF-STRUCTURE-PDFA",
        "severity": "info",
        "message": "the file declares PDF/A-3B; this is the declaration as the file writes it, and conformance to it was not validated"
      }
    ],
    "attachments": [
      {
        "position": 1,
        "selected": true,
        "name": "factur-x.xml",
        "kind": "CII",
        "invoice": true,
        "root": "{urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100}CrossIndustryInvoice",
        "mediaType": "text/xml",
        "declaredSize": 9622,
        "relationship": "Alternative",
        "associated": true
      }
    ]
  },
...
```

`ok` is the container verdict and nothing else: `false` where a `PDF-*` finding of error
severity was made, and silent about the invoice, which `invoice` answers on its own.
`attachment`, `attachmentIndex` and `kind` name the attachment the run was about, and are `null`
where a command reported a container without reading an invoice out of it. `profile` is the
profile as the report settled it and `en16931Invoice` whether the rules of the standard apply.

`esj` is the ESJ document beside the invoice: `null` where the file carries none, otherwise
`status` (`one` or `several`), `count`, `attachment`, `attachmentIndex`, `agrees` — `null`
wherever nothing was compared — and `pathsNotChecked` where it carried paths the invoice syntax
binds nothing of. `pdfa` is what the file declares about itself, with `validated: false` in the
object. `facturX` is the extension schema of the XMP packet, property by property; its namespace
says whether the file was written as Factur-X or as ZUGFeRD 2.0. `findings` carries the `PDF-*`
codes of [`pdf-input.md`](pdf-input.md). `attachments` lists everything the container carries:
`selected` marks the one the verdict is about, `kind` and `root` say what its bytes were, and
`name`, `mediaType`, `declaredSize` and `relationship` what the file claims — never believed.

### extract

`esj extract` hands back the bytes of the attachment, unchanged. `--list` says what the
container carries instead:

```console
$ esj extract conformance/pdf/factur-x.pdf --list
Input:        conformance/pdf/factur-x.pdf
Attachments:  1
  1  "factur-x.xml" (CII, 9622 bytes declared), media type "text/xml", AFRelationship "Alternative", in /AF
```

The classification is decided by the first bytes of each attachment and by nothing else: an
attachment called `factur-x.xml` that holds a UBL invoice is listed as a UBL invoice and the
disagreement is a finding. The declared size is what the file claims, not what was measured.

Without `--list` the attachment goes to the standard output, or to the file `--out` names:

```console
$ esj extract conformance/pdf/factur-x.pdf
<?xml version="1.0" encoding="UTF-8"?>
...
```

**The name of an attachment is never used as a file name.** It is attacker-controlled — path
separators, `..`, a misleading double extension, control characters (`SPEC.md`, section 12.5) —
so `esj extract` writes to the standard output or to the path `--out` names, and nowhere else.

`esj inspect` shows the same facts about the container as part of its page, before the usual
summary of the invoice. Where several attachments could be the invoice and none was named, it
prints that block and nothing else, leaves with exit code 2 and puts the refusal on the error
stream; `esj extract --list` lists the same attachments with exit code 0.

## Encoding: repair is not validation

An XML document declares the encoding of its own bytes, and the two sometimes disagree.
`esj validate` is strict, always: its verdict is about the bytes it was handed, so bytes that are
not written in the encoding they declare are a fatal syntax finding, category `XML`, code
`XML-ENCODING`, naming what was declared and what the bytes are, and the layers below are
reported as not checked. The syntax block opens with the row `XML bytes` and the finding under
it, the exit code is 1, and the row below it is `XML`. `--output json` carries the first as
`xml.ok` and `xml.findings` and the second under `syntax`.

`--after-repair` runs the checks a second time on the recoded bytes under a heading of its own;
the verdict line and the exit code stay those of the original bytes. Every other command exists
to get at the content, so it recodes — UTF-8, UTF-16, ISO-8859-1 and Windows-1252, and nothing
beyond those without saying so — whichever reader `--importer` chose. It writes one line to the
error stream and records the note `ENCODING_REPAIRED` in the report, where `esj inspect` and
`--output json` show it:

```text
warning: invoice.xml: encoding repaired: declared UTF-8, read as ISO-8859-1; esj validate is strict about this and --strict makes every command so
```

A repair is not a loss and is not counted as one. `--strict` turns it off for every command,
which is then exit code 2 with the reason.

## render

`esj render` writes an invoice as a PDF, or with `--html` as one self-contained HTML page; the
input is any of the three syntaxes or a PDF carrying one of them.

| Option | |
|---|---|
| `--out <file\|->` | where the rendering goes; required |
| `--html` | one self-contained HTML page instead of a PDF |
| `--lang de\|en` | language of the labels, the date picture and the decimal separator, not of the invoice. Default `de` |
| `--page A4\|LETTER` | the paper the PDF is laid out for; the HTML page has none. Default `A4` |
| `--layout generic\|letter` | the page layout of the PDF ([`rendering.md`](rendering.md#two-layouts)); without it the template decides, and where it says nothing, or there is none, the PDF is the letter. PDF only |
| `--template <file>` | a branded template: letterhead, logo, colours, fonts, margins, and the places it gives to terms of a model extension ([`templates.md`](templates.md)). PDF only |
| `--no-payment-code` | leave out the EPC QR code the letter layout draws where the invoice states a credit transfer ([`letter-layout.md`](letter-layout.md#the-payment-code)). Letter layout only |
| `--embed cii` | attach the invoice to the PDF as a cross industry invoice, so that one command writes the hybrid file ([`embed`](#embed)). PDF only |
| `--verapdf <path>` | with `--embed cii`, validate the rendering with a veraPDF of your own before writing into it ([below](#validating-the-pdfa-claim)) |
| `--no-esj` | with `--embed cii`, leave out the ESJ document that otherwise goes in beside the XML ([`embed`](#embed)) |
| `--from`, `--extension` | as for every other command |

```console
$ esj render examples/standard-invoice.esj.json --out invoice.pdf
$ echo $?
0
```

That PDF is the invoice as the letter a business sends ([`letter-layout.md`](letter-layout.md)).
`--layout generic` draws the shape of the semantic model instead, and a template may name either
layout, as `letterhead.json` names the generic one:

```console
$ esj render examples/standard-invoice.esj.json --layout generic --out generic.pdf
$ esj render examples/standard-invoice.esj.json \
             --template examples/templates/letterhead.json --out branded.pdf
$ echo $?
0
```

The lead use case is that line with `--embed cii` on it.

```console
$ esj render examples/standard-invoice.esj.json \
             --template examples/templates/letterhead.json --embed cii --out hybrid.pdf
the ESJ document of this invoice is attached beside it as "invoice.esj.json"
$ esj validate hybrid.pdf
...
Container:        OK
Invoice:          VALID
$ echo $?
0
```

```console
$ esj render conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml \
             --html --lang en --out invoice.html
warning: this page carries the content of a document somebody else wrote; open it as you would any file from a stranger. See docs/rendering.md
```

An HTML run that also names `--page` or `--template` is told that the option took no effect
rather than refused:

```console
$ esj render examples/minimal.esj.json --html --page LETTER --out invoice.html
warning: --page is the paper of the PDF; the HTML page has none, so the rendering is the same with and without it
warning: this page carries the content of a document somebody else wrote; open it as you would any file from a stranger. See docs/rendering.md
```

Every rendering is net and derives nothing, the same document twice gives the same bytes, and
the PDF is PDF/A-3b ([`pdf-output.md`](pdf-output.md)). **An HTML rendering is a page built
from a document somebody else wrote.** [`rendering.md`](rendering.md) is what each one shows.

Exit codes are those of every other command: 2 for a document that could not be read, recognized
or rendered and for a template that is not one, 7 for a bound of this run the rendering outgrew
— with nothing written — and 6 for a destination that could not be written.

The cost of `render` is the drawing: the PDF is built whole in memory before its first byte is
written, so a document inside every bound can still have a rendering that is not. `--max-pages`
is the bound here, `--max-runtime` defaults to 5m, and a heap that runs out ends the process
with 3; [`deployment-measurements.md`](deployment-measurements.md) says what to give it.

## embed

`esj embed` writes an invoice into a PDF/A-3 file: the pages stay what they were, the cross
industry invoice is attached as an associated file, the embedded files name tree names it, and
the XMP packet declares it through the Factur-X extension schema. `esj render --embed cii` is
the same step where the pages are this tool's own.

| Option | |
|---|---|
| `--out <file\|->` | where the hybrid invoice goes; required |
| `--profile <profile>` | what the container declares: `EN16931`, `BASIC`, `EXTENDED` or `XRECHNUNG`. Default: the profile BT-24 of the document names |
| `--name <name>` | the container specification the file declares itself under, named by its attachment: `factur-x.xml` (Factur-X 1.0, ZUGFeRD 2.1 and later) or `zugferd-invoice.xml` (ZUGFeRD 2.0). It decides the XMP namespace and `Version` with the name. Default: `factur-x.xml` |
| `--verapdf <path>` | validate the input PDF with a veraPDF of your own instead of believing its own declaration; a file it rejects is refused |
| `--no-esj` | leave out the ESJ document that otherwise goes in beside the XML as `invoice.esj.json` |
| `--from`, `--extension` | as for every other command |

```console
$ esj render examples/standard-invoice.esj.json --out pages.pdf
$ esj embed pages.pdf examples/standard-invoice.esj.json --out invoice.pdf
the ESJ document of this invoice is attached beside it as "invoice.esj.json"
$ esj validate invoice.pdf
...
Container:        OK
Invoice:          VALID
$ echo $?
0
```

What the cross industry invoice had no place for goes to the error stream, as under
`convert --to cii`. The same invoice goes in a second time as `invoice.esj.json`, declared as an
enclosure and never read as the invoice, where it and the XML are two accounts of one invoice; one
line says whether it was written, `--no-esj` leaves it out, and `esj validate` checks the pair
([`pdf-output.md`](pdf-output.md#the-esj-document-beside-the-invoice)).

**Nothing is converted and nothing is overwritten.** A PDF/A-1 or PDF/A-2 input is refused
rather than lifted to part 3, and a file that already carries something that could be the
invoice is refused rather than given a second one. Every refusal leaves with exit code 2;
[`pdf-output.md`](pdf-output.md) is the complete list, what is written into the file, and where
the facts about the format were taken from.

## inspect

The quickest way to see what a file says. The page has the same lines for every document — a
term that is absent is shown as absent — so two of these pages can be compared with `diff`:

```console
$ esj inspect examples/standard-invoice.esj.json
Input:                      examples/standard-invoice.esj.json
Detected syntax:            ESJ
Read with:                  (none — the input is already ESJ)
Semantic model:             EN16931-1:2017+A1:2019/AC:2020
Profile (BT-24):            urn:cen.eu:en16931:2017
Type code (BT-3):           380
Invoice number (BT-1):      RE-2026-0042
Issue date (BT-2):          2026-02-03
Currency (BT-5):            EUR
Seller (BT-27):             Example GmbH
Buyer (BT-44):              Muster AG
Invoice lines:              3
Line net total (BT-106):    2450
Total without VAT (BT-109): 2450
Total VAT (BT-110):         465.5
Total with VAT (BT-112):    2915.5
Amount due (BT-115):        2915.5
Validation pack:            not applicable (no XML)
Semantic digest:            6007ca07a940291633dd24e20a1b815532aa8be0cb24c5cca50c04ffe2ec8d2a
Document digest:            951a8ecf27a1d831f6ff042fd59445e7dec602547385f78337912820986964a4

Syntax
  not run (esj validate runs the official artefacts)
  official artefacts over the written CII:
    not run (esj validate runs the official artefacts over the written document)

Semantic
  ESJ format (L1):           OK
  Model (L2):                OK
  Cardinality (L3):          OK
  EN 16931 business rules (native, pack en16931/1.3.16): not run (esj validate runs the business rules)

INDETERMINATE — nothing fatal found; missing from the check: written-syntax (not-run-by-this-command), business-rules (not-run-by-this-command)
```

The last line of this page is never `VALID`, and that is the point: this command runs the
structural layers and neither the official artefacts nor the rule pack, so a component of the
complete check is always missing, and it says which in the same three words `esj validate` uses.
A file this page found nothing wrong with can still be rejected by `esj validate`, and the exit
code — 9, not 0 — says so without anybody reading the line.

The `Validation pack` line names the pack `esj validate` would run against the document, which
follows from its syntax and the customization identifier of BT-24; `--pack` names a different
one. Where the document names a profile that pack carries no rule set for, the line says so.

## get and list

`esj get` writes one canonical value string and nothing else:

```console
$ esj get examples/standard-invoice.esj.json /BG-22/BT-112
2915.5
```

The supplementary components of a value are not in that string; `--json` writes the whole value
object instead, in the canonical form of `SPEC.md` section 7:

```console
$ esj get examples/standard-invoice.esj.json /BT-1 --json
"RE-2026-0042"
```

A path the document carries no value at is not an error of the input: the message goes to the
error stream and the exit code is 1, the shape `grep` gives it:

```console
$ esj get examples/standard-invoice.esj.json /BG-3/0/BT-25
examples/standard-invoice.esj.json carries no value at /BG-3/0/BT-25
$ echo $?
1
```

Where the reason is a property of the path rather than of this document, the message says so in
the words the validator would use:

```console
$ esj get examples/standard-invoice.esj.json /BT-9999
examples/standard-invoice.esj.json carries no value at /BT-9999: the registry of EN 16931-1:2017+A1:2019/AC:2020 does not contain BT-9999
```

`esj list` writes the whole document as lines of path, type and canonical value, separated by
tabs and in canonical path order; a value carrying a line break is written on one line with the
escape `\n`, and `--format json` writes the same three fields as an array of objects.

```console
$ esj list examples/standard-invoice.esj.json
/BT-1	Identifier	RE-2026-0042
/BT-2	Date	2026-02-03
/BT-3	Code	380
/BT-5	Code	EUR
/BT-9	Date	2026-03-05
/BT-10	Text	KOST-4711
/BT-12	DocumentReference	RV-2025-118
/BT-13	DocumentReference	BE-2026-0091
/BT-19	Text	Cost centre 4711
/BT-20	Text	Payable within 30 days without deduction.
/BG-1/0/BT-22	Text	Delivery and performance according to contract RV-2025-118.\nPlease quote the invoice number with the payment.
/BG-2/BT-24	Identifier	urn:cen.eu:en16931:2017
...
```

## diff

Two documents are compared in the semantic model rather than in a syntax, so the two files may
be written in different ones. The KoSIT test suite carries forty business cases twice, once in
UBL and once in CII; here is a pair whose two renderings agree, printing nothing and leaving
with 0, the two semantic digests on the error stream:

```console
$ esj diff conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml \
           conformance/kosit/business-cases/standard/01.01a-INVOICE_uncefact.xml
semantic digest a: 93c410bd2be4e613ca102876cfcbb5c9e1cdc9c905667d99bc74b1b4cdca577c
semantic digest b: 93c410bd2be4e613ca102876cfcbb5c9e1cdc9c905667d99bc74b1b4cdca577c
$ echo $?
0
```

Equal semantic digests over two files written in different syntaxes are what the litmus test of
[`conformance.md`](conformance.md) claims over the whole corpus. Where a pair does not agree, the
difference is named by semantic path. In the pair below the city and the postcode of both parties
stand in the opposite fields — a property of the test instances, not of a reader:

```console
$ esj diff conformance/kosit/business-cases/standard/03.06a-INVOICE_ubl.xml \
           conformance/kosit/business-cases/standard/03.06a-INVOICE_uncefact.xml
--- conformance/kosit/business-cases/standard/03.06a-INVOICE_ubl.xml
+++ conformance/kosit/business-cases/standard/03.06a-INVOICE_uncefact.xml
-/BG-4/BG-5/BT-37 = 12345
+/BG-4/BG-5/BT-37 = Testhausen
-/BG-4/BG-5/BT-38 = Testhausen
+/BG-4/BG-5/BT-38 = 12345
-/BG-7/BG-8/BT-52 = 12345
+/BG-7/BG-8/BT-52 = Testhausen
-/BG-7/BG-8/BT-53 = Testhausen
+/BG-7/BG-8/BT-53 = 12345
semantic digest a: 63451759fa50dd78ef99667c4467448d9bb5839ee71b317af2c1ba54ad5866bc
semantic digest b: 229091c51378e8ec85f3854c38fb6d1f14bbc9b07d49224b674d8b8b35e76162
$ echo $?
1
```

`--summary` counts the differing paths instead of naming them.

## canonicalize

Without an option the command writes the canonical bytes of `SPEC.md` section 7, which end
without a line feed; `--digest` writes the two digests of section 8 taken over them instead:

```console
$ esj canonicalize examples/standard-invoice.esj.json --digest
semantic: 6007ca07a940291633dd24e20a1b815532aa8be0cb24c5cca50c04ffe2ec8d2a
document: 951a8ecf27a1d831f6ff042fd59445e7dec602547385f78337912820986964a4
```

The semantic digest is taken over the semantic model and the `values` object and answers *did
the invoice change*; the document digest is taken over the whole document and answers *is this
the file I received*. Two extractions of the same invoice differ in `source`, and therefore in
their document digests, while their semantic digests agree.

## Limits

Reading an invoice costs memory and time before anything is known about it, so a run reads
within bounds: how large an XML input may be, how large an ESJ document, how many values it may
carry, how long one value may be, how deep a path may go, how many nodes may sit inside
`extensions`, and — the one bound on what a run writes — how many pages one rendering may have.
They are the limits of `SPEC.md` section 12.2, and they are **policy of the reading party, not
conformance** (section 3.1).

Two profiles ship, and `--limits` chooses between them:

| Bound | `--limits default` | `--limits large` | Switch |
|---|---|---|---|
| XML input | 4 MiB | 256 MiB | `--max-input-bytes` |
| XML output | 64 MiB | 1 GiB | `--max-output-bytes` |
| ESJ document | 64 MiB | 512 MiB | `--max-document-bytes` |
| PDF file | 64 MiB | 512 MiB | `--max-pdf-bytes` |
| attachments of a PDF enumerated | 64 | 64 | `--max-attachments` |
| pages of one rendering | 2 000 | 200 000 | `--max-pages` |
| members of `values` | 100 000 | 8 000 000 | `--max-values` |
| one string value | 1 MiB | 1 MiB | `--max-string-bytes` |
| one binary value | 32 MiB | 32 MiB | `--max-binary-bytes` |
| segments of one path | 16 | 16 | `--max-path-segments` |
| nodes inside `extensions` | 100 000 | 8 000 000 | `--max-extension-nodes` |
| characters held of one element | 33 MiB | 33 MiB | `--max-buffered-bytes` |
| elements held of one element | 100 000 | 100 000 | `--max-buffered-elements` |

The XML output bounds what a writer may produce and is not the input bound read backwards: a
cross industry invoice runs to about three times the UBL invoice it was converted from. The last
two are the streaming reader's alone — a condition that asks about a child element is decided
only once that element has been read, so the element it stands on is held whole — and a refusal
says which of the two was met.

A bound is a promise that an input of that size will be read, so a profile needs the heap its
bounds imply. Measured as the smallest `-Xmx` at which an input **at** the bound is read and
judged: everything the default profile accepts is read within `-Xmx256m`, which is why `bin/esj`
runs `-Xmx512m`; `large` needs about `-Xmx1g` from a file argument and `-Xmx1536m` from the
standard input, and about `-Xmx3g` for XML at its 256 MiB bound under `--importer xslt`
([`deployment-measurements.md`](deployment-measurements.md)).

A PDF is read within two bounds of its own and shares a third. `--max-pdf-bytes` bounds the
file, `--max-attachments` how many attachments are enumerated, and the attachment carrying the
invoice is bounded by `--max-input-bytes`. A stream that inflates past that bound is cut off and
reported rather than buffered whole; the XMP packet is read within a library bound no switch
moves.

`--max-pdf-bytes` bounds a second thing: **everything one container decodes**. A PDF keeps its
objects in compressed streams that the library decodes while it opens the file, so this reader
measures each as the library reaches it against one budget for the file; spending it leaves with
exit code 7 and no verdict. The budget is raised where the attachment bounds of a run are larger
than the file bound, and in the same proportion it bounds how many objects the object streams may
declare together and how many objects the reader walks on the pages to find the files they refer
to. Two refusals behind no switch complete it: a structural stream whose filter
chain this reader does not run, and a `/Predictor` row wider than a mebibyte
([`pdf-input.md`](pdf-input.md)).

`large` moves seven bounds and no more: the XML input, the XML output, the document, the PDF, the
number of values, the nodes inside `extensions` and the pages of a rendering. The size of one value,
the length of a path and the depth of `extensions` stay where the default profile puts them;
`--max-string-bytes` and `--max-binary-bytes` move those, and a refusal names which case it is.

**Refusing at `large`'s own bounds costs nothing from a file and about twice the bound from a
stream.** A file argument is refused on the length the file system reports; the standard input
is collected until one byte past the bound, so a refusal there needs a heap that can hold about
twice it, and a process that cannot exhausts the heap first, which the JVM answers with its own
exit code 3. A switch takes a plain number of bytes or one with a `k`, `M` or `G` suffix for
multiples of 1024, applied on top of the profile whichever order the two were written in, so
`--limits large --max-values 250000` is a large profile with one bound moved down.
[`deployment-measurements.md`](deployment-measurements.md) has both bounds in both forms.

Reaching a bound is exit code 7 and not code 2, and the message names the bound and the switch
that raises it:

```console
$ esj convert examples/standard-invoice.esj.json --max-values 5
error: examples/standard-invoice.esj.json reached a limit of this run rather than a defect of the document: values carries more than 5 members (at values["/BT-10"]); --max-values raises that bound, and --limits large raises it with the other bounds of that profile
$ echo $?
7
```

`esj validate` answers the same way: a document that outgrew a bound leaves with 7 rather than 1,
and a bound a reader ran into leaves with 7 naming the switch that would have kept the value,
rather than writing a document with a term missing. `--max-runtime <duration>` bounds the time
instead of the bytes. It takes `500ms`, `90s`, `5m` or a bare number of seconds, and it is one
number over the whole run.

Two things enforce it. `esj validate` spends the time step by step and stops itself when it is
gone, which is why its refusal names the step; `esj render` arms the watchdog at five minutes
where the caller named no number. Behind both, a watchdog ends the process half a second later,
whatever state it is in, with one line on the error stream and code 7:

```text
esj: runtime limit of 30 s reached; no verdict on the document
```

It is a second layer: `timeout 30 esj validate - < invoice.xml`, or a `destroyForcibly()` on the
caller's side, stays the primary guard. Without `--max-runtime`, `esj validate` and `esj render`
still hold a deadline of five minutes; a heap that runs out first ends the process with code 3.
`conformance/scale/README.md` describes the synthetic instances the large profile was sized
against, and [`deployment.md`](deployment.md) is where these switches belong in a service.

## Exit codes

The exit codes are the interface a script is written against; their meanings do not change:

| Code | Meaning |
|---|---|
| 0 | success |
| 1 | a validation found an error, the profile of a container puts the rules of EN 16931 out of scope, two documents differ, or `esj get` found no value |
| 2 | the input could not be read, recognized or parsed, or the command line could not be parsed |
| 3 | not this tool's: the out-of-memory abort under `-XX:+ExitOnOutOfMemoryError`; its notice goes to the standard output, or to the error stream from the native executable, so it is a crash and no verdict |
| 4 | a feature this version does not implement, such as a ZUGFeRD 1.0 attachment |
| 5 | an internal error |
| 6 | the output could not be written in full: a full disk, or a `--report` the run could not deliver |
| 7 | a resource or time limit of this run was reached; no verdict on the document |
| 8 | the conversion cannot be completed as constrained (reserved) |
| 9 | nothing fatal was found and a component of the complete check did not run or did not complete: no verdict, and the report names which and why |

**Code 0 is a claim about coverage as well as about findings.** A command that reaches a verdict
returns it only where the complete check for that kind of input ran — the table of
[`validation.md`](validation.md#the-complete-check) — and nothing fatal was found; a run that
left part of it out leaves with 9, and `reasons` names each component and its cause. That is why
`esj inspect` never leaves with 0: it names the pack and runs none of it.

**A resource failure must be distinguishable from an invoice being invalid**, which is what
code 7 is for. Such a run carries no verdict word at all — the last line reads `NO VERDICT — a
limit of this run was reached …`, the `verdict` member is `null`, and `reasons` names the cause
`limit-reached` under each component the limit cut short.

A consumer that closes the pipe is not a failure. `esj list x | head -1` means "I have seen
enough", so the tool stops writing, says nothing, and leaves with the code the command reached;
which of the two a refused write is follows from what the standard output is attached to — a
pipe, a socket or a terminal has a reader that can walk away, a file or a device does not.

## What it does not do

It does not present **its own rule engine as the standard**. The business rules of EN 16931-1,
clause 6.4 are checked by the artefacts their publishers released, which this tool runs and does
not reimplement; the native pack of `--rules en16931` checks the same clause over the business
terms, and its findings are a layer of their own that is never ESJ conformance. Where the two
part company, [`../conformance/rules/ledger.md`](../conformance/rules/ledger.md) says so rule by
rule, and `esj --list-packs` names the release this build carries.

It reads **no invoice off the page of a PDF** — no optical character recognition, no layout
analysis, no heuristic extraction, none planned ([Hybrid invoices](#hybrid-invoices-pdf)).

It reads **extension terms only when they are asked for**: without `--extension` such an element
becomes a warning on the error stream, and such a path leaves the run `INDETERMINATE`.
