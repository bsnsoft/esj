#!/bin/sh
#
# Packages the esj command line. One entry point, no list of features in it: the
# artefacts are built from what the self-contained jar carries, the runs they are
# exercised and trained with are the cases of dist/cases.sh, and the commands
# those cover are checked against the tool's own help. A command added to the
# tool later needs a case there and nothing here.
#
#   dist/package.sh [target...] [--out <directory>] [--keep-going]
#
# Targets, in the order they are built when several are named; --all builds
# every target this host can build:
#
#   jar             the self-contained jar (mvn -DskipTests package); implied by
#                   every other target, and rebuilt only when it is missing or
#                   older than the sources
#   runtime-image   a Java 25 runtime image with the jar and an ahead-of-time
#                   cache in it, for this operating system and processor
#   native          a native executable built with GraalVM, for this operating
#                   system and processor
#   linux-native    the same executable for linux/arm64, built in Docker
#   docker          the container image of dist/Dockerfile
#   zip             the release archives of whatever has been built, each
#                   with its checksum beside it
#   smoke           dist/smoke.sh against every artefact that has been built
#
# The reference build is JDK 25: $ESJ_JDK25_HOME or the JDK the script runs on.
# GraalVM for the native image is $ESJ_GRAALVM_HOME, or a GraalVM found beside
# it; nothing on this machine is switched over to it, and no default is changed.
#
# Copyright 2026 BSNSoft Solutions GmbH. Author: Christian Bürckert. Licensed under the Apache License, Version 2.0.

set -eu

# The header of this file is its manual page.
usage() {
  awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"
}

here=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
root=$(dirname -- "$here")
. "$here/cases.sh"
. "$here/checks.sh"
out=$root/dist/out
targets=
keep_going=

while [ $# -gt 0 ]; do
  case $1 in
    jar|runtime-image|native|linux-native|docker|zip|smoke) targets="$targets $1"; shift ;;
    --all) targets="jar runtime-image native linux-native docker zip smoke"; shift ;;
    --out) out=$2; shift 2 ;;
    --keep-going) keep_going=yes; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "package: unknown argument $1" >&2; exit 2 ;;
  esac
done
[ -n "$targets" ] || targets="jar runtime-image native zip smoke"

say() { printf '\n== %s\n' "$*"; }
fail() { echo "package: $*" >&2; [ -n "$keep_going" ] || exit 1; }

# The directory this script writes into is its own, and it says so in a marker
# file. Nothing outside a directory carrying that marker is ever removed.
mkdir -p "$out"
marker=$out/.esj-dist
[ -f "$marker" ] || echo "Artefacts of dist/package.sh. Everything here is generated." > "$marker"
replace() {
  target=$1
  case $target in
    "$out"/*) ;;
    *) fail "refusing to replace $target, which is not under $out"; return 1 ;;
  esac
  [ -f "$marker" ] || { fail "no marker in $out"; return 1; }
  [ ! -e "$target" ] || rm -rf "$target"
}

jdk=${ESJ_JDK25_HOME:-${JAVA_HOME:-}}
if [ -z "$jdk" ]; then
  jdk=$(dirname -- "$(dirname -- "$(command -v java)")")
fi
java=$jdk/bin/java
release=$("$java" -version 2>&1 | sed -n '1s/.*"\([0-9][0-9]*\).*/\1/p')
[ "$release" -ge 25 ] 2>/dev/null ||
  echo "package: the packaged command line ships on Java 25; this is $release" >&2

case $(uname -s) in
  Darwin) os=macos ;;
  Linux) os=linux ;;
  *) os=$(uname -s | tr 'A-Z' 'a-z') ;;
esac
case $(uname -m) in
  arm64|aarch64) arch=arm64 ;;
  x86_64|amd64) arch=x64 ;;
  *) arch=$(uname -m) ;;
esac

jar=$root/esj-cli/target/esj.jar

build_jar() {
  say "self-contained jar"
  newest=$(find "$root"/esj-*/src "$root"/pom.xml "$root"/esj-*/pom.xml -newer "$jar" 2>/dev/null | head -1 || true)
  if [ -f "$jar" ] && [ -z "$newest" ]; then
    echo "up to date: $jar"
  else
    ( cd "$root" && JAVA_HOME=$jdk mvn -B -DskipTests package -pl esj-cli -am )
  fi
  version=$("$java" -jar "$jar" --version | head -1 | awk '{print $2}')
  echo "esj $version, $(du -h "$jar" | awk '{print $1}')"
}

build_runtime_image() {
  say "runtime image (Java $release, $os-$arch)"
  image=$out/esj-$version-$os-$arch
  replace "$image"
  modules=$("$jdk"/bin/jdeps --ignore-missing-deps --multi-release "$release" \
      --print-module-deps "$jar")
  # The locale data the jar does not carry: a packaged tool whose messages from
  # the virtual machine differ from the same jar on a full JDK is a different
  # tool, and the smoke test would be right to say so.
  modules="$modules,jdk.localedata"
  echo "modules: $modules"
  "$jdk"/bin/jlink --add-modules "$modules" --strip-debug --no-header-files \
      --no-man-pages --compress zip-6 --output "$image"
  mkdir -p "$image/app"
  cp "$jar" "$image/app/esj.jar"
  say "ahead-of-time cache: the training run of dist/cases.sh"
  # Recorded from inside the image and with a relative jar path: the cache
  # stores the class path it was trained with, and an absolute one would name
  # the build machine in every archive.
  ( cd "$image" && bin/java -XX:+UseCompactObjectHeaders \
      -XX:AOTCacheOutput=app/esj.aot -jar app/esj.jar \
      $(esj_training_case "$root") ) > /dev/null 2>&1 ||
    fail "the training run did not complete"
  [ -f "$image/app/esj.aot" ] || fail "no ahead-of-time cache was written"
  esj_no_build_paths "$image" "$root"
  install_launcher "$image/bin/esj" image
  chmod +x "$image/bin/esj"
  cp "$root/LICENSE" "$root/NOTICE" "$image/"
  echo "$image ($(du -sh "$image" | awk '{print $1}'))"
}

# The launcher of an artefact. It carries the defaults of the process boundary
# (docs/deployment.md): a heap ceiling and an abort on heap exhaustion, both
# replaced whole by ESJ_JAVA_OPTS.
install_launcher() {
  path=$1
  kind=$2
  mkdir -p "$(dirname -- "$path")"
  if [ "$kind" = image ]; then
    cat > "$path" <<'LAUNCHER'
#!/bin/sh
# Runs esj on the Java runtime of this directory, with the ahead-of-time cache
# recorded for it. ESJ_JAVA_OPTS replaces the options of the process boundary;
# ESJ_AOT=0 leaves the cache out.
#
# The cache and the object header layout are one setting: a cache recorded with
# compact headers is refused, loudly, by a virtual machine running without them,
# so the two are passed together and after the caller's options, which is where
# the last word about a flag is.
set -eu
here=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
home=$(dirname -- "$here")
aot=
if [ "${ESJ_AOT-1}" != 0 ] && [ -f "$home/app/esj.aot" ]; then
  aot="-XX:+UseCompactObjectHeaders -XX:AOTCache=$home/app/esj.aot"
fi
exec "$home/bin/java" \
  ${ESJ_JAVA_OPTS--Xms32m -Xmx512m -XX:+ExitOnOutOfMemoryError} \
  $aot -jar "$home/app/esj.jar" "$@"
LAUNCHER
  else
    cat > "$path" <<'LAUNCHER'
#!/bin/sh
# Runs esj from the jar beside this script on the Java runtime of this machine,
# which has to be 17 or newer. ESJ_JAVA_OPTS replaces the options.
set -eu
here=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
exec java ${ESJ_JAVA_OPTS--Xms32m -Xmx512m -XX:+ExitOnOutOfMemoryError} \
  -jar "$(dirname -- "$here")/lib/esj.jar" "$@"
LAUNCHER
  fi
}

# Where native-image is: named, or the Java this script runs on if it is a
# GraalVM, or one installed beside it. No default of the machine is changed.
graalvm() {
  if [ -n "${ESJ_GRAALVM_HOME-}" ]; then echo "$ESJ_GRAALVM_HOME"; return 0; fi
  if [ -x "${GRAALVM_HOME-}/bin/native-image" ]; then echo "$GRAALVM_HOME"; return 0; fi
  if [ -x "$jdk/bin/native-image" ]; then echo "$jdk"; return 0; fi
  for candidate in "$(dirname -- "$jdk")"/*graal*; do
    [ -x "$candidate/bin/native-image" ] && { echo "$candidate"; return 0; }
  done
  return 1
}

build_native() {
  say "native executable ($os-$arch)"
  graal=$(graalvm) || { fail "no GraalVM with native-image; set ESJ_GRAALVM_HOME"; return 0; }
  native=$out/esj-$version-native-$os-$arch
  replace "$native"
  mkdir -p "$native"
  ESJ_GRAALVM_HOME=$graal sh "$here/native-image.sh" "$jar" "$native"
  cp "$root/LICENSE" "$root/NOTICE" "$native/"
  # The size printed is the whole artefact, as it is for the runtime image: the
  # executable is kept together with the libraries and the licences beside it.
  echo "$native ($(du -sh "$native" | awk '{print $1}'))"
}

build_linux_native() {
  say "native executable (linux/arm64, in Docker)"
  command -v docker >/dev/null || { fail "no docker on this host"; return 0; }
  native=$out/esj-$version-native-linux-arm64
  replace "$native"
  mkdir -p "$native"
  ( cd "$root" && docker build --platform linux/arm64 -f dist/Dockerfile.native \
      --target artefact --output "type=local,dest=$native" . ) ||
    { fail "the Linux native build did not complete"; return 0; }
  # The build inside the container has paths of its own, which checks.sh leaves
  # alone; what it may not carry out is a path of this host.
  esj_no_build_paths "$native" "$root"
  echo "$native ($(du -sh "$native" | awk '{print $1}'))"
}

build_docker() {
  say "container image"
  command -v docker >/dev/null || { fail "no docker on this host"; return 0; }
  # The version and the build date of the base image would otherwise be read as
  # this image's own, so both are set here; the revision when there is one, with
  # the marker that says the tree it was built from carried changes that commit
  # does not have.
  revision=$(esj_revision "$root")
  ( cd "$root" && docker build --platform linux/arm64 -f dist/Dockerfile \
      --build-arg "ESJ_VERSION=$version" \
      --build-arg "ESJ_CREATED=$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
      --build-arg "ESJ_REVISION=$revision" \
      -t "esj:$version" -t esj:latest . ) || { fail "the image did not build"; return 0; }
  docker image inspect "esj:$version" --format 'esj:{{index .RepoTags 0}} {{.Size}} bytes'
}

# The checksum of a release archive, beside it, named relative to it so that the
# usual tools check it from the directory it lies in.
checksum() {
  file=$1
  ( cd "$(dirname -- "$file")" &&
    if command -v sha256sum > /dev/null; then sha256sum "$(basename -- "$file")"
    else shasum -a 256 "$(basename -- "$file")"; fi ) > "$file.sha256"
}

build_zip() {
  say "release archives"
  archive=$out/esj-$version.zip
  staging=$out/staging-esj-$version
  replace "$archive"
  replace "$staging"
  mkdir -p "$staging/esj-$version/lib" "$staging/esj-$version/bin" "$staging/esj-$version/docs"
  cp "$jar" "$staging/esj-$version/lib/esj.jar"
  install_launcher "$staging/esj-$version/bin/esj" jar
  chmod +x "$staging/esj-$version/bin/esj"
  cp "$root/LICENSE" "$root/NOTICE" "$root/README.md" "$staging/esj-$version/"
  for page in install.md cli.md deployment.md validation.md pdf-input.md rendering.md; do
    cp "$root/docs/$page" "$staging/esj-$version/docs/$page"
  done
  ( cd "$staging" && zip -qr "$archive" "esj-$version" )
  rm -rf "$staging"
  checksum "$archive"
  echo "$archive ($(du -h "$archive" | awk '{print $1}'))"
  for artefact in "$out"/esj-"$version"-*; do
    [ -d "$artefact" ] || continue
    replace "$artefact.zip"
    # The metadata the native build recorded stays on disk, where it says what the
    # executable was built from; it is not part of what a reader downloads.
    ( cd "$out" && zip -qr "$(basename "$artefact").zip" "$(basename "$artefact")" \
        -x "$(basename "$artefact")/metadata/*" )
    checksum "$artefact.zip"
    echo "$artefact.zip ($(du -h "$artefact.zip" | awk '{print $1}'))"
  done
}

run_smoke() {
  say "smoke test"
  # --small-heap is the same artefact with a ceiling no run fits in, in the form
  # that artefact takes one: it checks that the defaults of the process boundary
  # are the artefact's own and not the caller's (docs/deployment.md).
  image=$out/esj-$version-$os-$arch
  native=$out/esj-$version-native-$os-$arch
  # Each run's last lines are shown and its exit status decides: a pipe into
  # tail once swallowed a "22 of 94 cases differ" and let a broken macOS
  # executable ship as 0.9.0.
  if [ -x "$image/bin/esj" ]; then
    echo "-- $image/bin/esj"
    smoke_run "$image/bin/esj" --small-heap \
      "$image/bin/java -Xmx16m -XX:+ExitOnOutOfMemoryError -jar $image/app/esj.jar"
  fi
  if [ -x "$native/esj" ]; then
    echo "-- $native/esj"
    smoke_run "$native/esj" --small-heap "$native/esj -Xmx16m"
  fi
  # The container image, where the repository is its working directory: the cases
  # that write a file write one inside it, as the account that owns it. Its
  # ceiling is ESJ_MAX_HEAP, which cannot go below the -Xms the entry point
  # passes, so the boundary check is not run against it.
  if command -v docker >/dev/null && docker image inspect "esj:$version" >/dev/null 2>&1; then
    echo "-- esj:$version"
    smoke_run \
      "docker run --rm -i --user $(id -u):$(id -g) -v $root:/work -w /work esj:$version"
  fi
}

# Runs dist/smoke.sh with the given arguments, shows its last lines and fails
# the target when the artefact differs from the jar.
smoke_run() {
  smoke_log=$(mktemp)
  if sh "$here/smoke.sh" "$@" >"$smoke_log" 2>&1; then
    tail -4 "$smoke_log"
  else
    tail -8 "$smoke_log"
    rm -f "$smoke_log"
    fail "smoke test failed for $1"
    return 1
  fi
  rm -f "$smoke_log"
}

version=unknown
build_jar
for target in $targets; do
  case $target in
    jar) ;;
    runtime-image) build_runtime_image ;;
    native) build_native ;;
    linux-native) build_linux_native ;;
    docker) build_docker ;;
    zip) build_zip ;;
    smoke) run_smoke ;;
  esac
done
say "done: $out"
