#!/usr/bin/env bash
#
# Creates a new project from the modular-monolith blueprint.
#
#   bash <(curl -Ls https://raw.githubusercontent.com/Yacn9/springboot-blueprint/main/install.sh) [project-name]
#
# Run it with process substitution as above, not `curl ... | bash`: the script
# must not take over standard input, which the project-name prompt reads.
#
# It finds Java 17+, downloads the latest Blueprint CLI release (checked against
# the SHA-256 published beside it, cached in ~/.cache/blueprint) and runs
# `blueprint new` with your arguments in the current directory.
#
# Options and environment:
#   --from-source          build the CLI from the repository instead of downloading a release
#   BLUEPRINT_VERSION      release to use, such as 0.1.0 (default: the latest)
#   BLUEPRINT_HOME         cache directory (default: ~/.cache/blueprint)
#   BLUEPRINT_RELEASE_URL  a directory URL holding blueprint-cli.jar and blueprint-cli.jar.sha256 — a mirror
#   BLUEPRINT_SOURCE_URL   the source tarball for --from-source
#   JAVA_HOME              the Java to use (default: java on the PATH)
#
# Anything else is passed to `blueprint new`; see its --help.

set -euo pipefail

REPO="Yacn9/springboot-blueprint"
MIN_JAVA=17
JAR_NAME="blueprint-cli.jar"

say() { printf '==> %s\n' "$*" >&2; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

# --- Java -------------------------------------------------------------------

JAVA=""

find_java() {
  local candidate
  if [ -n "${JAVA_HOME:-}" ]; then
    for candidate in "$JAVA_HOME/bin/java" "$JAVA_HOME/bin/java.exe"; do
      if [ -x "$candidate" ]; then
        JAVA="$candidate"
        return
      fi
    done
  fi
  JAVA="$(command -v java || true)"
}

require_java() {
  local major minor
  find_java
  if [ -z "$JAVA" ]; then
    die "Java $MIN_JAVA or newer is required, and none was found.
Install one, for example:
  macOS    brew install openjdk@21
  Debian   sudo apt install openjdk-21-jre-headless
  any OS   https://adoptium.net, or SDKMAN: sdk install java"
  fi
  # `java -version` prints  openjdk version "21.0.2" ...  or, before Java 9,  "1.8.0_292".
  read -r major minor <<< "$("$JAVA" -version 2>&1 | sed -n 's/^[^"]*"\([0-9]*\)\.\{0,1\}\([0-9]*\).*/\1 \2/p' | head -n1)"
  if [ "${major:-}" = "1" ]; then
    major="${minor:-}"
  fi
  case "${major:-}" in
    ''|*[!0-9]*) die "Could not tell which Java version $JAVA is." ;;
  esac
  if [ "$major" -lt "$MIN_JAVA" ]; then
    die "Java $MIN_JAVA or newer is required; $JAVA is Java $major. Point JAVA_HOME at a newer one."
  fi
}

# --- Download ---------------------------------------------------------------

# fetch <url> [<file>] — writes to the file, or to standard output without one.
fetch() {
  if command -v curl >/dev/null 2>&1; then
    if [ $# -ge 2 ]; then curl -fL --progress-bar "$1" -o "$2"; else curl -fsSL "$1"; fi
  elif command -v wget >/dev/null 2>&1; then
    if [ $# -ge 2 ]; then wget -q -O "$2" "$1"; else wget -qO- "$1"; fi
  else
    die "curl or wget is required."
  fi
}

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | cut -d' ' -f1
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | cut -d' ' -f1
  elif command -v openssl >/dev/null 2>&1; then
    openssl dgst -sha256 "$1" | sed 's/^.*= //'
  else
    die "sha256sum, shasum or openssl is required to verify the download."
  fi
}

release_base() {
  if [ -n "${BLUEPRINT_RELEASE_URL:-}" ]; then
    printf '%s' "${BLUEPRINT_RELEASE_URL%/}"
  elif [ "$version" = "latest" ]; then
    printf 'https://github.com/%s/releases/latest/download' "$REPO"
  else
    printf 'https://github.com/%s/releases/download/v%s' "$REPO" "${version#v}"
  fi
}

# Sets JAR to the CLI to run: downloaded and verified, from the cache, or — when the
# release cannot be reached — the newest one cached.
obtain_release_jar() {
  local base expected actual cached
  base="$(release_base)"
  mkdir -p "$CACHE_DIR"

  if expected="$(fetch "$base/$JAR_NAME.sha256" 2>/dev/null | awk '{print $1}')" && [ -n "$expected" ]; then
    printf '%s' "$expected" | grep -Eq '^[0-9a-f]{64}$' || die "The checksum published at $base is not a SHA-256."
    JAR="$CACHE_DIR/blueprint-cli-${expected:0:12}.jar"
    if [ -f "$JAR" ]; then
      say "Using the cached Blueprint CLI (${expected:0:12})."
      return
    fi
    say "Downloading the Blueprint CLI from $base ..."
    fetch "$base/$JAR_NAME" "$JAR.part"
    actual="$(sha256_of "$JAR.part")"
    if [ "$actual" != "$expected" ]; then
      rm -f "$JAR.part"
      die "Checksum mismatch for the downloaded CLI (expected $expected, got $actual). Nothing was run."
    fi
    mv "$JAR.part" "$JAR"
  else
    cached="$(ls -t "$CACHE_DIR"/blueprint-cli-*.jar 2>/dev/null | head -n1 || true)"
    if [ -z "$cached" ]; then
      die "Could not reach $base and no CLI is cached.
Check the network, point BLUEPRINT_RELEASE_URL at a mirror, or build from the source with --from-source."
    fi
    say "Could not reach $base; using the cached Blueprint CLI."
    JAR="$cached"
  fi
}

# Builds the CLI from the repository's source and runs it. Slower, and needs only a JDK.
run_from_source() {
  local ref url src
  command -v tar >/dev/null 2>&1 || die "tar is required for --from-source."
  if [ "$version" = "latest" ]; then ref="heads/main"; else ref="tags/v${version#v}"; fi
  url="${BLUEPRINT_SOURCE_URL:-https://github.com/$REPO/archive/refs/$ref.tar.gz}"
  WORK_DIR="$(mktemp -d)"
  trap 'rm -rf "$WORK_DIR"' EXIT   # runs at exit, after this function's locals are gone

  say "Fetching the source from $url ..."
  fetch "$url" | tar -xz -C "$WORK_DIR"
  src="$(find "$WORK_DIR" -mindepth 1 -maxdepth 1 -type d | head -n1)"
  [ -f "$src/gradlew" ] || die "The source archive holds no Gradle wrapper."

  say "Building the Blueprint CLI (the first run downloads Gradle; this takes a minute) ..."
  (cd "$src" && sh ./gradlew -p cli jar --quiet --console=plain >&2)
  JAR="$(ls "$src"/cli/build/libs/blueprint-cli-*.jar | head -n1)"

  "$JAVA" -jar "$JAR" new ${args[@]+"${args[@]}"}
}

# --- Main -------------------------------------------------------------------

version="${BLUEPRINT_VERSION:-latest}"
CACHE_DIR="${BLUEPRINT_HOME:-${XDG_CACHE_HOME:-$HOME/.cache}/blueprint}"
JAR=""
WORK_DIR=""

from_source=false
args=()
for arg in "$@"; do
  if [ "$arg" = "--from-source" ]; then from_source=true; else args+=("$arg"); fi
done

require_java

if $from_source; then
  run_from_source
else
  obtain_release_jar
  exec "$JAVA" -jar "$JAR" new ${args[@]+"${args[@]}"}
fi
