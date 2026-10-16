package com.acme.blueprint.cli;

import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Turns the blueprint's own names into the new project's, in file paths and in
 * file contents.
 *
 * <p>Every name is replaced in <em>one pass</em> over the text, by a single
 * pattern whose alternatives are the tokens below, in order. What a
 * replacement inserts is never scanned again, so a project called
 * {@code blueprint-demo} cannot be rewritten twice.
 *
 * <p>Two tiers. The <em>structural</em> tokens are unambiguous names — the base
 * package, the Gradle project, the application class, the plugin ids — and
 * apply everywhere, documentation included. The <em>generic</em> tokens rewrite
 * every remaining {@code blueprint} (the database name in {@code compose.yaml},
 * for instance); they skip Markdown, where the word is ordinary prose.
 * Where two tokens could match at the same place, the earlier one wins, so
 * order matters: specific before generic.
 */
final class RewriteRules {

    /** A name to find, as a regular expression without capturing groups, and what replaces it. */
    private record Token(String regex, String replacement) {
    }

    private final List<Token> structural;
    private final List<Token> everything;
    private final Pattern structuralPattern;
    private final Pattern everythingPattern;

    RewriteRules(ProjectSpec spec) {
        this.structural = List.of(
                literal("com.acme.blueprint", spec.basePackage()),
                literal("com/acme/blueprint", spec.packagePath()),
                literal("modular-monolith-blueprint", spec.artifact()),
                literal("BlueprintApplication", spec.classPrefix() + "Application"),
                literal("blueprint-platform", spec.shortName() + "-platform"),
                // blueprint.java-conventions, blueprint.module-api — but not "…a blueprint." at a sentence end.
                new Token("blueprint\\.(?=[a-z]+-[a-z])", spec.shortName() + "."));
        List<Token> generic = List.of(
                literal("Blueprint", spec.classPrefix()),
                literal("blueprint", spec.shortName()));
        this.everything = Stream.concat(structural.stream(), generic.stream()).toList();
        this.structuralPattern = alternation(structural);
        this.everythingPattern = alternation(everything);
    }

    /** A path inside the template, with {@code /} separators. */
    String path(String templatePath) {
        return rewrite(everythingPattern, everything, templatePath);
    }

    String content(String text, boolean markdown) {
        return markdown
                ? rewrite(structuralPattern, structural, text)
                : rewrite(everythingPattern, everything, text);
    }

    private static Token literal(String text, String replacement) {
        return new Token(Pattern.quote(text), replacement);
    }

    /** {@code (token one)|(token two)|…}: group {@code n + 1} matched exactly when token {@code n} did. */
    private static Pattern alternation(List<Token> tokens) {
        return Pattern.compile(tokens.stream().map(token -> "(" + token.regex() + ")").collect(Collectors.joining("|")));
    }

    private static String rewrite(Pattern pattern, List<Token> tokens, String text) {
        return pattern.matcher(text).replaceAll(match -> Matcher.quoteReplacement(replacementFor(match, tokens)));
    }

    private static String replacementFor(MatchResult match, List<Token> tokens) {
        for (int index = 0; index < tokens.size(); index++) {
            if (match.group(index + 1) != null) {
                return tokens.get(index).replacement();
            }
        }
        throw new IllegalStateException("A match belongs to no token: " + match.group());
    }
}
