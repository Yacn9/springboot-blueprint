package com.acme.blueprint.cli;

import static com.acme.blueprint.cli.TestArchives.text;
import static com.acme.blueprint.cli.TestArchives.zip;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectGeneratorTest {

    private static final byte[] WRAPPER_JAR = {0x50, 0x4B, 'b', 'l', 'u', 'e', 'p', 'r', 'i', 'n', 't', 0x00, (byte) 0xFF};
    private static final String README = "# {{name}}\n\nPackage {{package}}\n";

    private final ProjectGenerator generator = new ProjectGenerator();
    private final ProjectSpec spec = ProjectSpec.defaultsFor(ProjectName.fromUserInput("my-shop"));

    @TempDir
    Path work;

    private static Map<String, byte[]> sampleTemplate() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("README.md", text("# Modular Monolith Blueprint\n\nThe template's own readme."));
        entries.put("settings.gradle.kts", text("rootProject.name = \"modular-monolith-blueprint\"\n"));
        entries.put(".gitignore", text("build/\n"));
        entries.put("gradlew", text("#!/bin/sh\n"));
        entries.put("gradle/wrapper/gradle-wrapper.jar", WRAPPER_JAR);
        entries.put("build-logic/src/main/kotlin/blueprint.java-conventions.gradle.kts", text("plugins { java }\n"));
        entries.put("app/build.gradle.kts", text("plugins { id(\"blueprint.spring-boot-application\") }\n"));
        entries.put("app/src/main/java/com/acme/blueprint/BlueprintApplication.java",
                text("package com.acme.blueprint;\nclass BlueprintApplication {}\n"));
        entries.put("docs/architecture.md", text("A blueprint with com.acme.blueprint.users inside.\n"));
        return entries;
    }

    private static ProjectTemplate templateOf(Map<String, byte[]> entries) {
        byte[] archive = zip(entries);
        return new ProjectTemplate(() -> new ByteArrayInputStream(archive), README);
    }

    private GeneratedProject generate(Map<String, byte[]> entries, Path target) {
        return generator.generate(templateOf(entries), spec, target);
    }

    @Test
    void rewritesPathsAndContents() throws IOException {
        Path target = work.resolve("my-shop");

        GeneratedProject project = generate(sampleTemplate(), target);

        assertThat(project.directory()).isEqualTo(target);
        assertThat(target.resolve("settings.gradle.kts")).hasContent("rootProject.name = \"my-shop\"\n");
        assertThat(target.resolve("app/build.gradle.kts")).hasContent("plugins { id(\"myshop.spring-boot-application\") }\n");
        assertThat(target.resolve("app/src/main/java/com/myshop/MyShopApplication.java"))
                .hasContent("package com.myshop;\nclass MyShopApplication {}\n");
        assertThat(target.resolve("build-logic/src/main/kotlin/myshop.java-conventions.gradle.kts")).exists();
        assertThat(allFiles(target)).noneMatch(path -> path.contains("blueprint") || path.contains("acme"));
    }

    @Test
    void rewritesTextWhateverItsExtension() throws IOException {
        Map<String, byte[]> entries = sampleTemplate();
        entries.put("scripts/run.sh", text("echo blueprint\n"));
        Path target = work.resolve("my-shop");

        generate(entries, target);

        assertThat(target.resolve("scripts/run.sh")).hasContent("echo myshop\n");
    }

    @Test
    void countsEveryFileIncludingTheGeneratedReadme() {
        GeneratedProject project = generate(sampleTemplate(), work.resolve("my-shop"));

        assertThat(project.fileCount()).isEqualTo(sampleTemplate().size());
    }

    @Test
    void copiesBinaryEntriesByteForByte() throws IOException {
        Path target = work.resolve("my-shop");

        generate(sampleTemplate(), target);

        assertThat(Files.readAllBytes(target.resolve("gradle/wrapper/gradle-wrapper.jar"))).isEqualTo(WRAPPER_JAR);
    }

    @Test
    void copiesAFileThatIsNotUtf8TextByteForByte() throws IOException {
        byte[] latin1 = {'b', 'l', 'u', 'e', 'p', 'r', 'i', 'n', 't', ' ', (byte) 0xE9};
        Map<String, byte[]> entries = sampleTemplate();
        entries.put("legacy.txt", latin1);
        Path target = work.resolve("my-shop");

        generate(entries, target);

        assertThat(Files.readAllBytes(target.resolve("legacy.txt"))).isEqualTo(latin1);
    }

    @Test
    void replacesTheTemplateReadmeWithOneForTheNewProject() throws IOException {
        Path target = work.resolve("my-shop");

        generate(sampleTemplate(), target);

        assertThat(Files.readString(target.resolve("README.md"), UTF_8)).isEqualTo("# my-shop\n\nPackage com.myshop\n");
    }

    @Test
    void keepsTheProseOfMarkdownDocumentsButRenamesTheirPackages() throws IOException {
        Path target = work.resolve("my-shop");

        generate(sampleTemplate(), target);

        assertThat(Files.readString(target.resolve("docs/architecture.md"), UTF_8))
                .isEqualTo("A blueprint with com.myshop.users inside.\n");
    }

    @Test
    void makesTheGradleWrapperScriptExecutable() {
        Path target = work.resolve("my-shop");

        generate(sampleTemplate(), target);

        assertThat(target.resolve("gradlew").toFile().canExecute()).isTrue();
    }

    @Test
    void fillsAnExistingEmptyDirectory() throws IOException {
        Path target = Files.createDirectory(work.resolve("my-shop"));

        generate(sampleTemplate(), target);

        assertThat(target.resolve("settings.gradle.kts")).exists();
    }

    @Test
    void refusesADirectoryThatIsNotEmptyAndLeavesItUntouched() throws IOException {
        Path target = Files.createDirectory(work.resolve("my-shop"));
        Files.writeString(target.resolve("keep.txt"), "mine");

        assertThatThrownBy(() -> generate(sampleTemplate(), target))
                .isInstanceOf(ProjectGenerationException.class)
                .hasMessageContaining("already exists and is not empty")
                .hasMessageContaining("another project name or parent directory");

        assertThat(allFiles(target)).containsExactly("keep.txt");
        assertThat(target.resolve("keep.txt")).hasContent("mine");
    }

    @Test
    void refusesAFileInThePlaceOfTheDirectory() throws IOException {
        Path target = Files.writeString(work.resolve("my-shop"), "a file");

        assertThatThrownBy(() -> generate(sampleTemplate(), target))
                .isInstanceOf(ProjectGenerationException.class)
                .hasMessageContaining("not a directory");
    }

    @Test
    void neverWritesOutsideTheProjectAndLeavesNothingBehind() {
        Map<String, byte[]> entries = sampleTemplate();
        entries.put("../evil.txt", text("escaped"));
        Path target = work.resolve("project/my-shop");

        assertThatThrownBy(() -> generate(entries, target))
                .isInstanceOf(ProjectGenerationException.class)
                .hasMessageContaining("outside the project");

        assertThat(work.resolve("project/evil.txt")).doesNotExist();
        assertThat(target).doesNotExist();
    }

    @Test
    void removesWhatItWroteWhenTheArchiveCannotBeRead() {
        Path target = work.resolve("my-shop");
        byte[] archive = zip(sampleTemplate());
        ProjectTemplate failing = new ProjectTemplate(() -> new ConnectionResetAfter(archive, 200), README);

        assertThatThrownBy(() -> generator.generate(failing, spec, target))
                .isInstanceOf(ProjectGenerationException.class)
                .hasMessageContaining("Could not create the project")
                .hasMessageContaining("connection reset");

        assertThat(target).doesNotExist();
    }

    @Test
    void keepsAnExistingEmptyDirectoryButEmptiesItAfterAFailure() throws IOException {
        Path target = Files.createDirectory(work.resolve("my-shop"));
        Map<String, byte[]> entries = sampleTemplate();
        entries.put("../evil.txt", text("escaped"));

        assertThatThrownBy(() -> generate(entries, target)).isInstanceOf(ProjectGenerationException.class);

        assertThat(target).isEmptyDirectory();
    }

    @Test
    void createsNothingWhenTheTemplateCannotBeOpened() {
        Path target = work.resolve("my-shop");
        ProjectTemplate missing = new ProjectTemplate(() -> {
            throw new ProjectGenerationException("This build has no project template.");
        }, README);

        assertThatThrownBy(() -> generator.generate(missing, spec, target))
                .isInstanceOf(ProjectGenerationException.class)
                .hasMessage("This build has no project template.");

        assertThat(target).doesNotExist();
    }

    private static List<String> allFiles(Path root) throws IOException {
        try (Stream<Path> tree = Files.walk(root)) {
            return tree.filter(Files::isRegularFile)
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .toList();
        }
    }

    /** Serves the archive in small pieces, then fails as a dropped connection would. */
    private static final class ConnectionResetAfter extends InputStream {
        private final ByteArrayInputStream source;
        private final int limit;
        private int served;

        ConnectionResetAfter(byte[] content, int limit) {
            this.source = new ByteArrayInputStream(content);
            this.limit = limit;
        }

        @Override
        public int read() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (served > limit) {
                throw new UncheckedIOException(new IOException("connection reset"));
            }
            int read = source.read(buffer, offset, Math.min(length, 50));
            served += Math.max(read, 0);
            return read;
        }
    }
}
