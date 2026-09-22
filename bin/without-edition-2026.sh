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
# directory from mktemp and is left behind for inspection.
#
#   bin/without-edition-2026.sh
#
# Exit code 0 means the build was green without the edition. Anything else is the
# exit code of the step that failed.
#
# Copyright 2026 BSNSoft Solutions GmbH. Author: Christian Bürckert. Licensed under the Apache License, Version 2.0.

set -eu

here=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
root=$(CDPATH='' cd -- "$here/.." && pwd)
list="$root/model/en16931/2026.paths"

if [ ! -f "$list" ]; then
  echo "error: $list does not exist" >&2
  exit 1
fi

copy=$(mktemp -d)
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
echo "building in $copy"
cd -- "$copy"
mvn -B -P without-edition-2026 clean verify
echo "green without EN 16931-1:2026; the copy is $copy"
