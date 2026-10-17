#!/bin/sh
#
# Writes the apt and dnf repositories of the esj packages into a site directory:
# the checkout of the branch gh-pages, which GitHub Pages serves at
# https://apt.bsnsoft.de, the apt repository at the root and the rpm repository
# under rpm/. Everything but the packages is regenerated from the packages
# present on every run, so the branch is the whole state: no database of a
# repository tool is kept, and a package deleted from it is gone from the
# indices after the next run. What the script does not write it leaves alone:
# the CNAME file GitHub Pages keeps for the domain, for one.
#
#   dist/repositories.sh <packages-dir> <site-dir> <gpg-key>
#   dist/repositories.sh --unlock <gpg-key>          (the passphrase on stdin)
#
#   <packages-dir>  the .deb and .rpm files of dist/packages.sh to add; it may
#                   hold none, and the run then only regenerates the indices,
#                   which is how a pruned repository is put right and how a
#                   version already on the branch is published again
#   <site-dir>      the tree the site is served from
#   <gpg-key>       the fingerprint of the key that signs the indices and the
#                   rpm packages; its public part is written beside them
#   --unlock        lets the signing commands of this script use a key that has
#                   a passphrase, without asking for it: gpg is told to accept
#                   the passphrase from a program (loopback pinentry), one
#                   signature is made with the passphrase read from the
#                   standard input, the passphrase is preset in the agent for
#                   every keygrip of the key (gpg-preset-passphrase), and a
#                   second signature is made without it. Once per keyring and
#                   agent, before the first run.
#
# What the site holds afterwards, under the address $ESJ_REPOSITORY_URL
# (https://apt.bsnsoft.de by default), which the .repo file and the index page
# name:
#
#   pool/main/e/esj/*.deb              every version ever added
#   dists/stable/Release, Release.gpg, InRelease
#   dists/stable/main/binary-{amd64,arm64}/Packages, Packages.gz, by-hash/
#   bsnsoft-esj.gpg, bsnsoft-esj.asc   the public key, binary and armoured
#   rpm/stable/{x86_64,aarch64}/*.rpm  each signed, with repodata/ and
#                                      repodata/repomd.xml.asc
#   rpm/esj.repo, rpm/bsnsoft-esj.asc
#   index.html, .nojekyll
#
# One suite, stable, carries every version, a release candidate as well as a
# release: 1.2.3-rc.1 is packaged as 1.2.3~rc.1 (dist/packages.sh), which apt
# and dnf sort after 1.2.2 and before 1.2.3, so a reader of the suite is given
# the candidate and, once it is there, the release that replaces it.
#
# From 0.9.8 to 1.0.0-rc.2 the site had a second suite, testing, for the
# release candidates. A run over a site that still has it moves its rpm
# packages from rpm/testing/<arch>/ to rpm/stable/<arch>/, the same files with
# their signatures, and removes dists/testing, rpm/testing and
# rpm/esj-testing.repo; the .deb packages are in the shared pool already. It
# removes them file by file and only files of that layout: anything else under
# them stops the run before the site changes. A second run finds nothing to
# move.
#
# A published package is never replaced: a .deb of the same name has to carry
# the same bytes, an .rpm the same header digest (signing adds a signature and
# leaves the header and the payload as they are). An rpm without a signature is
# signed with rpm --addsign; one with a signature is left as it is. Every rpm is
# then checked against the public key, and the indices are signed with
# detached and inline signatures (Release.gpg, InRelease, repomd.xml.asc).
#
# The apt indices are also written under by-hash/ and Release says
# Acquire-By-Hash: GitHub Pages lets a client cache for ten minutes, and an apt
# that reads a fresh InRelease fetches its indices by their digest rather than
# a Packages file a cache may still hold in its earlier version. A by-hash file
# of stable is never removed here; the old ones are a few kilobytes each.
#
# Needs, on Linux: apt-ftparchive (apt-utils), dpkg-deb, createrepo_c, rpm and
# rpmsign (rpm), gpg and gzip.
#
# Copyright 2026 BSNSoft Solutions GmbH. Author: Christian Bürckert. Licensed under the Apache License, Version 2.0.

set -eu

# The header of this file is its manual page.
usage() {
  awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"
}

here=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
fail() { echo "repositories: $*" >&2; exit 1; }
say() { echo "repositories: $*"; }

work=$(mktemp -d)
cleanup() { rm -rf "${work:?}"; }
trap cleanup EXIT INT TERM

# One signature of a probe file with the key, without a passphrase prompt.
probe() {
  printf 'probe\n' > "$work/probe.txt"
  rm -f "$work/probe.sig"
  gpg --batch --yes --pinentry-mode error --local-user "$1" \
    --detach-sign --output "$work/probe.sig" "$work/probe.txt" 2> "$work/probe.log" &&
    gpg --batch --verify "$work/probe.sig" "$work/probe.txt" 2>> "$work/probe.log"
}

case ${1-} in
  -h|--help) usage; exit 0 ;;
  --unlock)
    [ $# -eq 2 ] || fail "usage: dist/repositories.sh --unlock <gpg-key> < passphrase"
    key=$2
    home=$(gpgconf --list-dirs homedir)
    gpg --batch --list-secret-keys "$key" > /dev/null 2>&1 || fail "no secret key $key in $home"
    mkdir -p "$home"
    chmod 700 "$home"
    printf 'pinentry-mode loopback\n' >> "$home/gpg.conf"
    printf 'allow-loopback-pinentry\nallow-preset-passphrase\n' >> "$home/gpg-agent.conf"
    gpgconf --kill gpg-agent 2>/dev/null || true
    passphrase=
    IFS= read -r passphrase || [ -n "$passphrase" ] || fail "no passphrase on the standard input"
    printf 'probe\n' > "$work/probe.txt"
    printf '%s' "$passphrase" | gpg --batch --yes --pinentry-mode loopback --passphrase-fd 0 \
      --local-user "$key" --detach-sign --output "$work/probe.sig" "$work/probe.txt" ||
      fail "the passphrase does not unlock $key"
    preset=$(gpgconf --list-dirs libexecdir)/gpg-preset-passphrase
    [ -x "$preset" ] || fail "no gpg-preset-passphrase at $preset"
    gpg --batch --with-colons --with-keygrip --list-secret-keys "$key" |
      awk -F: '$1 == "grp" { print $10 }' > "$work/keygrips"
    while IFS= read -r keygrip; do
      printf '%s' "$passphrase" | "$preset" --preset "$keygrip"
    done < "$work/keygrips"
    passphrase=
    probe "$key" || { cat "$work/probe.log" >&2; fail "$key still asks for its passphrase"; }
    say "$key signs without a prompt ($(wc -l < "$work/keygrips" | tr -d ' ') keygrips preset)"
    exit 0 ;;
esac

[ $# -eq 3 ] || { echo "usage: dist/repositories.sh <packages-dir> <site-dir> <gpg-key>" >&2; exit 2; }
packages=$1
site=$2
key=$3
url=${ESJ_REPOSITORY_URL:-https://apt.bsnsoft.de}
url=${url%/}
# The one suite, of apt and of dnf.
suite=stable
[ -d "$packages" ] || fail "$packages is not a directory"
[ -d "$site" ] || fail "$site is not a directory"
site=$(CDPATH='' cd -- "$site" && pwd)
for tool in apt-ftparchive dpkg-deb createrepo_c rpm rpmsign rpmkeys gpg gzip; do
  command -v "$tool" > /dev/null || fail "needs $tool"
done
probe "$key" || { cat "$work/probe.log" >&2; fail "$key cannot sign without a prompt; run --unlock first"; }

sha256_of() { sha256sum "$1" | awk '{print $1}'; }

# The suite testing of an earlier layout (see the header): every file under
# dists/testing and rpm/testing, and rpm/esj-testing.repo, is listed and held
# against the files that layout held, and its rpm packages are planned to move
# to stable, before anything in the site changes.
cat > "$work/testing-layout" <<'LAYOUT'
^dists/testing/(Release|Release\.gpg|InRelease)$
^dists/testing/main/binary-(amd64|arm64)/Packages(\.gz)?$
^dists/testing/main/binary-(amd64|arm64)/by-hash/SHA256/[0-9a-f]{64}$
^rpm/testing/(x86_64|aarch64)/esj-[^/]+\.(x86_64|aarch64)\.rpm$
^rpm/testing/(x86_64|aarch64)/repodata/repomd\.xml(\.asc)?$
^rpm/testing/(x86_64|aarch64)/repodata/[0-9a-f]{64}-[a-z_]+\.xml(\.(gz|bz2|xz|zst))?$
^rpm/esj-testing\.repo$
LAYOUT
( cd "$site" &&
  for top in dists/testing rpm/testing rpm/esj-testing.repo; do
    if [ -e "$top" ] || [ -L "$top" ]; then find "$top" ! -type d; fi
  done ) > "$work/testing-files"
( cd "$site" &&
  for top in dists/testing rpm/testing; do
    if [ -d "$top" ] && [ ! -L "$top" ]; then find "$top" -type d; fi
  done ) | LC_ALL=C sort -r > "$work/testing-directories"
: > "$work/testing-moves"
while IFS= read -r path; do
  if [ ! -f "$site/$path" ] || [ -L "$site/$path" ] ||
      ! printf '%s\n' "$path" | grep -Eq -f "$work/testing-layout"; then
    fail "$path is no file the suite testing of an earlier layout held; nothing is migrated"
  fi
  case $path in
    rpm/testing/*.rpm)
      rpm -qp --nosignature --qf '%{NAME}\n%{ARCH}\n%{SHA256HEADER}\n' "$site/$path" \
        > "$work/fields" 2>/dev/null || fail "$path is not an rpm package"
      arch=${path#rpm/testing/}
      arch=${arch%%/*}
      [ "$(sed -n 1p "$work/fields")" = esj ] && [ "$(sed -n 2p "$work/fields")" = "$arch" ] ||
        fail "$path is not the esj package for $arch"
      target=rpm/$suite/$arch/${path##*/}
      if [ -e "$site/$target" ]; then
        [ "$(rpm -qp --nosignature --qf '%{SHA256HEADER}' "$site/$target")" = \
          "$(sed -n 3p "$work/fields")" ] ||
          fail "$path and $target are published with different headers"
      else
        printf '%s\n%s\n' "$path" "$target" >> "$work/testing-moves"
      fi ;;
  esac
done < "$work/testing-files"

# The packages to add, every one of them checked before anything in the site
# changes: a list of source and target, then the copies. An rpm the migration
# moves to stable counts as published there.
pool=$site/pool/main/e/esj
: > "$work/plan"
for deb in "$packages"/*.deb; do
  [ -f "$deb" ] || continue
  name=$(dpkg-deb --field "$deb" Package)
  version=$(dpkg-deb --field "$deb" Version)
  [ "$name" = esj ] || fail "$deb is the package $name, not esj"
  say "${deb##*/}: esj $version"
  target=$pool/${deb##*/}
  if [ -e "$target" ]; then
    cmp -s "$deb" "$target" ||
      fail "${deb##*/} is published with other contents; a published package is never replaced"
    say "${deb##*/} is there already"
  else
    printf '%s\n%s\n' "$deb" "$target" >> "$work/plan"
  fi
done
for package in "$packages"/*.rpm; do
  [ -f "$package" ] || continue
  # One query for the fields, each on a line of its own.
  rpm -qp --nosignature --qf '%{NAME}\n%{VERSION}\n%{ARCH}\n%{SHA256HEADER}\n' "$package" \
    > "$work/fields" 2>/dev/null || fail "$package is not an rpm package"
  name=$(sed -n 1p "$work/fields")
  version=$(sed -n 2p "$work/fields")
  arch=$(sed -n 3p "$work/fields")
  header=$(sed -n 4p "$work/fields")
  [ "$name" = esj ] || fail "$package is the package $name, not esj"
  case $arch in x86_64|aarch64) ;; *) fail "$package is for $arch" ;; esac
  say "${package##*/}: esj $version"
  target=$site/rpm/$suite/$arch/${package##*/}
  published=$target
  [ -e "$published" ] || published=$site/rpm/testing/$arch/${package##*/}
  if [ -e "$published" ]; then
    [ "$(rpm -qp --nosignature --qf '%{SHA256HEADER}' "$published")" = "$header" ] ||
      fail "${package##*/} is published with another header; a published package is never replaced"
    say "${package##*/} is there already"
  else
    printf '%s\n%s\n' "$package" "$target" >> "$work/plan"
  fi
done
# The migration, now that everything is checked: the rpm packages first, then
# every other file of the suite testing, then its directories, deepest first.
moved=0
while IFS= read -r source && IFS= read -r target; do
  mkdir -p "$site/${target%/*}"
  mv -- "$site/$source" "$site/$target"
  say "$source moved to $target"
  moved=$((moved + 1))
done < "$work/testing-moves"
removed=0
while IFS= read -r path; do
  [ -e "$site/$path" ] || continue
  rm -f -- "${site:?}/$path"
  removed=$((removed + 1))
done < "$work/testing-files"
while IFS= read -r directory; do
  rmdir -- "${site:?}/$directory"
done < "$work/testing-directories"
if [ -s "$work/testing-files" ] || [ -s "$work/testing-directories" ]; then
  say "the suite testing of an earlier layout: $moved packages moved to $suite, $removed other files removed"
fi

added=0
while IFS= read -r source && IFS= read -r target; do
  mkdir -p "$(dirname -- "$target")"
  cp "$source" "$target"
  say "${source##*/} added as ${target#"$site"/}"
  added=$((added + 1))
done < "$work/plan"
say "$added packages added"

# The public key, as apt and dnf read it: binary for signed-by, armoured for
# gpgkey= and for a reader who compares the fingerprint.
mkdir -p "$site/rpm"
gpg --batch --export --export-options export-minimal "$key" > "$work/key.gpg"
[ -s "$work/key.gpg" ] || fail "no public key $key to export"
gpg --batch --armor --export --export-options export-minimal "$key" > "$work/key.asc"
cp "$work/key.gpg" "$site/bsnsoft-esj.gpg"
cp "$work/key.asc" "$site/bsnsoft-esj.asc"
cp "$work/key.asc" "$site/rpm/bsnsoft-esj.asc"

# rpm: every package signed, and every signature checked against the public key
# alone, in an rpm database of its own.
mkdir -p "$work/rpmdb"
rpmkeys --dbpath "$work/rpmdb" --import "$work/key.asc"
for package in "$site"/rpm/$suite/*/*.rpm; do
  [ -f "$package" ] || continue
  # rpm keeps an RSA header signature in one tag and every other kind, the
  # EdDSA of this key among them, in the tag once named for DSA.
  if [ "$(rpm -qp --nosignature --qf '%{RSAHEADER:pgpsig} %{DSAHEADER:pgpsig}' "$package" \
      2>/dev/null)" = "(none) (none)" ]; then
    # rpm of Debian and Ubuntu names gpg2, which those systems do not install.
    rpmsign --addsign --define "__gpg $(command -v gpg)" --define "_gpg_name $key" \
      --define "_gpg_sign_cmd_extra_args --batch --pinentry-mode error" \
      "$package" > "$work/rpmsign.log" 2>&1 ||
      { cat "$work/rpmsign.log" >&2; fail "rpmsign could not sign ${package##*/}"; }
    say "${package##*/} signed"
  fi
  rpmkeys --dbpath "$work/rpmdb" --checksig "$package" > "$work/checksig.log" 2>&1 ||
    { cat "$work/checksig.log" >&2; fail "${package##*/} does not carry a valid signature by $key"; }
  grep -qi 'signatures ok' "$work/checksig.log" ||
    { cat "$work/checksig.log" >&2; fail "${package##*/} is not signed by $key"; }
done

# apt: one Packages index per processor, from the pool, with every version in
# it; the Release file over them, signed both ways.
staging=$work/dists/$suite
for arch in amd64 arm64; do
  mkdir -p "$staging/main/binary-$arch"
  if [ -d "$pool" ]; then
    ( cd "$site" && apt-ftparchive --arch "$arch" packages pool/main ) \
      > "$staging/main/binary-$arch/Packages"
  else
    : > "$staging/main/binary-$arch/Packages"
  fi
  gzip -9n < "$staging/main/binary-$arch/Packages" > "$staging/main/binary-$arch/Packages.gz"
done
apt-ftparchive \
  -o APT::FTPArchive::Release::Origin="BSNSoft Solutions GmbH" \
  -o APT::FTPArchive::Release::Label="esj" \
  -o APT::FTPArchive::Release::Suite="$suite" \
  -o APT::FTPArchive::Release::Codename="$suite" \
  -o APT::FTPArchive::Release::Architectures="amd64 arm64" \
  -o APT::FTPArchive::Release::Components="main" \
  -o APT::FTPArchive::Release::Description="esj, the command line tool of EN16931 Semantic JSON: every release and release candidate" \
  -o APT::FTPArchive::Release::Acquire-By-Hash="yes" \
  release "$staging" > "$work/Release"
mv "$work/Release" "$staging/Release"
gpg --batch --yes --pinentry-mode error --local-user "$key" --digest-algo SHA512 \
  --armor --detach-sign --output "$staging/Release.gpg" "$staging/Release"
gpg --batch --yes --pinentry-mode error --local-user "$key" --digest-algo SHA512 \
  --clearsign --output "$staging/InRelease" "$staging/Release"
target=$site/dists/$suite
for arch in amd64 arm64; do
  mkdir -p "$target/main/binary-$arch/by-hash/SHA256"
  for index in Packages Packages.gz; do
    cp "$staging/main/binary-$arch/$index" "$target/main/binary-$arch/$index"
    cp "$staging/main/binary-$arch/$index" \
      "$target/main/binary-$arch/by-hash/SHA256/$(sha256_of "$staging/main/binary-$arch/$index")"
  done
done
# The signatures last, so that a reader of the site as it is being written
# finds no Release that names an index not yet there.
for file in Release Release.gpg InRelease; do
  cp "$staging/$file" "$target/$file"
done
say "apt $suite: $(grep -c '^Package: ' "$staging"/main/binary-*/Packages |
  awk -F: '{ n += $2 } END { print n + 0 }') packages"

# dnf: the metadata of every processor, written by createrepo_c over the
# packages present, and repomd.xml signed. Two older sets of metadata stay
# beside the new one, for a client whose cached repomd.xml still names them.
for arch in x86_64 aarch64; do
  directory=$site/rpm/$suite/$arch
  mkdir -p "$directory"
  createrepo_c --quiet --no-database --checksum sha256 --retain-old-md 2 "$directory" ||
    fail "createrepo_c failed for rpm/$suite/$arch"
  gpg --batch --yes --pinentry-mode error --local-user "$key" --digest-algo SHA512 \
    --armor --detach-sign --output "$directory/repodata/repomd.xml.asc" \
    "$directory/repodata/repomd.xml"
done
say "rpm $suite: $(find "$site/rpm/$suite" -name '*.rpm' | wc -l | tr -d ' ') packages"

# The .repo file a reader installs: one repository, signed metadata and signed
# packages required.
repo_file() {
  cat <<REPO
[$1]
name=$2
baseurl=$url/rpm/$3/\$basearch/
enabled=1
gpgcheck=1
repo_gpgcheck=1
gpgkey=$url/rpm/bsnsoft-esj.asc
metadata_expire=6h
REPO
}
repo_file esj "esj (EN16931 Semantic JSON)" "$suite" > "$site/rpm/esj.repo"

# The page at the root of the site: the template with the address and the
# fingerprint in place.
fingerprint=$(gpg --batch --with-colons --fingerprint "$key" | awk -F: '$1 == "fpr" { print $10; exit }')
sed -e "s|@URL@|$url|g" -e "s|@FINGERPRINT@|$fingerprint|g" "$here/repositories-index.html" \
  > "$site/index.html"
: > "$site/.nojekyll"

say "the site holds $(du -sk --exclude=.git "$site" | awk '{ printf "%.0f MB", $1 / 1024 }')"
