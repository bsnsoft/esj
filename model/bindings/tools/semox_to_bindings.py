#!/usr/bin/env python3
"""Turn the syntax bindings of a SeMoX model file into the ESJ binding tables.

The source is the SeMoX model of the XRechnung CIUS published by KoSIT. That file is
not part of this repository and never becomes part of it: the path to it is given on
the command line, and only *facts* travel from it into the generated tables — the
business term identifier, the XPath the term is bound to, the XPaths of its
supplementary components, the note codes of the source model as machine flags, and
nothing else. No description, no note text, no rule and no example is copied.

Three tables are written, one per syntax:

    ubl-invoice.json      from the binding `ubl-inv`
    ubl-creditnote.json   from the binding `ubl-cn`
    cii.json              from the binding `cii`

Run it with --help for the options; `model/bindings/README.md` documents the format of
the result and how the tables are cross-checked.

This script has no dependencies beyond the Python standard library.
"""

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

#: The SeMoX model namespace.
M = "{http://semo-xml.org/2025/model}"

#: The format identifier written into every generated table.
FORMAT = "EN16931-Semantic-JSON-binding"

#: The format version written into every generated table.
FORMAT_VERSION = "0.1"

#: The semantic model the tables bind, and the edition of it. The tables bind the core
#: model of the standard; the CIUS the source model describes narrows that model, and a
#: narrowing is not a binding fact and is not carried over.
MODEL = "EN16931-1"
EDITION = "EN 16931-1:2017+A1:2019/AC:2020"

#: Which binding of the source model becomes which table, and under which syntax name.
#: The order of this list is the order the tables are written in.
SYNTAXES = [
    ("ubl-inv", "ubl-invoice.json", "UBL-Invoice", "OASIS UBL 2.1 Invoice"),
    ("ubl-cn", "ubl-creditnote.json", "UBL-CreditNote", "OASIS UBL 2.1 Credit Note"),
    ("cii", "cii.json", "CII", "UN/CEFACT Cross Industry Invoice D16B"),
]

#: Corrections this generator applies to the source, each with the reason for it. A
#: correction is a deliberate, documented departure from the source file; it is written
#: into the `corrections` member of the generated table so that a reader of the table
#: sees it without reading this script.
#:
#: Seven kinds of correction are needed today. The two UBL bindings bind a party
#: identifier to the element that also carries the bank assigned creditor identifier,
#: without the condition that tells the two apart. They also bind four terms that are
#: about value added tax to elements they share with other taxes, again without the
#: condition that tells them apart. The `ubl-cn` binding binds the project reference to
#: the same element as the invoiced object identifier, without the condition that tells
#: those two apart, and it discriminates the invoiced object identifier by a document
#: type code that the code list of that element does not contain. Finally, the `ubl-cn`
#: binding element of the source declares the UBL Invoice namespace as its default
#: namespace although every XPath in it is rooted at `/CreditNote`, whose namespace in
#: UBL 2.1 is the CreditNote namespace, and the second XPath the `cii` binding gives the
#: price base quantity unit ends in an attribute the schema modules of that syntax do
#: not have. The `cii` binding also leaves the supporting document group and three of
#: its terms on the element that carries four different references, without the type
#: code that tells them apart, and it binds the global identifier of four parties with
#: the condition that the element carries a scheme, which loses the identifier of a
#: conformant document that states none.
#:
#: A correction with a `from` and a `to` and the member `terms` rewrites that one XPath
#: wherever it occurs: as the path of a term, as one of its alternatives, and as the
#: anchor of a supplementary component. Where a correction also carries a `term`, only
#: that entry is rewritten, which is what a source that binds two terms to the very same
#: XPath needs.
def sepa_exclusion(root, party):
    """Returns the correction that keeps the SEPA creditor identifier out of a UBL term.

    A UBL document writes the bank assigned creditor identifier BT-90 as a party
    identifier with the scheme SEPA, inside the supplier party or inside the payee party.
    The source binds BT-90 to that element with the predicate that reads the scheme, and
    binds the party identifier of the same party — BT-29 for the seller, BT-60 for the
    payee — to the very same element without a predicate. One element then carries two
    terms, and a consumer that follows the table stores the creditor identifier a second
    time as a party identifier of a party. The condition added here leaves that occurrence
    to BT-90 and takes every other one.
    """
    path = "/" + root + "/" + party + "/cac:PartyIdentification/cbc:ID"
    return {
        "member": "terms",
        "from": path,
        "to": path + "[not(@schemeID = 'SEPA')]",
        "reason": (
            "The source binds BT-90 to this element with the predicate"
            " @schemeID = 'SEPA' and binds this term to the same element without a"
            " predicate, so without the condition one element carries both terms and the"
            " bank assigned creditor identifier is stored a second time as a party"
            " identifier. The condition leaves that occurrence to BT-90."
        ),
    }


def vat_scheme(root, owner, value, term, subject):
    """Returns the correction that narrows a UBL term to the value added tax scheme.

    UBL writes the tax scheme of a party, of a tax subtotal and of an invoice line item
    in a child element of the same element that carries the identifier or the category
    code. The source binds the seller's terms with the condition that reads that scheme
    and binds the terms corrected here to the element without one, so a document that
    states a second tax beside value added tax has the term taken from whichever element
    it happens to write first. The condition added here takes the occurrence whose scheme
    is VAT and leaves every other one alone. It is the condition the CEN validation
    artefact of `packs/` and the KoSIT visualization stylesheet vendored in `esj-xr` both
    write on this element.
    """
    path = "/" + root + "/" + owner + "/" + value
    return {
        "member": "terms",
        "term": term,
        "from": path,
        "to": "/" + root + "/" + owner + "[cac:TaxScheme/cbc:ID = 'VAT']/" + value,
        "reason": (
            "The source binds this term to the element without a condition on the tax"
            " scheme, so a " + subject + " that states a tax beside value added tax has"
            " the term taken from whichever element the document writes first. The CEN"
            " validation artefact of packs/ and the KoSIT visualization stylesheet"
            " vendored in esj-xr both read this element with the condition that its tax"
            " scheme is VAT, and the source itself writes that condition on the seller's"
            " terms bound to the same kind of element."
        ),
    }


def supporting_document(tail):
    """Returns the correction that keeps a CII supporting document term to type code 916.

    The CII syntax writes four different things into `ram:AdditionalReferencedDocument`
    and tells them apart by `ram:TypeCode`: 50 is the tender or lot reference BT-17, 130
    the invoiced object identifier BT-18, 916 the supporting document group BG-24. The
    source discriminates BT-17, BT-18 and BT-122 and leaves the group itself and the
    three remaining terms of it without a condition, so every reference of any kind opens
    a supporting document group, and a document that writes a non-916 reference before a
    916 one shifts the occurrence index of the real supporting document. An occurrence
    index is part of a semantic path, so that is a wrong document and not merely a spare
    group.
    """
    path = ("/rsm:CrossIndustryInvoice/rsm:SupplyChainTradeTransaction"
            "/ram:ApplicableHeaderTradeAgreement/ram:AdditionalReferencedDocument")
    return {
        "member": "terms",
        "from": path + tail,
        "to": path + "[ram:TypeCode = '916']" + tail,
        "reason": (
            "The syntax writes the tender or lot reference, the invoiced object"
            " identifier and the supporting document group into the same element and"
            " tells them apart by ram:TypeCode; the source states that condition for"
            " BT-17, BT-18 and BT-122 and omits it here. Without it every additional"
            " reference of any kind opens a supporting document group, and a reference"
            " that stands before the supporting document in the file moves that"
            " document's occurrence index, which is part of its semantic path. The"
            " condition 916 is the one the CEN validation artefact of packs/ writes on"
            " this element for BT-123 and BT-125."
        ),
    }


def global_identifier(owner):
    """Returns the correction that reads a CII party global identifier without a scheme.

    The CII syntax writes a party identifier into `ram:ID` and into `ram:GlobalID`, and
    the source binds the second of the two with the condition that the element carries a
    `schemeID` attribute. The attribute is optional in the schema of that syntax, so a
    document that states a global identifier and no scheme is conformant and loses the
    identifier: the semantic model holds an identifier whose scheme is absent, and the
    business rule that asks whether the invoice identifies its seller at all then fails on
    an invoice that does identify it. The CEN validation artefact of `packs/` reads this
    element without the condition — `(ram:ID) or (ram:GlobalID) or …` is what it asserts
    BR-CO-26 over — and the scheme stays bound as the supplementary component it is.
    """
    path = "/rsm:CrossIndustryInvoice/rsm:SupplyChainTradeTransaction/" + owner + "/ram:GlobalID"
    return {
        "member": "terms",
        "from": path + "[@schemeID]",
        "to": path,
        "reason": (
            "The schemeID attribute is optional in the schema of this syntax, so a"
            " document that states a global identifier without one is conformant and the"
            " condition loses the identifier rather than its scheme; BR-CO-26 then faults"
            " an invoice that does identify its seller. The CEN validation artefact of"
            " packs/ reads this element without the condition, and the scheme remains"
            " bound as the supplementary component of the term."
        ),
    }


def ubl_supporting_document(root, tail, excluded):
    """Returns the correction that keeps a UBL supporting document term to its own element.

    UBL writes the invoiced object identifier BT-18 into `cac:AdditionalDocumentReference`
    with the document type code 130, and the credit note, which has no
    `cac:ProjectReference`, writes the project reference BT-11 into the same element with
    the code 50. The source states those conditions on those two terms and leaves the
    supporting document group BG-24 and its BT-122, BT-123, BT-124 and BT-125 without one,
    so every reference of any kind opens a supporting document group, and a reference
    standing before the supporting document in the file moves that document's occurrence
    index. An occurrence index is part of a semantic path, so that is a wrong document and
    not merely a spare group. The CII table has carried the matching condition since the
    tables were first generated; these corrections give the two UBL tables theirs.
    """
    path = "/" + root + "/cac:AdditionalDocumentReference"
    condition = "".join(
        "[cbc:DocumentTypeCode != '%s']" % code for code in excluded)
    reasons = {
        "130": "the invoiced object identifier BT-18",
        "50": "the project reference BT-11",
    }
    named = " and ".join(reasons[code] + " (" + code + ")" for code in excluded)
    return {
        "member": "terms",
        "from": path + tail,
        "to": path + condition + tail,
        "reason": (
            "The syntax writes " + named + " and the supporting document group into the"
            " same element and tells them apart by cbc:DocumentTypeCode; the source states"
            " that condition on those terms and omits it here. Without it every additional"
            " document reference of any kind opens a supporting document group, and a"
            " reference that stands before the supporting document in the file moves that"
            " document's occurrence index, which is part of its semantic path. The CEN"
            " validation artefact of packs/ reads this element with the document type code"
            " 130 excluded, and the KoSIT visualization stylesheet vendored in esj-xr"
            " excludes 50 from the credit note."
        ),
    }


def ubl_supporting_documents(root, excluded):
    """Returns the corrections for the supporting document group and its four terms."""
    return [
        ubl_supporting_document(root, "", excluded),
        ubl_supporting_document(root, "/cbc:ID", excluded),
        ubl_supporting_document(root, "/cbc:DocumentDescription", excluded),
        ubl_supporting_document(
            root, "/cac:Attachment/cac:ExternalReference/cbc:URI", excluded),
        ubl_supporting_document(
            root, "/cac:Attachment/cbc:EmbeddedDocumentBinaryObject", excluded),
    ]


CORRECTIONS = {
    "ubl-inv": [
        sepa_exclusion("Invoice", "cac:AccountingSupplierParty/cac:Party"),
        sepa_exclusion("Invoice", "cac:PayeeParty"),
        vat_scheme("Invoice", "cac:AccountingCustomerParty/cac:Party/cac:PartyTaxScheme",
                   "cbc:CompanyID", "BT-48", "buyer"),
        vat_scheme("Invoice", "cac:TaxRepresentativeParty/cac:PartyTaxScheme",
                   "cbc:CompanyID", "BT-63", "tax representative"),
        vat_scheme("Invoice", "cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory",
                   "cbc:ID", "BT-118", "tax breakdown"),
        vat_scheme("Invoice", "cac:InvoiceLine/cac:Item/cac:ClassifiedTaxCategory",
                   "cbc:ID", "BT-151", "line item"),
        *ubl_supporting_documents("Invoice", ["130"]),
        {
            "member": "terms",
            "term": "BT-151",
            "from": ("/Invoice/cac:InvoiceLine//cac:SubInvoiceLine/cac:Item"
                     "/cac:ClassifiedTaxCategory/cbc:ID"),
            "to": ("/Invoice/cac:InvoiceLine//cac:SubInvoiceLine/cac:Item"
                   "/cac:ClassifiedTaxCategory[cac:TaxScheme/cbc:ID = 'VAT']/cbc:ID"),
            "reason": (
                "The same omission as on the invoice line above, on the copy of the term"
                " that the sub invoice line of the extension reuses: the element is bound"
                " without a condition on the tax scheme, so a sub line that states a tax"
                " beside value added tax has its category code taken from whichever element"
                " the document writes first. The condition is the one the CEN validation"
                " artefact of packs/ and the KoSIT visualization stylesheet write on the"
                " element of an invoice line, and the extension states that a sub invoice"
                " line carries the business terms of a line."
            ),
        },
    ],
    "ubl-cn": [
        sepa_exclusion("CreditNote", "cac:AccountingSupplierParty/cac:Party"),
        sepa_exclusion("CreditNote", "cac:PayeeParty"),
        vat_scheme("CreditNote",
                   "cac:AccountingCustomerParty/cac:Party/cac:PartyTaxScheme",
                   "cbc:CompanyID", "BT-48", "buyer"),
        vat_scheme("CreditNote", "cac:TaxRepresentativeParty/cac:PartyTaxScheme",
                   "cbc:CompanyID", "BT-63", "tax representative"),
        vat_scheme("CreditNote", "cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory",
                   "cbc:ID", "BT-118", "tax breakdown"),
        vat_scheme("CreditNote", "cac:CreditNoteLine/cac:Item/cac:ClassifiedTaxCategory",
                   "cbc:ID", "BT-151", "line item"),
        {
            "member": "terms",
            "term": "BT-11",
            "from": "/CreditNote/cac:AdditionalDocumentReference/cbc:ID",
            "to": ("/CreditNote/cac:AdditionalDocumentReference"
                   "[cbc:DocumentTypeCode = '50']/cbc:ID"),
            "reason": (
                "UBL 2.1 gives the credit note no cac:ProjectReference, so the source"
                " binds the project reference to the additional document reference — the"
                " very element, without a condition, that it also binds the supporting"
                " document reference BT-122 to. One element then carries both terms, and"
                " every supporting document of a credit note is read as a project"
                " reference the document does not state. The KoSIT visualization"
                " stylesheet vendored in esj-xr reads this term from the occurrence whose"
                " document type code is 50, which is the condition added here."
            ),
        },
        {
            "member": "terms",
            "term": "BT-18",
            "from": ("/CreditNote/cac:AdditionalDocumentReference"
                     "[cbc:DocumentTypeCode = 'ATS']/cbc:ID"),
            "to": ("/CreditNote/cac:AdditionalDocumentReference"
                   "[cbc:DocumentTypeCode = '130']/cbc:ID"),
            "reason": (
                "ATS is not a code of UNTDID 1001, which is the code list of"
                " cbc:DocumentTypeCode, so no conformant credit note matches the XPath the"
                " source states and the invoiced object identifier is lost without a"
                " word. The UBL Invoice binding of the same source, the CII binding of the"
                " same source, the KoSIT visualization stylesheet vendored in esj-xr and"
                " the CEN validation artefact of packs/ all read this term from the"
                " occurrence whose document type code is 130."
            ),
        },
        *ubl_supporting_documents("CreditNote", ["130", "50"]),
        {
            "member": "namespaces",
            "prefix": "",
            "from": "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
            "to": "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2",
            "reason": (
                "Every XPath of this binding is rooted at the unprefixed step CreditNote,"
                " and the namespace of that element in UBL 2.1 is the CreditNote"
                " namespace. The source declares the Invoice namespace as the default"
                " namespace of the binding element."
            ),
        }
    ],
    "cii": [
        global_identifier("ram:ApplicableHeaderTradeAgreement/ram:SellerTradeParty"),
        global_identifier("ram:ApplicableHeaderTradeAgreement/ram:BuyerTradeParty"),
        global_identifier("ram:ApplicableHeaderTradeSettlement/ram:PayeeTradeParty"),
        global_identifier("ram:ApplicableHeaderTradeDelivery/ram:ShipToTradeParty"),
        supporting_document(""),
        supporting_document("/ram:Name"),
        supporting_document("/ram:URIID"),
        supporting_document("/ram:AttachmentBinaryObject"),
        {
            "member": "terms",
            "from": (
                "/rsm:CrossIndustryInvoice/rsm:SupplyChainTradeTransaction"
                "/ram:IncludedSupplyChainTradeLineItem/ram:SpecifiedLineTradeAgreement"
                "/ram:NetPriceProductTradePrice/ram:BasisQuantity/@UnitCode"
            ),
            "to": (
                "/rsm:CrossIndustryInvoice/rsm:SupplyChainTradeTransaction"
                "/ram:IncludedSupplyChainTradeLineItem/ram:SpecifiedLineTradeAgreement"
                "/ram:NetPriceProductTradePrice/ram:BasisQuantity/@unitCode"
            ),
            "reason": (
                "The quantity type of UN/CEFACT CII D16B carries the unit of measure on"
                " an attribute named unitCode, and the schema modules of that syntax"
                " declare no attribute named UnitCode on it or on anything else. Every"
                " other unit of measure the source binds, in this syntax and in the two"
                " UBL ones, is bound to the attribute with the lower case first letter."
            ),
        }
    ],
}

#: Values written where the syntax requires an element that no business term of the
#: semantic model states. A convention is not taken from the source model and is not a
#: departure from it: the source binds business terms, and these elements carry none. Each
#: one names the element, the condition it applies under, the value, what asks for the
#: element and where the value comes from, and goes into the `conventions` member of the
#: generated table so that the writer and the reader read it rather than carry it in code.
FC_SOURCE = (
    "The cross industry invoice binding of the same source model writes BT-32 at an"
    " element whose schemeID is FC, the UNTDID 1153 code for a fiscal number, so the code"
    " is the one the standard already uses for this registration."
)

NA_ORDER = (
    "Peppol BIS Billing 3.0 records NA at this element for a document that states a sales"
    " order reference and no purchase order reference."
)

NA_CARD = (
    "Peppol BIS Billing 3.0 records NA as the value of this element, which its syntax"
    " requires and no business term of EN 16931-1 names."
)


def ubl_conventions(root):
    """Returns the three conventions of a UBL binding, rooted at its document element."""
    return [
        {
            "element": "/%s/cac:AccountingSupplierParty/cac:Party/cac:PartyTaxScheme"
            "/cac:TaxScheme/cbc:ID" % root,
            "condition": "parent-written-element-absent",
            "value": "FC",
            "option": "taxRegistrationScheme",
            "requiredBy": "UBL-SR-53",
            "source": FC_SOURCE,
        },
        {
            "element": "/%s/cac:OrderReference/cbc:ID" % root,
            "condition": "parent-written-element-absent",
            "value": "NA",
            "term": "BT-13",
            "notReadBeside": "cbc:SalesOrderID",
            "requiredBy": "schema",
            "source": NA_ORDER,
        },
        {
            "element": "/%s/cac:PaymentMeans/cac:CardAccount/cbc:NetworkID" % root,
            "condition": "parent-written-element-absent",
            "value": "NA",
            "requiredBy": "schema",
            "source": NA_CARD,
        },
    ]


CONVENTIONS = {
    "ubl-inv": ubl_conventions("Invoice"),
    "ubl-cn": ubl_conventions("CreditNote"),
    "cii": [],
}

#: The component identifier prefixes of the source model and the ESJ value member each
#: one feeds. ESJ knows four supplementary component roles and no others; a component
#: whose identifier matches none of these prefixes is an error rather than a silent
#: omission.
COMPONENT_ROLES = [
    ("scheme-version-id-", "schemeVersion"),
    ("scheme-id-", "scheme"),
    ("mime-code-", "mimeCode"),
    ("doc-filename-", "filename"),
]

#: The order the component roles are written in, so that the tables are byte-stable.
ROLE_ORDER = ["scheme", "schemeVersion", "mimeCode", "filename"]

#: The note codes of the source model, with the meaning ESJ gives each one. The source
#: model writes these codes without defining them; their normative definitions belong to
#: the CEN syntax binding specifications, which this project does not hold and does not
#: restate. Each line below says what the bindings carrying the code have in common in
#: the release the tables were generated from, and `conformance/bindings/crosscheck.md`
#: gives the counts behind it.
SOURCE_FLAGS = {
    "CAR-2": "The semantic model requires the term where the syntax leaves the element optional.",
    "CAR-3": "The syntax lets the element repeat where the semantic model allows the term only once.",
    "CAR-4": "The syntax bounds the element more narrowly than the semantic model bounds the term.",
    "SEM-2": "The syntax element is semantically wider than the term; a predicate or a convention picks out the occurrence that carries it.",
    "SEM-3": "The syntax element means something else in its own syntax, so the binding is a convention rather than a like-for-like match.",
    "STR-2": "The syntax nests the element inside a group that the semantic model places it outside of.",
    "STR-3": "No element of the syntax corresponds one to one to the term; it is reached through an element that stands for something else, or through more than one.",
    "STR-4": "One syntax element serves several terms or groups, and an indicator element or an attribute value tells them apart.",
    "STR-5": "A series of like terms of the semantic model is spread over differently named syntax elements.",
    "SYN-1": "The syntax offers two elements for the value, and which one carries it depends on whether an identification scheme can be given.",
    "SYN-2": "The lexical form in the syntax is not the semantic value written plainly: it is prefixed, formatted or otherwise encoded.",
}

#: Flags ESJ derives from the bound XPaths themselves. They are lower case so that no
#: reader mistakes them for codes of the source model.
DERIVED_FLAGS = {
    "not-represented": "The source model states that this syntax has no representation for the term, so the entry carries no XPath.",
    "code-list-2475": "The bound element states the code in UNTDID 2475, the list this syntax gives it, while the term's code list is UNTDID 2005; a reader and a writer translate between the two.",
    "date-format-102": "The bound element is a CII date string whose format attribute is 102, so the value is written as ccyymmdd rather than as an ISO date.",
    "subject-code-prefix": "The subject code is written into the text value as a #CODE# prefix instead of into an element of its own.",
    "scheme-as-sibling-element": "The identification scheme is carried by a sibling element of the value rather than by an attribute of it.",
    "extension-not-bound": "The extension that defines this term states a binding for other syntaxes only, so this table has no XPath for it.",
}

#: Matches the marker the source model uses for the note subject code prefix of BT-21.
#: The marker is a fact about the UBL binding; the sentence it stands in is not copied.
SUBJECT_CODE_MARKER = re.compile(r"#\s*subject code\s*#", re.IGNORECASE)

#: Matches the CII date format attribute inside a bound XPath.
DATE_FORMAT_102 = re.compile(r"@format\s*=\s*'102'")

#: The element the cross industry invoice writes the value added tax point date code in.
#: The syntax gives it the code list UNTDID 2475 and EN 16931-1 gives BT-8 a restriction of
#: UNTDID 2005, so the two spell the same three events differently. The path is the marker
#: the derived flag is set from; the translation itself is the reader's and the writer's.
DUE_DATE_TYPE_CODE = "/ram:DueDateTypeCode"

#: Matches an `xmlns` or `xmlns:prefix` attribute of a start tag.
XMLNS = re.compile(r"""\sxmlns(?::([A-Za-z_][\w.\-]*))?\s*=\s*"([^"]*)\"""")

#: Matches a prefixed name in an XPath step, so that the prefixes a table uses can be
#: collected and checked against the namespaces it declares.
PREFIXED_NAME = re.compile(r"(?<![\w\-])([A-Za-z_][\w.\-]*):(?=[A-Za-z_])")


def split_steps(xpath):
    """Splits an absolute XPath into its steps, ignoring slashes inside predicates.

    A CII binding predicate may hold an absolute path of its own, so a plain split on
    the slash would tear such a step apart.
    """
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
    if steps and steps[0] == "":
        steps = steps[1:]
    return steps


def split_union(xpath):
    """Splits an expression at the union bars that stand outside predicate and group."""
    parts = []
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
        elif char in "[(":
            depth += 1
            current += char
        elif char in "])":
            depth -= 1
            current += char
        elif char == "|" and depth == 0:
            parts.append(current)
            current = ""
        else:
            current += char
    parts.append(current)
    return [part for part in parts if part.strip()]


def expand_group(xpath):
    """Expands one parenthesised branch group into the absolute paths it stands for.

    The tailoring model of the extension writes the two places a reused term may sit as
    one expression with a common prefix — `/Invoice/cac:InvoiceLine/(.//cac:SubInvoiceLine
    /cbc:ID | cbc:ID)`. Each branch, appended to the prefix, is a plain path of the shape
    the rest of this generator and the binding tables use; a branch that opens with the
    context step and the descendant axis keeps that axis and loses the context step.
    """
    depth = 0
    quote = None
    opened = -1
    for index, char in enumerate(xpath):
        if quote:
            if char == quote:
                quote = None
        elif char in "'\"":
            quote = char
        elif char == "[":
            depth += 1
        elif char == "]":
            depth -= 1
        elif char == "(" and depth == 0:
            opened = index
            break
    if opened < 0:
        return [xpath]
    depth = 0
    closed = -1
    for index in range(opened, len(xpath)):
        if xpath[index] == "(":
            depth += 1
        elif xpath[index] == ")":
            depth -= 1
            if depth == 0:
                closed = index
                break
    if closed < 0:
        raise SystemExit("a bound expression of the source has an unclosed group: " + xpath)
    prefix = xpath[:opened].rstrip("/")
    suffix = xpath[closed + 1:]
    paths = []
    for branch in split_union(xpath[opened + 1:closed]):
        branch = branch.strip()
        if branch.startswith("./"):
            branch = branch[1:]
        elif not branch.startswith("/"):
            branch = "/" + branch
        paths.extend(expand_group(prefix + branch + suffix))
    return paths


def split_alternatives(xpath):
    """Splits a bound expression of the source into the plain paths it stands for."""
    paths = []
    for part in split_union(xpath):
        for path in expand_group(" ".join(part.split())):
            if path.strip():
                paths.append(path)
    return paths


def bare(step):
    """Returns a step without its predicates, for comparing two paths step by step."""
    return step.split("[", 1)[0].strip()


def relative_path(anchor, absolute):
    """Expresses `absolute` relative to the element `anchor` is bound to.

    Returns the relative path and the number of steps it has to climb. Both are needed:
    the caller picks the anchor that climbs the least among the paths a term is bound to.
    """
    anchor_steps = [bare(s) for s in split_steps(anchor)]
    target_steps = split_steps(absolute)
    shared = 0
    while (
        shared < len(anchor_steps)
        and shared < len(target_steps)
        and anchor_steps[shared] == bare(target_steps[shared])
    ):
        shared += 1
    climb = len(anchor_steps) - shared
    return "/".join([".."] * climb + target_steps[shared:]), climb


def raw_bindings(path):
    """Cuts the model file into the source text of each binding, by binding identifier.

    ElementTree keeps neither namespace declarations nor comments, and this generator
    needs both: the declarations become the namespace map of a table, and a comment is
    how the source states that a term has no representation in a syntax.
    """
    text = Path(path).read_text(encoding="utf-8")
    starts = [
        (match.start(), match.group(0), re.search(r'\sid\s*=\s*"([^"]*)"', match.group(0)))
        for match in re.finditer(r"<m:binding\b[^>]*>", text, re.S)
    ]
    sections = {}
    for index, (offset, tag, identifier) in enumerate(starts):
        if identifier is None:
            continue
        end = starts[index + 1][0] if index + 1 < len(starts) else len(text)
        sections[identifier.group(1)] = (tag, text[offset:end])
    return sections


def namespaces_of(tag):
    """Reads the namespace declarations of a binding start tag."""
    return {(prefix or ""): uri for prefix, uri in XMLNS.findall(tag)}


def note_codes(term):
    """Returns the note codes of a term of the source model, in source order."""
    return [
        (note.text or "").strip()
        for note in term.findall(M + "notes/" + M + "note")
        if (note.text or "").strip()
    ]


def has_subject_code_marker(term):
    """Says whether a usage note of the term carries the subject code prefix marker."""
    for note in term.findall(M + "notes/" + M + "usage-note"):
        if SUBJECT_CODE_MARKER.search("".join(note.itertext())):
            return True
    return False


def not_represented(raw):
    """Returns the terms the source model marks as not represented in this syntax.

    The source states them as comments between the bound terms, in the form
    `<!-- BG-1 not represented syntactically -->`.
    """
    return [
        match.group(1)
        for match in re.finditer(
            r"<!--\s*((?:BT|BG)-[\w\-]+)\s+not represented syntactically\s*-->", raw
        )
    ]


def collect_terms(binding):
    """Groups the term elements of a binding by the business term they bind.

    A term may be bound more than once — the source states two separate term elements
    for it, or one whose XPath is a union — and every bound path is kept.
    """
    grouped = {}
    order = []
    for term in binding.findall(M + "term"):
        identifier = term.get("ref")
        if identifier not in grouped:
            grouped[identifier] = []
            order.append(identifier)
        grouped[identifier].append(term)
    return order, grouped


def split_predicates(text):
    """Returns the bodies of the predicates a step carries, in the order it writes them."""
    bodies = []
    depth = 0
    quoted = False
    start = 0
    for index, char in enumerate(text):
        if char == "'":
            quoted = not quoted
        elif not quoted and char == "[":
            depth += 1
            if depth == 1:
                start = index + 1
        elif not quoted and char == "]":
            depth -= 1
            if depth == 0:
                bodies.append(text[start:index].strip())
    return bodies


def set_instance(entry, xpath):
    """Writes the element and the condition of a group's last step into its entry.

    The two members keep their place directly after `xpath` whether they are written for
    the first time or written again after a correction, so that the generated file is the
    same whichever way an entry reached its XPath.
    """
    last = split_steps(xpath)[-1]
    ordered = {}
    for key, value in list(entry.items()):
        if key in ("instanceElement", "instancePredicates"):
            continue
        ordered[key] = value
        if key == "xpath":
            ordered["instanceElement"] = bare(last)
            predicates = split_predicates(last[len(bare(last)):])
            if predicates:
                ordered["instancePredicates"] = predicates
    entry.clear()
    entry.update(ordered)


def rewrite_path(entries, source, target, term=None):
    """Replaces one XPath wherever a set of entries states it.

    A correction that names a term rewrites that entry alone, which is what a source
    that binds two terms to the very same XPath needs.

    Where the entry is a business group, the members that repeat its last step —
    `instanceElement` and `instancePredicates` — are derived again from the new XPath, so
    that a correction which adds a condition to a group cannot leave the table saying two
    different things about the same element.
    """
    found = False
    for entry in entries:
        if term is not None and entry["id"] != term:
            continue
        if entry.get("xpath") == source:
            entry["xpath"] = target
            if "instanceElement" in entry:
                set_instance(entry, target)
            found = True
        for alternative in entry.get("alternatives", []):
            if alternative["xpath"] == source:
                alternative["xpath"] = target
                found = True
        for component in entry.get("components", []):
            if component["anchor"] == source:
                component["anchor"] = target
                found = True
    if not found:
        raise SystemExit("a correction names an XPath the source does not state: " + source)


def build_entry(identifier, elements, registry, exclude=()):
    """Builds one entry of a binding table from every term element that binds it.

    `exclude` names paths the entry does not take although the element binds them. It is
    how a reused term of the extension is kept to the places inside the extension group:
    the source binds such a term to its own place and to the one inside the group in one
    expression, and the first of the two is the entry the core table already carries.
    """
    paths = []
    flags = []
    components = []
    for element in elements:
        codes = note_codes(element)
        for path in split_alternatives(element.get("xpath")):
            if path in exclude:
                continue
            # The source binds a term twice where it also unions the two paths into one
            # term element, so the same path can arrive more than once. It is one path
            # with the flags of every element that states it.
            known = next((each for each in paths if each["xpath"] == path), None)
            if known is None:
                paths.append({"xpath": path, "flags": list(codes)})
            else:
                known["flags"] += [c for c in codes if c not in known["flags"]]
        for code in codes:
            if code not in flags:
                flags.append(code)
        if has_subject_code_marker(element) and "subject-code-prefix" not in flags:
            flags.append("subject-code-prefix")
        for component in element.findall(M + "component"):
            components.append(
                (component.get("ref"), split_alternatives(component.get("xpath"))))

    if not paths:
        raise SystemExit("the source binds " + identifier
                         + " nowhere this entry may take it")
    if any(DATE_FORMAT_102.search(path["xpath"]) for path in paths):
        flags.append("date-format-102")
    if any(path["xpath"].endswith(DUE_DATE_TYPE_CODE) for path in paths):
        flags.append("code-list-2475")

    entry = {
        "id": identifier,
        "kind": registry[identifier]["kind"],
        "bound": True,
        "xpath": paths[0]["xpath"],
    }
    if registry[identifier]["kind"] == "BG":
        set_instance(entry, paths[0]["xpath"])
    if len(paths) > 1:
        entry["alternatives"] = [
            {"xpath": path["xpath"], "flags": path["flags"]} for path in paths[1:]
        ]

    resolved = []
    for reference, absolutes in components:
        role = role_of(reference)
        best = None
        for absolute in absolutes:
            for path in paths:
                relative, climb = relative_path(path["xpath"], absolute)
                if best is None or climb < best[1]:
                    best = (relative, climb, path["xpath"])
        if best is None:
            # The source binds the component beside a path this entry does not take, so
            # the component belongs to the entry that takes that path.
            continue
        found = {"role": role, "xpath": best[0], "anchor": best[2]}
        if found in resolved:
            continue
        resolved.append(found)
        if not best[0].startswith("@") and role == "scheme":
            if "scheme-as-sibling-element" not in flags:
                flags.append("scheme-as-sibling-element")
    resolved.sort(key=lambda component: ROLE_ORDER.index(component["role"]))
    if resolved:
        entry["components"] = resolved
    if flags:
        entry["flags"] = sorted(flags, key=flag_sort_key)
    return entry


def role_of(reference):
    """Maps a component identifier of the source model to an ESJ component role."""
    for prefix, role in COMPONENT_ROLES:
        if reference.startswith(prefix):
            return role
    raise SystemExit("unknown supplementary component in the source model: " + reference)


def flag_sort_key(flag):
    """Sorts the note codes of the source model before the flags ESJ derives."""
    return (1, flag) if flag in DERIVED_FLAGS else (0, flag)


def build_extension_entries(tailoring, binding_id, extension_registry):
    """Builds the entries of the extension terms for one syntax.

    The extension states its bindings on the term itself rather than in a binding
    section, one `syntax-binding` element per syntax it binds the term for. A term the
    extension does not bind for this syntax becomes an unbound entry.
    """
    entries = []
    for term in tailoring.iter(M + "term"):
        identifier = term.get("id")
        if identifier is None or identifier not in extension_registry:
            continue
        bindings = [
            element
            for element in term.findall(M + "syntax-binding")
            if element.get("for-syntax") == binding_id
        ]
        if not bindings:
            entries.append(
                {
                    "id": identifier,
                    "kind": extension_registry[identifier]["kind"],
                    "bound": False,
                    "flags": ["extension-not-bound"],
                }
            )
            continue
        entries.append(build_entry(identifier, bindings, extension_registry))
    entries.sort(key=lambda entry: extension_registry[entry["id"]]["order"])
    return entries


def build_reused_entries(tailoring, binding_id, registry, extension_registry, entries):
    """Builds the entries of the core terms the extension groups carry.

    An extension group of the XRechnung extension is a sub invoice line, and a sub invoice
    line carries the business terms of an invoice line: its identifier, its quantity, its
    net amount, its item, its category, its price. The registry records that reuse in the
    `reusesTerms` member of the group, and the source states, for every term reused that
    way, one expression that binds the term both where it sits by itself and where it sits
    inside the group. The entry built here takes the second of the two: the first is the
    entry the core table already carries.

    A syntax the extension binds no group for has no such expression, and every reused
    term of it is an unbound entry, exactly as the extension's own terms are.

    @param tailoring          the root of the tailoring model
    @param binding_id         the binding of the source this table is built from
    @param registry           the core registry, which gives kind and order
    @param extension_registry the extension registry, whose groups state the reuse
    @param entries            the core entries of this table, whose paths are excluded
    """
    bound = {}
    for term in tailoring.iter(M + "term"):
        reference = term.get("{http://semo-xml.org/2025/model/tailoring}ref")
        if reference is None:
            continue
        for element in term.findall(M + "syntax-binding"):
            if element.get("for-syntax") == binding_id:
                bound.setdefault(reference, []).append(element)

    taken = {}
    for entry in entries:
        taken[entry["id"]] = {entry["xpath"]} if entry.get("xpath") else set()
        for alternative in entry.get("alternatives", []):
            taken[entry["id"]].add(alternative["xpath"])

    reused = []
    seen = set()
    for group in extension_registry.values():
        for identifier in group.get("reusesTerms", []):
            if identifier in extension_registry or identifier in seen:
                continue
            if identifier not in registry:
                raise SystemExit("an extension group reuses a term no registry knows: "
                                 + identifier)
            seen.add(identifier)
            elements = bound.get(identifier, [])
            if not elements:
                reused.append({
                    "id": identifier,
                    "kind": registry[identifier]["kind"],
                    "bound": False,
                    "flags": ["extension-not-bound"],
                })
                continue
            reused.append(build_entry(identifier, elements, registry,
                                       exclude=taken.get(identifier, set())))
    return reused


def build_table(binding, raw, syntax, syntax_name, namespaces, registry, release,
                source_file, extension=None):
    """Builds one binding table from one binding element of the source model."""
    order, grouped = collect_terms(binding)
    unbound = not_represented(raw)

    entries = []
    for identifier in order:
        entries.append(build_entry(identifier, grouped[identifier], registry))
    for identifier in unbound:
        entries.append(
            {
                "id": identifier,
                "kind": registry[identifier]["kind"],
                "bound": False,
                "flags": ["not-represented"],
            }
        )
    entries.sort(key=lambda entry: registry[entry["id"]]["order"])
    extension_entries = extension["terms"] if extension else []
    reused_entries = build_reused_entries(
        extension["tailoring"], binding.get("id"), registry, extension["registry"],
        entries) if extension else []
    all_entries = entries + extension_entries + reused_entries

    corrections = CORRECTIONS.get(binding.get("id"), [])
    conventions = CONVENTIONS.get(binding.get("id"), [])
    namespaces = dict(namespaces)
    for correction in corrections:
        if correction["member"] == "namespaces":
            namespaces[correction["prefix"]] = correction["to"]
        elif correction["member"] == "terms":
            rewrite_path(all_entries, correction["from"], correction["to"],
                         correction.get("term"))

    used = set()
    for entry in all_entries:
        for path in [entry.get("xpath", "")] + [
            alternative["xpath"] for alternative in entry.get("alternatives", [])
        ]:
            used.update(PREFIXED_NAME.findall(path))
    missing = sorted(prefix for prefix in used if prefix not in namespaces)
    if missing:
        raise SystemExit(
            "the source binding uses prefixes it does not declare: " + ", ".join(missing)
        )

    flags_used = sorted(
        {flag for entry in all_entries for flag in entry.get("flags", [])}
        | {
            flag
            for entry in all_entries
            for alternative in entry.get("alternatives", [])
            for flag in alternative["flags"]
        },
        key=flag_sort_key,
    )
    definitions = {}
    for flag in flags_used:
        if flag in SOURCE_FLAGS:
            definitions[flag] = SOURCE_FLAGS[flag]
        elif flag in DERIVED_FLAGS:
            definitions[flag] = DERIVED_FLAGS[flag]
        else:
            raise SystemExit("the source model uses an unknown note code: " + flag)

    bound = [entry for entry in entries if entry["bound"]]
    extension_bound = [entry for entry in extension_entries if entry["bound"]]
    reused_bound = [entry for entry in reused_entries if entry["bound"]]
    table = {
        "format": FORMAT,
        "version": FORMAT_VERSION,
        "syntax": syntax,
        "syntaxName": syntax_name,
        "model": MODEL,
        "edition": EDITION,
        "rootElement": split_steps(bound[0]["xpath"])[0],
        "license": "Apache-2.0",
        "notice": (
            "The identifiers, XPaths, supplementary component XPaths and note codes in"
            " this file are facts taken from the SeMoX model of the XRechnung CIUS"
            " published by KoSIT (Koordinierungsstelle für IT-Standards), release "
            + release
            + ", binding "
            + binding.get("id")
            + ". No description, note text, rule or example of that model is reproduced"
            " here: every sentence in this file is the author's own wording. The licence"
            " of the model repository the facts come from is being clarified with KoSIT;"
            " see model/bindings/README.md. This file itself is Apache-2.0. ESJ is not an"
            " XRechnung format and this file is not a KoSIT deliverable."
        ),
        "source": {
            "name": "KoSIT SeMoX model of the XRechnung CIUS",
            "file": source_file,
            "binding": binding.get("id"),
            "release": release,
        },
        "generator": "model/bindings/tools/semox_to_bindings.py",
        "namespaces": dict(sorted(namespaces.items())),
        "corrections": corrections,
        "conventions": conventions,
        "flagDefinitions": definitions,
        "counts": {
            "terms": len(entries),
            "bound": len(bound),
            "notRepresented": len(entries) - len(bound),
            "components": sum(len(entry.get("components", [])) for entry in entries),
            "extensionTerms": len(extension_entries),
            "extensionBound": len(extension_bound),
            "extensionReusedTerms": len(reused_entries),
            "extensionReusedBound": len(reused_bound),
        },
        "terms": entries,
    }
    if extension is not None:
        table["extension"] = {
            "model": extension["model"],
            "edition": extension["edition"],
            "source": {
                "name": "KoSIT SeMoX tailoring model of the XRechnung extension",
                "file": extension["file"],
                "release": release,
            },
            "terms": extension_entries,
            "reusedTerms": reused_entries,
        }
    return table


def registry_of(path):
    """Reads a registry file and returns its terms by identifier."""
    with open(path, encoding="utf-8") as stream:
        return {term["id"]: term for term in json.load(stream)["terms"]}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "model",
        help="path to the SeMoX model file to read; it is never copied into this"
        " repository",
    )
    parser.add_argument(
        "--out",
        default=str(Path(__file__).resolve().parent.parent),
        help="directory the tables are written to (default: model/bindings)",
    )
    parser.add_argument(
        "--registry",
        default=str(
            Path(__file__).resolve().parents[2] / "en16931" / "2017.json"
        ),
        help="path to the core registry, which fixes the order of the entries",
    )
    parser.add_argument(
        "--tailoring",
        help="path to the SeMoX tailoring model of the XRechnung extension, which binds"
        " the extension terms; without it the tables carry the core model alone",
    )
    parser.add_argument(
        "--extension-registry",
        default=str(
            Path(__file__).resolve().parents[2] / "xrechnung" / "3.0.2.json"
        ),
        help="path to the extension registry, which fixes the order of the extension"
        " entries",
    )
    parser.add_argument(
        "--release",
        required=True,
        help="release of the source model, as recorded in standards/kosit",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="compare with the files on disk instead of writing them",
    )
    arguments = parser.parse_args(argv)

    registry = registry_of(arguments.registry)
    sections = raw_bindings(arguments.model)
    root = ET.parse(arguments.model).getroot()
    bindings = {
        binding.get("id"): binding
        for binding in root.find(M + "syntax-bindings").findall(M + "binding")
    }
    source_file = Path(arguments.model).name

    tailoring = None
    extension_registry = None
    if arguments.tailoring:
        tailoring = ET.parse(arguments.tailoring).getroot()
        with open(arguments.extension_registry, encoding="utf-8") as stream:
            extension_file = json.load(stream)
        extension_registry = {term["id"]: term for term in extension_file["terms"]}

    differences = 0
    for binding_id, file_name, syntax, syntax_name in SYNTAXES:
        if binding_id not in bindings:
            raise SystemExit("the model file has no binding " + binding_id)
        extension = None
        if tailoring is not None:
            extension = {
                "model": extension_file["model"],
                "edition": extension_file["edition"],
                "file": Path(arguments.tailoring).name,
                "terms": build_extension_entries(
                    tailoring, binding_id, extension_registry
                ),
                "tailoring": tailoring,
                "registry": extension_registry,
            }
        table = build_table(
            bindings[binding_id],
            sections[binding_id][1],
            syntax,
            syntax_name,
            namespaces_of(sections[binding_id][0]),
            registry,
            arguments.release,
            source_file,
            extension,
        )
        text = json.dumps(table, indent=2, ensure_ascii=False) + "\n"
        target = Path(arguments.out) / file_name
        if arguments.check:
            current = target.read_text(encoding="utf-8") if target.exists() else ""
            if current != text:
                print(target.name + " differs from the model", file=sys.stderr)
                differences += 1
            else:
                print(target.name + " is up to date")
        else:
            target.write_text(text, encoding="utf-8")
            print(
                "%s: %d core terms, %d bound; %d extension terms, %d bound;"
                " %d reused terms, %d bound; %d components"
                % (
                    target.name,
                    table["counts"]["terms"],
                    table["counts"]["bound"],
                    table["counts"]["extensionTerms"],
                    table["counts"]["extensionBound"],
                    table["counts"]["extensionReusedTerms"],
                    table["counts"]["extensionReusedBound"],
                    table["counts"]["components"],
                )
            )
    return 1 if differences else 0


if __name__ == "__main__":
    sys.exit(main())
