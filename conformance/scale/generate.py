#!/usr/bin/env python3
"""Grow a corpus invoice into a synthetic instance with many invoice lines.

The instance is built by replicating the first invoice line of the source document and
recomputing every total from the replicated lines, so that the result stays
arithmetically consistent: BT-131 = BT-129 x BT-146 per line, BT-106 the sum of them,
BT-109 = BT-106, one VAT breakdown with BT-116 = BT-109 and BT-117 = BT-116 x rate,
BT-110 = BT-117, BT-112 = BT-109 + BT-110 and BT-115 = BT-112.

With --dense the replicated line is not the one the source carries but the smallest line
EN 16931 admits: the line identifier, the invoiced quantity with its unit, the line net
amount, the item name, the classified VAT category and the item net price, and nothing
else. A corpus line is a rich line and costs between 1.5 and 2.6 kB; a dense line costs a
few hundred bytes, so the same number of bytes buys several times as many lines. The two
classes answer different questions -- how many bytes have to pass the front door, and how
many values the document carries -- and the measurements name which of the two they were
taken at.

With --text-bytes every generated line instead carries that many bytes of filler words in
its item name, which is how the documents for the rendering measurements are made: they
are large in what one value says rather than in how many values they have, and a renderer
pays for the first in pages. A dense line has no room for filler, so the two options are
not combined.

Only a source with a single VAT rate and without document level allowances or charges
is accepted, because those are the two things this arithmetic does not model; a source
that carries one is refused by name rather than grown into a document whose totals are
wrong.

Nothing is parsed into a tree. The document is split into the text before the lines, the
text of the first line and the text after the lines, the totals are substituted in the
two outer parts, and the file is written line by line, so an instance of a few hundred
thousand lines costs a constant amount of memory.

Run it with --help for the options. The generated files are not part of the repository;
see README.md in this directory.
"""

import argparse
import re
import sys
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

#: How many decimal places an amount is written with.
AMOUNT = Decimal("0.01")

#: The width of the generated line identifier, so that every rendered line is the same
#: number of bytes and the size of the file can be computed rather than measured.
ID_WIDTH = 9

#: The invoiced quantity of a dense line, one unit of the unit code below.
DENSE_QUANTITY = Decimal("1")

#: The item net price of a dense line.
DENSE_PRICE = Decimal("1.00")

#: The unit of measure of a dense line: one piece, UN/ECE Recommendation 20 code C62.
DENSE_UNIT = "C62"

#: The item name of a dense line.
DENSE_ITEM = "Item"

#: The VAT category code of a dense line: standard rate.
DENSE_CATEGORY = "S"

#: The largest number of iterations the search for a line count that reaches
#: --target-bytes takes. The size of the file grows strictly with the number of lines,
#: and only the digits of the totals change with it, so the search settles at once.
GROWTH_ROUNDS = 8


#: The word the filler of --text-bytes is made of, with the space that follows it.
FILLER_WORD = "text "


class Unsuitable(Exception):
    """The source document is not one this generator can grow."""


def filler(text_bytes):
    """Returns that many bytes of filler words, whole words only, or an empty string."""
    if not text_bytes:
        return ""
    words = max(1, text_bytes // len(FILLER_WORD))
    return (FILLER_WORD * words).strip()


def element(prefix, name):
    """Returns a pattern for one element with a text content, attributes included."""
    return re.compile(r"<" + prefix + ":" + name + r"(\s[^>]*)?>([^<]*)</"
                      + prefix + ":" + name + r">")


def block(prefix, name):
    """Returns a pattern for one element with everything inside it."""
    return re.compile(r"<" + prefix + ":" + name + r"(?:\s[^>]*)?>.*?</"
                      + prefix + ":" + name + r">", re.DOTALL)


def sole(pattern, text, what):
    """Returns the one match of a pattern, and refuses a document with none or several."""
    matches = pattern.findall(text)
    if len(matches) != 1:
        raise Unsuitable("the source holds " + str(len(matches)) + " " + what
                         + ", and this generator grows a document with exactly one")
    return matches[0]


def content(pattern, text, what):
    """Returns the text content of the one element a pattern matches."""
    match = pattern.search(text)
    if match is None:
        raise Unsuitable("the source holds no " + what)
    return match.group(2)


def substitute(pattern, text, value, what):
    """Replaces the content of the one element a pattern matches, keeping its attributes."""
    replaced, count = pattern.subn(
        lambda match: match.group(0)[:match.start(2) - match.start(0)] + value
        + match.group(0)[match.end(2) - match.start(0):], text, count=1)
    if count != 1:
        raise Unsuitable("the source holds no " + what)
    return replaced


def refuse(pattern, text, what):
    """Refuses a source that carries something this generator does not model."""
    if pattern.search(text) is not None:
        raise Unsuitable("the source carries " + what + ", which this generator does not"
                         " recompute; choose an instance without one")


def amount(value):
    """Returns an amount rounded to two decimal places, as a decimal string."""
    return str(value.quantize(AMOUNT, rounding=ROUND_HALF_UP))


def totals(quantity, price, rate, lines):
    """Returns the recomputed totals of a document of the given number of equal lines."""
    line_net = (quantity * price).quantize(AMOUNT, rounding=ROUND_HALF_UP)
    bt106 = line_net * lines
    bt109 = bt106
    bt116 = bt109
    bt117 = (bt116 * rate / Decimal(100)).quantize(AMOUNT, rounding=ROUND_HALF_UP)
    bt110 = bt117
    bt112 = bt109 + bt110
    return {
        "BT-131": amount(line_net),
        "BT-106": amount(bt106),
        "BT-109": amount(bt109),
        "BT-116": amount(bt116),
        "BT-117": amount(bt117),
        "BT-110": amount(bt110),
        "BT-112": amount(bt112),
        "BT-115": amount(bt112),
    }


class Ubl:
    """The element names of a UBL 2.1 invoice this generator touches."""

    syntax = "ubl"
    root = "Invoice"
    line = block("cac", "InvoiceLine")
    line_open = "<cac:InvoiceLine>"
    line_close = "</cac:InvoiceLine>"

    line_id = element("cbc", "ID")
    item_name = element("cbc", "Name")
    quantity = element("cbc", "InvoicedQuantity")
    line_net = element("cbc", "LineExtensionAmount")
    price_block = block("cac", "Price")
    price = element("cbc", "PriceAmount")
    line_refusals = [(element("cbc", "BaseQuantity"), "a line with a base quantity"),
                     (block("cac", "AllowanceCharge"), "a line allowance or charge")]

    tax_total = block("cac", "TaxTotal")
    tax_subtotal = block("cac", "TaxSubtotal")
    taxable = element("cbc", "TaxableAmount")
    tax = element("cbc", "TaxAmount")
    rate = element("cbc", "Percent")
    monetary_total = block("cac", "LegalMonetaryTotal")
    tax_exclusive = element("cbc", "TaxExclusiveAmount")
    tax_inclusive = element("cbc", "TaxInclusiveAmount")
    payable = element("cbc", "PayableAmount")
    document_refusals = [
        (block("cac", "AllowanceCharge"), "a document level allowance or charge"),
        (element("cbc", "AllowanceTotalAmount"), "a document level allowance total"),
        (element("cbc", "ChargeTotalAmount"), "a document level charge total"),
        (element("cbc", "PrepaidAmount"), "a prepaid amount"),
        (element("cbc", "PayableRoundingAmount"), "a rounding amount")]

    currency = element("cbc", "DocumentCurrencyCode")

    @staticmethod
    def rates(head, tail):
        """Returns the VAT rate of the one breakdown, and refuses a document with more."""
        totals_block = sole(Ubl.tax_total, head + tail, "document level tax totals")
        subtotal = sole(Ubl.tax_subtotal, totals_block, "VAT breakdowns")
        return Decimal(content(Ubl.rate, subtotal, "VAT rate"))

    @staticmethod
    def dense(head, tail, rate):
        """Returns the smallest invoice line EN 16931 admits, as a UBL template."""
        currency = content(Ubl.currency, head + tail, "document currency code")
        return ("<cac:InvoiceLine>"
                "<cbc:ID>%s</cbc:ID>"
                '<cbc:InvoicedQuantity unitCode="' + DENSE_UNIT + '">'
                + str(DENSE_QUANTITY) + "</cbc:InvoicedQuantity>"
                '<cbc:LineExtensionAmount currencyID="' + currency + '">%s'
                "</cbc:LineExtensionAmount>"
                "<cac:Item><cbc:Name>" + DENSE_ITEM + "</cbc:Name>"
                "<cac:ClassifiedTaxCategory><cbc:ID>" + DENSE_CATEGORY + "</cbc:ID>"
                "<cbc:Percent>" + str(rate) + "</cbc:Percent>"
                "<cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>"
                "</cac:ClassifiedTaxCategory></cac:Item>"
                '<cac:Price><cbc:PriceAmount currencyID="' + currency + '">'
                + str(DENSE_PRICE) + "</cbc:PriceAmount></cac:Price>"
                "</cac:InvoiceLine>")

    @staticmethod
    def line_figures(template):
        """Returns the quantity and the item net price of the template line."""
        quantity = Decimal(content(Ubl.quantity, template, "invoiced quantity"))
        price_block = sole(Ubl.price_block, template, "line prices")
        return quantity, Decimal(content(Ubl.price, price_block, "item net price"))

    @staticmethod
    def render_line(template, number, figures, padding):
        """Returns the template line with a new identifier and the recomputed line net."""
        line = substitute(Ubl.line_id, template, "L%0*d" % (ID_WIDTH, number),
                          "line identifier")
        line = substitute(Ubl.line_net, line, figures["BT-131"], "line net amount")
        if padding:
            line = substitute(Ubl.item_name, line,
                              content(Ubl.item_name, line, "item name") + " " + padding,
                              "item name")
        return line

    @staticmethod
    def render_document(head, tail, figures):
        """Returns the head and the tail with every document level total recomputed."""
        edits = Edits(head, tail)
        edits.inside(Ubl.tax_total, lambda text: Ubl.render_tax(text, figures))
        edits.inside(Ubl.monetary_total, lambda text: Ubl.render_totals(text, figures))
        return edits.head, edits.tail

    @staticmethod
    def render_tax(text, figures):
        """Returns the document level tax total with the VAT breakdown recomputed."""
        text = substitute(Ubl.tax, text, figures["BT-110"], "document level tax amount")
        subtotal = Ubl.tax_subtotal.search(text)
        inner = substitute(Ubl.taxable, subtotal.group(0), figures["BT-116"],
                           "taxable amount")
        inner = substitute(Ubl.tax, inner, figures["BT-117"], "category tax amount")
        return text[:subtotal.start()] + inner + text[subtotal.end():]

    @staticmethod
    def render_totals(text, figures):
        """Returns the monetary total with every sum recomputed."""
        text = substitute(Ubl.line_net, text, figures["BT-106"], "sum of line net amounts")
        text = substitute(Ubl.tax_exclusive, text, figures["BT-109"], "tax exclusive amount")
        text = substitute(Ubl.tax_inclusive, text, figures["BT-112"], "tax inclusive amount")
        return substitute(Ubl.payable, text, figures["BT-115"], "amount due for payment")


class Cii:
    """The element names of a UN/CEFACT CII D16B invoice this generator touches."""

    syntax = "cii"
    root = "CrossIndustryInvoice"
    line = block("ram", "IncludedSupplyChainTradeLineItem")
    line_open = "<ram:IncludedSupplyChainTradeLineItem>"
    line_close = "</ram:IncludedSupplyChainTradeLineItem>"

    line_id = element("ram", "LineID")
    item_name = element("ram", "Name")
    quantity = element("ram", "BilledQuantity")
    price_block = block("ram", "NetPriceProductTradePrice")
    price = element("ram", "ChargeAmount")
    line_summation = block("ram", "SpecifiedTradeSettlementLineMonetarySummation")
    line_net = element("ram", "LineTotalAmount")
    line_refusals = [(element("ram", "BasisQuantity"), "a line with a base quantity"),
                     (block("ram", "SpecifiedTradeAllowanceCharge"),
                      "a line allowance or charge")]

    settlement = block("ram", "ApplicableHeaderTradeSettlement")
    trade_tax = block("ram", "ApplicableTradeTax")
    taxable = element("ram", "BasisAmount")
    tax = element("ram", "CalculatedAmount")
    rate = element("ram", "RateApplicablePercent")
    summation = block("ram", "SpecifiedTradeSettlementHeaderMonetarySummation")
    tax_basis = element("ram", "TaxBasisTotalAmount")
    tax_total = element("ram", "TaxTotalAmount")
    grand_total = element("ram", "GrandTotalAmount")
    payable = element("ram", "DuePayableAmount")
    document_refusals = [
        (element("ram", "AllowanceTotalAmount"), "a document level allowance total"),
        (element("ram", "ChargeTotalAmount"), "a document level charge total"),
        (element("ram", "TotalPrepaidAmount"), "a prepaid amount"),
        (element("ram", "RoundingAmount"), "a rounding amount")]

    @staticmethod
    def dense(head, tail, rate):
        """Returns the smallest invoice line EN 16931 admits, as a CII template."""
        return ("<ram:IncludedSupplyChainTradeLineItem>"
                "<ram:AssociatedDocumentLineDocument><ram:LineID>%s</ram:LineID>"
                "</ram:AssociatedDocumentLineDocument>"
                "<ram:SpecifiedTradeProduct><ram:Name>" + DENSE_ITEM + "</ram:Name>"
                "</ram:SpecifiedTradeProduct>"
                "<ram:SpecifiedLineTradeAgreement><ram:NetPriceProductTradePrice>"
                "<ram:ChargeAmount>" + str(DENSE_PRICE) + "</ram:ChargeAmount>"
                "</ram:NetPriceProductTradePrice></ram:SpecifiedLineTradeAgreement>"
                '<ram:SpecifiedLineTradeDelivery><ram:BilledQuantity unitCode="'
                + DENSE_UNIT + '">' + str(DENSE_QUANTITY) + "</ram:BilledQuantity>"
                "</ram:SpecifiedLineTradeDelivery>"
                "<ram:SpecifiedLineTradeSettlement><ram:ApplicableTradeTax>"
                "<ram:TypeCode>VAT</ram:TypeCode>"
                "<ram:CategoryCode>" + DENSE_CATEGORY + "</ram:CategoryCode>"
                "<ram:RateApplicablePercent>" + str(rate) + "</ram:RateApplicablePercent>"
                "</ram:ApplicableTradeTax>"
                "<ram:SpecifiedTradeSettlementLineMonetarySummation>"
                "<ram:LineTotalAmount>%s</ram:LineTotalAmount>"
                "</ram:SpecifiedTradeSettlementLineMonetarySummation>"
                "</ram:SpecifiedLineTradeSettlement>"
                "</ram:IncludedSupplyChainTradeLineItem>")

    @staticmethod
    def rates(head, tail):
        """Returns the VAT rate of the one breakdown, and refuses a document with more."""
        settlement = sole(Cii.settlement, head + tail, "header trade settlements")
        refuse(block("ram", "SpecifiedTradeAllowanceCharge"), settlement,
               "a document level allowance or charge")
        breakdown = sole(Cii.trade_tax, settlement, "VAT breakdowns")
        return Decimal(content(Cii.rate, breakdown, "VAT rate"))

    @staticmethod
    def line_figures(template):
        """Returns the quantity and the item net price of the template line."""
        quantity = Decimal(content(Cii.quantity, template, "billed quantity"))
        price_block = sole(Cii.price_block, template, "line prices")
        return quantity, Decimal(content(Cii.price, price_block, "item net price"))

    @staticmethod
    def render_line(template, number, figures, padding):
        """Returns the template line with a new identifier and the recomputed line net."""
        line = substitute(Cii.line_id, template, "L%0*d" % (ID_WIDTH, number),
                          "line identifier")
        summation = Cii.line_summation.search(line)
        if summation is None:
            raise Unsuitable("the source holds no line monetary summation")
        inner = substitute(Cii.line_net, summation.group(0), figures["BT-131"],
                           "line net amount")
        line = line[:summation.start()] + inner + line[summation.end():]
        if padding:
            line = substitute(Cii.item_name, line,
                              content(Cii.item_name, line, "item name") + " " + padding,
                              "item name")
        return line

    @staticmethod
    def render_document(head, tail, figures):
        """Returns the head and the tail with every document level total recomputed."""
        edits = Edits(head, tail)
        edits.inside(Cii.settlement, lambda text: Cii.render_settlement(text, figures))
        return edits.head, edits.tail

    @staticmethod
    def render_settlement(text, figures):
        """Returns the header settlement with the breakdown and the sums recomputed."""
        breakdown = Cii.trade_tax.search(text)
        inner = substitute(Cii.tax, breakdown.group(0), figures["BT-117"],
                           "category tax amount")
        inner = substitute(Cii.taxable, inner, figures["BT-116"], "taxable amount")
        text = text[:breakdown.start()] + inner + text[breakdown.end():]
        summation = Cii.summation.search(text)
        if summation is None:
            raise Unsuitable("the source holds no header monetary summation")
        sums = substitute(Cii.line_net, summation.group(0), figures["BT-106"],
                          "sum of line net amounts")
        sums = substitute(Cii.tax_basis, sums, figures["BT-109"], "tax basis total")
        sums = substitute(Cii.tax_total, sums, figures["BT-110"], "tax total")
        sums = substitute(Cii.grand_total, sums, figures["BT-112"], "grand total")
        sums = substitute(Cii.payable, sums, figures["BT-115"], "amount due for payment")
        return text[:summation.start()] + sums + text[summation.end():]


class Edits:
    """The two outer parts of the document, edited wherever the totals happen to sit.

    A UBL invoice carries its totals before its lines and a CII invoice after them, so
    a substitution is applied to whichever of the two parts holds the element rather than
    to a part fixed per syntax.
    """

    def __init__(self, head, tail):
        self.head = head
        self.tail = tail

    def inside(self, pattern, edit):
        """Applies an edit to the one block a pattern matches, in the head or in the tail."""
        for part in ("head", "tail"):
            text = getattr(self, part)
            match = pattern.search(text)
            if match is not None:
                setattr(self, part, text[:match.start()] + edit(match.group(0))
                        + text[match.end():])
                return
        raise Unsuitable("the source holds no " + pattern.pattern.split(":")[1].split("(")[0])


def syntax_of(source, named):
    """Returns the syntax of the source document, from --syntax or from the root element."""
    for candidate in (Ubl, Cii):
        if re.search(r"<(?:\w+:)?" + candidate.root + r"[\s>]", source) is not None:
            if named is not None and named != candidate.syntax:
                raise Unsuitable("--syntax " + named + " was asked for and the source is a "
                                 + candidate.syntax.upper() + " document")
            return candidate
    raise Unsuitable("the source is neither a UBL invoice nor a cross industry invoice")


def split(source, syntax):
    """Returns the text before the lines, the first line and the text after the lines."""
    first = source.find(syntax.line_open)
    last = source.rfind(syntax.line_close)
    if first < 0 or last < 0:
        raise Unsuitable("the source holds no invoice line")
    match = syntax.line.search(source, first)
    if match is None:
        raise Unsuitable("the source holds no complete invoice line")
    return source[:first], match.group(0), source[last + len(syntax.line_close):]


def parts(head, template, tail, syntax, lines, dense=False, padding=""):
    """Returns the head, the rendered line and the tail of a document of so many lines."""
    if dense:
        quantity, price = DENSE_QUANTITY, DENSE_PRICE
    else:
        quantity, price = syntax.line_figures(template)
    figures = totals(quantity, price, syntax.rates(head, tail), Decimal(lines))
    head, tail = syntax.render_document(head, tail, figures)
    if dense:
        return head, lambda number: template % ("L%0*d" % (ID_WIDTH, number),
                                                figures["BT-131"]), tail
    return head, lambda number: syntax.render_line(template, number, figures, padding), tail


def count_for(head, template, tail, syntax, target, dense=False, padding=""):
    """Returns the smallest number of lines whose document reaches the target size."""
    lines = 1
    for _ in range(GROWTH_ROUNDS):
        grown, render, ending = parts(head, template, tail, syntax, lines, dense, padding)
        per_line = len(render(1).encode("utf-8"))
        outer = len(grown.encode("utf-8")) + len(ending.encode("utf-8"))
        if outer + lines * per_line >= target:
            return lines
        lines = max(lines + 1, -(-(target - outer) // per_line))
    return lines


def write(destination, head, render, tail, lines):
    """Writes the document, one line at a time, so that memory does not grow with it."""
    with open(destination, "w", encoding="utf-8", newline="") as out:
        out.write(head)
        for number in range(1, lines + 1):
            out.write(render(number))
        out.write(tail)


def main(argv):
    """Runs the generator and returns the exit code of the process."""
    parser = argparse.ArgumentParser(
        description="Grow a corpus invoice into a synthetic instance with many lines.")
    parser.add_argument("--source", required=True, type=Path,
                        help="the corpus invoice to grow, UBL or CII")
    parser.add_argument("--syntax", choices=["ubl", "cii"],
                        help="the syntax of the source; recognized from it when left out")
    parser.add_argument("--lines", type=int,
                        help="how many invoice lines the instance carries")
    parser.add_argument("--target-bytes", type=str,
                        help="grow the instance until it is at least this large; a plain"
                             " number of bytes, or one with a k, M or G suffix for"
                             " multiples of 1024")
    parser.add_argument("--dense", action="store_true",
                        help="replicate the smallest invoice line EN 16931 admits instead"
                             " of the line the source carries, so that the same number of"
                             " bytes buys several times as many lines")
    parser.add_argument("--text-bytes", type=str,
                        help="pad the item name of every line with this many bytes of"
                             " filler words; a plain number of bytes, or one with a k, M"
                             " or G suffix for multiples of 1024")
    parser.add_argument("--out", required=True, type=Path,
                        help="the file to write")
    arguments = parser.parse_args(argv)

    if (arguments.lines is None) == (arguments.target_bytes is None):
        parser.error("name either --lines or --target-bytes")
    if arguments.dense and arguments.text_bytes:
        parser.error("--dense writes the smallest line the standard admits, which has no"
                     " room for the filler of --text-bytes")
    if arguments.lines is not None and arguments.lines < 1:
        parser.error("--lines is at least 1")

    source = arguments.source.read_text(encoding="utf-8")
    syntax = syntax_of(source, arguments.syntax)
    head, template, tail = split(source, syntax)
    for pattern, what in syntax.document_refusals:
        refuse(pattern, head + tail, what)
    if arguments.dense:
        template = syntax.dense(head, tail, syntax.rates(head, tail))
    else:
        for pattern, what in syntax.line_refusals:
            refuse(pattern, template, what)

    padding = filler(suffixed(arguments.text_bytes, parser, "--text-bytes")
                     if arguments.text_bytes else 0)
    lines = arguments.lines
    if lines is None:
        lines = count_for(head, template, tail, syntax,
                          suffixed(arguments.target_bytes, parser, "--target-bytes"),
                          arguments.dense, padding)
    head, render, tail = parts(head, template, tail, syntax, lines, arguments.dense, padding)
    arguments.out.parent.mkdir(parents=True, exist_ok=True)
    write(arguments.out, head, render, tail, lines)
    print("%s: %d lines, %d bytes" % (arguments.out, lines,
                                      arguments.out.stat().st_size))
    return 0


def suffixed(text, parser, option):
    """Returns a byte count written as a plain number or with a k, M or G suffix."""
    multipliers = {"k": 1024, "M": 1024 ** 2, "G": 1024 ** 3}
    multiplier = multipliers.get(text[-1:])
    try:
        value = int(text[:-1] if multiplier else text) * (multiplier or 1)
    except ValueError:
        parser.error(option + " takes a number, optionally with k, M or G, not "
                     + repr(text))
    if value < 1:
        parser.error(option + " is at least 1")
    return value


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except Unsuitable as unsuitable:
        print("generate.py: " + str(unsuitable), file=sys.stderr)
        sys.exit(2)
