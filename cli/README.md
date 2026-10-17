# Blueprint CLI

Creates a new project from this blueprint. It asks for one thing — the project
name — and writes a complete, building project with the name applied
everywhere.

```
$ bash <(curl -Ls https://raw.githubusercontent.com/Yacn9/springboot-blueprint/main/install.sh)
==> Downloading the Blueprint CLI from https://github.com/Yacn9/springboot-blueprint/releases/latest/download ...
? Project name (for example my-shop): order-hub

Created order-hub in /work/order-hub (111 files, base package com.orderhub).
```

Nothing to clone and nothing to install but Java 17+. Arguments go to
`blueprint new`: add the name to skip the prompt (`bash <(curl …) order-hub`).
The project is created in a new directory named after it, inside the current
directory — or inside `--parent-dir <dir>` (short form `-d`), so `-d /work`
gives `/work/order-hub`.

Use `bash <(curl …)` as shown, not `curl … | bash`: piped into bash, the script
would take over standard input and the prompt could not read your answer.

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

[`install.sh`](../install.sh) runs on macOS, Linux and Git Bash on Windows. It:

1. finds Java 17+ (`JAVA_HOME`, else `java` on the PATH) and says how to get it if there is none;
2. reads the checksum published with the latest release, and downloads
   `blueprint-cli.jar` only if that checksum is not cached yet in
   `~/.cache/blueprint` — verifying the download before anything runs;
3. runs `blueprint new` with your arguments. When GitHub cannot be reached it
   uses the newest cached CLI, and says so.

| Setting | Effect |
| --- | --- |
| `--from-source` | Build the CLI from the repository's source with its Gradle wrapper instead of downloading a release. Slower; needs only a JDK. |
| `BLUEPRINT_VERSION=0.1.0` | Use that release instead of the latest. |
| `BLUEPRINT_RELEASE_URL=<dir url>` | Download from a mirror holding `blueprint-cli.jar` and `blueprint-cli.jar.sha256`. |
| `BLUEPRINT_HOME=<dir>` | Cache somewhere other than `~/.cache/blueprint`. |
| `BLUEPRINT_SOURCE_URL=<tarball>` | The source archive for `--from-source`. |

The checksum guards against a broken or truncated download. It comes from the
same release as the jar, so it does not prove who published it — that trust is
in the GitHub repository.

From a clone, the same CLI runs without the script:

```bash
./gradlew -p cli jar
java -jar cli/build/libs/blueprint-cli-0.1.0.jar new
```

The generated project needs Docker for its tests and `bootRun`; Gradle
provisions JDK 25 itself.

## Releasing

1. Set `version` in `cli/build.gradle.kts` (for example `0.2.0`) and commit.
2. Tag that commit `v0.2.0` and push the tag.

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
`cli/`, `install.sh`, `.github/` — are never included.

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
java -jar cli/build/libs/blueprint-cli-0.1.0.jar new demo-app -d /tmp
cd /tmp/demo-app && ./gradlew build
```

## Not yet

Asking for more than the name (base package, database, Gradle or Maven),
`blueprint doctor`, and adding a module to an existing project. Each fits the same structure: more answers in `ProjectSpec`, more
tokens in `RewriteRules`. Maven and Oracle also need *overlays* — files the
template does not have, or has in another form — and with the first one the
generator's unpack step grows a plan (keep, rewrite, replace, drop) in place of
today's single loop. An overlay is not built by the repository's own build, so
each needs a generate-and-build test of its own.
