package com.acme.blueprint.cli;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;
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
                "Exit codes: 0 project created, 1 could not create it, 2 bad command line,",
                "130 cancelled (Ctrl-C at a question)."})
final class NewCommand implements Callable<Integer> {

    private static final String SETUP_DEFAULT = "default";
    private static final String SETUP_CUSTOM = "custom";
    private static final String BOTH = "both";
    private static final String NONE = "none";
    private static final String PROMETHEUS = "prometheus";

    @Parameters(index = "0", arity = "0..1", paramLabel = "<project-name>",
            description = "Name of the project, for example my-shop. Asked for when left out.")
    private String projectName;

    @Option(names = {"-d", "--parent-dir"}, paramLabel = "<dir>",
            description = "Parent directory: the project is created in <dir>/<project-name>. "
                    + "Default: the current directory.")
    private Path parentDirectory;

    @Option(names = "--database", paramLabel = "<database>",
            description = "postgresql or oracle. Asked for when left out.")
    private String database;

    @Option(names = "--cache", paramLabel = "<cache>",
            description = "none, memory, redis or memcached. Asked for when left out.")
    private String cache;

    @Option(names = "--broker", paramLabel = "<broker>",
            description = "none, kafka, redpanda or rabbitmq: where the modules' events are also published. Asked for when left out.")
    private String broker;

    @Option(names = "--storage", paramLabel = "<storage>",
            description = "none, minio, seaweedfs or rustfs: S3-compatible object storage behind shared's ObjectStore. "
                    + "Asked for when left out.")
    private String storage;

    @Option(names = "--samples", negatable = true,
            description = "Keep both sample modules (users, notifications), or start without (--no-samples). Asked for when left out.")
    private Boolean samples;

    @Option(names = "--modules", paramLabel = "<modules>",
            description = "Sample modules to keep: both, users or none. Asked for when left out.")
    private String modules;

    @Option(names = "--deploy", paramLabel = "<deploy>",
            description = "none, docker or kubernetes (Helm chart). Asked for when left out.")
    private String deploy;

    @Option(names = "--tests", negatable = true, description = "Keep the tests of every sample module kept (default), or none.")
    private Boolean tests;

    @Option(names = "--compose", negatable = true, description = "Docker Compose for local services (default), or not.")
    private Boolean compose;

    @Option(names = "--ci", negatable = true, description = "A GitHub Actions workflow, or not.")
    private Boolean ci;

    @Option(names = "--security", negatable = true, description = "JWT security (OAuth2 resource server), or not.")
    private Boolean security;

    @Option(names = "--observability", negatable = true, description = "Prometheus metrics and OpenTelemetry tracing, or none.")
    private Boolean observability;

    @Option(names = "--dependabot", negatable = true, description = "Dependabot configuration, or not.")
    private Boolean dependabot;

    @Option(names = {"-y", "--defaults"}, negatable = true,
            description = "Take every default without asking (--no-defaults: ask about each). Other options still apply.")
    private Boolean defaults;

    @Option(names = "--scans", negatable = true, description = "All three OWASP scans in CI (CodeQL, Dependency-Check, ZAP), or none.")
    private Boolean scans;

    @Option(names = "--git", negatable = true,
            description = "git init with a first commit, or not (--no-git). Asked for when left out.")
    private Boolean git;

    @Spec
    private CommandSpec spec;

    private final ProjectTemplate template;
    private final BufferedReader input;
    /** The interactive terminal, for menus; null where there is none. */
    private final Keyboard terminal;
    private final ProjectGenerator generator;
    private final Releases releases;
    private final SystemTools tools;
    private final Style style;

    NewCommand(ProjectTemplate template, BufferedReader input, Keyboard terminal, ProjectGenerator generator,
            Releases releases, SystemTools tools, Style style) {
        this.style = style;
        this.template = template;
        this.input = input;
        this.terminal = terminal;
        this.generator = generator;
        this.releases = releases;
        this.tools = tools;
    }

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        out.print(style.banner());
        Prompter prompter = new Prompter(terminal != null ? terminal : Keyboard.lines(input, out), out, style);
        ProjectName name = projectName != null ? nameFromArgument() : nameFromPrompt(prompter);
        ProjectSpec project = ProjectSpec.defaultsFor(name);
        Optional<ProjectVersions> templateVersions = ProjectVersions.of(template);
        boolean useDefaults = false;
        if (templateVersions.isPresent()) {
            // No answer (piped input) means the custom path, where every unanswered question keeps the template as it is.
            useDefaults = defaults != null ? defaults
                    : prompter.pick("How do you want to set up the project?", List.of(
                            Choice.of(SETUP_DEFAULT, "Default", "recommended settings, no more questions"),
                            Choice.of(SETUP_CUSTOM, "Custom", "choose each setting")), SETUP_DEFAULT)
                            .orElse(SETUP_CUSTOM).equals(SETUP_DEFAULT);
            if (useDefaults) {
                prompter = prompter.answeringDefaults();
            }
            project = project.withVersions(chooseVersions(prompter, templateVersions.get()))
                    .withDatabase(database != null ? databaseFromOption()
                            : Database.of(prompter.select("Database", choices(Database.values(), Database::choice), Database.POSTGRESQL.choice().id())))
                    .withCache(cache != null ? cacheFromOption()
                            : CacheProvider.of(prompter.select("Cache", choices(CacheProvider.values(), CacheProvider::choice), CacheProvider.NONE.id())))
                    .withBroker(broker != null ? brokerFromOption()
                            : BrokerProvider.of(prompter.select("Message broker", choices(BrokerProvider.values(), BrokerProvider::choice), BrokerProvider.NONE.id())))
                    .withStorage(storage != null ? storageFromOption() : chooseStorage(prompter))
                    .withModules(chooseModules(prompter));
            project = project.withTests(chooseTests(prompter, project.modules()));
            Set<Extra> extras = EnumSet.noneOf(Extra.class);
            if (answer(prompter, compose, Extra.COMPOSE)) {
                extras.add(Extra.COMPOSE);
            }
            project = project.withDeploy(deploy != null ? deploymentFromOption()
                    : Deployment.of(prompter.select("Deploy", choices(Deployment.values(), Deployment::choice), Deployment.NONE.id())));
            if (answer(prompter, ci, Extra.CI)) {
                extras.add(Extra.CI);
            }
            if (answer(prompter, security, Extra.SECURITY)) {
                extras.add(Extra.SECURITY);
            }
            if (chooseObservability(prompter)) {
                extras.add(Extra.OBSERVABILITY);
            }
            if (answer(prompter, dependabot, Extra.DEPENDABOT)) {
                extras.add(Extra.DEPENDABOT);
            }
            project = project.withExtras(extras).withScans(chooseScans(prompter));
        }
        boolean initGit = git != null ? git : templateVersions.isPresent()
                && prompter.confirm("Initialize a git repository with a first commit?", false);
        if (useDefaults) {
            out.println(style.dim("Default setup: " + describe(project, initGit)));
        }
        checkTools(prompter, out, project.versions());

        Path parent = parentDirectory != null ? parentDirectory : Path.of("");
        GeneratedProject generated = generator.generate(template, project, parent.resolve(project.artifact()));

        if (initGit) {
            tools.initGit(generated.directory()).ifPresentOrElse(
                    reason -> out.println("No git repository: " + reason + "."),
                    () -> out.println(style.ok("Initialized a git repository on main: \"" + SystemTools.INITIAL_COMMIT + "\".")));
        }
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

    private Database databaseFromOption() {
        if (!Database.ids().contains(database)) {
            throw new ParameterException(spec.commandLine(),
                    "Unknown database '" + database + "': choose one of " + String.join(", ", Database.ids()) + ".");
        }
        return Database.of(database);
    }

    /** One line of what the default setup chose. */
    private static String describe(ProjectSpec project, boolean git) {
        List<String> parts = new ArrayList<>();
        if (project.versions() != null) {
            parts.add("Java " + project.versions().java());
            parts.add("Spring Boot " + project.versions().springBoot());
            parts.add("Gradle " + project.versions().gradle());
        }
        parts.add(project.database().displayName());
        parts.add("cache " + project.cache().id());
        parts.add("broker " + project.broker().id());
        parts.add("storage " + project.storage().id());
        parts.add(project.samples() ? "sample modules " + String.join(", ", inOrder(project.modules())) : "no sample modules");
        if (project.samples()) {
            parts.add(project.testedModules().isEmpty() ? "no tests" : "tests of " + String.join(", ", inOrder(project.testedModules())));
        }
        parts.add("deploy " + project.deploy().id());
        project.extras().stream().map(Extra::id).sorted().forEach(parts::add);
        project.scans().stream().sorted().map(scan -> "scan " + scan.id()).forEach(parts::add);
        if (git) {
            parts.add("git");
        }
        return String.join(" · ", parts) + ".";
    }

    /** The option when given; otherwise asked — Enter takes the extra's default, no answer keeps what the template has. */
    private static boolean answer(Prompter prompter, Boolean option, Extra extra) {
        return option != null ? option : prompter.confirm(extra.question(), extra.byDefault(), extra.inTemplate());
    }

    private Deployment deploymentFromOption() {
        if (!Deployment.ids().contains(deploy)) {
            throw new ParameterException(spec.commandLine(),
                    "Unknown deployment '" + deploy + "': choose one of " + String.join(", ", Deployment.ids()) + ".");
        }
        return Deployment.of(deploy);
    }

    private StorageProvider storageFromOption() {
        if (!StorageProvider.ids().contains(storage)) {
            throw new ParameterException(spec.commandLine(),
                    "Unknown storage '" + storage + "': choose one of " + String.join(", ", StorageProvider.ids()) + ".");
        }
        return StorageProvider.of(storage);
    }

    /** The S3-compatible servers there are; Garage and Ceph are shown as coming. */
    private static StorageProvider chooseStorage(Prompter prompter) {
        List<Choice> choices = new ArrayList<>(choices(StorageProvider.values(), StorageProvider::choice));
        // Garage sets up its layout and keys by commands its image has no shell to run; Ceph has no image fit for compose.
        choices.add(Choice.soon("garage", "Garage"));
        choices.add(Choice.soon("ceph", "Ceph (RADOS Gateway)"));
        return StorageProvider.of(prompter.select("Object storage", choices, StorageProvider.NONE.id()));
    }

    private BrokerProvider brokerFromOption() {
        if (!BrokerProvider.ids().contains(broker)) {
            throw new ParameterException(spec.commandLine(),
                    "Unknown broker '" + broker + "': choose one of " + String.join(", ", BrokerProvider.ids()) + ".");
        }
        return BrokerProvider.of(broker);
    }

    private CacheProvider cacheFromOption() {
        if (!CacheProvider.ids().contains(cache)) {
            throw new ParameterException(spec.commandLine(),
                    "Unknown cache '" + cache + "': choose one of " + String.join(", ", CacheProvider.ids()) + ".");
        }
        return CacheProvider.of(cache);
    }

    private ProjectName nameFromPrompt(Prompter prompter) {
        return prompter
                .ask("Project name (for example my-shop)", ProjectName::fromUserInput)
                .orElseThrow(() -> new ParameterException(spec.commandLine(),
                        "No project name given. Pass one as an argument, for example: blueprint new my-shop"));
    }

    /**
     * Java, Spring Boot and Gradle, each from the releases found online. Java's
     * default is the one installed; Spring Boot and Gradle are typed when their
     * releases cannot be listed. Spring Boot's BOM brings Hibernate along.
     */
    private ProjectVersions chooseVersions(Prompter prompter, ProjectVersions template) {
        String java = chooseJava(prompter);
        String springBoot = releases.springBoot(template.springBoot())
                .map(found -> prompter.select("Spring Boot version", versionChoices(found, template.springBoot()), found.get(0)))
                .orElseGet(() -> prompter.type("Spring Boot version (its releases could not be listed)", template.springBoot(),
                        typed -> Releases.sameLine(typed, template.springBoot(), "Spring Boot")));
        String gradle = releases.gradle(template.gradle())
                .map(found -> prompter.select("Gradle version", versionChoices(found, template.gradle()), found.get(0)))
                .orElseGet(() -> prompter.type("Gradle version (its releases could not be listed)", template.gradle(),
                        typed -> checksummed(Releases.sameLine(typed, template.gradle(), "Gradle"), template)));
        String gradleSha256 = gradle.equals(template.gradle())
                ? template.gradleSha256()
                : releases.gradleSha256(gradle).orElseThrow(() -> new ProjectGenerationException(
                        "Could not fetch the checksum of Gradle " + gradle + ". Try again, or choose " + template.gradle() + "."));
        return new ProjectVersions(java, springBoot, gradle, gradleSha256);
    }

    /** A typed Gradle version whose checksum can be fetched — the wrapper verifies the download against it. */
    private String checksummed(String gradle, ProjectVersions template) {
        if (!gradle.equals(template.gradle()) && releases.gradleSha256(gradle).isEmpty()) {
            throw new IllegalArgumentException("Could not fetch the checksum of Gradle " + gradle
                    + ". Enter another version, or press Enter for " + template.gradle() + ".");
        }
        return gradle;
    }

    /**
     * The Java releases found online; the default, and why, in parentheses: the
     * installed Java when the template builds on it, else the newest LTS.
     */
    private String chooseJava(Prompter prompter) {
        List<Releases.Java> found = new ArrayList<>(releases.java());
        Optional<Integer> installed = tools.javaVersion().map(SystemTools::javaMajor);
        int newestLts = found.stream().filter(Releases.Java::lts).mapToInt(Releases.Java::feature).max()
                .orElse(Integer.parseInt(Releases.JAVA_LTS.get(0)));
        int latest = found.stream().mapToInt(Releases.Java::feature).max().orElse(newestLts);
        int byDefault;
        String why;
        if (installed.filter(feature -> feature >= Releases.MIN_JAVA).isPresent()) {
            byDefault = installed.get();
            why = "installed";
            if (found.stream().noneMatch(release -> release.feature() == installed.get())) {
                found.add(new Releases.Java(byDefault, false));
                found.sort(Comparator.comparingInt(Releases.Java::feature).reversed());
            }
        } else {
            byDefault = newestLts;
            why = installed.map(feature -> "Java " + feature + " installed is too old").orElse("no JDK installed");
        }
        List<Choice> choices = new ArrayList<>();
        for (Releases.Java release : found) {
            List<String> notes = new ArrayList<>();
            if (release.lts()) {
                notes.add("LTS");
            } else if (release.feature() == latest) {
                notes.add("latest");
            }
            if (release.feature() == byDefault) {
                notes.add("default: " + why);
            }
            choices.add(choice(String.valueOf(release.feature()), notes));
        }
        return prompter.select("Java version", choices, String.valueOf(byDefault));
    }

    /** Versions newest first: the newest marked latest, the template's own marked as what the blueprint is built with. */
    private static List<Choice> versionChoices(List<String> versions, String templateVersion) {
        List<Choice> choices = new ArrayList<>();
        for (String version : versions) {
            List<String> notes = new ArrayList<>();
            if (version.equals(versions.get(0)) && versions.size() > 1) {
                notes.add("latest");
            }
            if (version.equals(templateVersion)) {
                notes.add("the blueprint's");
            }
            choices.add(choice(version, notes));
        }
        return choices;
    }

    private static Choice choice(String id, List<String> notes) {
        return notes.isEmpty() ? Choice.of(id, id) : Choice.of(id, id, String.join(", ", notes));
    }

    /** Both sample modules, users alone, or none; notifications alone is shown as coming — it needs users. */
    private Set<String> chooseModules(Prompter prompter) {
        String chosen;
        if (modules != null) {
            if (!List.of(BOTH, SampleModules.USERS, NONE).contains(modules)) {
                throw new ParameterException(spec.commandLine(),
                        "Unknown sample modules '" + modules + "': choose one of both, users, none.");
            }
            chosen = modules;
        } else if (samples != null) {
            chosen = samples ? BOTH : NONE;
        } else {
            chosen = prompter.select("Sample modules", List.of(
                    Choice.of(BOTH, "Both", "users, and notifications reacting to its events"),
                    Choice.of(SampleModules.USERS, "Users only", "the full reference slice"),
                    new Choice(SampleModules.NOTIFICATIONS, "Notifications only", "soon: it builds on users", false),
                    Choice.of(NONE, "None", "an empty project")), BOTH);
        }
        return switch (chosen) {
            case BOTH -> Set.copyOf(SampleModules.ALL);
            case NONE -> Set.of();
            default -> Set.of(chosen);
        };
    }

    /** The tests of which kept modules; asked only when modules are kept. */
    private Set<String> chooseTests(Prompter prompter, Set<String> kept) {
        if (kept.isEmpty()) {
            return Set.of();
        }
        if (tests != null) {
            return tests ? kept : Set.of();
        }
        List<String> names = inOrder(kept);
        List<Choice> choices = new ArrayList<>();
        if (names.size() > 1) {
            choices.add(Choice.of(BOTH, "Both", "unit, web slice and module tests"));
            names.forEach(module -> choices.add(Choice.of(module, capitalised(module) + " only")));
        } else {
            choices.add(Choice.of(names.get(0), capitalised(names.get(0)), "unit, web slice and module tests"));
        }
        choices.add(Choice.of(NONE, "None"));
        String chosen = prompter.select("Sample modules' tests", choices, choices.get(0).id());
        return switch (chosen) {
            case BOTH -> kept;
            case NONE -> Set.of();
            default -> Set.of(chosen);
        };
    }

    /**
     * One stack of those listed exists so far — Prometheus with OpenTelemetry;
     * the others are shown as coming. No answer keeps the template as it is: none.
     */
    private boolean chooseObservability(Prompter prompter) {
        if (observability != null) {
            return observability;
        }
        return prompter.pick("Observability", List.of(
                Choice.of(NONE, "None"),
                Choice.of(PROMETHEUS, "Prometheus + OpenTelemetry", "metrics at /actuator/prometheus, OTLP traces"),
                Choice.soon("grafana-lgtm", "Grafana LGTM: Loki, Grafana, Tempo, Mimir"),
                Choice.soon("elk", "ELK: Elasticsearch, Logstash, Kibana"),
                Choice.soon("jaeger", "OpenTelemetry Collector + Jaeger")),
                Extra.OBSERVABILITY.byDefault() ? PROMETHEUS : NONE).orElse(NONE).equals(PROMETHEUS);
    }

    /** Any of the three OWASP scans, as a checklist; none ticked to begin with. */
    private Set<Scan> chooseScans(Prompter prompter) {
        if (scans != null) {
            return scans ? EnumSet.allOf(Scan.class) : Set.of();
        }
        List<Choice> choices = Arrays.stream(Scan.values()).map(Scan::choice).toList();
        return prompter.pickAny("OWASP security scans in CI", choices, Set.of()).stream()
                .map(Scan::of).collect(Collectors.toCollection(() -> EnumSet.noneOf(Scan.class)));
    }

    private static <T> List<Choice> choices(T[] values, Function<T, Choice> choice) {
        return Arrays.stream(values).map(choice).toList();
    }

    /** The sample modules in the order the build names them. */
    private static List<String> inOrder(Set<String> modules) {
        return SampleModules.ALL.stream().filter(modules::contains).toList();
    }

    private static String capitalised(String word) {
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }

    /** Reports Java, git and Gradle, and offers to install what is missing — never required to go on. */
    private void checkTools(Prompter prompter, PrintWriter out, ProjectVersions versions) {
        String java = versions != null ? versions.java() : Releases.JAVA_LTS.get(0);
        out.println();
        out.println(style.bold("Checking tools:"));

        Optional<String> javaVersion = tools.javaVersion();
        if (javaVersion.filter(version -> SystemTools.javaMajor(version) >= SystemTools.MIN_JAVA).isPresent()) {
            out.println("  " + style.ok("java    " + javaVersion.get()));
        } else {
            out.println("  " + style.warn("java    %s; ./gradlew needs Java %d+".formatted(
                    javaVersion.map(version -> version + " is too old").orElse("not found"), SystemTools.MIN_JAVA)));
            offer(prompter, out, "Java " + java + " (LTS)", tools.javaInstall(java), "https://adoptium.net");
        }

        Optional<String> gitVersion = tools.gitVersion();
        if (gitVersion.filter(version -> SystemTools.gitMajor(version) >= SystemTools.MIN_GIT).isPresent()) {
            out.println("  " + style.ok("git     " + gitVersion.get()));
        } else {
            out.println("  " + style.warn("git     %s; git %d+ is recommended".formatted(
                    gitVersion.map(version -> version + " is too old").orElse("not found"), SystemTools.MIN_GIT)));
            offer(prompter, out, "git", tools.gitInstall(), "https://git-scm.com/downloads");
        }

        if (tools.hasGradle()) {
            out.println("  " + style.ok("gradle  installed (projects run their own ./gradlew)"));
        } else if (versions != null) {
            out.println("  " + style.dim("gradle  not found (optional: projects run their own ./gradlew)"));
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
        out.println(style.ok(style.bold("Created " + project.artifact()) + " in %s (%d files, base package %s, %s, cache: %s)."
                .formatted(generated.directory(), generated.fileCount(), project.basePackage(),
                        project.database().displayName(), project.cache().id())
                        + (project.broker() != BrokerProvider.NONE ? " Events also go to " + project.broker().id() + "." : "")
                        + (project.storage() != StorageProvider.NONE ? " Files go to " + project.storage().id() + "." : "")));
        out.println();
        out.println(style.bold("Next steps:"));
        out.println("  " + style.cyan("cd " + whereToEnter(project, generated)));
        out.println("  " + style.cyan("./gradlew build") + "           " + style.dim("# all tests; needs Docker for the database"));
        out.println("  " + style.cyan("./gradlew :app:bootRun") + "    " + style.dim("# starts the database and the app on :8080"));
        out.println();
        out.println(style.dim("Read docs/architecture.md before adding code."));
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
