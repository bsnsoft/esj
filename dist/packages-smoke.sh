#!/bin/sh
#
# Installs the Linux packages of dist/packages.sh in containers of the
# distributions they are for, each from a minimal image with nothing else
# installed, and runs the tool there as a user would: the version, the
# validation of a UBL invoice and of a hybrid PDF, a letter rendered to PDF with
# the invoice embedded, the validation of that PDF with a PDF report, the page of
# esj view over the hybrid PDF, and one request to esj serve; and finds the
# desktop entry and its icon where a desktop looks for them. Then the package is removed and nothing of it may stay.
# A dependency the packages do not declare shows here as a library that does not
# load, and a declared one the image lacks as a package the installer adds,
# which is counted and printed.
#
#   dist/packages-smoke.sh <packages-dir> [image...]
#
#   <packages-dir>  holds the .deb and the .rpm for the processor of the Docker
#                   host (amd64/x86_64 or arm64/aarch64)
#   [image...]      the images to install in; an image whose name starts with
#                   debian or ubuntu takes the .deb, any other the .rpm (dnf).
#                   By default debian:12-slim, ubuntu:22.04, ubuntu:24.04,
#                   fedora:latest and rockylinux:9: the oldest and newest
#                   releases the C library floor admits (dist/checks.sh)
#
# Every container runs with --rm under a time limit inside it, behind Docker's
# init process (the timeout of coreutils 9.10, Fedora 44, exits with 125 and
# does nothing when it is the first process of a container); a trap removes what
# is left when the script ends early. The repository is mounted read-only;
# the files a run writes stay inside its container. Needs Docker.
#
# Copyright 2026 BSNSoft Solutions GmbH. Author: Christian Bürckert. Licensed under the Apache License, Version 2.0.

set -eu

# The header of this file is its manual page.
usage() {
  awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"
}

# What runs inside one container: install, exercise, remove. The package is the
# one argument, the repository is mounted at /work.
inside() {
  package=$1
  ubl=/work/conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml
  hybrid=/work/conformance/pdf/factur-x.pdf
  w=$(mktemp -d)
  cd "$w"
  step() { echo "  $*"; }
  # A command under a time limit, its output kept, its exit code checked.
  check() {
    expected=$1; shift
    status=0
    timeout 120 "$@" > "$w/out" 2> "$w/err" || status=$?
    if [ "$status" -ne "$expected" ]; then
      echo "FAILED: $* exited with $status, not $expected" >&2
      tail -20 "$w/out" "$w/err" >&2
      exit 1
    fi
  }
  . /etc/os-release
  echo "$PRETTY_NAME, glibc $(ldd --version | head -1 | awk '{ print $NF }')"
  case $package in
    *.deb)
      export DEBIAN_FRONTEND=noninteractive
      before=$(dpkg-query -W -f '${Package}\n' | wc -l)
      timeout 600 apt-get update -qq > /dev/null
      timeout 600 apt-get install -y -qq "$package" > "$w/install.log" 2>&1 ||
        { cat "$w/install.log" >&2; exit 1; }
      after=$(dpkg-query -W -f '${Package}\n' | wc -l)
      step "installed $(dpkg-query -W -f '${Package} ${Version} ${Architecture}' esj);" \
        "$((after - before)) package(s) added"
      ;;
    *.rpm)
      before=$(rpm -qa | wc -l)
      timeout 600 dnf install -y -q "$package" > "$w/install.log" 2>&1 ||
        { cat "$w/install.log" >&2; exit 1; }
      after=$(rpm -qa | wc -l)
      step "installed $(rpm -q esj); $((after - before)) package(s) added"
      ;;
  esac
  [ -L /usr/bin/esj ] || { echo "FAILED: /usr/bin/esj is not a link" >&2; exit 1; }
  check 0 esj --version
  step "$(head -1 "$w/out") from /usr/bin/esj -> $(readlink /usr/bin/esj)"
  check 0 esj validate "$ubl"
  step "validate UBL: $(tail -1 "$w/out")"
  check 0 esj validate "$hybrid"
  step "validate hybrid PDF: $(grep '^Invoice:' "$w/out" | tr -s ' ')"
  check 0 esj render --template /work/examples/templates/letter.json --embed cii \
    --out "$w/letter.pdf" "$ubl"
  head -c 5 "$w/letter.pdf" | grep -q '%PDF-' || { echo "FAILED: no PDF rendered" >&2; exit 1; }
  step "render letter with letterhead, logo and CII: $(wc -c < "$w/letter.pdf") bytes"
  check 0 esj validate --report "$w/report.pdf" "$w/letter.pdf"
  head -c 5 "$w/report.pdf" | grep -q '%PDF-' || { echo "FAILED: no PDF report" >&2; exit 1; }
  step "validate the letter, PDF report: $(grep '^Container:' "$w/out" | tr -s ' '),"\
    "$(wc -c < "$w/report.pdf") bytes"
  # The page of esj view, written where it is told and opened nowhere, and the
  # two files that offer it under "Open with" on a desktop.
  check 0 esj view --out "$w/view" --lang en --report "$hybrid"
  [ "$(cat "$w/out")" = "$w/view/index.html" ] && [ -f "$w/view/index.html" ] &&
    [ -f "$w/view/received/factur-x.pdf" ] && [ -f "$w/view/factur-x.letter.pdf" ] &&
    [ -f "$w/view/factur-x.report.pdf" ] ||
    { echo "FAILED: esj view wrote no page" >&2; cat "$w/out" "$w/err" >&2; exit 1; }
  grep -q '^Exec=esj view --report %f$' /usr/share/applications/esj.desktop &&
    grep -q '^Icon=esj$' /usr/share/applications/esj.desktop &&
    [ -f /usr/share/icons/hicolor/scalable/apps/esj.svg ] ||
    { echo "FAILED: no desktop entry or icon" >&2; exit 1; }
  step "view: $(ls "$w/view" | wc -l | tr -d ' ') files beside the page; desktop entry and icon"
  # One request to esj serve, over the shell's own TCP client so that no HTTP
  # client is installed into the image under test.
  timeout 90 esj serve --port 18080 > "$w/serve.out" 2> "$w/serve.log" &
  server=$!
  deadline=$(( $(date +%s) + 30 ))
  until grep -q 'listening on' "$w/serve.log" 2>/dev/null; do
    if [ "$(date +%s)" -ge "$deadline" ] || ! kill -0 "$server" 2>/dev/null; then
      cat "$w/serve.log" >&2; echo "FAILED: esj serve did not start" >&2; exit 1
    fi
    sleep 0.2
  done
  length=$(wc -c < "$ubl")
  bash -c 'exec 3<>/dev/tcp/127.0.0.1/18080 &&
    printf "POST /api/validate HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: %s\r\nConnection: close\r\n\r\n" "$1" >&3 &&
    cat "$2" >&3 && timeout 60 cat <&3' sh "$length" "$ubl" > "$w/response" ||
    { echo "FAILED: no answer from esj serve" >&2; cat "$w/serve.log" >&2; exit 1; }
  kill "$server" 2>/dev/null || true
  wait "$server" 2>/dev/null || true
  head -1 "$w/response" | grep -q '^HTTP/1.1 200' &&
    grep -q '"verdict": *"VALID"' "$w/response" ||
    { echo "FAILED: esj serve answered:" >&2; head -20 "$w/response" >&2; exit 1; }
  step "serve: POST /api/validate $(head -1 "$w/response" | tr -d '\r' | cut -d' ' -f2), VALID;" \
    "children $(sed -n 's/^children: \([^ ]*\).*/\1/p' "$w/serve.log")"
  case $package in
    *.deb) timeout 300 apt-get purge -y -qq esj > /dev/null 2>&1 ;;
    *.rpm) timeout 300 dnf remove -y -q esj > /dev/null 2>&1 ;;
  esac
  for path in /usr/bin/esj /usr/lib/esj /usr/share/doc/esj \
      /usr/share/applications/esj.desktop /usr/share/icons/hicolor/scalable/apps/esj.svg; do
    if [ -e "$path" ] || [ -L "$path" ]; then
      echo "FAILED: $path is left after the package was removed" >&2; exit 1
    fi
  done
  step "removed; nothing left under /usr/bin/esj, /usr/lib/esj, /usr/share/doc/esj, nor the" \
    "desktop entry and its icon"
}

case ${1-} in
  -h|--help) usage; exit 0 ;;
  --inside) shift; inside "$@"; exit 0 ;;
esac
[ $# -ge 1 ] || { echo "usage: dist/packages-smoke.sh <packages-dir> [image...]" >&2; exit 2; }
command -v docker > /dev/null || { echo "packages-smoke: needs docker" >&2; exit 2; }

here=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
root=$(dirname -- "$here")
packages=$(CDPATH='' cd -- "$1" && pwd)
shift
[ $# -gt 0 ] || set -- debian:12-slim ubuntu:22.04 ubuntu:24.04 fedora:latest rockylinux:9

case $(docker version --format '{{.Server.Arch}}') in
  amd64|x86_64) deb_arch=amd64; rpm_arch=x86_64 ;;
  arm64|aarch64) deb_arch=arm64; rpm_arch=aarch64 ;;
  *) echo "packages-smoke: no packages for the Docker host's processor" >&2; exit 2 ;;
esac
deb=$(find "$packages" -maxdepth 1 -name "esj_*_$deb_arch.deb" | head -1)
rpm=$(find "$packages" -maxdepth 1 -name "esj-*.$rpm_arch.rpm" | head -1)

containers=
cleanup() {
  for container in $containers; do
    docker rm -f "$container" > /dev/null 2>&1 || true
  done
}
trap cleanup EXIT INT TERM

failed=0
number=0
for image in "$@"; do
  case $image in
    debian*|ubuntu*) package=$deb ;;
    *) package=$rpm ;;
  esac
  [ -n "$package" ] ||
    { echo "packages-smoke: no package for $image in $packages" >&2; exit 2; }
  number=$((number + 1))
  container=esj-packages-smoke-$$-$number
  containers="$containers $container"
  echo "== $image: ${package##*/}"
  start=$(date +%s)
  if docker run --rm --init --name "$container" \
      -v "$root:/work:ro" -v "$packages:/packages:ro" "$image" \
      timeout 1500 sh /work/dist/packages-smoke.sh --inside "/packages/${package##*/}"; then
    echo "  ok in $(( $(date +%s) - start )) s"
  else
    echo "  FAILED in $(( $(date +%s) - start )) s"
    failed=$((failed + 1))
  fi
done
if [ "$failed" -gt 0 ]; then
  echo "packages-smoke: $failed of $number images failed" >&2
  exit 1
fi
echo "packages-smoke: $number of $number images passed"
