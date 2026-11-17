#!/bin/sh
# dist/graalvm.sh <directory>
#
# Fetches the one GraalVM Community build the native executables are made
# with into <directory> and prints its home. The build is pinned by release
# and SHA-256 rather than chosen by a major version: the 25.0.x line of the
# community builds produces a macOS image that cannot load libawt ("platform
# encoding not initialized" when the first PDF is rendered), the innovation
# line (25.3 and later) does, and a release must not depend on which of the
# two an installer happens to pick. The pin follows the quarterly security
# update of the JDK, because the executable carries the class library of the
# build it was made with. The same script serves the CI, the release workflow
# and a packager without a GraalVM of their own
# (ESJ_GRAALVM_HOME=$(dist/graalvm.sh /some/dir)); a directory that already
# holds the build is reused.
set -eu

dir=${1:?usage: dist/graalvm.sh <directory>}
release=graal-25.5.5.0.3          # GraalVM Community 25 Innovation 5
version=25i5-25.0.5.0.3           # JDK 25.0.5.0.3 (October 2026 update), native-image 25.0.5.0.3
case "$(uname -s)-$(uname -m)" in
  Darwin-arm64)
    platform=macos-aarch64; home=Contents/Home
    sum=54369d4a5931a5a2101c7c52444a4e7e6647a3a4e041335053cda6fd22295e0c ;;
  Linux-x86_64)
    platform=linux-x64; home=.
    sum=c6438dee6c3ade2220d40b7ccdcad24b512d30e233e61bb4fc70519522de34ef ;;
  Linux-aarch64)
    platform=linux-aarch64; home=.
    sum=41741c0650ae7564a78335f9e4fc353a902e6ff1d4140f2e4fabd1c55d5cce66 ;;
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
