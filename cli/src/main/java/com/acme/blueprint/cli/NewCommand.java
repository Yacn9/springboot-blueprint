package com.acme.blueprint.cli;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** {@code blueprint new [project-name]} — creates a project from the blueprint. */
@Command(name = "new", mixinStandardHelpOptions = true,
        description = "Create a new project from the blueprint.",
        footerHeading = "%nExamples:%n",
        footer = {
                "  blueprint new my-shop",
                "  blueprint new \"Order Hub\" --parent-dir ~/work",
                "",
                "A project name is lower-case letters, digits and hyphens, starting with a letter, up to 40",
                "characters. Spaces and underscores become hyphens. Without a name, it is asked for.",
                "",
                "Exit codes: 0 project created, 1 could not create it, 2 bad command line."})
final class NewCommand implements Callable<Integer> {

    @Parameters(index = "0", arity = "0..1", paramLabel = "<project-name>",
            description = "Name of the project, for example my-shop. Asked for when left out.")
    private String projectName;

    @Option(names = {"-d", "--parent-dir"}, paramLabel = "<dir>",
            description = "Parent directory: the project is created in <dir>/<project-name>. "
                    + "Default: the current directory.")
    private Path parentDirectory;

    @Spec
    private CommandSpec spec;

    private final ProjectTemplate template;
    private final BufferedReader input;
    private final ProjectGenerator generator;
    private final Releases releases;
    private final SystemTools tools;

    NewCommand(ProjectTemplate template, BufferedReader input, ProjectGenerator generator,
            Releases releases, SystemTools tools) {
        this.template = template;
        this.input = input;
        this.generator = generator;
        this.releases = releases;
        this.tools = tools;
    }

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        Prompter prompter = new Prompter(input, out);
        ProjectName name = projectName != null ? nameFromArgument() : nameFromPrompt(prompter);
        ProjectSpec project = ProjectSpec.defaultsFor(name);
        Optional<ProjectVersions> templateVersions = ProjectVersions.of(template);
        if (templateVersions.isPresent()) {
            project = project.withVersions(chooseVersions(prompter, templateVersions.get()));
        }
        checkTools(prompter, out, project.versions());

        Path parent = parentDirectory != null ? parentDirectory : Path.of("");
        GeneratedProject generated = generator.generate(template, project, parent.resolve(project.artifact()));

        printSummary(out, project, generated);
        return BlueprintCli.SUCCESS;
    }

    private ProjectName nameFromArgument() {
        try {
            return ProjectName.fromUserInput(projectName);
        } catch (InvalidProjectNameException invalid) {
            throw new ParameterException(spec.commandLine(), invalid.getMessage(), invalid);
        }
    }

    private ProjectName nameFromPrompt(Prompter prompter) {
        return prompter
                .ask("Project name (for example my-shop)", ProjectName::fromUserInput)
                .orElseThrow(() -> new ParameterException(spec.commandLine(),
                        "No project name given. Pass one as an argument, for example: blueprint new my-shop"));
    }

    /** Java LTS, Spring Boot and Gradle; Enter takes the newest. Spring Boot's BOM brings Hibernate along. */
    private ProjectVersions chooseVersions(Prompter prompter, ProjectVersions template) {
        String java = prompter.choose("Java version (LTS)", Releases.JAVA_LTS);
        String springBoot = prompter.choose("Spring Boot version", releases.springBoot(template.springBoot()));
        String gradle = prompter.choose("Gradle version", releases.gradle(template.gradle()));
        String gradleSha256 = gradle.equals(template.gradle())
                ? template.gradleSha256()
                : releases.gradleSha256(gradle).orElseThrow(() -> new ProjectGenerationException(
                        "Could not fetch the checksum of Gradle " + gradle + ". Try again, or choose " + template.gradle() + "."));
        return new ProjectVersions(java, springBoot, gradle, gradleSha256);
    }

    /** Reports Java, git and Gradle, and offers to install what is missing — never required to go on. */
    private void checkTools(Prompter prompter, PrintWriter out, ProjectVersions versions) {
        String java = versions != null ? versions.java() : Releases.JAVA_LTS.get(0);
        out.println();
        out.println("Checking tools:");

        Optional<String> javaVersion = tools.javaVersion();
        if (javaVersion.filter(version -> SystemTools.javaMajor(version) >= SystemTools.MIN_JAVA).isPresent()) {
            out.printf("  java    %s%n", javaVersion.get());
        } else {
            out.printf("  java    %s; ./gradlew needs Java %d+%n",
                    javaVersion.map(version -> version + " is too old").orElse("not found"), SystemTools.MIN_JAVA);
            offer(prompter, out, "Java " + java + " (LTS)", tools.javaInstall(java), "https://adoptium.net");
        }

        Optional<String> gitVersion = tools.gitVersion();
        if (gitVersion.filter(version -> SystemTools.gitMajor(version) >= SystemTools.MIN_GIT).isPresent()) {
            out.printf("  git     %s%n", gitVersion.get());
        } else {
            out.printf("  git     %s; git %d+ is recommended%n",
                    gitVersion.map(version -> version + " is too old").orElse("not found"), SystemTools.MIN_GIT);
            offer(prompter, out, "git", tools.gitInstall(), "https://git-scm.com/downloads");
        }

        if (tools.hasGradle()) {
            out.println("  gradle  installed (projects run their own ./gradlew)");
        } else if (versions != null) {
            out.println("  gradle  not found (optional: projects run their own ./gradlew)");
            offer(prompter, out, "Gradle " + versions.gradle(), tools.gradleInstall(versions.gradle()),
                    "https://gradle.org/install");
        }
        out.flush();
    }

    private void offer(Prompter prompter, PrintWriter out, String what, List<List<String>> commands, String manual) {
        if (commands.isEmpty()) {
            out.println("          Install " + what + " yourself: " + manual);
            return;
        }
        String described = commands.stream().map(command -> String.join(" ", command)).collect(Collectors.joining(" && "));
        if (!prompter.confirm("Install " + what + " now? (" + described + ")")) {
            out.println("          Skipped. Install it later: " + manual);
            return;
        }
        out.flush();
        out.println(tools.install(commands)
                ? "          Installed " + what + ". Open a new terminal to use it."
                : "          Could not install " + what + ". Install it yourself: " + manual);
    }

    private void printSummary(PrintWriter out, ProjectSpec project, GeneratedProject generated) {
        out.println();
        out.printf("Created %s in %s (%d files, base package %s).%n",
                project.artifact(), generated.directory(), generated.fileCount(), project.basePackage());
        out.println();
        out.println("Next steps:");
        out.println("  cd " + whereToEnter(project, generated));
        out.println("  ./gradlew build           # all tests; needs Docker for the database");
        out.println("  ./gradlew :app:bootRun    # starts the database and the app on :8080");
        out.println();
        out.println("Read docs/architecture.md before adding code.");
        out.flush();
    }

    /** The short name when the project is in the current directory; otherwise the full path, quoted if it needs it. */
    private String whereToEnter(ProjectSpec project, GeneratedProject generated) {
        if (parentDirectory == null) {
            return project.artifact();
        }
        String path = generated.directory().toString();
        return path.chars().anyMatch(Character::isWhitespace) ? "\"" + path + "\"" : path;
    }
}
