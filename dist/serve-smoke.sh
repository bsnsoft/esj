#!/bin/sh
#
# Runs esj serve and esj mcp of a packaged artefact and of the self-contained
# jar side by side and fails on the first difference in what they answer: the
# status and the body of every request, the bytes of every artefact, and every
# line esj mcp writes. A packaged server that answers differently from the
# jar's is not the same server.
#
#   dist/serve-smoke.sh <artefact> [--reference <command>]
#   dist/serve-smoke.sh --docker <image> [--reference <command>]
#
#   <artefact>    the packaged tool: a native binary or the launcher of a
#                 runtime image
#   --docker      a container image instead, run with `docker run -p` and the
#                 address it sets for esj serve (ESJ_SERVE_BIND=0.0.0.0), which
#                 is checked too: the server has to be reachable through the
#                 published port and has to say that it runs without a token
#   --reference   what to compare it with; the self-contained jar by default
#
# Every server runs under a hard time limit and is stopped with SIGTERM at the
# end, after which its temporary directory has to be gone; a trap stops what is
# left when the script ends early. Needs curl.
#
# Copyright 2026 BSNSoft Solutions GmbH. Author: Christian Bürckert. Licensed under the Apache License, Version 2.0.

set -eu

# The header of this file is its manual page.
usage() {
  awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"
}

here=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
root=$(dirname -- "$here")

artefact=
image=
reference="java -jar $root/esj-cli/target/esj.jar"
while [ $# -gt 0 ]; do
  case $1 in
    --reference) reference=$2; shift 2 ;;
    --docker) image=$2; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    -*) echo "serve-smoke: unknown option $1" >&2; exit 2 ;;
    *) artefact=$1; shift ;;
  esac
done
if [ -z "$artefact" ] && [ -z "$image" ]; then
  echo "usage: dist/serve-smoke.sh <artefact> | --docker <image> [--reference <command>]" >&2
  exit 2
fi
command -v curl > /dev/null || { echo "serve-smoke: needs curl" >&2; exit 2; }
case $artefact in /*|'') ;; */*) artefact=$PWD/$artefact ;; esac

work=$(mktemp -d)
# What runs is recorded in files, because a server is started in a command
# substitution, whose variables do not reach this shell.
cleanup() {
  for record in "$work"/*.pid; do
    [ -f "$record" ] && kill "$(cat "$record")" 2>/dev/null || true
  done
  for record in "$work"/*.container; do
    [ -f "$record" ] && docker rm -f "$(cat "$record")" > /dev/null 2>&1 || true
  done
  rm -rf "$work"
}
trap cleanup EXIT INT TERM
cd "$root"

# The limit every server runs under, whatever happens to this script.
LIMIT=600

# Starts a server and prints its base URL once it listens.
start() {
  side=$1; shift
  # The words of a command are split here on purpose.
  # shellcheck disable=SC2086
  perl -e 'alarm shift; exec @ARGV' "$LIMIT" $* serve --port 0 --max-jobs 2 \
    > "$work/$side.out" 2> "$work/$side.log" &
  pid=$!
  echo "$pid" > "$work/$side.pid"
  waited=0
  while ! grep -q 'listening on ' "$work/$side.log" 2>/dev/null; do
    sleep 1
    waited=$((waited + 1))
    if [ "$waited" -gt 90 ] || ! kill -0 "$pid" 2>/dev/null; then
      cat "$work/$side.log" >&2
      echo "serve-smoke: the $side server did not start" >&2
      return 1
    fi
  done
  sed -n 's/.*listening on \(http:[^ ]*\) .*/\1/p' "$work/$side.log" | head -1
}

# Starts the container image with its published port and prints its base URL.
start_docker() {
  container=$(docker run -d --pull never -p 127.0.0.1::8080 --read-only \
    --tmpfs /tmp:size=512m,mode=1777 "$image" serve --max-jobs 2)
  echo "$container" > "$work/a.container"
  port=$(docker port "$container" 8080/tcp | sed -n 's/.*:\([0-9][0-9]*\)$/\1/p' | head -1)
  waited=0
  until curl -s -o /dev/null "http://127.0.0.1:$port/"; do
    sleep 1
    waited=$((waited + 1))
    if [ "$waited" -gt 90 ]; then
      docker logs "$container" >&2
      echo "serve-smoke: the container did not start" >&2
      return 1
    fi
  done
  echo "http://127.0.0.1:$port"
}

# Removes from an answer what differs between two servers by design: the
# address, and the identifier and time of an upload or an artefact. A link is
# absolute where the Host of the request is the server's own address and the
# path on the server otherwise (a container reached through another port), so
# the address is removed and both are the path.
normal() {
  sed -E \
    -e 's#http://(127\.0\.0\.1|localhost):[0-9]+##g' \
    -e 's#[0-9a-f]{32}#<id>#g' \
    -e 's#("expires": ?")[^"]+"#\1<time>"#g'
}

# One request: its status and its body, normalized, under a name.
request() {
  side=$1; name=$2; shift 2
  curl -s -o "$work/$side.body" -w '%{http_code}\n' "$@" > "$work/$side/$name.status"
  normal < "$work/$side.body" > "$work/$side/$name.body"
}

# The bytes of the first artefact an answer links, by their SHA-256.
artefact_digest() {
  side=$1; name=$2; base=$3
  path=$(grep -o '/api/artifacts/[0-9a-f]\{32\}' "$work/$side.body" | head -1)
  curl -s "$base$path" | { if command -v sha256sum > /dev/null; then sha256sum; else shasum -a 256; fi; } |
    awk '{print $1}' > "$work/$side/$name.artefact"
}

# The requests every server is asked.
requests() {
  side=$1; base=$2
  mkdir -p "$work/$side"
  pdf=conformance/pdf/factur-x.pdf
  esj=examples/standard-invoice.esj.json
  ubl=conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml
  request "$side" self "$base/"
  request "$side" openapi "$base/openapi.json"
  request "$side" validate-pdf --data-binary "@$pdf" "$base/api/validate"
  request "$side" validate-invalid --data-binary @examples/invalid/arithmetic-mismatch.esj.json \
    "$base/api/validate"
  request "$side" validate-report --data-binary "@$ubl" "$base/api/validate?report=html&lang=en"
  request "$side" summary --data-binary "@$ubl" "$base/api/summary"
  request "$side" get --data-binary "@$pdf" "$base/api/get?paths=/BT-1&paths=/BG-22"
  request "$side" convert --data-binary "@$esj" "$base/api/convert?to=ubl"
  artefact_digest "$side" convert "$base"
  request "$side" render --data-binary "@$esj" "$base/api/render?embed=cii"
  artefact_digest "$side" render "$base"
  request "$side" render-html --data-binary "@$esj" "$base/api/render?format=html"
  artefact_digest "$side" render-html "$base"
  request "$side" extract --data-binary "@$pdf" "$base/api/extract?list=true"
  request "$side" inspect --data-binary "@$pdf" "$base/api/inspect"
  request "$side" unreadable --data-binary @README.md "$base/api/validate"
  request "$side" upload --data-binary "@$esj" "$base/api/documents"
  id=$(grep -o '"id": "[0-9a-f]*"' "$work/$side.body" | sed 's/.*"\([0-9a-f]*\)"/\1/')
  request "$side" by-id -X POST "$base/api/summary?document=$id"
  json='Content-Type: application/json'
  request "$side" mcp-initialize -H "$json" --data-binary '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"smoke","version":"1"}}}' "$base/mcp"
  request "$side" mcp-list -H "$json" -H 'MCP-Protocol-Version: 2025-11-25' \
    --data-binary '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' "$base/mcp"
  request "$side" mcp-call -H "$json" -H 'MCP-Protocol-Version: 2025-11-25' \
    --data-binary "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"validate\",\"arguments\":{\"document\":\"$id\"}}}" \
    "$base/mcp"
  request "$side" mcp-get -X GET "$base/mcp"
}

# Stops a server with SIGTERM and checks that it left no temporary directory.
stop() {
  side=$1
  pid=$(cat "$work/$side.pid")
  temporary=$(sed -n 's/.*temporary directory \(.*\)$/\1/p' "$work/$side.log" | head -1)
  # The server is the process perl exec'd, so the pid is the server's.
  kill -TERM "$pid"
  waited=0
  while kill -0 "$pid" 2>/dev/null; do
    sleep 1
    waited=$((waited + 1))
    [ "$waited" -le 30 ] || { echo "serve-smoke: the $side server ignored SIGTERM" >&2; return 1; }
  done
  rm -f "$work/$side.pid"
  if [ -n "$temporary" ] && [ -e "$temporary" ]; then
    echo "serve-smoke: the $side server left $temporary behind" >&2
    return 1
  fi
  echo "ok   $side: SIGTERM ended the server and removed its temporary directory"
}

# The standard streams: three messages in, every line out, compared as bytes.
stdio() {
  side=$1; shift
  {
    printf '%s\n' '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"smoke","version":"1"}}}'
    printf '%s\n' '{"jsonrpc":"2.0","method":"notifications/initialized"}'
    printf '%s\n' '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
    printf '%s\n' "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"summary\",\"arguments\":{\"path\":\"$root/examples/standard-invoice.esj.json\"}}}"
  } > "$work/stdio.in"
  # shellcheck disable=SC2086
  perl -e 'alarm shift; exec @ARGV' 300 $* mcp < "$work/stdio.in" \
    > "$work/$side/stdio.out" 2> /dev/null
  echo $? > "$work/$side/stdio.exit"
}

# What the jar's server answers, so that two servers failing alike is no pass.
expected() {
  for pair in self:200 openapi:200 validate-pdf:200 validate-invalid:200 validate-report:200 \
      summary:200 get:200 convert:200 render:200 render-html:200 extract:200 inspect:200 \
      unreadable:422 upload:201 by-id:200 mcp-initialize:200 mcp-list:200 mcp-call:200 \
      mcp-get:405; do
    name=${pair%%:*}
    status=$(cat "$work/b/$name.status")
    [ "$status" = "${pair#*:}" ] ||
      { echo "serve-smoke: the jar answered $name with $status, not ${pair#*:}" >&2; exit 1; }
  done
  grep -q '"verdict": "VALID"' "$work/b/validate-pdf.body" ||
    { echo "serve-smoke: the jar did not find the hybrid PDF valid" >&2; exit 1; }
  [ "$(wc -l < "$work/b/stdio.out" | tr -d ' ')" = 3 ] ||
    { echo "serve-smoke: esj mcp of the jar did not answer three requests" >&2; exit 1; }
}

failures=0
compare() {
  for file in "$work"/b/*; do
    name=$(basename "$file")
    if cmp -s "$file" "$work/a/$name"; then
      echo "ok   $name"
    else
      echo "FAIL $name"
      diff -u "$file" "$work/a/$name" | sed -n '1,12p' | sed 's/^/     /'
      failures=$((failures + 1))
    fi
  done
}

base_b=$(start b "$reference")
requests b "$base_b"
stdio b "$reference"
stop b
expected

if [ -n "$image" ]; then
  base_a=$(start_docker)
  requests a "$base_a"
  container=$(cat "$work/a.container")
  docker logs "$container" 2>&1 | grep -q 'warning: listening on 0.0.0.0 without a token' ||
    { echo "serve-smoke: the container does not say that it listens without a token" >&2
      failures=$((failures + 1)); }
  docker stop -t 20 "$container" > /dev/null
  docker logs "$container" 2>&1 | grep -q '^stopping: ' ||
    { echo "serve-smoke: the container did not stop on SIGTERM" >&2; failures=$((failures + 1)); }
  echo "ok   container: listens on the published port, stops on SIGTERM"
  # The standard streams of the image: docker run -i.
  mkdir -p "$work/a"
  {
    printf '%s\n' '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"smoke","version":"1"}}}'
    printf '%s\n' '{"jsonrpc":"2.0","method":"notifications/initialized"}'
    printf '%s\n' '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
    printf '%s\n' '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"summary","arguments":{"path":"/work/examples/standard-invoice.esj.json"}}}'
  } > "$work/stdio-docker.in"
  perl -e 'alarm shift; exec @ARGV' 300 docker run --rm -i --pull never -v "$root:/work:ro" \
    "$image" mcp < "$work/stdio-docker.in" > "$work/a/stdio.out" 2> /dev/null
  echo $? > "$work/a/stdio.exit"
else
  # Started by its bare name from the PATH, as an installed tool is: the server has to
  # find its own executable for the children however it was started.
  by_name="env PATH=$(dirname -- "$artefact"):/usr/bin:/bin $(basename -- "$artefact")"
  base_a=$(start a "$by_name")
  requests a "$base_a"
  stdio a "$by_name"
  stop a
fi

compare
if [ "$failures" -gt 0 ]; then
  echo "serve-smoke: $failures answers differ between the artefact and the jar" >&2
  exit 1
fi
echo "serve-smoke: $(ls "$work/b" | wc -l | tr -d ' ') answers, no difference between the artefact and the jar"
