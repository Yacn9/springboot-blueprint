package com.acme.blueprint.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Generates a project from the real template — the repository, packed by the
 * {@code templateArchive} Gradle task — and checks what no unit test can: that
 * the blueprint's own names are gone, everywhere, and that the result has the
 * shape the architecture document promises.
 */
class TemplateIntegrationTest {

    private static final ProjectGenerator GENERATOR = new ProjectGenerator();

    private static Path archive;
    private static Path project;

    @BeforeAll
    static void generate(@TempDir Path work) {
        archive = Path.of(System.getProperty("blueprint.template.zip"));
        project = generateNamed("my-shop", work);
    }

    private static Path generateNamed(String name, Path work) {
        return generate(ProjectSpec.defaultsFor(ProjectName.fromUserInput(name)), work);
    }

    private static Path generate(ProjectSpec spec, Path work) {
        String name = spec.artifact();
        ProjectTemplate template = new ProjectTemplate(TemplateIntegrationTest::openArchive, "# {{name}}\n\nBase package {{package}}\n");
        return GENERATOR.generate(template, spec, work.resolve(name)).directory();
    }

    private static InputStream openArchive() {
        try {
            return Files.newInputStream(archive);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    @Test
    void leavesNoTraceOfTheBlueprintsOwnNamesOutsideMarkdownProse() throws IOException {
        List<String> offenders = textFiles(project).stream()
                .filter(file -> !file.toString().endsWith(".md"))
                .filter(file -> mentions(file, "acme", "blueprint"))
                .map(file -> project.relativize(file).toString())
                .toList();

        assertThat(offenders).isEmpty();
        try (Stream<Path> tree = Files.walk(project)) {
            assertThat(tree.map(path -> project.relativize(path).toString().toLowerCase()))
                    .noneMatch(path -> path.contains("acme") || path.contains("blueprint"));
        }
    }

    @Test
    void movesTheCodeIntoTheNewPackage() throws IOException {
        Path user = project.resolve("modules/users/impl/src/main/java/com/myshop/users/domain/User.java");

        assertThat(Files.readString(user, UTF_8)).startsWith("package com.myshop.users.domain;");
        assertThat(project.resolve("modules/users/api/src/main/java/com/myshop/users/UserDirectory.java")).exists();
        assertThat(project.resolve("shared/src/main/java/com/myshop/shared/ConstraintViolations.java")).exists();
        assertThat(project.resolve("com")).doesNotExist();
    }

    @Test
    void renamesTheApplicationAndItsTests() throws IOException {
        assertThat(Files.readString(project.resolve("app/src/main/java/com/myshop/MyShopApplication.java"), UTF_8))
                .contains("class MyShopApplication");
        assertThat(project.resolve("app/src/test/java/com/myshop/MyShopApplicationTests.java")).exists();
        assertThat(project.resolve("app/src/test/java/com/myshop/ArchitectureRulesTests.java")).exists();
    }

    @Test
    void renamesTheBuildAndItsPlugins() throws IOException {
        assertThat(Files.readString(project.resolve("settings.gradle.kts"), UTF_8)).contains("rootProject.name = \"my-shop\"");
        assertThat(project.resolve("build-logic/src/main/kotlin/myshop.java-conventions.gradle.kts")).exists();
        assertThat(Files.readString(project.resolve("app/build.gradle.kts"), UTF_8))
                .contains("id(\"myshop.spring-boot-application\")");
        assertThat(Files.readString(project.resolve("modules/users/impl/build.gradle.kts"), UTF_8))
                .contains("id(\"myshop.module-impl\")");
    }

    @Test
    void renamesThePlatformDefaultsConsistently() throws IOException {
        assertThat(project.resolve("shared/src/main/resources/config/myshop-platform.yml")).exists();
        assertThat(Files.readString(project.resolve("shared/src/main/resources/META-INF/spring.factories"), UTF_8))
                .contains("com.myshop.shared.config.PlatformDefaultsEnvironmentPostProcessor");
        assertThat(Files.readString(project.resolve("shared/src/main/java/com/myshop/shared/config/PlatformDefaultsEnvironmentPostProcessor.java"), UTF_8))
                .contains("myshop-platform.yml");
    }

    @Test
    void keepsTheDocumentationButFollowsItsPackageAndPaths() throws IOException {
        String architecture = Files.readString(project.resolve("docs/architecture.md"), UTF_8);

        assertThat(Files.readString(project.resolve("README.md"), UTF_8)).startsWith("# my-shop");
        assertThat(architecture).contains("com.myshop.", "com/myshop/accounts").doesNotContain("com.acme").doesNotContain("com/acme");
    }

    @Test
    void shipsTheGradleWrapperAndTheLocalDatabase() throws IOException {
        assertThat(project.resolve("gradlew")).exists();
        assertThat(project.resolve("gradlew").toFile().canExecute()).isTrue();
        assertThat(Files.size(project.resolve("gradle/wrapper/gradle-wrapper.jar"))).isPositive();
        assertThat(Files.readString(project.resolve("compose.yaml"), UTF_8)).contains("POSTGRES_DB: myshop");
        assertThat(project.resolve(".gitignore")).exists();
    }

    @Test
    void leavesOutBuildOutputToolStateAndTheCliItself() {
        assertThat(project.resolve("cli")).doesNotExist();
        assertThat(project.resolve("install.sh")).doesNotExist();
        assertThat(project.resolve("jbang-catalog.json")).doesNotExist();
        assertThat(project.resolve("overlays")).doesNotExist();
        assertThat(project.resolve(".github")).doesNotExist();
        assertThat(project.resolve(".git")).doesNotExist();
        assertThat(project.resolve(".claude")).doesNotExist();
        assertThat(project.resolve("app/build")).doesNotExist();
        assertThat(project.resolve("build-logic/.gradle")).doesNotExist();
    }

    /**
     * The Oracle overlay replaces every vendor spot the architecture document
     * lists and adds the privilege script; whatever still names PostgreSQL
     * outside Markdown prose was missed. Whether the result builds is CI's
     * job: it needs Docker and an Oracle container.
     */
    @Test
    void generatesAnOracleProjectWithNothingLeftOfPostgresql(@TempDir Path work) throws IOException {
        Path oracle = generate(ProjectSpec.defaultsFor(ProjectName.fromUserInput("oracle-shop")).withDatabase(Database.ORACLE), work);

        List<String> offenders = textFiles(oracle).stream()
                .filter(file -> !file.toString().endsWith(".md"))
                .filter(file -> mentions(file, "postgres", "acme", "blueprint"))
                // Vendor-neutral by design: it normalises the constraint names both databases report.
                .filter(file -> !file.getFileName().toString().startsWith("ConstraintViolations"))
                .map(file -> oracle.relativize(file).toString())
                .toList();
        assertThat(offenders).isEmpty();
        assertThat(Files.readString(oracle.resolve("gradle/libs.versions.toml"), UTF_8))
                .contains("gvenzl/oracle-free:23-slim-faststart", "ojdbc17", "flyway-database-oracle", "testcontainers-oracle-free");
        assertThat(Files.readString(oracle.resolve("compose.yaml"), UTF_8)).contains("APP_USER: oracleshop");
        assertThat(Files.readString(oracle.resolve("shared/src/testDatabase/resources/oracle-init/01-privileges.sql"), UTF_8))
                .contains("TO oracleshop;");
        assertThat(oracle.resolve("overlays")).doesNotExist();
    }

    /** A name that contains the blueprint's own word must come out once, not rewritten again. */
    @Test
    void generatesConsistentlyForANameThatContainsBlueprint(@TempDir Path work) throws IOException {
        Path demo = generateNamed("blueprint-demo", work);

        assertThat(Files.readString(demo.resolve("app/src/main/java/com/blueprintdemo/BlueprintDemoApplication.java"), UTF_8))
                .startsWith("package com.blueprintdemo;")
                .contains("class BlueprintDemoApplication");
        assertThat(Files.readString(demo.resolve("settings.gradle.kts"), UTF_8)).contains("rootProject.name = \"blueprint-demo\"");
        assertThat(demo.resolve("build-logic/src/main/kotlin/blueprintdemo.java-conventions.gradle.kts")).exists();
        assertThat(textFiles(demo)).noneMatch(file -> mentions(file, "demodemo"));
    }

    private static List<Path> textFiles(Path root) throws IOException {
        try (Stream<Path> tree = Files.walk(root)) {
            return tree.filter(Files::isRegularFile).filter(file -> isText(file)).toList();
        }
    }

    private static boolean isText(Path file) {
        try {
            Files.readString(file, UTF_8);
            return true;
        } catch (CharacterCodingException binary) {
            return false;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static boolean mentions(Path file, String... words) {
        try {
            String content = Files.readString(file, UTF_8).toLowerCase();
            return Stream.of(words).anyMatch(content::contains);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
