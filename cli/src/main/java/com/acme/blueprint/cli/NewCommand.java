package com.acme.blueprint.cli;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

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

    NewCommand(ProjectTemplate template, BufferedReader input, ProjectGenerator generator) {
        this.template = template;
        this.input = input;
        this.generator = generator;
    }

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        ProjectName name = projectName != null ? nameFromArgument() : nameFromPrompt(out);
        ProjectSpec project = ProjectSpec.defaultsFor(name);

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

    private ProjectName nameFromPrompt(PrintWriter out) {
        return new Prompter(input, out)
                .ask("Project name (for example my-shop)", ProjectName::fromUserInput)
                .orElseThrow(() -> new ParameterException(spec.commandLine(),
                        "No project name given. Pass one as an argument, for example: blueprint new my-shop"));
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
