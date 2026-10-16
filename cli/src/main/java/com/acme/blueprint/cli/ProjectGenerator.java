package com.acme.blueprint.cli;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Unpacks the template archive into a new directory, renaming everything the
 * {@link RewriteRules} know about, and writes the new project's own README in
 * place of the blueprint's.
 */
final class ProjectGenerator {

    private static final String TEMPLATE_README = "README.md";
    private static final String README_NAME_PLACEHOLDER = "{{name}}";
    private static final String README_PACKAGE_PLACEHOLDER = "{{package}}";

    /** Files that start with this many bytes free of NUL are text; anything else is copied untouched. */
    private static final int BINARY_SNIFF_LENGTH = 8000;

    /**
     * @param template the blueprint to generate from
     * @param spec     what to generate
     * @param target   a directory that does not exist yet, or is empty
     * @throws ProjectGenerationException if the target is in use or the archive cannot be unpacked;
     *                                    nothing of the project is left behind in that case
     */
    GeneratedProject generate(ProjectTemplate template, ProjectSpec spec, Path target) {
        TargetDirectory directory = TargetDirectory.claim(target);
        try (InputStream archive = template.archive().get()) {
            directory.create();
            int files = unpack(archive, new RewriteRules(spec), directory);
            writeReadme(template.readme(), spec, directory);
            return new GeneratedProject(directory.path(), files + 1);
        } catch (IOException | RuntimeException failure) {
            directory.discard();
            if (failure instanceof ProjectGenerationException known) {
                throw known;
            }
            throw new ProjectGenerationException("Could not create the project: " + failure, failure);
        }
    }

    private static int unpack(InputStream archive, RewriteRules rules, TargetDirectory directory) throws IOException {
        int files = 0;
        ZipInputStream zip = new ZipInputStream(archive);
        for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
            String source = entry.getName();
            if (entry.isDirectory() || source.equals(TEMPLATE_README)) {
                continue;
            }
            Path file = directory.resolve(rules.path(source));
            Files.createDirectories(file.getParent());
            Files.write(file, rewritten(zip.readAllBytes(), source, rules));
            if (file.getFileName().toString().equals("gradlew")) {
                file.toFile().setExecutable(true);
            }
            files++;
        }
        return files;
    }

    /** Text is renamed as UTF-8; whatever is not text (the Gradle wrapper jar) passes through byte for byte. */
    private static byte[] rewritten(byte[] content, String source, RewriteRules rules) {
        return asText(content)
                .map(text -> rules.content(text, source.endsWith(".md")).getBytes(UTF_8))
                .orElse(content);
    }

    private static Optional<String> asText(byte[] content) {
        for (int index = 0; index < Math.min(content.length, BINARY_SNIFF_LENGTH); index++) {
            if (content[index] == 0) {
                return Optional.empty();
            }
        }
        try {
            return Optional.of(UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString());
        } catch (CharacterCodingException notUtf8) {
            return Optional.empty();
        }
    }

    private static void writeReadme(String readmeTemplate, ProjectSpec spec, TargetDirectory directory) throws IOException {
        String readme = readmeTemplate
                .replace(README_NAME_PLACEHOLDER, spec.artifact())
                .replace(README_PACKAGE_PLACEHOLDER, spec.basePackage());
        Files.writeString(directory.resolve(TEMPLATE_README), readme, UTF_8);
    }
}
