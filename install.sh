#!/bin/sh
#
# Creates a new project from the modular-monolith blueprint:
#
#   curl -fsSL https://raw.githubusercontent.com/Yacn9/springboot-blueprint/main/install.sh | sh
#   curl -fsSL https://raw.githubusercontent.com/Yacn9/springboot-blueprint/main/install.sh | sh -s -- my-shop
#
# It installs JBang (https://jbang.dev, into ~/.jbang) when it is missing. JBang
# downloads a JDK 17+ if none is installed, fetches the latest Blueprint CLI
# release and runs `blueprint new` in the current directory, which asks for the
# project name. Arguments go to `blueprint new`; see its --help.
#
# Environment:
#   BLUEPRINT_CLI  what JBang runs (default: the `blueprint` alias in this repository's
#                  jbang-catalog.json, the latest release). A jar URL pins a version:
#                  https://github.com/Yacn9/springboot-blueprint/releases/download/v0.0.1/blueprint-cli.jar

set -eu

CLI="${BLUEPRINT_CLI:-blueprint@Yacn9/springboot-blueprint}"

say() { printf '==> %s\n' "$*" >&2; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

if ! command -v jbang >/dev/null 2>&1; then
  PATH="$HOME/.jbang/bin:$PATH"
  if ! command -v jbang >/dev/null 2>&1; then
    command -v curl >/dev/null 2>&1 || die "curl is required to install JBang."
    command -v bash >/dev/null 2>&1 || die "bash is required to install JBang."
    say "Installing JBang into ~/.jbang ..."
    curl -fsSL https://sh.jbang.dev | bash -s - app setup >&2 \
      || die "Could not install JBang. Install it yourself (https://www.jbang.dev/download) and run this again."
  fi
fi

# JBang asks (in a pop-up on desktops) before running anything from an unknown URL.
# This script already comes from the repository, so trusting it grants nothing new.
jbang trust add https://github.com/Yacn9/springboot-blueprint/ >/dev/null 2>&1 || true

# --fresh: always the latest release, as `npm create …@latest` would.
# Piped into sh, standard input is this script, so the name prompt reads the terminal instead.
if [ ! -t 0 ] && (: </dev/tty) 2>/dev/null; then
  exec jbang --fresh --java 17+ "$CLI" new "$@" </dev/tty
fi
exec jbang --fresh --java 17+ "$CLI" new "$@"
