#!/bin/sh
#
# Builds ESJ Viewer.app beside the native macOS executable: the application
# Finder offers under "Open With" for an XML or a PDF file, which runs
# `esj view --report` on it (docs/view.md). It is an AppleScript applet,
# compiled with osacompile from dist/desktop/viewer.applescript, because Finder
# hands a document to an application as an open-documents event and an applet
# is what receives one without a line of native code. It declares public.xml
# and com.adobe.pdf with the role Viewer and the rank Alternate, so it is listed
# under "Open With" and never becomes the default of either type; its icon is
# written from dist/desktop/esj.svg by dist/desktop/Icon.java. It is signed ad
# hoc and not notarised, like the executable beside it.
#
#   dist/viewer-app.sh <directory> <version>
#
#   <directory>  the directory of the native executable, esj in it; the
#                application is written beside it and must not exist yet
#   <version>    the version of the tool, for the bundle
#
# macOS only: osacompile, PlistBuddy and codesign are part of the system. The
# icon is drawn by the Java of $JAVA_HOME, or the java on the PATH.
#
# Copyright 2026 BSNSoft Solutions GmbH. Author: Christian Bürckert. Licensed under the Apache License, Version 2.0.

set -eu

here=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
root=$(dirname -- "$here")
. "$here/checks.sh"

fail() { echo "viewer-app.sh: $*" >&2; exit 1; }

directory=${1:?usage: dist/viewer-app.sh <directory> <version>}
version=${2:?usage: dist/viewer-app.sh <directory> <version>}
[ "$(uname -s)" = Darwin ] || fail "ESJ Viewer.app is built on macOS"
[ -x "$directory/esj" ] || fail "no executable esj in $directory"
app=$directory/$ESJ_VIEWER_APP
[ ! -e "$app" ] || fail "$app exists; build it into a new directory"

java=java
[ -z "${JAVA_HOME-}" ] || java=$JAVA_HOME/bin/java
work=$(mktemp -d)
cleanup() { rm -rf "${work:?}"; }
trap cleanup EXIT INT TERM

"$java" "$here/desktop/Icon.java" "$here/desktop/esj.svg" "$work/esj.icns" > /dev/null
osacompile -o "$app" "$here/desktop/viewer.applescript"

# osacompile writes a droplet that takes any file, with an icon in an asset
# catalog that would win over the one given here: the bundle is made the
# viewer of the two types, with the identifier, the version and the icon of
# this project.
plist=$app/Contents/Info.plist
pb() { /usr/libexec/PlistBuddy -c "$1" "$plist"; }
# Which keys the applet template carries differs between macOS releases (macOS 15 writes no
# CFBundleIconName, macOS 26 does), so a key is removed only where it is, and set by removing
# it first: PlistBuddy's Set fails on a key that is missing, its Add on one that is there.
has_key() { pb "Print :$1" > /dev/null 2>&1; }
drop_key() { if has_key "$1"; then pb "Delete :$1"; fi; }
put_key() { drop_key "$1"; pb "Add :$1 $2"; }
put_key CFBundleIdentifier "string $ESJ_VIEWER_IDENTIFIER"
put_key CFBundleName "string 'ESJ Viewer'"
put_key CFBundleDisplayName "string 'ESJ Viewer'"
# The bundle version is three numbers; a pre-release suffix is the tool's, not its.
put_key CFBundleShortVersionString "string ${version%%-*}"
put_key CFBundleVersion "string ${version%%-*}"
drop_key CFBundleIconName
put_key CFBundleIconFile "string esj"
drop_key CFBundleDocumentTypes
set_key() { pb "$1"; }
set_key "Add :CFBundleDocumentTypes array"
set_key "Add :CFBundleDocumentTypes:0 dict"
set_key "Add :CFBundleDocumentTypes:0:CFBundleTypeName string 'Electronic invoice'"
set_key "Add :CFBundleDocumentTypes:0:CFBundleTypeRole string Viewer"
set_key "Add :CFBundleDocumentTypes:0:LSHandlerRank string Alternate"
set_key "Add :CFBundleDocumentTypes:0:LSItemContentTypes array"
set_key "Add :CFBundleDocumentTypes:0:LSItemContentTypes:0 string public.xml"
set_key "Add :CFBundleDocumentTypes:0:LSItemContentTypes:1 string com.adobe.pdf"
# The two files of the icon osacompile wrote, in the bundle this run created.
rm -f "${app:?}/Contents/Resources/Assets.car" "${app:?}/Contents/Resources/droplet.icns"
cp "$work/esj.icns" "$app/Contents/Resources/esj.icns"
codesign --force --deep --sign - "$app"

esj_viewer_app "$app"
esj_no_build_paths "$app" "$root"
echo "viewer-app.sh: $app"
