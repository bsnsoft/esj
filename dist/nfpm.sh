#!/bin/sh
# dist/nfpm.sh <directory>
#
# Fetches the one nfpm release the Linux packages are made with into
# <directory> and prints the path of its executable. nfpm writes a .deb and an
# .rpm from a list of files, with no packaging toolchain of either distribution,
# so one runner of either processor builds the packages of both. The release is
# pinned by version and SHA-256, as dist/graalvm.sh pins GraalVM: a package
# a release attests must not depend on which nfpm an installer happens to pick.
# A directory that already holds the release is reused.
set -eu

dir=${1:?usage: dist/nfpm.sh <directory>}
version=2.47.0                    # released 2026-06-20
case "$(uname -s)-$(uname -m)" in
  Linux-x86_64)
    platform=Linux_x86_64
    sum=0660ca602b2d2d2ae4781a06c692b3eeb9d437ffea05b831d76e41f4a3188783 ;;
  Linux-aarch64)
    platform=Linux_arm64
    sum=1c0f5f2999b9a974bfb04fdb0cc3306096de530ac5dbb25d739cc5f5219c919c ;;
  Darwin-arm64)
    platform=Darwin_arm64
    sum=e8c9d1d9ac218eeed479375143dc46b8d51a2b8dbba8e2f9f15ecc8faa2e404b ;;
  *)
    echo "nfpm.sh: no pinned nfpm release for $(uname -s)-$(uname -m)" >&2
    exit 1 ;;
esac
file=nfpm_${version}_${platform}.tar.gz
home=$dir/nfpm-$version

if [ ! -x "$home/nfpm" ]; then
  mkdir -p "$home"
  curl -fsSL -o "$dir/$file" \
    "https://github.com/goreleaser/nfpm/releases/download/v$version/$file"
  if command -v sha256sum >/dev/null 2>&1; then
    actual=$(sha256sum "$dir/$file" | cut -d' ' -f1)
  else
    actual=$(shasum -a 256 "$dir/$file" | cut -d' ' -f1)
  fi
  if [ "$actual" != "$sum" ]; then
    echo "nfpm.sh: $file has SHA-256 $actual, the pinned release has $sum" >&2
    exit 1
  fi
  tar -xzf "$dir/$file" -C "$home" nfpm
  rm -f "$dir/$file"
fi
reported=$("$home/nfpm" --version 2>&1 | sed -n 's/^GitVersion: *v\{0,1\}//p' | head -1)
if [ "$reported" != "$version" ]; then
  echo "nfpm.sh: $home/nfpm reports version ${reported:-<none>}, not $version" >&2
  exit 1
fi
echo "nfpm.sh: nfpm $version" >&2
echo "$home/nfpm"
