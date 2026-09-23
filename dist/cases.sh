#!/bin/sh
#
# The runs every packaged artefact is checked with: one line per run, the case
# name first and the arguments of one `esj` call after it. dist/smoke.sh runs
# each of them against two artefacts and compares what they print, and
# dist/package.sh replays them to record what a native image must reach and what
# the ahead-of-time cache is trained on.
#
# A run that writes a file names it @OUT; the runner substitutes a path of its
# own and compares the bytes that arrive there. A run that reads what an earlier
# case wrote names it @FROM:<case>, so that the two steps of a hybrid file — the
# rendering and the invoice written into it — are exercised as they are used.
#
# Paths are relative to the root of the repository. The corpus subset of
# esj_cases is the one the continuous integration runs; esj_cases --full adds
# every remaining instance of the business case corpus in both syntaxes.
#
# Every command the tool offers has at least one case: smoke.sh takes the list
# of commands from --help and fails when one of them appears in no case here.
#
# Copyright 2026 BSNSoft Solutions GmbH. Author: Christian Bürckert. Licensed under the Apache License, Version 2.0.

set -eu

CORPUS=conformance/kosit/business-cases/standard
TECHNICAL=conformance/kosit/technical-cases
EXAMPLES=examples
PDF=conformance/pdf/factur-x.pdf

esj_cases() {
  cat <<CASES
version --version
help --help
packs --list-packs
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
render-no-payment-code render --no-payment-code --out @OUT $EXAMPLES/standard-invoice.esj.json
render-letter-pages render --out @OUT $EXAMPLES/multiple-lines.esj.json
validate-hybrid validate @FROM:render-embed
inspect-hybrid inspect @FROM:render-embed
embed-rendering embed @FROM:render-pdf $EXAMPLES/standard-invoice.esj.json --out @OUT
embed-name embed @FROM:render-pdf $EXAMPLES/standard-invoice.esj.json --name zugferd-invoice.xml --out @OUT
embed-profile embed @FROM:render-pdf $EXAMPLES/standard-invoice.esj.json --profile BASIC --out @OUT
embed-carrying embed @FROM:render-embed $EXAMPLES/standard-invoice.esj.json --out @OUT
embed-no-esj embed @FROM:render-pdf $EXAMPLES/standard-invoice.esj.json --no-esj --out @OUT
render-embed-no-esj render --embed cii --no-esj --out @OUT $EXAMPLES/standard-invoice.esj.json
extract-esj-list extract --list @FROM:render-embed
extract-esj extract --attachment invoice.esj.json @FROM:render-embed
extract-hybrid-invoice extract --attachment factur-x.xml @FROM:render-embed
render-of-hybrid render --out @OUT @FROM:render-embed
validate-hybrid-json validate --output json @FROM:render-embed
upgrade-2026 upgrade $EXAMPLES/standard-invoice.esj.json --to 2026 --out @OUT
upgrade-2017 upgrade @FROM:upgrade-2026 --to 2017 --out @OUT
upgrade-json upgrade $EXAMPLES/minimal.esj.json --to 2026 --output json --out @OUT
upgrade-refused upgrade $CORPUS/01.01a-INVOICE_ubl.xml --to 2026
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
