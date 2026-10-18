package com.acme.blueprint.cli;

import java.util.Set;

/**
 * Everything the generator needs to know about the project to create: the
 * answers, with each role a name plays under its own accessor. Today every
 * role derives from the project name; a later question (a base package, a
 * database) changes the one accessor it concerns and nothing else.
 *
 * @param name          what the user calls the project
 * @param basePackage   the Java base package, such as {@code com.myshop}
 * @param versions      the Java, Spring Boot and Gradle versions to pin; null keeps the template's
 * @param database      the database the project runs on
 * @param cache         what holds the cache entries
 * @param broker        the message broker the events are also published to
 * @param storage       where files outside the database are kept
 * @param modules       the sample modules kept, of {@link SampleModules#ALL}
 * @param testedModules the sample modules whose tests are kept — of those kept
 * @param deploy        where the project is meant to run
 * @param extras        the yes-or-no parts it has
 * @param scans         the OWASP scans its security workflow runs; none, no workflow
 */
record ProjectSpec(ProjectName name, String basePackage, ProjectVersions versions, Database database,
        CacheProvider cache, BrokerProvider broker, StorageProvider storage, Set<String> modules, Set<String> testedModules, Deployment deploy,
        Set<Extra> extras, Set<Scan> scans) {

    ProjectSpec {
        modules = Set.copyOf(modules);
        testedModules = Set.copyOf(testedModules);
        extras = Set.copyOf(extras);
        scans = Set.copyOf(scans);
        // notifications compiles against users' contract (UserId, UserDirectory, UserRegistered).
        if (modules.contains(SampleModules.NOTIFICATIONS) && !modules.contains(SampleModules.USERS)) {
            throw new IllegalArgumentException("The notifications sample module needs the users module.");
        }
    }

    ProjectSpec(ProjectName name, String basePackage) {
        this(name, basePackage, null, Database.POSTGRESQL, CacheProvider.NONE, BrokerProvider.NONE, StorageProvider.NONE,
                Set.copyOf(SampleModules.ALL), Set.copyOf(SampleModules.ALL), Deployment.NONE, Set.of(Extra.COMPOSE), Set.of());
    }

    /** The defaults for a project named only by its name. */
    static ProjectSpec defaultsFor(ProjectName name) {
        return new ProjectSpec(name, "com." + name.kebabCase().replace("-", ""));
    }

    ProjectSpec withVersions(ProjectVersions chosen) {
        return new ProjectSpec(name, basePackage, chosen, database, cache, broker, storage, modules, testedModules, deploy, extras, scans);
    }

    ProjectSpec withDatabase(Database chosen) {
        return new ProjectSpec(name, basePackage, versions, chosen, cache, broker, storage, modules, testedModules, deploy, extras, scans);
    }

    ProjectSpec withCache(CacheProvider chosen) {
        return new ProjectSpec(name, basePackage, versions, database, chosen, broker, storage, modules, testedModules, deploy, extras, scans);
    }

    ProjectSpec withBroker(BrokerProvider chosen) {
        return new ProjectSpec(name, basePackage, versions, database, cache, chosen, storage, modules, testedModules, deploy, extras, scans);
    }

    ProjectSpec withStorage(StorageProvider chosen) {
        return new ProjectSpec(name, basePackage, versions, database, cache, broker, chosen, modules, testedModules, deploy, extras, scans);
    }

    ProjectSpec withModules(Set<String> chosen) {
        return new ProjectSpec(name, basePackage, versions, database, cache, broker, storage, chosen, testedModules, deploy, extras, scans);
    }

    /** All the sample modules, or none. */
    ProjectSpec withSamples(boolean kept) {
        return withModules(kept ? Set.copyOf(SampleModules.ALL) : Set.of());
    }

    ProjectSpec withTests(Set<String> chosen) {
        return new ProjectSpec(name, basePackage, versions, database, cache, broker, storage, modules, chosen, deploy, extras, scans);
    }

    ProjectSpec withDeploy(Deployment chosen) {
        return new ProjectSpec(name, basePackage, versions, database, cache, broker, storage, modules, testedModules, chosen, extras, scans);
    }

    ProjectSpec withExtras(Set<Extra> chosen) {
        return new ProjectSpec(name, basePackage, versions, database, cache, broker, storage, modules, testedModules, deploy, chosen, scans);
    }

    ProjectSpec withScans(Set<Scan> chosen) {
        return new ProjectSpec(name, basePackage, versions, database, cache, broker, storage, modules, testedModules, deploy, extras, chosen);
    }

    boolean has(Extra extra) {
        return extras.contains(extra);
    }

    /** Whether any sample module is kept. */
    boolean samples() {
        return !modules.isEmpty();
    }

    /** Whether the sample module is kept, its tests too. */
    boolean tested(String module) {
        return modules.contains(module) && testedModules.contains(module);
    }

    /** The Gradle project and the directory: {@code my-shop}. */
    String artifact() {
        return name.kebabCase();
    }

    /** The base package as a path: {@code com/myshop}. */
    String packagePath() {
        return basePackage.replace('.', '/');
    }

    /** The prefix of the application class: {@code MyShop}. */
    String classPrefix() {
        StringBuilder prefix = new StringBuilder();
        for (String word : name.kebabCase().split("-")) {
            prefix.append(Character.toUpperCase(word.charAt(0))).append(word, 1, word.length());
        }
        return prefix.toString();
    }

    /** The one lower-case word for plugin ids, config file names and the database: {@code myshop}. */
    String shortName() {
        return name.kebabCase().replace("-", "");
    }
}
