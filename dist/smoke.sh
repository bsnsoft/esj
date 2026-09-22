#!/bin/sh
#
# Runs every command of the tool with a packaged artefact and with the
# self-contained jar and fails on the first difference in what they write, in
# the verdict they reach or in the code they leave with. A packaged artefact
# that answers differently from the jar is not the same tool.
#
#   dist/smoke.sh <artefact> [--reference <command>] [--small-heap <command>]
#                 [--full] [--keep]
#
#   <artefact>    the packaged tool: a native binary, the launcher of a runtime
#                 image, or a container run that mounts this repository writable
#                 at its working directory, because the cases that write a file
#                 write it under dist/out/smoke:
#                 `docker run --rm -i -v "$PWD:/work" -w /work esj:<tag>`
#   --reference   what to compare it with; the self-contained jar by default
#   --small-heap  the same artefact with a heap ceiling no run fits in, in the
#                 form that artefact takes one. One run with it has to end with
#                 exit code 3, the abort of the process boundary, and has to say
#                 so on one of its two streams: a heap that runs out ends the
#                 process rather than leaving it alive and useless
#                 (docs/deployment.md). Which stream carries that notice is the
#                 artefact's, not the tool's — a virtual machine writes it to
#                 the standard output and a native executable to the error
#                 stream — so it is reported and not asserted. Left out, the
#                 check is skipped
#   --full        run the whole business case corpus rather than the subset
#   --keep        keep the directory the outputs were written to
#
# A command word list may be given as one argument with spaces in it; no path in
# it may contain a space. Run it from anywhere: the cases are read relative to
# the repository this script lies in.
#
# Copyright 2026 BSNSoft Solutions GmbH. Author: Christian Bürckert. Licensed under the Apache License, Version 2.0.

set -eu

# The header of this file is its manual page.
usage() {
  awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"
}

here=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
root=$(dirname -- "$here")
. "$here/cases.sh"

artefact=
reference="java -jar $root/esj-cli/target/esj.jar"
small_heap=
full=
keep=
while [ $# -gt 0 ]; do
  case $1 in
    --reference) reference=$2; shift 2 ;;
    --small-heap) small_heap=$2; shift 2 ;;
    --full) full=--full; shift ;;
    --keep) keep=yes; shift ;;
    -h|--help) usage; exit 0 ;;
    -*) echo "smoke: unknown option $1" >&2; exit 2 ;;
    *) artefact=$1; shift ;;
  esac
done
if [ -z "$artefact" ]; then
  echo "usage: dist/smoke.sh <artefact> [--reference <command>] [--full] [--keep]" >&2
  exit 2
fi

work=$(mktemp -d)
# What a case writes goes to a path inside the repository, named relative to it,
# so that an artefact running in a container writes where the runner can read it.
written=dist/out/smoke
mkdir -p "$root/$written"
cleanup() { [ -n "$keep" ] || rm -rf "$work"; }
trap cleanup EXIT INT TERM
cd "$root"

# Every command the tool offers, taken from its own help rather than from a list
# in this script: a command added later and forgotten here fails the run.
commands=$($reference --help 2>/dev/null |
  sed -n '/^Commands:/,/^Exit codes:/p' | sed -n 's/^  \([a-z][a-z-]*\) .*/\1/p')
cases=$(esj_cases $full)
missing=
for command in $commands; do
  echo "$cases" | awk -v c="$command" '{ if ($2 == c) found = 1 } END { exit !found }' ||
    missing="$missing $command"
done
if [ -n "$missing" ]; then
  echo "smoke: no case runs:$missing (add one to dist/cases.sh)" >&2
  exit 1
fi

# One run of one artefact. The output file of a case that writes one is compared
# as bytes; standard output, standard error and the exit code are compared as
# text.
run() {
  side=$1; runner=$2; name=$3; shift 3
  out="$work/$name.$side.out"
  args=
  file=
  for argument in "$@"; do
    case $argument in
      # What this case writes, and what an earlier case wrote: each side reads
      # back only its own output, so a chain compares two chains.
      @OUT) file=$written/$name.$side; argument=$file ;;
      @FROM:*) argument=$written/${argument#@FROM:}.$side ;;
    esac
    args="$args $argument"
  done
  [ -z "$file" ] || rm -f "$root/$file"
  set -- $runner $args
  # Every run is given the input it asks for and nothing else: an artefact that
  # reads the standard input when it was not asked to would otherwise eat the
  # case list of the loop this runs in.
  if [ "$name" = "convert-stdin-esj" ]; then
    "$@" < "$(esj_stdin)" > "$out" 2> "$work/$name.$side.err"
  else
    "$@" < /dev/null > "$out" 2> "$work/$name.$side.err"
  fi
  echo $? > "$work/$name.$side.exit"
  if [ -n "$file" ] && [ -f "$root/$file" ]; then
    cp "$root/$file" "$work/$name.$side.file"
  fi
}

echo "$cases" | while IFS= read -r line; do
  [ -n "$line" ] || continue
  name=${line%% *}
  rest=${line#* }
  run a "$artefact" "$name" $rest || true
  run b "$reference" "$name" $rest || true
  difference=
  for part in out err exit; do
    # Two things the two are not expected to agree on. How long a run took:
    # --verbose reports it, in milliseconds, and no two processes spend the same
    # number of them. And which side wrote a file a later case reads back: each
    # side reads its own, and a command that echoes the path it was given would
    # otherwise report a difference that is only the name of the side. Nothing
    # else in either stream is rewritten.
    for side in a b; do
      sed -E -e 's/[0-9]+ (ms|s)$/<duration>/' \
             -e "s|$written/([a-z0-9-]+)\.[ab]|$written/\1|g" \
          "$work/$name.$side.$part" > "$work/$name.$side.$part.compared"
    done
    if ! cmp -s "$work/$name.a.$part.compared" "$work/$name.b.$part.compared"; then
      difference="$difference $part"
    fi
  done
  if [ -f "$work/$name.a.file" ] || [ -f "$work/$name.b.file" ]; then
    if ! cmp -s "$work/$name.a.file" "$work/$name.b.file"; then
      difference="$difference written-file"
    fi
  fi
  if [ -n "$difference" ]; then
    echo "FAIL $name:$difference"
    for part in $difference; do
      [ "$part" = written-file ] && continue
      diff -u "$work/$name.b.$part.compared" "$work/$name.a.$part.compared" |
        sed -n '1,12p' | sed 's/^/     /'
    done
    echo fail >> "$work/failures"
  else
    echo "ok   $name (exit $(cat "$work/$name.a.exit"))"
  fi
done

if [ -f "$work/failures" ]; then
  echo "smoke: $(wc -l < "$work/failures" | tr -d ' ') of $(echo "$cases" | wc -l | tr -d ' ') cases differ" >&2
  exit 1
fi
echo "smoke: $(echo "$cases" | wc -l | tr -d ' ') cases, no difference between the artefact and the jar"

# The defaults of the process boundary are part of the artefact. The run below
# is given a heap no run fits in and has to leave with 3, the code of a virtual
# machine that aborted, rather than with a verdict or with 7 — and it has to
# leave a notice behind, so that a caller reading the streams of a crashed run
# finds one. Which stream it lands on is the artefact's: it is printed, so that
# a packaging run says what docs/deployment.md states for each artefact.
if [ -n "$small_heap" ]; then
  boundary=0
  $small_heap validate conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml \
    < /dev/null > "$work/boundary.out" 2> "$work/boundary.err" || boundary=$?
  notice="neither stream"
  if grep -q OutOfMemoryError "$work/boundary.out"; then notice="the standard output"; fi
  if grep -q OutOfMemoryError "$work/boundary.err"; then notice="the error stream"; fi
  if [ "$boundary" = 3 ] && [ "$notice" != "neither stream" ]; then
    echo "ok   process boundary: a heap that runs out ends the run with 3," \
      "its notice on $notice"
  else
    echo "smoke: the process boundary left with $boundary and its notice on $notice;" \
      "expected 3 and a notice on one of the streams" >&2
    exit 1
  fi
fi
