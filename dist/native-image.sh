#!/bin/sh
#
# Builds the native executable of the esj command line with GraalVM. It is what
# dist/package.sh runs for the executable of this machine and what
# dist/Dockerfile.native runs for the Linux one, so both are built the same way.
#
#   dist/native-image.sh <jar> <output directory>
#
# What the image has to reach is recorded rather than declared: the agent of
# GraalVM runs every case of dist/cases.sh over the jar, and the resources of
# the jar — the validation packs, the term registry, the rule packs, the fonts
# and the stylesheets — are listed from the jar itself. Neither step names a
# feature, so a command added later is covered by adding its case.
#
# GraalVM is $ESJ_GRAALVM_HOME, $GRAALVM_HOME or $JAVA_HOME; no default of the
# machine is changed and nothing is switched over.
#
# Copyright 2026 BSNSoft Solutions GmbH. Author: Christian Bürckert. Licensed under the Apache License, Version 2.0.

set -eu

here=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
root=$(dirname -- "$here")
. "$here/cases.sh"
. "$here/checks.sh"

jar=${1:?usage: dist/native-image.sh <jar> <output directory>}
target=${2:?usage: dist/native-image.sh <jar> <output directory>}
graal=${ESJ_GRAALVM_HOME:-${GRAALVM_HOME:-${JAVA_HOME:-}}}
[ -x "${graal}/bin/native-image" ] ||
  { echo "native-image.sh: no native-image in ${graal:-<unset>}" >&2; exit 1; }

case $jar in /*) ;; *) jar=$(pwd)/$jar ;; esac
case $target in /*) ;; *) target=$(pwd)/$target ;; esac
mkdir -p "$target/metadata/traced" "$target/metadata/resources"
scratch=$(mktemp -d)
cleanup() { rm -rf "$scratch"; }
trap cleanup EXIT INT TERM
cd "$root"

echo "native-image.sh: $("$graal"/bin/native-image --version | head -1)"

runs=0
esj_cases | while IFS= read -r line; do
  case $line in convert-stdin-*) continue ;; esac
  name=${line%% *}
  args=
  # The same two tokens the smoke runner substitutes: what a case writes, and
  # what an earlier one wrote. A chain is traced as a chain, so the paths a
  # hybrid file is written and read back over are recorded too.
  for argument in ${line#* }; do
    case $argument in
      @OUT) argument=$scratch/$name ;;
      @FROM:*) argument=$scratch/${argument#@FROM:} ;;
    esac
    args="$args $argument"
  done
  "$graal"/bin/java \
    "-agentlib:native-image-agent=config-merge-dir=$target/metadata/traced" \
    -jar "$jar" $args >/dev/null 2>&1 || true
done
runs=$(esj_cases | grep -cv '^convert-stdin-')
[ -f "$target/metadata/traced/reachability-metadata.json" ] ||
  { echo "native-image.sh: the agent recorded nothing over $runs runs" >&2; exit 1; }

# The resources of the jar as metadata of their own, discovered from the jar.
"$graal"/bin/jar --list --file "$jar" |
  awk '
    /\/$/ { next }
    /\.class$/ { next }
    /^META-INF\// && !/^META-INF\/services\// { next }
    { print }
  ' |
  sort |
  awk '
    BEGIN { print "{"; print "  \"resources\": ["; separator = "" }
    { gsub(/\\/, "\\\\"); gsub(/"/, "\\\""); printf "%s    { \"glob\": \"%s\" }", separator, $0; separator = ",\n" }
    END { print ""; print "  ]"; print "}" }
  ' > "$target/metadata/resources/reachability-metadata.json"
echo "native-image.sh: traced $runs runs, $(grep -c '"glob"' "$target/metadata/resources/reachability-metadata.json") resources"

# The defaults of the process boundary are built into the image, because there
# is no launcher beside an executable to pass them: a heap ceiling of 512 MiB
# and an abort on heap exhaustion, the same two every other artefact passes on
# its command line (docs/deployment.md). -Xmx replaces the ceiling at run time.
#
# Which prefix a run-time option takes differs between GraalVM releases, so it
# is read from the installed native-image rather than assumed: a spelling this
# one does not know is refused by the build, never dropped quietly.
prefix=$("$graal"/bin/native-image --expert-options-all 2>/dev/null |
  sed -n 's/^  \(-[RH]:\)MaxHeapSize=.*/\1/p' | head -1)
[ -n "$prefix" ] ||
  { echo "native-image.sh: no MaxHeapSize option in $("$graal"/bin/native-image --version |
      head -1)" >&2; exit 1; }
echo "native-image.sh: the defaults of the process boundary as ${prefix}MaxHeapSize"

"$graal"/bin/native-image -jar "$jar" -o "$target/esj" --no-fallback \
  "${prefix}MaxHeapSize=536870912" "${prefix}+ExitOnOutOfMemoryError" \
  -H:ConfigurationFileDirectories="$target/metadata/traced,$target/metadata/resources" \
  -J-Xmx8g

# macOS records in the executable's symbol table which object file every symbol
# came from, and for the parts linked in from the toolchain those object files
# lie inside the GraalVM installation of whoever ran the build: an absolute path
# of the build machine, in the debug map rather than in the text, where
# `strings` does not report it. `strip -S` drops the debugging entries and keeps
# the symbols the runtime resolves; it re-signs what it rewrote, and where a
# toolchain does not, the ad-hoc signature is put back, because an arm64
# executable without a valid one does not start.
case $(uname -s) in
  Darwin)
    strip -S "$target/esj"
    codesign --verify "$target/esj" >/dev/null 2>&1 ||
      codesign --force --sign - "$target/esj" >/dev/null 2>&1 ||
      { echo "native-image.sh: the executable has no valid signature after stripping" >&2
        exit 1; }
    ;;
esac

# The runtime libraries beside the executable (checks.sh): copied from this
# GraalVM where native-image left them out, and a hard check either way.
esj_runtime_libraries "$target" "$graal" || exit 1

# The executable carries parts of the Substrate VM runtime and loads Java
# runtime libraries from beside it, all under GPLv2 with the Classpath
# Exception (NOTICE). Their licences travel with them, as jlink lets them
# travel with a runtime image.
mkdir -p "$target/legal"
for licence in LICENSE.txt LICENSE_NATIVEIMAGE.txt THIRD_PARTY_LICENSE.txt; do
  if [ -f "$graal/$licence" ]; then cp "$graal/$licence" "$target/legal/$licence"; fi
done
if [ -d "$graal/legal" ]; then cp -R "$graal"/legal/. "$target/legal/"; fi
echo "native-image.sh: $(find "$target/legal" -type f | wc -l | tr -d " ") licence files"
# Nothing that leaves this machine names it. The check reads the artefacts as
# bytes and prints no path of its own accord.
esj_no_build_paths "$target" "$root"
echo "native-image.sh: $target/esj"
