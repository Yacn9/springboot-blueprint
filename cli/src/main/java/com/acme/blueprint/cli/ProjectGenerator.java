package com.acme.blueprint.cli;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final String README_DATABASE_PLACEHOLDER = "{{database}}";

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
            int files = unpack(archive, overlay(template, spec), new RewriteRules(spec), spec, directory);
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

    /**
     * Writes every template file, renamed — the database's overlay file instead
     * where it has one, then the overlay files the template lacks. No overlay
     * itself reaches the project.
     */
    private static int unpack(InputStream archive, Map<String, byte[]> overlay, RewriteRules rules, ProjectSpec spec,
            TargetDirectory directory) throws IOException {
        int files = 0;
        ZipInputStream zip = new ZipInputStream(archive);
        for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
            String source = entry.getName();
            if (entry.isDirectory() || source.equals(TEMPLATE_README) || source.startsWith(Database.OVERLAYS)
                    || !SampleModules.keeps(spec, source)
                    || !spec.has(Extra.COMPOSE) && source.equals(CacheProvider.COMPOSE)) {
                continue;
            }
            byte[] content = overlay.containsKey(source) ? overlay.remove(source) : zip.readAllBytes();
            write(source, content, rules, spec, directory);
            files++;
        }
        for (Map.Entry<String, byte[]> added : overlay.entrySet()) {
            if (!SampleModules.keeps(spec, added.getKey())
                    || !spec.has(Extra.COMPOSE) && added.getKey().equals(StorageProvider.SEAWEEDFS_CONFIG)) {
                continue;
            }
            write(added.getKey(), added.getValue(), rules, spec, directory);
            files++;
        }
        return files;
    }

    /** The chosen options' overlay files, keyed by the template path they replace or add. */
    private static Map<String, byte[]> overlay(ProjectTemplate template, ProjectSpec spec) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        List<String> overlays = new ArrayList<>(List.of(spec.database().overlay(), spec.cache().overlay()));
        SampleModules.overlay(spec).ifPresent(overlays::add);
        overlays.addAll(spec.broker().overlays());
        overlays.addAll(spec.storage().overlays());
        overlays.addAll(spec.deploy().overlays());
        spec.extras().stream().filter(extra -> !extra.inTemplate()).map(Extra::overlay).forEach(overlays::add);
        if (!spec.scans().isEmpty()) {
            overlays.add(Scan.OVERLAY);
        }
        for (String overlay : overlays) {
            try (InputStream archive = template.archive().get(); ZipInputStream zip = new ZipInputStream(archive)) {
                for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    if (!entry.isDirectory() && entry.getName().startsWith(overlay)) {
                        String path = entry.getName().substring(overlay.length());
                        if (overlay.equals(Scan.OVERLAY) && path.equals(Scan.BUILD_SCRIPT)
                                && !spec.scans().contains(Scan.DEPENDENCY_CHECK)) {
                            continue;
                        }
                        files.put(path, zip.readAllBytes());
                    }
                }
            }
        }
        return files;
    }

    private static void write(String source, byte[] content, RewriteRules rules, ProjectSpec spec, TargetDirectory directory)
            throws IOException {
        Path file = directory.resolve(rules.path(source));
        Files.createDirectories(file.getParent());
        Files.write(file, rewritten(content, source, rules, spec));
        if (file.getFileName().toString().equals("gradlew")) {
            file.toFile().setExecutable(true);
        }
    }

    /** Text is renamed as UTF-8; whatever is not text (the Gradle wrapper jar) passes through byte for byte. */
    private static byte[] rewritten(byte[] content, String source, RewriteRules rules, ProjectSpec spec) {
        return asText(content)
                .map(text -> spec.versions() == null ? text : spec.versions().pin(source, text))
                .map(text -> spec.database().pin(source, text))
                .map(text -> spec.cache().pin(source, text))
                .map(text -> spec.broker().pin(source, text))
                .map(text -> spec.storage().pin(source, text))
                .map(text -> SampleModules.remove(spec, source, text))
                .map(text -> Extras.pin(spec, source, text))
                .map(text -> Scan.pin(spec.scans(), source, text))
                .map(text -> rules.content(text, source.endsWith(".md")))
                .map(text -> text.getBytes(UTF_8))
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
                .replace(README_PACKAGE_PLACEHOLDER, spec.basePackage())
                .replace(README_DATABASE_PLACEHOLDER, spec.database().displayName())
                .replace("{{java}}", spec.versions() != null ? spec.versions().java() : Releases.JAVA_LTS.get(0))
                .replace("{{spring-boot}}", spec.versions() != null ? spec.versions().springBoot().replaceAll("\\.\\d+$", "") : "4.1");
        Files.writeString(directory.resolve(TEMPLATE_README), Extras.readme(spec, SampleModules.readme(spec, readme)), UTF_8);
    }
}
