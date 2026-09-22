#!/bin/sh
# dist/graalvm.sh <directory>
#
# Fetches the one GraalVM Community build the native executables are made
# with into <directory> and prints its home. The build is pinned by release
# and SHA-256 rather than chosen by a major version: the 25.0.x line of the
# community builds produces a macOS image that cannot load libawt ("platform
# encoding not initialized" when the first PDF is rendered), the 25.3 line
# does, and a release must not depend on which of the two an installer
# happens to pick. The same script serves the CI, the release workflow and a
# packager without a GraalVM of their own (ESJ_GRAALVM_HOME=$(dist/graalvm.sh
# /some/dir)); a directory that already holds the build is reused.
set -eu

dir=${1:?usage: dist/graalvm.sh <directory>}
release=graal-25.3.4.1            # GraalVM Community 25 Innovation 3
version=25i3-25.0.4.1             # JDK 25.0.4.1, native-image 25.0.4.1
case "$(uname -s)-$(uname -m)" in
  Darwin-arm64)
    platform=macos-aarch64; home=Contents/Home
    sum=ebfab1d74420f355a459076162012d6835fa6068bd9d2f230f1fcaf7ee0dd923 ;;
  Linux-x86_64)
    platform=linux-x64; home=.
    sum=b2bc38d0c4141426eb44d0eefa3cc172c96faf92727d703b61541699128b6fc7 ;;
  Linux-aarch64)
    platform=linux-aarch64; home=.
    sum=7e8a3fbc2e4c28566107039a298cef690d86a37599a8e50536fc5a65b7f1bd56 ;;
  *)
    echo "graalvm.sh: no pinned GraalVM build for $(uname -s)-$(uname -m)" >&2
    exit 1 ;;
esac
file=graalvm-community-jdk-${version}_${platform}_bin.tar.gz

if [ ! -x "$dir/home/bin/native-image" ]; then
  mkdir -p "$dir"
  unpacked=$(find "$dir" -mindepth 1 -maxdepth 1 -type d -name 'graalvm-community-*' | head -1)
  if [ -z "$unpacked" ]; then
    curl -sSL -o "$dir/$file" \
      "https://github.com/graalvm/graalvm-ce-builds/releases/download/$release/$file"
    if command -v sha256sum >/dev/null 2>&1; then
      actual=$(sha256sum "$dir/$file" | cut -d' ' -f1)
    else
      actual=$(shasum -a 256 "$dir/$file" | cut -d' ' -f1)
    fi
    if [ "$actual" != "$sum" ]; then
      echo "graalvm.sh: $file has SHA-256 $actual, the pinned build has $sum" >&2
      exit 1
    fi
    tar -xzf "$dir/$file" -C "$dir"
    rm -f "$dir/$file"
    unpacked=$(find "$dir" -mindepth 1 -maxdepth 1 -type d -name 'graalvm-community-*' | head -1)
    if [ -z "$unpacked" ]; then
      echo "graalvm.sh: $file unpacked no graalvm-community-* directory" >&2
      exit 1
    fi
  fi
  ln -sfn "$unpacked/$home" "$dir/home"
  if [ ! -x "$dir/home/bin/native-image" ]; then
    echo "graalvm.sh: no native-image under $unpacked/$home" >&2
    exit 1
  fi
fi
"$dir/home/bin/native-image" --version 2>&1 | head -1 | sed 's/^/graalvm.sh: /' >&2
echo "$dir/home"
