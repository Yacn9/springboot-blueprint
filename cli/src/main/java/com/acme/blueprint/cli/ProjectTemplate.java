package com.acme.blueprint.cli;

import java.io.InputStream;
import java.util.function.Supplier;

/**
 * What a project is generated from.
 *
 * @param archive opens the template archive — a zip of the blueprint — afresh on every call; the caller closes it
 * @param readme  the README of the generated project, with {@code {{name}}} and {@code {{package}}} placeholders
 */
record ProjectTemplate(Supplier<InputStream> archive, String readme) {
}
