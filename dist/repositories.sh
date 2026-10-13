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
#   dist/repositories.sh <packages-dir> <site-dir> <stable|testing> <gpg-key>
#   dist/repositories.sh --unlock <gpg-key>          (the passphrase on stdin)
#
#   <packages-dir>  the .deb and .rpm files of dist/packages.sh to add; it may
#                   hold none, and the run then only regenerates the indices,
#                   which is how a pruned repository is put right
#   <site-dir>      the tree the site is served from
#   <suite>         where the packages go: stable for a release, testing for a
#                   pre-release (dist/packages.sh --versions names it); a
#                   package whose version belongs to the other suite is refused
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
# (https://apt.bsnsoft.de by default), which the .repo files and the index page
# name:
#
#   pool/main/e/esj/*.deb              every version ever added, both suites
#   dists/<suite>/Release, Release.gpg, InRelease
#   dists/<suite>/main/binary-{amd64,arm64}/Packages, Packages.gz, by-hash/
#   bsnsoft-esj.gpg, bsnsoft-esj.asc   the public key, binary and armoured
#   rpm/<suite>/{x86_64,aarch64}/*.rpm each signed, with repodata/ and
#                                      repodata/repomd.xml.asc
#   rpm/esj.repo, rpm/esj-testing.repo, rpm/bsnsoft-esj.asc
#   index.html, .nojekyll
#
# stable carries the releases and testing the pre-releases; a reader who wants
# the release candidates adds testing beside stable, and apt and dnf take the
# newer of the two. Both suites are written on every run, the one without
# packages with empty indices, so that either can be added at any time.
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
# is never removed here; the old ones are a few kilobytes each.
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

[ $# -eq 4 ] || { echo "usage: dist/repositories.sh <packages-dir> <site-dir> <stable|testing> <gpg-key>" >&2; exit 2; }
packages=$1
site=$2
suite=$3
key=$4
url=${ESJ_REPOSITORY_URL:-https://apt.bsnsoft.de}
url=${url%/}
case $suite in stable|testing) ;; *) fail "the suite is stable or testing, not $suite" ;; esac
[ -d "$packages" ] || fail "$packages is not a directory"
[ -d "$site" ] || fail "$site is not a directory"
site=$(CDPATH='' cd -- "$site" && pwd)
for tool in apt-ftparchive dpkg-deb createrepo_c rpm rpmsign rpmkeys gpg gzip; do
  command -v "$tool" > /dev/null || fail "needs $tool"
done
probe "$key" || { cat "$work/probe.log" >&2; fail "$key cannot sign without a prompt; run --unlock first"; }

# The suite a package version belongs to: a pre-release carries ~.
suite_of_version() {
  case $1 in *~*) echo testing ;; *) echo stable ;; esac
}

sha256_of() { sha256sum "$1" | awk '{print $1}'; }

# The packages to add, every one of them checked before anything in the site
# changes: a list of source and target, then the copies.
pool=$site/pool/main/e/esj
: > "$work/plan"
for deb in "$packages"/*.deb; do
  [ -f "$deb" ] || continue
  name=$(dpkg-deb --field "$deb" Package)
  version=$(dpkg-deb --field "$deb" Version)
  [ "$name" = esj ] || fail "$deb is the package $name, not esj"
  [ "$(suite_of_version "$version")" = "$suite" ] ||
    fail "$deb carries version $version, which does not go to $suite"
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
  [ "$(suite_of_version "$version")" = "$suite" ] ||
    fail "$package carries version $version, which does not go to $suite"
  target=$site/rpm/$suite/$arch/${package##*/}
  if [ -e "$target" ]; then
    [ "$(rpm -qp --nosignature --qf '%{SHA256HEADER}' "$target")" = "$header" ] ||
      fail "${package##*/} is published with another header; a published package is never replaced"
    say "${package##*/} is there already"
  else
    printf '%s\n%s\n' "$package" "$target" >> "$work/plan"
  fi
done
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
for package in "$site"/rpm/*/*/*.rpm; do
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

# apt: one Packages index per suite and processor, from the pool, with the
# versions of that suite; the Release file over them, signed both ways.
for each_suite in stable testing; do
  staging=$work/dists/$each_suite
  for arch in amd64 arm64; do
    mkdir -p "$staging/main/binary-$arch"
    if [ -d "$pool" ]; then
      ( cd "$site" && apt-ftparchive --arch "$arch" packages pool/main ) > "$work/all"
    else
      : > "$work/all"
    fi
    # Stanzas are separated by blank lines; a stanza is kept when its version
    # belongs to this suite.
    awk -v suite="$each_suite" '
      BEGIN { RS = ""; ORS = "\n\n" }
      {
        version = ""
        n = split($0, line, "\n")
        for (i = 1; i <= n; i++) if (line[i] ~ /^Version: /) version = substr(line[i], 10)
        if ((index(version, "~") > 0) == (suite == "testing")) print
      }' "$work/all" > "$staging/main/binary-$arch/Packages"
    gzip -9n < "$staging/main/binary-$arch/Packages" > "$staging/main/binary-$arch/Packages.gz"
  done
  case $each_suite in
    stable) description="esj, the command line tool of EN16931 Semantic JSON: releases" ;;
    testing) description="esj, the command line tool of EN16931 Semantic JSON: pre-releases" ;;
  esac
  apt-ftparchive \
    -o APT::FTPArchive::Release::Origin="BSNSoft Solutions GmbH" \
    -o APT::FTPArchive::Release::Label="esj" \
    -o APT::FTPArchive::Release::Suite="$each_suite" \
    -o APT::FTPArchive::Release::Codename="$each_suite" \
    -o APT::FTPArchive::Release::Architectures="amd64 arm64" \
    -o APT::FTPArchive::Release::Components="main" \
    -o APT::FTPArchive::Release::Description="$description" \
    -o APT::FTPArchive::Release::Acquire-By-Hash="yes" \
    release "$staging" > "$work/Release"
  mv "$work/Release" "$staging/Release"
  gpg --batch --yes --pinentry-mode error --local-user "$key" --digest-algo SHA512 \
    --armor --detach-sign --output "$staging/Release.gpg" "$staging/Release"
  gpg --batch --yes --pinentry-mode error --local-user "$key" --digest-algo SHA512 \
    --clearsign --output "$staging/InRelease" "$staging/Release"
  target=$site/dists/$each_suite
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
  say "apt $each_suite: $(grep -c '^Package: ' "$staging"/main/binary-*/Packages |
    awk -F: '{ n += $2 } END { print n + 0 }') packages"
done

# dnf: the metadata of every suite and processor, written by createrepo_c over
# the packages present, and repomd.xml signed. Two older sets of metadata stay
# beside the new one, for a client whose cached repomd.xml still names them.
for each_suite in stable testing; do
  for arch in x86_64 aarch64; do
    directory=$site/rpm/$each_suite/$arch
    mkdir -p "$directory"
    createrepo_c --quiet --no-database --checksum sha256 --retain-old-md 2 "$directory" ||
      fail "createrepo_c failed for rpm/$each_suite/$arch"
    gpg --batch --yes --pinentry-mode error --local-user "$key" --digest-algo SHA512 \
      --armor --detach-sign --output "$directory/repodata/repomd.xml.asc" \
      "$directory/repodata/repomd.xml"
  done
  say "rpm $each_suite: $(find "$site/rpm/$each_suite" -name '*.rpm' | wc -l | tr -d ' ') packages"
done

# The .repo files a reader installs: one repository each, signed metadata and
# signed packages required.
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
repo_file esj "esj (EN16931 Semantic JSON), releases" stable > "$site/rpm/esj.repo"
repo_file esj-testing "esj (EN16931 Semantic JSON), pre-releases" testing > "$site/rpm/esj-testing.repo"

# The page at the root of the site: the template with the address and the
# fingerprint in place.
fingerprint=$(gpg --batch --with-colons --fingerprint "$key" | awk -F: '$1 == "fpr" { print $10; exit }')
sed -e "s|@URL@|$url|g" -e "s|@FINGERPRINT@|$fingerprint|g" "$here/repositories-index.html" \
  > "$site/index.html"
: > "$site/.nojekyll"

say "the site holds $(du -sk --exclude=.git "$site" | awk '{ printf "%.0f MB", $1 / 1024 }')"
