package com.acme.blueprint.cli;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What the user calls the project: lower-case letters, digits and single
 * hyphens, starting with a letter — the intersection of what Gradle, Java
 * packages and Kotlin script-plugin ids accept without escaping.
 *
 * <p>This type validates and normalises, nothing more; what a name turns into
 * (package, class prefix, …) is {@link ProjectSpec}'s concern.
 */
final class ProjectName {

    static final int MAX_LENGTH = 40;

    private static final Pattern FORM = Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*");

    /** Words that cannot be a package segment in Java or Kotlin, or that Gradle owns as plugin-id prefixes. */
    private static final Set<String> RESERVED = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while", "true", "false", "null",
            "fun", "in", "is", "object", "typealias", "typeof", "val", "var", "when", "as",
            "java", "javax", "kotlin", "gradle");

    private final String kebabCase;

    private ProjectName(String kebabCase) {
        this.kebabCase = kebabCase;
    }

    /**
     * Accepts what a person types: case is ignored and spaces or underscores
     * between words become hyphens, so {@code Order Hub} is {@code order-hub}.
     *
     * @throws InvalidProjectNameException with a message quoting the input as typed
     */
    static ProjectName fromUserInput(String input) {
        if (input == null || input.isBlank()) {
            throw new InvalidProjectNameException("The project name must not be empty.");
        }
        String typed = input.strip();
        String normalised = typed.toLowerCase(Locale.ROOT).replaceAll("[\\s_]+", "-");
        if (!FORM.matcher(normalised).matches()) {
            throw new InvalidProjectNameException(("'%s' is not a valid project name: use lower-case letters, digits "
                    + "and hyphens (spaces and underscores become hyphens), starting with a letter and without "
                    + "doubled hyphens — for example my-shop.").formatted(typed));
        }
        if (normalised.length() > MAX_LENGTH) {
            throw new InvalidProjectNameException(
                    "'%s' is too long: a project name has at most %d characters.".formatted(typed, MAX_LENGTH));
        }
        String withoutHyphens = normalised.replace("-", "");
        if (RESERVED.contains(withoutHyphens)) {
            throw new InvalidProjectNameException(("'%s' cannot be used: without its hyphens it is '%s', a reserved "
                    + "word, and it would become the base package.").formatted(typed, withoutHyphens));
        }
        return new ProjectName(normalised);
    }

    /** The normalised name, such as {@code my-shop}. */
    String kebabCase() {
        return kebabCase;
    }

    @Override
    public String toString() {
        return kebabCase;
    }
}
