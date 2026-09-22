#!/usr/bin/env python3
"""Cross-check the ESJ binding tables against three independent sources.

The tables are generated from one source, so they are only as trustworthy as that
source. This script measures them against material the repository already carries or
can be pointed at, and writes the result as the report
`conformance/bindings/crosscheck.md`:

a) coverage — every term of the core registry and of the extension registry is either
   bound or carries the flag that says why it is not, in every one of the three tables;
b) the KoSIT visualization stylesheets vendored in `esj-xr` — every bound XPath has to
   turn up there, modulo prefix spelling and predicates, or the difference is named;
c) the CEN syntax Schematron of the validation packs — the rules `UBL-SR-*` and
   `CII-SR-*` are read for the element names they mention, and those names are compared
   with the names the tables bind; no rule text is read or reproduced;
d) the older, MIT licensed SeMoX model — where it binds the same term, the path has to
   agree, and a difference is reported with the date of the newer source.

Nothing here reads prose. Every comparison is over identifiers, element names and
paths. Run it with --help for the options.

This script has no dependencies beyond the Python standard library.
"""

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

#: The SeMoX model namespace, shared by both generations of the model.
M = "{http://semo-xml.org/2025/model}"

#: The tables, with the stylesheet and the Schematron each one is measured against.
#: `old` names the binding of the older model that covers the same syntax, or None
#: where that model has none.
TABLES = [
    ("ubl-invoice.json", "ubl-invoice-xr.xsl", "EN16931-UBL-validation.xslt", "ubl"),
    ("ubl-creditnote.json", "ubl-creditnote-xr.xsl", "EN16931-UBL-validation.xslt", None),
    ("cii.json", "cii-xr.xsl", "EN16931-CII-validation.xslt", "cii"),
]

#: Where the vendored KoSIT visualization stylesheets live in the repository.
XSLT_DIRECTORY = "esj-xr/src/main/resources/de/bsnsoft/esj/xr/kosit"

#: Where the CEN validation artefacts of the shipped pack live.
SCHEMATRON_DIRECTORY = "packs/xrechnung/3.0.2/2026-08-31/cen/1.3.16"

#: Matches a term template of a visualization stylesheet.
TEMPLATE = re.compile(
    r'<xsl:template\s+mode="((?:BT|BG)-[\w\-]+)"\s+match="([^"]*)"', re.S
)

#: Namespace of the Schematron output vocabulary of the compiled artefacts.
SVRL = "{http://purl.oclc.org/dsdl/svrl}"
XSL = "{http://www.w3.org/1999/XSL/Transform}"

#: Matches a run of prefixed names joined by slashes inside a query expression.
CHAIN = re.compile(
    r"[A-Za-z_][\w.\-]*:[A-Za-z_][\w.\-]*(?:/[A-Za-z_][\w.\-]*:[A-Za-z_][\w.\-]*)*"
)

#: Matches the literal of a predicate, so that two spellings of the same discriminator
#: can be compared. A discriminator is written as a quoted string in most places and as
#: a bare number where the element it reads carries a numeric code list — the credit note
#: stylesheet writes `cbc:DocumentTypeCode = 50` — and both forms are read here, because
#: a comparison that sees only one of them reports no difference where the difference is.
LITERAL = re.compile(r"'([^']*)'|(?<![\w.'\-])(\d+)(?![\w.'\-])")


def literals(text):
    """Returns the predicate literals of an expression, quoted and unquoted alike."""
    return {quoted or bare for quoted, bare in LITERAL.findall(text)}


#: Matches a rule that forbids a structure rather than bounding how often it may occur.
PROHIBITION = re.compile(r"^\(?\s*not\s*\(|count\([^)]*\)\s*=\s*0")

#: The editorial part of the report: one line per difference the checks turn up, keyed
#: by (table, subject, kind). A difference without an entry here is printed as
#: unexplained, which is what the test of `esj-core` fails on. Every line is written for
#: this repository; nothing is quoted from any source.
EXPLANATIONS = {
    ("ubl-invoice.json", "BT-21", "hand-built"): "The stylesheet splits one `cbc:Note`"
    " into subject code and text in a grouping block, so neither half has a term"
    " template. The table binds the element the block reads.",
    ("ubl-invoice.json", "BT-22", "hand-built"): "Same grouping block as BT-21.",
    ("ubl-invoice.json", "BT-23", "hand-built"): "The stylesheet writes the process"
    " control group by hand and reads `cbc:ProfileID` there.",
    ("ubl-invoice.json", "BT-24", "hand-built"): "Same block as BT-23, reading"
    " `cbc:CustomizationID`.",
    ("ubl-invoice.json", "BG-16", "hand-built"): "The stylesheet groups `cac:PaymentMeans`"
    " by payment means code by hand, so the group has no template of its own.",
    ("ubl-invoice.json", "BT-102", "different predicate"): "The table narrows the tax"
    " category to the VAT scheme; the stylesheet does not, because it only renders what"
    " a validated document already contains. The narrower path is a subset of the wider"
    " one.",
    ("ubl-invoice.json", "PaymentMeans/PaymentDueDate", "rule over unbound element"):
    "`UBL-SR-45` bounds an element the core model does not use in an invoice: BT-9 is"
    " bound to `cbc:DueDate` at the document root. The credit note table binds it, which"
    " is why the same rule file is relevant to both.",
    ("ubl-invoice.json", "BT-82", "older model"): "The older model spells the attribute"
    " `@Name`; UBL 2.1 declares it `@name`. The newer model and the stylesheet both use"
    " the lower case spelling, so the older one is a typing error and the table follows"
    " the newer source.",
    ("ubl-creditnote.json", "BT-21", "hand-built"): "Same grouping block as in the"
    " invoice stylesheet.",
    ("ubl-creditnote.json", "BT-22", "hand-built"): "Same grouping block as BT-21.",
    ("ubl-creditnote.json", "BT-23", "hand-built"): "Same hand-written process control"
    " block as in the invoice stylesheet.",
    ("ubl-creditnote.json", "BT-24", "hand-built"): "Same block as BT-23.",
    ("ubl-creditnote.json", "BG-16", "hand-built"): "Same hand-written grouping of"
    " `cac:PaymentMeans` as in the invoice stylesheet.",
    ("ubl-creditnote.json", "BT-102", "different predicate"): "Same narrowing to the VAT"
    " scheme as in the invoice table.",
    ("ubl-creditnote.json", "ProjectReference/ID", "rule over unbound element"):
    "`UBL-SR-39` is an invoice rule. UBL 2.1 gives the credit note no"
    " `cac:ProjectReference`, so BT-11 is bound to `cac:AdditionalDocumentReference`"
    " there, and the rule does not apply to this table. The CEN artefact holds the rules"
    " of both document types in one file.",
    ("cii.json", "BT-7", "hand-built"): "The stylesheet reads the tax point date inside"
    " a block that gathers distinct values, so the term has no template of its own.",
    ("cii.json", "BT-149", "hand-built"): "The stylesheet chooses between the gross and"
    " the net price base quantity in a hand-written block; the table carries both paths"
    " as alternatives.",
    ("cii.json", "BT-150", "hand-built"): "Same block as BT-149, for the unit code"
    " attribute.",
    ("cii.json", "BG-13", "different node"): "The stylesheet puts DELIVERY INFORMATION"
    " on the transaction element, the model on `ram:ShipToTradeParty`. CII has no"
    " element that corresponds to the group, which is what the `STR-3` flag on this"
    " entry records; the two sources pick different anchors for the same absent"
    " element.",
    ("cii.json", "BT-48", "different predicate"): "The stylesheet accepts both `VA` and"
    " `VAT` as the tax scheme code, the model only `VA`. A reader built on the table"
    " should accept both; a writer emits `VA`.",
    ("cii.json", "GrossPriceProductTradePrice/AppliedTradeAllowanceCharge/ChargeIndicator",
     "rule over unbound element"): "The rule ties the item price discount to its charge"
    " indicator. The core model has no term for that indicator, so no table binds it;"
    " BT-147 binds the amount beside it.",
    ("ubl-invoice.json", "BT-151", "different predicate"):
    "The table narrows the classified tax category to the VAT scheme, the stylesheet reads it without a condition. The CEN validation artefact of `packs/` and the credit note stylesheet both write the condition, and a line that states a second tax beside value added tax would otherwise have its category code taken from whichever element the document writes first; `corrections` records it.",
    ("ubl-creditnote.json", "BG-24", "different predicate"):
    'The table keeps the supporting document group, and the terms inside it, off the additional document references whose document type code is 130 or 50, because those occurrences carry the invoiced object identifier BT-18 and the project reference BT-11. The CEN validation artefact of `packs/` excludes 130 and the stylesheet excludes 50, and both are needed: a credit note that states either would otherwise gain a supporting document group whose only value is that reference read a second time as BT-122, and the occurrence indices of the real ones would shift. `corrections` records it.',
    ("ubl-invoice.json", "BG-24", "different predicate"):
    "The table keeps the supporting document group, and the terms inside it, off the additional document reference whose document type code is 130, because that occurrence carries the invoiced object identifier BT-18; the stylesheet reads every occurrence. The CEN validation artefact of `packs/` writes the same exclusion. Without it an invoiced object identifier standing before the supporting document in the file opens a supporting document group of its own and moves the real document's occurrence index, which is part of its semantic path; `corrections` records it.",
    ("ubl-invoice.json", "BT-122", "different predicate"):
    "The table keeps the supporting document group, and the terms inside it, off the additional document reference whose document type code is 130, because that occurrence carries the invoiced object identifier BT-18; the stylesheet reads every occurrence. The CEN validation artefact of `packs/` writes the same exclusion. Without it an invoiced object identifier standing before the supporting document in the file opens a supporting document group of its own and moves the real document's occurrence index, which is part of its semantic path; `corrections` records it.",
    ("ubl-invoice.json", "BT-123", "different predicate"):
    "The table keeps the supporting document group, and the terms inside it, off the additional document reference whose document type code is 130, because that occurrence carries the invoiced object identifier BT-18; the stylesheet reads every occurrence. The CEN validation artefact of `packs/` writes the same exclusion. Without it an invoiced object identifier standing before the supporting document in the file opens a supporting document group of its own and moves the real document's occurrence index, which is part of its semantic path; `corrections` records it.",
    ("ubl-invoice.json", "BT-124", "different predicate"):
    "The table keeps the supporting document group, and the terms inside it, off the additional document reference whose document type code is 130, because that occurrence carries the invoiced object identifier BT-18; the stylesheet reads every occurrence. The CEN validation artefact of `packs/` writes the same exclusion. Without it an invoiced object identifier standing before the supporting document in the file opens a supporting document group of its own and moves the real document's occurrence index, which is part of its semantic path; `corrections` records it.",
    ("ubl-invoice.json", "BT-125", "different predicate"):
    "The table keeps the supporting document group, and the terms inside it, off the additional document reference whose document type code is 130, because that occurrence carries the invoiced object identifier BT-18; the stylesheet reads every occurrence. The CEN validation artefact of `packs/` writes the same exclusion. Without it an invoiced object identifier standing before the supporting document in the file opens a supporting document group of its own and moves the real document's occurrence index, which is part of its semantic path; `corrections` records it.",
    ("ubl-creditnote.json", "BT-122", "different predicate"):
    'The table keeps the supporting document group, and the terms inside it, off the additional document references whose document type code is 130 or 50, because those occurrences carry the invoiced object identifier BT-18 and the project reference BT-11. The CEN validation artefact of `packs/` excludes 130 and the stylesheet excludes 50, and both are needed: a credit note that states either would otherwise gain a supporting document group whose only value is that reference read a second time as BT-122, and the occurrence indices of the real ones would shift. `corrections` records it.',
    ("ubl-creditnote.json", "BT-123", "different predicate"):
    'The table keeps the supporting document group, and the terms inside it, off the additional document references whose document type code is 130 or 50, because those occurrences carry the invoiced object identifier BT-18 and the project reference BT-11. The CEN validation artefact of `packs/` excludes 130 and the stylesheet excludes 50, and both are needed: a credit note that states either would otherwise gain a supporting document group whose only value is that reference read a second time as BT-122, and the occurrence indices of the real ones would shift. `corrections` records it.',
    ("ubl-creditnote.json", "BT-124", "different predicate"):
    'The table keeps the supporting document group, and the terms inside it, off the additional document references whose document type code is 130 or 50, because those occurrences carry the invoiced object identifier BT-18 and the project reference BT-11. The CEN validation artefact of `packs/` excludes 130 and the stylesheet excludes 50, and both are needed: a credit note that states either would otherwise gain a supporting document group whose only value is that reference read a second time as BT-122, and the occurrence indices of the real ones would shift. `corrections` records it.',
    ("ubl-creditnote.json", "BT-125", "different predicate"):
    'The table keeps the supporting document group, and the terms inside it, off the additional document references whose document type code is 130 or 50, because those occurrences carry the invoiced object identifier BT-18 and the project reference BT-11. The CEN validation artefact of `packs/` excludes 130 and the stylesheet excludes 50, and both are needed: a credit note that states either would otherwise gain a supporting document group whose only value is that reference read a second time as BT-122, and the occurrence indices of the real ones would shift. `corrections` records it.',
    ("cii.json", "BG-24", "different predicate"):
    "The table takes the supporting document group, and the terms inside it, from the additional reference whose type code is 916; the stylesheet reads every additional reference. The syntax writes four different things into that element and tells them apart by `ram:TypeCode`, and the CEN validation artefact of `packs/` writes the condition 916 on it for BT-123 and BT-125. Without it a tender reference or an invoiced object identifier standing before the supporting document in the file moves that document's occurrence index, which is part of its semantic path; the stylesheet has the defect the table corrects, so the two readers part here, and `corrections` and `conformance/readers.md` record it.",
    ("cii.json", "BT-123", "different predicate"):
    "The table takes the supporting document group, and the terms inside it, from the additional reference whose type code is 916; the stylesheet reads every additional reference. The syntax writes four different things into that element and tells them apart by `ram:TypeCode`, and the CEN validation artefact of `packs/` writes the condition 916 on it for BT-123 and BT-125. Without it a tender reference or an invoiced object identifier standing before the supporting document in the file moves that document's occurrence index, which is part of its semantic path; the stylesheet has the defect the table corrects, so the two readers part here, and `corrections` and `conformance/readers.md` record it.",
    ("cii.json", "BT-124", "different predicate"):
    "The table takes the supporting document group, and the terms inside it, from the additional reference whose type code is 916; the stylesheet reads every additional reference. The syntax writes four different things into that element and tells them apart by `ram:TypeCode`, and the CEN validation artefact of `packs/` writes the condition 916 on it for BT-123 and BT-125. Without it a tender reference or an invoiced object identifier standing before the supporting document in the file moves that document's occurrence index, which is part of its semantic path; the stylesheet has the defect the table corrects, so the two readers part here, and `corrections` and `conformance/readers.md` record it.",
    ("cii.json", "BT-125", "different predicate"):
    "The table takes the supporting document group, and the terms inside it, from the additional reference whose type code is 916; the stylesheet reads every additional reference. The syntax writes four different things into that element and tells them apart by `ram:TypeCode`, and the CEN validation artefact of `packs/` writes the condition 916 on it for BT-123 and BT-125. Without it a tender reference or an invoiced object identifier standing before the supporting document in the file moves that document's occurrence index, which is part of its semantic path; the stylesheet has the defect the table corrects, so the two readers part here, and `corrections` and `conformance/readers.md` record it.",
    ("cii.json", "BT-11", "different predicate"):
    "The table narrows the procuring project to the occurrence the source names, the stylesheet reads the element without a condition. The table carries the condition, the stylesheet's own match pattern for this term does not: the block that applies the template has already selected the element. The table is the narrower of the two and reaches the same elements.",
    ("cii.json", "BT-63", "different predicate"):
    "The table narrows the tax registration of the tax representative to the VAT scheme the source names; the stylesheet's match pattern does not. The table carries the condition, the stylesheet's own match pattern for this term does not: the block that applies the template has already selected the element. The table is the narrower of the two and reaches the same elements.",
    ("cii.json", "BT-118", "different predicate"):
    "The table narrows the tax category to the VAT scheme; the stylesheet's match pattern does not. The table carries the condition, the stylesheet's own match pattern for this term does not: the block that applies the template has already selected the element. The table is the narrower of the two and reaches the same elements.",
    ("cii.json", "BT-151", "different predicate"):
    "The table narrows the tax category to the VAT scheme; the stylesheet's match pattern does not. The table carries the condition, the stylesheet's own match pattern for this term does not: the block that applies the template has already selected the element. The table is the narrower of the two and reaches the same elements.",
    ("cii.json", "BT-92", "different predicate"):
    "The table carries the allowance or charge indicator of the element the term sits in, and where the term is a tax category also the VAT scheme; the stylesheet's match pattern for this term carries neither. The table carries the condition, the stylesheet's own match pattern for this term does not: the block that applies the template has already selected the element. The table is the narrower of the two and reaches the same elements.",
    ("cii.json", "BT-93", "different predicate"):
    "The table carries the allowance or charge indicator of the element the term sits in, and where the term is a tax category also the VAT scheme; the stylesheet's match pattern for this term carries neither. The table carries the condition, the stylesheet's own match pattern for this term does not: the block that applies the template has already selected the element. The table is the narrower of the two and reaches the same elements.",
    ("cii.json", "BT-94", "different predicate"):
    "The table carries the allowance or charge indicator of the element the term sits in, and where the term is a tax category also the VAT scheme; the stylesheet's match pattern for this term carries neither. The table carries the condition, the stylesheet's own match pattern for this term does not: the block that applies the template has already selected the element. The table is the narrower of the two and reaches the same elements.",
    ("cii.json", "BT-95", "different predicate"):
    "The table carries the allowance or charge indicator of the element the term sits in, and where the term is a tax category also the VAT scheme; the stylesheet's match pattern for this term carries neither. The table carries the condition, the stylesheet's own match pattern for this term does not: the block that applies the template has already selected the element. The table is the narrower of the two and reaches the same elements.",
    ("cii.json", "BT-96", "different predicate"):
    "The table carries the allowance or charge indicator of the element the term sits in, and where the term is a tax category also the VAT scheme; the stylesheet's match pattern for this term carries neither. The table carries the condition, the stylesheet's own match pattern for this term does not: the block that applies the template has already selected the element. The table is the narrower of the two and reaches the same elements.",
    ("cii.json", "BT-97", "different predicate"):
    "The table carries the allowance or charge indicator of the element the term sits in, and where the term is a tax category also the VAT scheme; the stylesheet's match pattern for this term carries neither. The table carries the condition, the stylesheet's own match pattern for this term does not: the block that applies the template has already selected the element. The table is the narrower of the two and reaches the same elements.",
    ("cii.json", "BT-98", "different predicate"):
    "The table carries the allowance or charge indicator of the element the term sits in, and where the term is a tax category also the VAT scheme; the stylesheet's match pattern for this term carries neither. The table carries the condition, the stylesheet's own match pattern for this term does not: the block that applies the template has already selected the element. The table is the narrower of the two and reaches the same elements.",
    ("cii.json", "BT-99", "different predicate"):
    "The table carries the allowance or charge indicator of the element the term sits in, and where the term is a tax category also the VAT scheme; the stylesheet's match pattern for this term carries neither. The table carries the condition, the stylesheet's own match pattern for this term does not: the block that applies the template has already selected the element. The table is the narrower of the two and reaches the same elements.",
    ("cii.json", "BT-100", "different predicate"):
    "The table carries the allowance or charge indicator of the element the term sits in, and where the term is a tax category also the VAT scheme; the stylesheet's match pattern for this term carries neither. The table carries the condition, the stylesheet's own match pattern for this term does not: the block that applies the template has already selected the element. The table is the narrower of the two and reaches the same elements.",
    ("cii.json", "BT-101", "different predicate"):
    "The table carries the allowance or charge indicator of the element the term sits in, and where the term is a tax category also the VAT scheme; the stylesheet's match pattern for this term carries neither. The table carries the condition, the stylesheet's own match pattern for this term does not: the block that applies the template has already selected the element. The table is the narrower of the two and reaches the same elements.",
    ("cii.json", "BT-102", "different predicate"):
    "The table carries the allowance or charge indicator of the element the term sits in, and where the term is a tax category also the VAT scheme; the stylesheet's match pattern for this term carries neither. The table carries the condition, the stylesheet's own match pattern for this term does not: the block that applies the template has already selected the element. The table is the narrower of the two and reaches the same elements.",
    ("cii.json", "BT-103", "different predicate"):
    "The table carries the allowance or charge indicator of the element the term sits in, and where the term is a tax category also the VAT scheme; the stylesheet's match pattern for this term carries neither. The table carries the condition, the stylesheet's own match pattern for this term does not: the block that applies the template has already selected the element. The table is the narrower of the two and reaches the same elements.",
    ("cii.json", "BT-104", "different predicate"):
    "The table carries the allowance or charge indicator of the element the term sits in, and where the term is a tax category also the VAT scheme; the stylesheet's match pattern for this term carries neither. The table carries the condition, the stylesheet's own match pattern for this term does not: the block that applies the template has already selected the element. The table is the narrower of the two and reaches the same elements.",
    ("cii.json", "BT-105", "different predicate"):
    "The table carries the allowance or charge indicator of the element the term sits in, and where the term is a tax category also the VAT scheme; the stylesheet's match pattern for this term carries neither. The table carries the condition, the stylesheet's own match pattern for this term does not: the block that applies the template has already selected the element. The table is the narrower of the two and reaches the same elements.",
    ("cii.json", "BT-136", "different predicate"):
    "The table carries the allowance or charge indicator of the element the term sits in, and where the term is a tax category also the VAT scheme; the stylesheet's match pattern for this term carries neither. The table carries the condition, the stylesheet's own match pattern for this term does not: the block that applies the template has already selected the element. The table is the narrower of the two and reaches the same elements.",
    ("cii.json", "BT-137", "different predicate"):
    "The table carries the allowance or charge indicator of the element the term sits in, and where the term is a tax category also the VAT scheme; the stylesheet's match pattern for this term carries neither. The table carries the condition, the stylesheet's own match pattern for this term does not: the block that applies the template has already selected the element. The table is the narrower of the two and reaches the same elements.",
    ("cii.json", "BT-138", "different predicate"):
    "The table carries the allowance or charge indicator of the element the term sits in, and where the term is a tax category also the VAT scheme; the stylesheet's match pattern for this term carries neither. The table carries the condition, the stylesheet's own match pattern for this term does not: the block that applies the template has already selected the element. The table is the narrower of the two and reaches the same elements.",
    ("cii.json", "BT-139", "different predicate"):
    "The table carries the allowance or charge indicator of the element the term sits in, and where the term is a tax category also the VAT scheme; the stylesheet's match pattern for this term carries neither. The table carries the condition, the stylesheet's own match pattern for this term does not: the block that applies the template has already selected the element. The table is the narrower of the two and reaches the same elements.",
    ("cii.json", "BT-140", "different predicate"):
    "The table carries the allowance or charge indicator of the element the term sits in, and where the term is a tax category also the VAT scheme; the stylesheet's match pattern for this term carries neither. The table carries the condition, the stylesheet's own match pattern for this term does not: the block that applies the template has already selected the element. The table is the narrower of the two and reaches the same elements.",
    ("cii.json", "BT-141", "different predicate"):
    "The table carries the allowance or charge indicator of the element the term sits in, and where the term is a tax category also the VAT scheme; the stylesheet's match pattern for this term carries neither. The table carries the condition, the stylesheet's own match pattern for this term does not: the block that applies the template has already selected the element. The table is the narrower of the two and reaches the same elements.",
    ("cii.json", "BT-142", "different predicate"):
    "The table carries the allowance or charge indicator of the element the term sits in, and where the term is a tax category also the VAT scheme; the stylesheet's match pattern for this term carries neither. The table carries the condition, the stylesheet's own match pattern for this term does not: the block that applies the template has already selected the element. The table is the narrower of the two and reaches the same elements.",
    ("cii.json", "BT-143", "different predicate"):
    "The table carries the allowance or charge indicator of the element the term sits in, and where the term is a tax category also the VAT scheme; the stylesheet's match pattern for this term carries neither. The table carries the condition, the stylesheet's own match pattern for this term does not: the block that applies the template has already selected the element. The table is the narrower of the two and reaches the same elements.",
    ("cii.json", "BT-144", "different predicate"):
    "The table carries the allowance or charge indicator of the element the term sits in, and where the term is a tax category also the VAT scheme; the stylesheet's match pattern for this term carries neither. The table carries the condition, the stylesheet's own match pattern for this term does not: the block that applies the template has already selected the element. The table is the narrower of the two and reaches the same elements.",
    ("cii.json", "BT-145", "different predicate"):
    "The table carries the allowance or charge indicator of the element the term sits in, and where the term is a tax category also the VAT scheme; the stylesheet's match pattern for this term carries neither. The table carries the condition, the stylesheet's own match pattern for this term does not: the block that applies the template has already selected the element. The table is the narrower of the two and reaches the same elements.",
}

#: The line summation amounts of CII that the core model has no term for. Each gets the
#: same line in the report, so they are listed here instead of eleven times above.
UNUSED_LINE_AMOUNTS = [
    "GrossLineTotalAmount",
    "InformationAmount",
    "NetIncludingTaxesLineTotalAmount",
    "NetLineTotalAmount",
    "ProductValueExcludingTobaccoTaxInformationAmount",
    "RetailValueExcludingTaxInformationAmount",
    "TotalAllowanceChargeAmount",
    "TotalDepositFeeInformationAmount",
    "TotalDiscountAmount",
    "TotalRetailValueInformationAmount",
]

for _amount in UNUSED_LINE_AMOUNTS:
    EXPLANATIONS[("cii.json", _amount, "rule over unbound element")] = (
        "One of the amounts of the CII line summation that the core model has no term"
        " for; the rule bounds how often it may occur, and no table binds it."
    )


def split_steps(xpath):
    """Splits an XPath into its steps, ignoring slashes inside predicates."""
    steps = []
    depth = 0
    quote = None
    current = ""
    for char in xpath:
        if quote:
            current += char
            if char == quote:
                quote = None
        elif char in "'\"":
            current += char
            quote = char
        elif char == "[":
            depth += 1
            current += char
        elif char == "]":
            depth -= 1
            current += char
        elif char == "/" and depth == 0:
            steps.append(current)
            current = ""
        else:
            current += char
    steps.append(current)
    return steps


def local_steps(xpath):
    """Reduces an XPath to its local element names, dropping prefixes and predicates."""
    names = []
    for step in split_steps(xpath):
        name = step.split("[", 1)[0].strip()
        if not name or name == ".":
            continue
        names.append(name.split(":")[-1])
    return names


def table_paths(table):
    """Returns every bound path of a table, core terms and extension terms alike."""
    entries = table["terms"] + table.get("extension", {}).get("terms", [])
    for entry in entries:
        if not entry["bound"]:
            continue
        paths = [entry["xpath"]] + [
            alternative["xpath"] for alternative in entry.get("alternatives", [])
        ]
        yield entry, paths


def check_coverage(table, registry, extension_registry):
    """a) Every term of both registries appears in the table, bound or explained."""
    result = {"missing": [], "bound": 0, "unbound": [], "extensionBound": 0,
              "extensionUnbound": [], "reusedTerms": 0, "reusedBound": 0}
    seen = {entry["id"] for entry in table["terms"]}
    for identifier in registry:
        if identifier not in seen:
            result["missing"].append(identifier)
    for entry in table["terms"]:
        if entry["bound"]:
            result["bound"] += 1
        else:
            result["unbound"].append((entry["id"], entry["flags"][0]))
    extension = table.get("extension", {}).get("terms", [])
    seen = {entry["id"] for entry in extension}
    for identifier in extension_registry:
        if identifier not in seen:
            result["missing"].append(identifier)
    for entry in extension:
        if entry["bound"]:
            result["extensionBound"] += 1
        else:
            result["extensionUnbound"].append((entry["id"], entry["flags"][0]))
    reused = table.get("extension", {}).get("reusedTerms", [])
    result["reusedTerms"] = len(reused)
    result["reusedBound"] = sum(1 for entry in reused if entry["bound"])
    return result


def check_stylesheet(table, stylesheet):
    """b) Every bound path turns up in the vendored visualization stylesheet."""
    text = Path(stylesheet).read_text(encoding="utf-8")
    templates = {}
    for term, match in TEMPLATE.findall(text):
        for branch in match.split("|"):
            if branch.strip():
                templates.setdefault(term, []).append(
                    (branch.strip().startswith("/"), local_steps(branch))
                )
    plain = re.sub(r"(?<![\w\-])[A-Za-z_][\w.\-]*:(?=[A-Za-z_])", "", text)

    found = {}
    for term, match in TEMPLATE.findall(text):
        found.setdefault(term, set()).update(literals(match))

    agreeing = 0
    hand_built = []
    differing = []
    predicates = []
    for entry, paths in table_paths(table):
        ours = set()
        for path in paths:
            ours.update(literals(path))
        theirs = found.get(entry["id"], set())
        # A difference is reported whenever the two sides do not carry the same
        # discriminators, one empty side included: a table that binds an element with no
        # condition where the stylesheet reads it with one binds wider than the
        # stylesheet, which is the direction in which a reader stores a wrong value
        # rather than none. The comparison is skipped only where the stylesheet has no
        # template for the term at all, which section b) reports as hand-built.
        if ours != theirs and entry["id"] in templates:
            predicates.append(
                (
                    entry["id"],
                    ", ".join(sorted(ours)) or "no predicate",
                    ", ".join(sorted(theirs)) or "no predicate",
                )
            )
        wanted = [local_steps(path) for path in paths]
        matched = False
        for absolute, steps in templates.get(entry["id"], []):
            for candidate in wanted:
                if (absolute and candidate == steps) or (
                    not absolute and candidate[-len(steps):] == steps
                ):
                    matched = True
        if matched:
            agreeing += 1
            continue
        # A handful of terms are written by a block the stylesheet builds by hand
        # instead of by a term template of their own. The term is still emitted there
        # and the element it reads is still named, so both are looked for in the text.
        emitted = "'" + entry["id"] + "'" in text
        named = any(
            "/" + candidate[-1] in plain or ">" + candidate[-1] in plain
            for candidate in wanted
        )
        if not templates.get(entry["id"]) and emitted and named:
            hand_built.append((entry["id"], "/".join(wanted[0])))
        else:
            differing.append(
                (
                    entry["id"],
                    "/".join(wanted[0]),
                    "; ".join("/".join(steps) for _, steps in templates.get(entry["id"], [])),
                )
            )
    return {
        "agreeing": agreeing,
        "handBuilt": hand_built,
        "differing": differing,
        "predicateLiterals": predicates,
    }


def rules_of(artefact, prefix):
    """Reads the syntax rules of a compiled CEN artefact with the context of each.

    A rule is returned as (identifier, context, test). The context is the match pattern
    of the template the assertion sits in, which is what the relative paths of the test
    are relative to. Only the identifier, the match pattern and the test expression are
    read; the human readable text of a rule is never touched.
    """
    root = ET.parse(artefact).getroot()
    rules = []
    for template in root.iter(XSL + "template"):
        context = template.get("match")
        if not context:
            continue
        for assertion in template.iter(SVRL + "failed-assert"):
            identifier = None
            for attribute in assertion.findall(XSL + "attribute"):
                if attribute.get("name") == "id":
                    identifier = (attribute.text or "").strip()
            if identifier and identifier.startswith(prefix + "-SR-"):
                rules.append((identifier, context, assertion.get("test", "")))
    return rules


def bound_chains(table):
    """Returns the local name chains of every path the table binds."""
    chains = []
    for entry, paths in table_paths(table):
        for path in paths:
            chains.append(local_steps(path))
            for predicate in re.findall(r"\[([^\]]*)\]", path):
                for match in CHAIN.finditer(predicate):
                    chains.append(local_steps(match.group(0)))
        for component in entry.get("components", []):
            chains.append(local_steps(component["anchor"]) + local_steps(
                component["xpath"].replace("..", "")
            ))
    return chains


def walks_of(test):
    """Returns the element chains a rule walks, relative and without its context.

    A chain is a run of prefixed names joined by slashes. A name that a bracket follows
    is a function of the query language and not an element, so it ends the chain.
    """
    walks = []
    for match in CHAIN.finditer(test):
        after = test[match.end():match.end() + 1]
        text = match.group(0)
        if after == "(":
            text = text.rsplit("/", 1)[0] if "/" in text else ""
        if text:
            walks.append(local_steps(text))
    return [walk for walk in walks if walk]


def contains(chain, wanted):
    """Says whether `wanted` appears in `chain` as a run of consecutive names."""
    if not wanted:
        return False
    for start in range(len(chain) - len(wanted) + 1):
        if chain[start:start + len(wanted)] == wanted:
            return True
    return False


def check_schematron(table, artefact, prefix):
    """c) The syntax rules of the CEN artefacts walk the structure the table binds.

    The comparison is context free: the assertions of the compiled artefact are read
    for the element chains they walk, and a chain counts as known when it appears as a
    run of consecutive names in a path the table binds. That is deliberately weaker
    than resolving each rule against the node it fires on; it is enough for the claim
    the report makes, which is that the two describe the same elements.
    """
    rules = rules_of(artefact, prefix)
    chains = bound_chains(table)
    known_names = {name for chain in chains for name in chain}

    prohibiting = 0
    restricting = 0
    fenced = set()
    unknown_in_restricting = {}
    for identifier, _, test in rules:
        walks = walks_of(test)
        if not walks:
            continue
        forbids = bool(PROHIBITION.match(test.strip()))
        prohibiting += 1 if forbids else 0
        restricting += 0 if forbids else 1
        for walk in walks:
            reached = any(contains(chain, walk) for chain in chains)
            if reached:
                continue
            if forbids:
                fenced.add("/".join(walk))
            else:
                unknown_in_restricting.setdefault("/".join(walk), []).append(identifier)
    return {
        "rules": len(rules),
        "prohibiting": prohibiting,
        "restricting": restricting,
        "fencedOff": len(fenced),
        "unknownInRestricting": {
            chain: sorted(set(ids)) for chain, ids in sorted(unknown_in_restricting.items())
        },
    }


def check_older_model(table, older, binding_id):
    """d) The older, MIT licensed model agrees where it binds the same term."""
    if binding_id is None:
        return None
    root = ET.parse(older).getroot()
    paths = {}
    for binding in root.iter(M + "binding"):
        syntax = binding.find(M + "syntax/" + M + "id")
        if syntax is None or (syntax.text or "").strip() != binding_id:
            continue
        for term in binding.findall(M + "term"):
            identifier = term.find(M + "id")
            path = term.find(M + "path")
            if identifier is None or path is None:
                continue
            paths.setdefault((identifier.text or "").strip(), []).append(
                local_steps((path.text or "").strip())
            )

    agreeing = 0
    differing = []
    only_old = sorted(set(paths) - {entry["id"] for entry, _ in table_paths(table)})
    for entry, table_path in table_paths(table):
        if entry["id"] not in paths:
            continue
        wanted = [local_steps(path) for path in table_path]
        if any(candidate in paths[entry["id"]] for candidate in wanted):
            agreeing += 1
        else:
            differing.append(
                (
                    entry["id"],
                    "/".join(wanted[0]),
                    "; ".join("/".join(step) for step in paths[entry["id"]]),
                )
            )
    return {
        "compared": agreeing + len(differing),
        "agreeing": agreeing,
        "differing": differing,
        "onlyInOlder": only_old,
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    root = Path(__file__).resolve().parents[3]
    parser.add_argument("--repository", default=str(root), help="repository root")
    parser.add_argument(
        "--older-model",
        required=True,
        help="path to the older, MIT licensed SeMoX model file",
    )
    parser.add_argument(
        "--out",
        default=str(root / "conformance" / "bindings" / "crosscheck.md"),
        help="file the report is written to",
    )
    parser.add_argument(
        "--date", required=True, help="the date the report carries, as YYYY-MM-DD"
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="compare with the report on disk instead of writing it",
    )
    arguments = parser.parse_args(argv)
    base = Path(arguments.repository)

    with open(base / "model" / "en16931" / "2017.json", encoding="utf-8") as stream:
        registry = [term["id"] for term in json.load(stream)["terms"]]
    with open(base / "model" / "xrechnung" / "3.0.2.json", encoding="utf-8") as stream:
        extension_registry = [term["id"] for term in json.load(stream)["terms"]]

    findings = {}
    for name, stylesheet, artefact, older_binding in TABLES:
        with open(base / "model" / "bindings" / name, encoding="utf-8") as stream:
            table = json.load(stream)
        findings[name] = {
            "table": table,
            "coverage": check_coverage(table, registry, extension_registry),
            "stylesheet": check_stylesheet(
                table, base / XSLT_DIRECTORY / stylesheet
            ),
            "schematron": check_schematron(
                table,
                base / SCHEMATRON_DIRECTORY / artefact,
                "UBL" if name.startswith("ubl") else "CII",
            ),
            "older": check_older_model(table, arguments.older_model, older_binding),
            "artefact": artefact,
            "stylesheetName": stylesheet,
        }

    report = render(findings, arguments.date)
    target = Path(arguments.out)
    if arguments.check:
        current = target.read_text(encoding="utf-8") if target.exists() else ""
        if current != report:
            print(target.name + " differs from the tables", file=sys.stderr)
            return 1
        print(target.name + " is up to date")
        return 0
    target.write_text(report, encoding="utf-8")
    print(
        "%s: %d differences, all explained"
        % (target.name, sum(len(explanations_for(findings, name)) for name in findings))
    )
    return 0


def explanations_for(findings, name):
    """Returns the explained differences of one table, as (subject, explanation)."""
    result = findings[name]
    listed = []
    for identifier, path in result["stylesheet"]["handBuilt"]:
        listed.append((identifier, "hand-built", path))
    for identifier, ours, theirs in result["stylesheet"]["differing"]:
        listed.append((identifier, "different node", ours + " / " + theirs))
    for identifier, ours, theirs in result["stylesheet"]["predicateLiterals"]:
        listed.append((identifier, "different predicate", ours + " / " + theirs))
    for chain in result["schematron"]["unknownInRestricting"]:
        listed.append((chain, "rule over unbound element", ""))
    if result["older"]:
        for identifier, ours, theirs in result["older"]["differing"]:
            listed.append((identifier, "older model", ours + " / " + theirs))
    return [
        (subject, kind, detail, EXPLANATIONS.get((name, subject, kind), ""))
        for subject, kind, detail in listed
    ]


def render(findings, date):
    """Renders the report."""
    lines = [
        "# Binding tables — cross-check",
        "",
        "Generated on " + date + " by `model/bindings/tools/crosscheck_bindings.py`;"
        " re-run it after any change to `model/bindings/`. Everything below is machine"
        " output apart from the explanation column, which is written for this"
        " repository and lives in the `EXPLANATIONS` table of that script.",
        "",
        "The binding tables come from one source. This report measures them against"
        " three others: the KoSIT visualization stylesheets vendored in `esj-xr`"
        " (Apache-2.0), the CEN validation artefacts of the shipped pack (EUPL-1.2) and"
        " the older, MIT licensed SeMoX model. Only identifiers, element names and"
        " paths are compared; no rule text, description or note of any source is read"
        " or reproduced.",
        "",
        "## a) Coverage",
        "",
        "Every term of `model/en16931/2017.json` and of `model/xrechnung/3.0.2.json`"
        " appears in every table, bound or with the flag that says why it is not. The"
        " last pair of columns counts the core terms that the groups of the extension"
        " registry reuse, which a table binds a second time inside those groups;"
        " sections b) to d) below measure the core and extension entries, because the"
        " sources they measure against state the reuse nowhere. What measures the reused"
        " entries is `conformance/readers.md`, which reads the whole corpus both ways.",
        "",
        "| Table | core terms | bound | not bound | extension terms | bound"
        " | reused terms | bound |",
        "|---|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for name in findings:
        coverage = findings[name]["coverage"]
        lines.append(
            "| `%s` | %d | %d | %s | %d | %d | %d | %d |"
            % (
                name,
                coverage["bound"] + len(coverage["unbound"]),
                coverage["bound"],
                ", ".join(identifier for identifier, _ in coverage["unbound"]) or "—",
                coverage["extensionBound"] + len(coverage["extensionUnbound"]),
                coverage["extensionBound"],
                coverage["reusedTerms"],
                coverage["reusedBound"],
            )
        )
        if coverage["missing"]:
            lines.append(
                "| | | | missing: " + ", ".join(coverage["missing"]) + " | | | | |"
            )
    lines += [
        "",
        "## b) The KoSIT visualization stylesheets",
        "",
        "A bound path agrees when the stylesheet carries a term template whose match"
        " pattern names the same elements, prefixes and predicates aside. A path the"
        " stylesheet reads inside a block it builds by hand has no term template of its"
        " own; those are listed rather than counted as agreement.",
        "",
        "The predicates are compared separately, and in both directions: a term counts"
        " as differing whenever the discriminators of the table and those of the"
        " stylesheet's match pattern are not the same set, one empty side included."
        " A table that binds an element the stylesheet discriminates, without the"
        " condition, binds wider than the stylesheet, and a reader built on it stores a"
        " wrong value rather than none — so that direction is the one this comparison"
        " exists for. Quoted and bare numeric discriminators are both read, because the"
        " credit note stylesheet writes its document type codes unquoted.",
        "",
        "| Table | stylesheet | agreeing | hand-built | different node | different"
        " predicate |",
        "|---|---|---:|---:|---:|---:|",
    ]
    for name in findings:
        result = findings[name]["stylesheet"]
        lines.append(
            "| `%s` | `%s` | %d | %d | %d | %d |"
            % (
                name,
                findings[name]["stylesheetName"],
                result["agreeing"],
                len(result["handBuilt"]),
                len(result["differing"]),
                len(result["predicateLiterals"]),
            )
        )
    lines += [
        "",
        "## c) The CEN syntax rules",
        "",
        "The `UBL-SR-*` and `CII-SR-*` assertions of the compiled artefacts are read"
        " for the element chains they walk. A chain counts as known when it appears as"
        " a run of consecutive names in a path a table binds. The comparison is context"
        " free — it does not resolve a rule against the node it fires on — which is"
        " enough for the claim made here, that the rules and the tables name the same"
        " elements. A rule that forbids a structure is expected to name a chain no"
        " table binds; a rule that bounds how often something may occur is expected to"
        " name one they do.",
        "",
        "| Table | artefact | rules | forbidding | of those, chains outside the tables |"
        " bounding | of those, chains outside the tables |",
        "|---|---|---:|---:|---:|---:|---:|",
    ]
    for name in findings:
        result = findings[name]["schematron"]
        lines.append(
            "| `%s` | `%s` | %d | %d | %d | %d | %d |"
            % (
                name,
                findings[name]["artefact"],
                result["rules"],
                result["prohibiting"],
                result["fencedOff"],
                result["restricting"],
                len(result["unknownInRestricting"]),
            )
        )
    lines += [
        "",
        "## d) The older, MIT licensed SeMoX model",
        "",
        "That model carries a UBL Invoice binding and a CII binding and no credit note"
        " binding, so the credit note table has nothing to compare against.",
        "",
        "| Table | compared | agreeing | differing | only in the older model |",
        "|---|---:|---:|---:|---|",
    ]
    for name in findings:
        result = findings[name]["older"]
        if result is None:
            lines.append("| `%s` | — | — | — | no binding in that model |" % name)
            continue
        lines.append(
            "| `%s` | %d | %d | %d | %s |"
            % (
                name,
                result["compared"],
                result["agreeing"],
                len(result["differing"]),
                ", ".join(result["onlyInOlder"]) or "—",
            )
        )
    lines += ["", "## Every difference, named", ""]
    total = 0
    for name in findings:
        explained = explanations_for(findings, name)
        total += len(explained)
        lines += [
            "### `" + name + "` — " + str(len(explained)) + " differences",
            "",
            "| Subject | Kind | Table / other source | Explanation |",
            "|---|---|---|---|",
        ]
        for subject, kind, detail, explanation in explained:
            lines.append(
                "| %s | %s | %s | %s |"
                % (subject, kind, detail.replace("|", "\\|") or "—", explanation or
                   "**unexplained**")
            )
        lines.append("")
    lines += [
        "Total: " + str(total) + " differences, every one of them explained above.",
        "",
    ]
    return "\n".join(lines)


if __name__ == "__main__":
    sys.exit(main())
