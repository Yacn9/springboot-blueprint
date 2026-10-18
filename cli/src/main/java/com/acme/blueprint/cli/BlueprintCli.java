package com.acme.blueprint.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Entry point of the {@code blueprint} command.
 *
 * <p>Exit codes, for every command: {@link #SUCCESS}, {@link #GENERATION_FAILED}
 * (the request was fine, the work was not) and {@link #USAGE_ERROR} (the command
 * line was not). Failures print {@code error: <reason>} on standard error and
 * never a stack trace.
 */
@Command(name = "blueprint", mixinStandardHelpOptions = true, versionProvider = BlueprintCli.Version.class,
        description = "Create projects from the modular-monolith blueprint.")
public final class BlueprintCli implements Runnable {

    static final int SUCCESS = 0;
    static final int GENERATION_FAILED = 1;
    static final int USAGE_ERROR = 2;

    /** The template packed into the jar by the {@code templateArchive} Gradle task. */
    private static final String BUNDLED_TEMPLATE = "/template/template.zip";
    private static final String BUNDLED_README = "/project-readme.md";

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    /** The command line with its subcommands wired to the given template and input. */
    static CommandLine commandLine(ProjectTemplate template, BufferedReader promptInput) {
        return commandLine(template, promptInput, Releases.online(), SystemTools.local(), Style.PLAIN);
    }

    /** As above, with where newer releases come from and the machine's tools made explicit — for tests. */
    static CommandLine commandLine(ProjectTemplate template, BufferedReader promptInput, Releases releases, SystemTools tools) {
        return commandLine(template, promptInput, releases, tools, Style.PLAIN);
    }

    static CommandLine commandLine(ProjectTemplate template, BufferedReader promptInput, Releases releases, SystemTools tools,
            Style style) {
        CommandLine commandLine = new CommandLine(new BlueprintCli());
        commandLine.addSubcommand(new NewCommand(template, promptInput, new ProjectGenerator(), releases, tools, style));
        // Set after the subcommands are added: a setting reaches only the commands that exist when it is made.
        commandLine.setParameterExceptionHandler((exception, arguments) -> {
            CommandLine failed = exception.getCommandLine();
            failed.getErr().println("error: " + exception.getMessage());
            failed.getErr().println("Try '" + failed.getCommandSpec().qualifiedName() + " --help' for usage.");
            return USAGE_ERROR;
        });
        commandLine.setExecutionExceptionHandler((exception, failed, parsed) -> {
            failed.getErr().println("error: " + (exception.getMessage() != null ? exception.getMessage() : exception));
            return GENERATION_FAILED;
        });
        return commandLine;
    }

    public static void main(String[] args) {
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in, Charset.defaultCharset()));
        ProjectTemplate template = new ProjectTemplate(BlueprintCli::bundledArchive, bundledReadme());
        System.exit(commandLine(template, stdin, Releases.online(), SystemTools.local(), Style.forTerminal()).execute(args));
    }

    private static InputStream bundledArchive() {
        InputStream archive = BlueprintCli.class.getResourceAsStream(BUNDLED_TEMPLATE);
        if (archive == null) {
            throw new ProjectGenerationException(
                    "This build has no project template. Build the jar with Gradle (`./gradlew -p cli jar`).");
        }
        return archive;
    }

    private static String bundledReadme() {
        try (InputStream readme = BlueprintCli.class.getResourceAsStream(BUNDLED_README)) {
            if (readme == null) {
                throw new IllegalStateException("Missing resource " + BUNDLED_README);
            }
            return new String(readme.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    static final class Version implements IVersionProvider {
        @Override
        public String[] getVersion() {
            String version = BlueprintCli.class.getPackage().getImplementationVersion();
            return new String[] {"blueprint " + (version != null ? version : "(development build)")};
        }
    }
}
