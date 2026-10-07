#!/bin/sh
#
# Builds ESJ without the files of EN 16931-1:2026 and reports whether it is green.
#
# Whether a registry built from the 2026 edition may be published is a question that
# is open (docs/editions.md, "Separability"), and the answer must not decide whether
# the rest of the project builds. model/en16931/2026.paths lists every file that
# carries facts of that edition; this script copies the tree without those files and
# runs the full build over the copy under the Maven profile without-edition-2026.
#
# It copies rather than deletes: the copy is assembled from the files that are not in
# the list, so nothing of the checkout is ever removed. The copy goes into a fresh
# directory under $TMPDIR (or /tmp), which this script made and which it removes when
# it ends, however it ends; --keep leaves it behind for inspection.
#
#   bin/without-edition-2026.sh              the Maven build, then both bindings
#   bin/without-edition-2026.sh --bindings   the two bindings alone, as the CI runs it
#   bin/without-edition-2026.sh --keep       either of the two, and the copy is kept
#
# The bindings run where their toolchain is on the PATH: the TypeScript binding with
# npm, the C# binding with dotnet, each with its tests and the fixture runner. Without
# --bindings a missing toolchain is reported and passed over; with it, it is an error.
#
# Exit code 0 means the build was green without the edition. Anything else is the
# exit code of the step that failed.
#
# Copyright 2026 BSNSoft Solutions GmbH. Author: Christian Bürckert. Licensed under the Apache License, Version 2.0.

set -eu

mode=all
keep=no
for argument in "$@"; do
  case "$argument" in
    --bindings) mode=bindings ;;
    --keep) keep=yes ;;
    *)
      echo "usage: $0 [--bindings] [--keep]" >&2
      exit 2
      ;;
  esac
done

here=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
root=$(CDPATH='' cd -- "$here/.." && pwd)
list="$root/model/en16931/2026.paths"

if [ ! -f "$list" ]; then
  echo "error: $list does not exist" >&2
  exit 1
fi

# mktemp -d alone ignores TMPDIR on macOS. The directory is this run's own, and the
# trap removes that one path and nothing else.
temporary=${TMPDIR:-/tmp}
copy=$(mktemp -d "${temporary%/}/esj-no2026.XXXXXX")
if [ "$keep" = no ]; then
  trap 'rm -rf -- "${copy:?}"' EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM
fi
echo "copying the tree without the files of EN 16931-1:2026 into $copy"

# The excluded paths, without the comments and the blank lines. A line that names a
# directory covers everything under it, which the prefix test below implements.
excluded=$(sed -e 's/#.*//' -e 's/[[:space:]]*$//' -e '/^$/d' "$list")

skipped=0
copied=0
# Tracked files and files that are new and not ignored: the same set a commit would
# carry, so that the check works on a tree whose phase has not been committed yet. A
# tracked file that has been deleted and not yet committed as deleted is simply not
# there and is not copied.
for file in $(git -C "$root" ls-files --cached --others --exclude-standard); do
  [ -f "$root/$file" ] || continue
  skip=no
  for path in $excluded; do
    case "$file" in
      "$path" | "$path"/*) skip=yes ;;
    esac
  done
  if [ "$skip" = yes ]; then
    skipped=$((skipped + 1))
    continue
  fi
  mkdir -p "$copy/$(dirname -- "$file")"
  cp -- "$root/$file" "$copy/$file"
  copied=$((copied + 1))
done

echo "$copied files copied, $skipped left out"
cd -- "$copy"
if [ "$mode" = all ]; then
  echo "building in $copy"
  mvn -B -P without-edition-2026 clean verify
fi

# Says that a binding's toolchain is missing, which fails the run only where the
# bindings are all it was asked for.
missing() {
  echo "$1 is not on the PATH: the $2 binding was not run without the edition" >&2
  if [ "$mode" = bindings ]; then
    exit 1
  fi
}

if command -v npm >/dev/null 2>&1; then
  echo "running the TypeScript binding in $copy"
  (cd bindings/typescript && npm ci --ignore-scripts && npm test && npm run fixtures)
else
  missing npm TypeScript
fi
if command -v dotnet >/dev/null 2>&1; then
  echo "running the C# binding in $copy"
  dotnet test bindings/csharp/En16931.SemanticJson.sln
  python3 conformance/fixtures/run.py --binding \
    dotnet run --no-build --project bindings/csharp/En16931.SemanticJson.Fixtures -- .
else
  missing dotnet C#
fi
if [ "$keep" = yes ]; then
  echo "green without EN 16931-1:2026; the copy is $copy"
else
  echo "green without EN 16931-1:2026"
fi
