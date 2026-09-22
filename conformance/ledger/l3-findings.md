# Cardinality findings (L3) over the conformance corpus

Validation layer L3 of the specification, section 9.3, measures a document against the
cardinalities the registry records: a mandatory term that is absent, a group instance
that is missing where its parent exists, an occurrence index with a gap in it, a term
that occurs more often than its maximum allows.

Layer L2 is clean for the whole corpus and the test asserts that. L3 is not, and it is
not expected to be: a test suite contains documents that exercise the edge of a rule,
and a finding below belongs to the instance it names, not to the importer. The list is
therefore recorded rather than asserted away — but it *is* asserted to be exactly this
list, so that a change in the importer or in the registry shows up here.

Instances in the corpus: 86. Instances with at least one finding: 2. Findings: 46.

## Findings

```text
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0  BT-129 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0  BT-130 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-GROUP  /BG-25/0/BG-DEX-01/0/BG-DEX-01/0  BG-DEX-07 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/0  BT-129 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/0  BT-130 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-GROUP  /BG-25/0/BG-DEX-01/0/BG-DEX-01/1  BG-DEX-07 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/1  BT-129 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/1  BT-130 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-GROUP  /BG-25/0/BG-DEX-01/0/BG-DEX-01/2  BG-DEX-07 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/2  BT-129 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/2  BT-130 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/2/BG-DEX-01/1  BT-130 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/2/BG-DEX-01/7  BT-130 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-GROUP  /BG-25/0/BG-DEX-01/0/BG-DEX-01/3  BG-DEX-07 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/3  BT-129 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/3  BT-130 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-GROUP  /BG-25/0/BG-DEX-01/0/BG-DEX-01/4  BG-DEX-07 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/4  BT-129 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/4  BT-130 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/4/BG-DEX-01/0  BT-130 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/4/BG-DEX-01/3  BT-130 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/4/BG-DEX-01/7  BT-130 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/4/BG-DEX-01/8  BT-130 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-GROUP  /BG-25/0/BG-DEX-01/0/BG-DEX-01/5  BG-DEX-07 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/5  BT-129 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/5  BT-130 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/5/BG-DEX-01/1  BT-130 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/5/BG-DEX-01/2  BT-130 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/5/BG-DEX-01/3  BT-130 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-GROUP  /BG-25/0/BG-DEX-01/0/BG-DEX-01/6  BG-DEX-07 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/6  BT-129 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/6  BT-130 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-GROUP  /BG-25/0/BG-DEX-01/0/BG-DEX-01/7  BG-DEX-07 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/7  BT-129 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/7  BT-130 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/7/BG-DEX-01/0  BT-130 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/7/BG-DEX-01/2  BT-130 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/7/BG-DEX-01/6  BT-130 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/7/BG-DEX-01/7  BT-130 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/7/BG-DEX-01/8  BT-130 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-GROUP  /BG-25/0/BG-DEX-01/0/BG-DEX-01/8  BG-DEX-07 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/8  BT-129 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/8  BT-130 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/8/BG-DEX-01/0  BT-130 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.03a-INVOICE_ubl.xml  ESJ-L3-MISSING-TERM  /BG-25/0/BG-DEX-01/0/BG-DEX-01/8/BG-DEX-01/1  BT-130 is declared 1..1 and is missing in this instance of BG-DEX-01
business-cases/extension/04.04a-INVOICE_ubl.xml  ESJ-L3-MISSING-GROUP  /BG-25/0/BG-DEX-01/0  BG-DEX-07 is declared 1..1 and is missing in this instance of BG-DEX-01
```

Four fields per line, separated by two spaces: the instance, the finding code, the
semantic path the finding sits at, and the message. `ConformanceCorpusTest` parses
this block and compares it with what it recomputes.

## What they say

Every finding concerns `BG-DEX-01`, the sub invoice line of the XRechnung extension, and
every one of them is a property of the two instances that carry it. They are not an
artefact of how the importer places that group. Read against the two XML files, they
account for themselves completely:

| Instance | Sub invoice lines | What the source omits | Findings |
|---|---|---|---:|
| `04.03a` | the outer line | no `cbc:InvoicedQuantity` at all | BT-129, BT-130 — 2 |
| `04.03a` | its 9 direct children | no `cbc:InvoicedQuantity` and no `cac:Price` | BT-129, BT-130, BG-DEX-07 — 27 |
| `04.03a` | 16 of their 46 children | a `cbc:InvoicedQuantity` without a `unitCode` | BT-130 — 16 |
| `04.04a` | the outer line | no `cac:Price` | BG-DEX-07 — 1 |

BT-129 and BT-130 are the invoiced quantity and its unit of measure, both declared 1..1
inside a sub invoice line; BG-DEX-07 is the price group, likewise 1..1. Where the KoSIT
file leaves the element out, the term is missing from the source, and ESJ says so at the
instance it is missing from. Nothing here is dropped on the way in: the import report of
the whole corpus carries no unplaceable element, no unknown term and no malformed value.

### What this list looked like before 0.1 placed nested sub invoice lines

Until the extension registry recorded `BG-DEX-01` inside itself (specification, section
5.6), a sub invoice line inside a sub invoice line had no path, and the importer skipped
it with everything below it — 627 values across three instances. The recorded list was
then three findings rather than 46, and it was tempting to read the shorter list as the
better one. It was not: the three findings that were recorded then are the first and the
last row of the table above, they were properties of the fixtures then as now, and the 43
that joined them are the same kind of statement about content that had simply not been
read. A cardinality ledger that grows because more of the document arrives is a ledger
that got better.
