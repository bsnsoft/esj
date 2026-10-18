#!/bin/sh
#
# The runs every packaged artefact is checked with: one line per run, the case
# name first and the arguments of one `esj` call after it. dist/smoke.sh runs
# each of them against two artefacts and compares what they print, and
# dist/package.sh replays them to record what a native image must reach and what
# the ahead-of-time cache is trained on.
#
# A run that writes a file names it @OUT; the runner substitutes a path of its
# own and compares the bytes that arrive there. esj view writes a directory
# there, and every file in it is compared. A run that reads what an earlier
# case wrote names it @FROM:<case>, so that the two steps of a hybrid file — the
# rendering and the invoice written into it — are exercised as they are used.
#
# Paths are relative to the root of the repository. The corpus subset of
# esj_cases is the one the continuous integration runs; esj_cases --full adds
# every remaining instance of the business case corpus in both syntaxes.
#
# Every command the tool offers has at least one case: smoke.sh takes the list
# of commands from --help and fails when one of them appears in no case here.
# The two servers are run where they end by themselves: esj mcp on a standard
# input that is closed at once, and esj serve with its help and with an option
# it refuses; dist/serve-smoke.sh is what talks to them.
#
# A command is not all a case has to cover. The images a render template brings
# are decoded by code of the Java runtime that a native image carries only where
# a traced run reached it: a JPEG goes through the decoder in libjavajpeg, an
# image with a colour profile through the colour management of the runtime, and
# a PNG that is not plain 8-bit RGB through the PNG reader of ImageIO, whose
# rasters and colour models differ by bit depth. The templates under
# dist/artwork bring one of each, as letterhead and as logo; without them a
# native executable ended on the first JPEG logo with an internal error.
#
# Nor is a sound document all an input can be. The XML parser of the Java
# runtime formats the message of every error it finds from a resource bundle,
# even where the tool then prints a sentence of its own, and a native image
# carries a bundle only where a traced run loaded it: one for a document that is
# not well-formed, one for a document its schema refuses. The files under
# dist/inputs bring every kind of input a reader refuses or reads the hard way —
# XML cut short, with a wrong tag or character, with a document type
# declaration, in another encoding than the one it declares or than UTF-8, or
# against its schema; ESJ that is no JSON text; a PDF cut short, or damaged in
# its attachment or its XMP packet; an empty file — through the commands that
# read them. Without them a native executable ended on the first document that
# was not well-formed with an internal error. An encoding is the one thing a
# traced run does not record: the charsets an artefact carries are set in
# dist/native-image.sh and dist/package.sh, and the cases in Windows-1252,
# ISO-8859-15 and Shift_JIS are what holds both to the jar.
#
# Copyright 2026 BSNSoft Solutions GmbH. Author: Christian Bürckert. Licensed under the Apache License, Version 2.0.

set -eu

CORPUS=conformance/kosit/business-cases/standard
TECHNICAL=conformance/kosit/technical-cases
EXAMPLES=examples
PDF=conformance/pdf/factur-x.pdf
ARTWORK=dist/artwork
VIEW=dist/view
INPUTS=dist/inputs

esj_cases() {
  cat <<CASES
version --version
help --help
packs --list-packs
packs-list packs list
packs-list-directory packs list --packs $EXAMPLES
packs-fetch-unknown packs fetch no-such-recipe --into @OUT
convert-ubl convert $CORPUS/01.01a-INVOICE_ubl.xml
convert-cii convert $CORPUS/01.01a-INVOICE_uncefact.xml
convert-json convert --output json $CORPUS/02.01a-INVOICE_ubl.xml
convert-to-cii convert --to cii $EXAMPLES/standard-invoice.esj.json
convert-to-ubl convert --to ubl $EXAMPLES/standard-invoice.esj.json
convert-to-ubl-creditnote convert --to ubl --ubl-document creditnote $EXAMPLES/credit-note.esj.json
convert-to-ubl-convention convert --to ubl $EXAMPLES/charges.esj.json
convert-level-shift convert --to cii --extension xrechnung conformance/kosit/business-cases/extension/04.01a-INVOICE_ubl.xml
convert-fail-on-loss convert --to cii --extension xrechnung --fail-on-loss conformance/kosit/business-cases/extension/04.01a-INVOICE_ubl.xml
convert-fail-on-loss-b2c convert --to cii --extension b2c --fail-on-loss $EXAMPLES/b2c-gross.esj.json
convert-pdf convert $PDF
convert-xslt convert --importer xslt $CORPUS/01.01a-INVOICE_ubl.xml
convert-stdin-esj convert --from esj -
validate-ubl validate $CORPUS/01.01a-INVOICE_ubl.xml
validate-cii validate $CORPUS/01.01a-INVOICE_uncefact.xml
validate-json validate --output json $CORPUS/01.01a-INVOICE_ubl.xml
validate-esj validate $EXAMPLES/standard-invoice.esj.json
validate-esj-smallest validate $EXAMPLES/smallest-valid.esj.json
validate-esj-structure validate $EXAMPLES/minimal.esj.json
validate-via-ubl validate --via ubl $EXAMPLES/standard-invoice.esj.json
validate-esj-invalid validate $EXAMPLES/invalid/arithmetic-mismatch.esj.json
validate-pdf validate $PDF
validate-cius validate --extension xrechnung $TECHNICAL/cius/01.01_comprehensive_test_ubl.xml
validate-b2c validate --extension b2c $EXAMPLES/b2c-gross.esj.json
validate-extensions validate --extension xrechnung,b2c $EXAMPLES/b2c-gross.esj.json
validate-no-syntax validate --no-syntax $CORPUS/01.01a-INVOICE_ubl.xml
validate-packs-directory validate --packs $EXAMPLES $CORPUS/01.01a-INVOICE_ubl.xml
validate-edition-2026 validate --output json $EXAMPLES/edition-2026.esj.json
validate-report-html validate --report @OUT --report-format html $CORPUS/01.01a-INVOICE_ubl.xml
validate-report-pdf validate --report @OUT --report-format pdf $PDF
validate-report-letter validate --report @OUT --report-format pdf --report-page LETTER $CORPUS/01.01a-INVOICE_ubl.xml
validate-report-stdout validate --report - --report-format html --report-lang en $EXAMPLES/standard-invoice.esj.json
validate-report-unwritable validate --report no-such-directory/report.html $EXAMPLES/invalid/arithmetic-mismatch.esj.json
validate-missing validate $CORPUS/there-is-no-such-file.xml
render-pdf render --out @OUT $EXAMPLES/standard-invoice.esj.json
render-html render --html --out @OUT $EXAMPLES/standard-invoice.esj.json
render-html-de render --html --lang de --out @OUT $CORPUS/01.01a-INVOICE_ubl.xml
inspect-ubl inspect $CORPUS/01.01a-INVOICE_ubl.xml
inspect-cii inspect $CORPUS/01.01a-INVOICE_uncefact.xml
inspect-esj inspect $EXAMPLES/standard-invoice.esj.json
inspect-pdf inspect $PDF
extract-list extract --list $PDF
extract-xml extract $PDF
extract-out extract --out @OUT $PDF
get-bt1 get $EXAMPLES/standard-invoice.esj.json /BT-1
get-line get $EXAMPLES/multiple-lines.esj.json /BG-25/1/BT-131
get-json get --json $EXAMPLES/standard-invoice.esj.json /BG-4/BT-27
get-absent get $EXAMPLES/minimal.esj.json /BT-22
list-esj list $EXAMPLES/extended.esj.json
list-b2c list $EXAMPLES/b2c-gross.esj.json
list-b2c-extension list --extension b2c $EXAMPLES/b2c-gross.esj.json
list-json list --format json $EXAMPLES/standard-invoice.esj.json
list-ubl list $CORPUS/01.01a-INVOICE_ubl.xml
diff-equal diff $EXAMPLES/standard-invoice.esj.json $EXAMPLES/standard-invoice.esj.json
diff-different diff $EXAMPLES/minimal.esj.json $EXAMPLES/standard-invoice.esj.json
diff-syntaxes diff $CORPUS/01.01a-INVOICE_ubl.xml $CORPUS/01.01a-INVOICE_uncefact.xml
canonicalize-esj canonicalize $EXAMPLES/allowances.esj.json
canonicalize-digest canonicalize --digest $EXAMPLES/standard-invoice.esj.json
canonicalize-ubl canonicalize $CORPUS/01.01a-INVOICE_ubl.xml
validate-large validate --limits large $CORPUS/01.01a-INVOICE_ubl.xml
validate-bounds validate --max-input-bytes 8000000 --max-values 200000 $CORPUS/01.01a-INVOICE_ubl.xml
validate-runtime validate --max-runtime 120s $CORPUS/01.01a-INVOICE_ubl.xml
validate-level validate --level l2 $EXAMPLES/extended.esj.json
convert-large convert --limits large --to esj $CORPUS/01.01a-INVOICE_uncefact.xml
convert-from convert --from ubl $CORPUS/01.02a-INVOICE_ubl.xml
convert-strict convert --strict $CORPUS/01.01a-INVOICE_ubl.xml
inspect-verbose inspect --verbose $CORPUS/01.01a-INVOICE_ubl.xml
extract-attachment extract --attachment factur-x.xml $PDF
extract-index extract --attachment-index 1 $PDF
diff-summary diff --summary $EXAMPLES/minimal.esj.json $EXAMPLES/standard-invoice.esj.json
render-page render --page LETTER --out @OUT $EXAMPLES/standard-invoice.esj.json
render-template render --template $EXAMPLES/templates/letterhead.json --out @OUT $EXAMPLES/standard-invoice.esj.json
render-embed render --template $EXAMPLES/templates/letterhead.json --embed cii --out @OUT $EXAMPLES/standard-invoice.esj.json
render-template-b2c render --extension b2c --template $EXAMPLES/templates/gross.json --out @OUT $EXAMPLES/b2c-gross.esj.json
render-layout-generic render --layout generic --out @OUT $EXAMPLES/standard-invoice.esj.json
render-template-letter render --template $EXAMPLES/templates/letter.json --out @OUT $EXAMPLES/standard-invoice.esj.json
render-artwork-jpeg render --template $ARTWORK/jpeg.json --out @OUT $EXAMPLES/standard-invoice.esj.json
render-artwork-icc render --template $ARTWORK/icc.json --out @OUT $EXAMPLES/standard-invoice.esj.json
render-artwork-png render --template $ARTWORK/png.json --out @OUT $EXAMPLES/standard-invoice.esj.json
render-artwork-grey render --template $ARTWORK/grey.json --out @OUT $EXAMPLES/standard-invoice.esj.json
render-no-payment-code render --no-payment-code --out @OUT $EXAMPLES/standard-invoice.esj.json
render-letter-pages render --out @OUT $EXAMPLES/multiple-lines.esj.json
validate-hybrid validate @FROM:render-embed
inspect-hybrid inspect @FROM:render-embed
embed-rendering embed @FROM:render-pdf $EXAMPLES/standard-invoice.esj.json --out @OUT
embed-name embed @FROM:render-pdf $EXAMPLES/standard-invoice.esj.json --name zugferd-invoice.xml --out @OUT
embed-profile embed @FROM:render-pdf $EXAMPLES/standard-invoice.esj.json --profile BASIC --out @OUT
embed-carrying embed @FROM:render-embed $EXAMPLES/standard-invoice.esj.json --out @OUT
extract-hybrid-list extract --list @FROM:render-embed
extract-hybrid-invoice extract --attachment factur-x.xml @FROM:render-embed
render-of-hybrid render --out @OUT @FROM:render-embed
validate-hybrid-json validate --output json @FROM:render-embed
upgrade-2026 upgrade $EXAMPLES/standard-invoice.esj.json --to 2026 --out @OUT
upgrade-2017 upgrade @FROM:upgrade-2026 --to 2017 --out @OUT
upgrade-json upgrade $EXAMPLES/minimal.esj.json --to 2026 --output json --out @OUT
upgrade-refused upgrade $CORPUS/01.01a-INVOICE_ubl.xml --to 2026
input-truncated-validate validate $INPUTS/truncated.xml
input-truncated-inspect inspect $INPUTS/truncated.xml
input-truncated-convert convert $INPUTS/truncated.xml
input-truncated-html render --html --out @OUT $INPUTS/truncated.xml
input-truncated-xslt convert --importer xslt $INPUTS/truncated.xml
input-truncated-from validate --from ubl $INPUTS/truncated.xml
input-mismatched-tag-validate validate $INPUTS/mismatched-tag.xml
input-mismatched-tag-inspect inspect $INPUTS/mismatched-tag.xml
input-mismatched-tag-convert convert $INPUTS/mismatched-tag.xml
input-mismatched-tag-html render --html --out @OUT $INPUTS/mismatched-tag.xml
input-undeclared-entity-validate validate $INPUTS/undeclared-entity.xml
input-control-character-validate validate $INPUTS/control-character.xml
input-after-root-validate validate $INPUTS/after-root.xml
input-after-root-inspect inspect $INPUTS/after-root.xml
input-after-root-convert convert $INPUTS/after-root.xml
input-after-root-html render --html --out @OUT $INPUTS/after-root.xml
input-after-root-xslt convert --importer xslt $INPUTS/after-root.xml
input-after-root-json validate --output json $INPUTS/after-root.xml
input-doctype-validate validate $INPUTS/doctype.xml
input-doctype-inspect inspect $INPUTS/doctype.xml
input-doctype-convert convert $INPUTS/doctype.xml
input-doctype-html render --html --out @OUT $INPUTS/doctype.xml
input-doctype-xslt convert --importer xslt $INPUTS/doctype.xml
input-unknown-encoding-validate validate $INPUTS/unknown-encoding.xml
input-unknown-encoding-convert convert $INPUTS/unknown-encoding.xml
input-latin1-validate validate $INPUTS/latin1-declared-utf8.xml
input-latin1-inspect inspect $INPUTS/latin1-declared-utf8.xml
input-latin1-convert convert $INPUTS/latin1-declared-utf8.xml
input-latin1-html render --html --out @OUT $INPUTS/latin1-declared-utf8.xml
input-cp1252-validate validate $INPUTS/cp1252-declared-utf8.xml
input-cp1252-inspect inspect $INPUTS/cp1252-declared-utf8.xml
input-cp1252-convert convert $INPUTS/cp1252-declared-utf8.xml
input-cp1252-html render --html --out @OUT $INPUTS/cp1252-declared-utf8.xml
input-cp1252-strict convert --strict $INPUTS/cp1252-declared-utf8.xml
input-windows-1252-validate validate $INPUTS/windows-1252.xml
input-windows-1252-inspect inspect $INPUTS/windows-1252.xml
input-windows-1252-convert convert $INPUTS/windows-1252.xml
input-windows-1252-html render --html --out @OUT $INPUTS/windows-1252.xml
input-windows-1252-xslt convert --importer xslt $INPUTS/windows-1252.xml
input-iso-8859-15-validate validate $INPUTS/iso-8859-15.xml
input-iso-8859-15-convert convert $INPUTS/iso-8859-15.xml
input-shift-jis-validate validate $INPUTS/shift-jis.xml
input-shift-jis-convert convert $INPUTS/shift-jis.xml
input-utf-16-validate validate $INPUTS/utf-16.xml
input-utf-16-convert convert $INPUTS/utf-16.xml
input-schema-invalid-validate validate $INPUTS/schema-invalid.xml
input-schema-invalid-inspect inspect $INPUTS/schema-invalid.xml
input-schema-invalid-report validate --report @OUT --report-format html $INPUTS/schema-invalid.xml
input-datatype-invalid-validate validate $INPUTS/datatype-invalid.xml
input-esj-truncated-validate validate $INPUTS/truncated.esj.json
input-esj-truncated-inspect inspect $INPUTS/truncated.esj.json
input-esj-truncated-convert convert $INPUTS/truncated.esj.json
input-esj-truncated-html render --html --out @OUT $INPUTS/truncated.esj.json
input-esj-syntax-error-validate validate $INPUTS/syntax-error.esj.json
input-esj-not-utf8-validate validate $INPUTS/not-utf8.esj.json
input-pdf-truncated-validate validate $INPUTS/truncated.pdf
input-pdf-truncated-inspect inspect $INPUTS/truncated.pdf
input-pdf-truncated-convert convert $INPUTS/truncated.pdf
input-pdf-truncated-html render --html --out @OUT $INPUTS/truncated.pdf
input-pdf-garbage-validate validate $INPUTS/garbage.pdf
input-pdf-garbage-extract extract --list $INPUTS/garbage.pdf
input-pdf-attachment-validate validate $INPUTS/attachment-truncated.pdf
input-pdf-attachment-inspect inspect $INPUTS/attachment-truncated.pdf
input-pdf-attachment-convert convert $INPUTS/attachment-truncated.pdf
input-pdf-attachment-html render --html --out @OUT $INPUTS/attachment-truncated.pdf
input-pdf-attachment-extract extract $INPUTS/attachment-truncated.pdf
input-pdf-attachment-doctype-validate validate $INPUTS/attachment-doctype.pdf
input-pdf-attachment-doctype-inspect inspect $INPUTS/attachment-doctype.pdf
input-pdf-xmp-validate validate $INPUTS/xmp-malformed.pdf
input-pdf-xmp-inspect inspect $INPUTS/xmp-malformed.pdf
input-pdf-xmp-extract extract --list $INPUTS/xmp-malformed.pdf
input-pdf-no-invoice-validate validate @FROM:render-pdf
input-empty-validate validate $INPUTS/empty.xml
input-empty-inspect inspect $INPUTS/empty.xml
input-empty-convert convert $INPUTS/empty.xml
input-empty-html render --html --out @OUT $INPUTS/empty.xml
serve-help serve --help
serve-refused serve --job-heap 1x
mcp-closed mcp --max-jobs 1 --max-queue 1
mcp-help mcp --help
view-esj view --out @OUT --lang de $EXAMPLES/standard-invoice.esj.json
view-ubl view --out @OUT --lang en $CORPUS/01.01a-INVOICE_ubl.xml
view-cii-report view --out @OUT --lang en --report $CORPUS/01.01a-INVOICE_uncefact.xml
view-pdf-report view --out @OUT --lang de --report $PDF
view-template view --out @OUT --lang de --template $EXAMPLES/templates/letter.json $EXAMPLES/standard-invoice.esj.json
view-edition-2026 view --out @OUT --lang en $EXAMPLES/edition-2026.esj.json
view-no-invoice view --out @OUT --lang en --report $EXAMPLES/templates/letterhead.pdf
view-not-well-formed view --out @OUT --lang de $VIEW/not-well-formed.xml
view-limit view --out @OUT --lang de --max-input-bytes 1000 $CORPUS/01.01a-INVOICE_ubl.xml
view-json view --out @OUT --lang en --output json --report $EXAMPLES/standard-invoice.esj.json
CASES
  if [ "${1-}" = "--full" ]; then
    for instance in "$CORPUS"/*.xml; do
      case $instance in
        *01.01a-INVOICE_ubl.xml|*01.01a-INVOICE_uncefact.xml) continue ;;
      esac
      name=$(basename "$instance" .xml)
      echo "corpus-validate-$name validate $instance"
      echo "corpus-convert-$name convert $instance"
    done
  fi
}

# The one run the ahead-of-time cache is recorded from. A cache is written by a
# single process, so it is one run and not the list: this one opens a container,
# reads an invoice out of it and runs both validation engines over it, which is
# most of what any other run does. A directory given as the argument is put in
# front of the file it reads, for a runner whose working directory is not the
# repository.
esj_training_case() {
  echo "validate ${1:+$1/}$PDF"
}

# The input of the one case that reads the standard input.
esj_stdin() {
  echo "$EXAMPLES/standard-invoice.esj.json"
}
