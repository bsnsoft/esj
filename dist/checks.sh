#!/bin/sh
#
# What the packaging checks about the artefacts rather than about the tool
# inside them: which revision an image may claim to have been built from, and
# that nothing a reader downloads carries a path of the machine that built it.
#
# Sourced, like dist/cases.sh, by dist/package.sh and dist/native-image.sh, so
# that the host build and the container build make the same checks and a test
# can make them on their own.
#
# Copyright 2026 BSNSoft Solutions GmbH. Author: Christian Bürckert. Licensed under the Apache License, Version 2.0.

# The revision an artefact may claim: the commit, and `-dirty` behind it when
# the working tree carries changes that commit does not have. A bare revision on
# an image built from a modified tree names sources that are not the ones inside
# it — down to the Dockerfile the image was built with. Prints nothing where
# there is no repository to ask, which is what a build from an unpacked archive
# is.
#
#   esj_revision <directory>
esj_revision() {
  esj_revision_root=${1:?usage: esj_revision <directory>}
  esj_revision_head=$( ( cd "$esj_revision_root" && git rev-parse HEAD ) 2>/dev/null ) ||
    return 0
  [ -n "$esj_revision_head" ] || return 0
  if [ -n "$( ( cd "$esj_revision_root" && git status --porcelain ) 2>/dev/null )" ]; then
    esj_revision_head=$esj_revision_head-dirty
  fi
  echo "$esj_revision_head"
}

# Echoes a path that is worth looking for and nothing at all for one that is not,
# saying which it dropped without naming it.
esj_distinctive_path() {
  case ${1-} in
    /*/?*) echo "$1" ;;
    ?*) echo "checks: $2 is one path segment; not looked for" >&2 ;;
  esac
}

# Fails when an artefact carries a path of the machine that built it: the home
# directory of the account that ran the build, or the directory the sources were
# read from. Both are looked for in the bytes of every file rather than in its
# printable strings, because that is where they are — the debug map of a Mach-O
# executable records which object file each symbol came from, inside the
# toolchain installation of whoever built it, and `strings` reports none of it.
#
# Neither path is ever printed: a failure names the file and which of the two it
# carries — the more specific one, the source directory, where a build reads its
# sources from under its own home — and whoever runs the packaging knows both
# paths already.
#
# A path of a single segment — `/root` and `/src`, which is what a build inside a
# container has — is not looked for. It cannot be told from ordinary text: the
# licence of a third-party library names `/src`, and the check would fail every
# container build over it. Nothing private is lost with it, because a path like
# that belongs to the container and not to a person.
#
# What the build recorded about itself under metadata/ is left out. It stays on
# disk, where it says what the executable was built from, and dist/package.sh
# keeps it out of the archive a reader downloads.
#
#   esj_no_build_paths <artefact directory> <source directory>

esj_no_build_paths() {
  esj_paths_directory=${1:?usage: esj_no_build_paths <artefact directory> <source directory>}
  esj_paths_home=$(esj_distinctive_path "${HOME-}" "the home directory of the build")
  esj_paths_sources=$(esj_distinctive_path "${2-}" "the directory the sources were read from")
  esj_paths_carried=$(
    find "$esj_paths_directory" -type f ! -path '*/metadata/*' -print |
      while IFS= read -r esj_paths_file; do
        if [ -n "$esj_paths_sources" ] &&
            LC_ALL=C grep -a -q -F -e "$esj_paths_sources" -- "$esj_paths_file"; then
          echo "  ${esj_paths_file##*/}: the directory the sources were read from"
        elif [ -n "$esj_paths_home" ] &&
            LC_ALL=C grep -a -q -F -e "$esj_paths_home" -- "$esj_paths_file"; then
          echo "  ${esj_paths_file##*/}: the home directory of the account that built it"
        fi
      done
  )
  if [ -n "$esj_paths_carried" ]; then
    echo "checks: a path of the build machine travels inside the artefact:" >&2
    echo "$esj_paths_carried" >&2
    return 1
  fi
  echo "checks: no path of the build machine in $(find "$esj_paths_directory" -type f \
    ! -path '*/metadata/*' -print | wc -l | tr -d ' ') files"
}
