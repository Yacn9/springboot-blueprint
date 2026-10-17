# Blueprint CLI

Creates a new project from this blueprint. It asks for the project name, then
the Java, Spring Boot and Gradle versions, checks your tools, and writes a
complete, building project.

```
$ curl -fsSL https://raw.githubusercontent.com/Yacn9/springboot-blueprint/main/install.sh | sh
==> Installing JBang into ~/.jbang ...
? Project name (for example my-shop): order-hub
? Java version (LTS) (25 / 21) [25]:
? Spring Boot version (4.2.0 / 4.1.1) [4.2.0]:
? Gradle version: 9.8.1

Checking tools:
  java    not found; ./gradlew needs Java 17+
? Install Java 25 (LTS) now? (jbang jdk install 25 && …) [Y/n]: y
  git     2.43.0
  gradle  installed (projects run their own ./gradlew)

Created order-hub in /work/order-hub (111 files, base package com.orderhub).
```

Nothing to clone and nothing to install by hand — not even Java. Arguments go
to `blueprint new`: add the name to skip the prompt (`curl … | sh -s -- order-hub`).
The project is created in a new directory named after it, inside the current
directory — or inside `--parent-dir <dir>` (short form `-d`), so `-d /work`
gives `/work/order-hub`.

Already have [JBang](https://www.jbang.dev)? Skip the script:

```bash
jbang blueprint@Yacn9/springboot-blueprint new
```

## The questions

| Question | Choices | Default (Enter) |
| --- | --- | --- |
| Project name | see below | — |
| Java version | the LTS releases the template compiles on: 25, 21 | the newest |
| Spring Boot version | the template's and newer releases of its major line (4.x), up to 3, from Maven Central | the newest |
| Gradle version | the template's and the current release, if it is of the same major line (9.x) | the newest |
| Database | `postgresql` or `oracle` (also `--database <id>`) | postgresql |
| Cache | `none`, `memory` (Caffeine), `redis` or `memcached` (also `--cache <id>`) | none |

Hibernate, Spring Modulith's runtime and the other managed libraries follow
from the Spring Boot version (its BOM). A question with one choice is shown,
not asked. Offline — or with `BLUEPRINT_OFFLINE=1` — only the template's own
versions are offered. A newer major (Spring Boot 5, Gradle 10) is never
offered: the template is not built for it. The choices are written to
`gradle/libs.versions.toml` (`java`, `spring-boot`) and the wrapper
(`distributionUrl` and its `distributionSha256Sum`, fetched from Gradle).

Then it checks the tools on the machine and offers to install what is
missing — nothing is installed without a yes, and nothing is required to go on:

| Tool | Needed | Installed with |
| --- | --- | --- |
| Java 17+ (`JAVA_HOME`, else the PATH) | to start `./gradlew`; the project's own JDK comes through the Gradle toolchain | JBang: `jbang jdk install <LTS>`, `jbang jdk default`, `jbang app setup --java` |
| git 2+ | recommended | the package manager: brew, apt-get, dnf, pacman, zypper (with sudo) or winget; `xcode-select --install` on a Mac without brew |
| Gradle | optional — projects run their own `./gradlew` | SDKMAN (installed first if missing): `sdk install gradle <version>`; not on Windows |

Open a new terminal after an install so the PATH change applies.

## What the name becomes

| You type | Becomes |
| --- | --- |
| `order-hub` | Gradle project `order-hub` · base package `com.orderhub` · application class `OrderHubApplication` · convention plugins `orderhub.java-conventions`, … · `orderhub-platform.yml` · database `orderhub` in `compose.yaml` |

Names are lower-case letters, digits and single hyphens, starting with a
letter, up to 40 characters. Case is ignored and spaces or underscores become
hyphens: `Order Hub` and `order_hub` both give `order-hub`. On a command line a
name with a space needs quotes (`blueprint new "Order Hub"`); at the prompt it
does not. Words that cannot be a package (`class`, `new`, `java`, …) are
refused with an explanation.

The generated project is complete on its own: its own README, the Gradle
wrapper, the architecture tests, `docs/architecture.md`. It contains nothing of
the CLI (a test checks that).

## Exit codes

| Code | Meaning |
| --- | --- |
| `0` | The project was created. |
| `1` | The command line was fine but the project could not be created — the directory is in use, the template is missing, the disk failed. |
| `2` | The command line was not: an invalid name, an unknown option, no name and no input to ask for one. |

Failures print `error: <reason>` on standard error — never a stack trace — and
usage errors add a pointer to `--help`.

## The install script

[`install.sh`](../install.sh) is POSIX `sh` for macOS, Linux and Git Bash on Windows. It:

1. installs [JBang](https://www.jbang.dev) into `~/.jbang` when `jbang` is not
   found (its installer also adds it to your shell's PATH);
2. trusts `https://github.com/Yacn9/springboot-blueprint/` in JBang, which would
   otherwise ask first — the script already comes from there;
3. runs `jbang --fresh blueprint@Yacn9/springboot-blueprint new` with your
   arguments. The alias lives in [`jbang-catalog.json`](../jbang-catalog.json)
   and points at the latest release's `blueprint-cli.jar`; JBang downloads a
   JDK 17+ first if none is installed, and `--fresh` makes every run use the
   newest release;
4. when piped (`curl … | sh`), reads the name prompt from the terminal, since
   standard input is the script itself.

| Setting | Effect |
| --- | --- |
| `BLUEPRINT_CLI=<jar url or path>` | Run that jar instead of the latest release — pins a version: `https://github.com/Yacn9/springboot-blueprint/releases/download/v0.0.1/blueprint-cli.jar`. |

From a clone, the same CLI runs without the script:

```bash
./gradlew -p cli jar
java -jar cli/build/libs/blueprint-cli-0.0.2.jar new
```

The generated project needs Docker for its tests and `bootRun`; Gradle
provisions JDK 25 itself.

## Releasing

1. Set `version` in `cli/build.gradle.kts` (for example `0.0.2`) and commit.
2. Tag that commit `v0.0.2` and push the tag.

[`.github/workflows/release.yml`](../.github/workflows/release.yml) refuses a tag
that does not match the version, builds and tests the CLI, and publishes
`blueprint-cli.jar` with `blueprint-cli.jar.sha256` on a GitHub release. The
install script picks the latest one up on its next run.

## How it works

`./gradlew -p cli jar` packs the repository itself into `template/template.zip`
inside the jar (`templateArchive` task). It includes the files git would show —
tracked, or untracked and not ignored — so a new `.gitignore` entry keeps its
files out of every generated project; without git it falls back to a fixed
list. `.git`, tool state, build output and the blueprint's own distribution —
`cli/`, `install.sh`, `jbang-catalog.json`, `.github/` — are never included.

Generating a project unpacks that archive. `ProjectSpec` holds the answers and
the role each plays (`artifact`, `basePackage`, `classPrefix`, `shortName`);
`RewriteRules` turns the blueprint's names into the project's, in file paths and
in file contents, in **one pass**, so a name that contains `blueprint` is not
rewritten twice:

1. **Structural names** (everywhere, Markdown included): the base package
   `com.acme.blueprint` (and as a path), the project name,
   `BlueprintApplication`, `blueprint-platform`, and plugin ids such as
   `blueprint.module-api`.
2. **Every remaining `blueprint`** (everything except Markdown, where it is
   ordinary prose).

A database other than PostgreSQL is an **overlay**: the files under
`overlays/<database>/` replace the template's at the same paths, or join them
(Oracle's privilege script), and `Database` rewrites the catalog's `database-*`
entries. No `overlays/` directory reaches any generated project. The repository's
own build never compiles an overlay — `.github/workflows/ci.yml` generates a
project per database and runs its full build, and `TemplateIntegrationTest`
checks that an Oracle project names PostgreSQL nowhere.

Whatever is not UTF-8 text — the Gradle wrapper jar — is copied byte for byte.
`README.md` is replaced by the generated project's own. The template is always
the repository as it is, so it cannot drift from what the repository's build
tests.

## Develop

```bash
./gradlew -p cli build
```

Run it wherever the root build runs: a change anywhere in the repository can
change what gets generated. `TemplateIntegrationTest` generates projects from the
real template and fails if any `acme` or `blueprint` is left outside Markdown
prose, if a name containing `blueprint` is mangled, or if the result loses its
expected shape. For a full check, generate a project and build it:

```bash
java -jar cli/build/libs/blueprint-cli-0.0.2.jar new demo-app -d /tmp
cd /tmp/demo-app && ./gradlew build
```

## Not yet

Asking for a base package, or Maven instead of Gradle,
`blueprint doctor`, and adding a module to an existing project. Each fits the same structure: more answers in `ProjectSpec`, more
tokens in `RewriteRules`. Maven and Oracle also need *overlays* — files the
template does not have, or has in another form — and with the first one the
generator's unpack step grows a plan (keep, rewrite, replace, drop) in place of
today's single loop. An overlay is not built by the repository's own build, so
each needs a generate-and-build test of its own.
