package com.acme.blueprint.cli;

import java.util.Set;

/**
 * Everything the generator needs to know about the project to create: the
 * answers, with each role a name plays under its own accessor. Today every
 * role derives from the project name; a later question (a base package, a
 * database) changes the one accessor it concerns and nothing else.
 *
 * @param name        what the user calls the project
 * @param basePackage the Java base package, such as {@code com.myshop}
 * @param versions    the Java, Spring Boot and Gradle versions to pin; null keeps the template's
 * @param database    the database the project runs on
 * @param cache       what holds the cache entries
 * @param broker      the message broker the events are also published to
 * @param samples     whether the sample modules (users, notifications) are kept
 * @param deploy      where the project is meant to run
 * @param extras      the yes-or-no parts it has
 */
record ProjectSpec(ProjectName name, String basePackage, ProjectVersions versions, Database database,
        CacheProvider cache, BrokerProvider broker, boolean samples, Deployment deploy, Set<Extra> extras) {

    ProjectSpec(ProjectName name, String basePackage) {
        this(name, basePackage, null, Database.POSTGRESQL, CacheProvider.NONE, BrokerProvider.NONE, true, Deployment.NONE, Set.of(Extra.TESTS, Extra.COMPOSE));
    }

    /** The defaults for a project named only by its name. */
    static ProjectSpec defaultsFor(ProjectName name) {
        return new ProjectSpec(name, "com." + name.kebabCase().replace("-", ""));
    }

    ProjectSpec withVersions(ProjectVersions chosen) {
        return new ProjectSpec(name, basePackage, chosen, database, cache, broker, samples, deploy, extras);
    }

    ProjectSpec withDatabase(Database chosen) {
        return new ProjectSpec(name, basePackage, versions, chosen, cache, broker, samples, deploy, extras);
    }

    ProjectSpec withCache(CacheProvider chosen) {
        return new ProjectSpec(name, basePackage, versions, database, chosen, broker, samples, deploy, extras);
    }

    ProjectSpec withDeploy(Deployment chosen) {
        return new ProjectSpec(name, basePackage, versions, database, cache, broker, samples, chosen, extras);
    }

    ProjectSpec withExtras(Set<Extra> chosen) {
        return new ProjectSpec(name, basePackage, versions, database, cache, broker, samples, deploy, Set.copyOf(chosen));
    }

    boolean has(Extra extra) {
        return extras.contains(extra);
    }

    ProjectSpec withBroker(BrokerProvider chosen) {
        return new ProjectSpec(name, basePackage, versions, database, cache, chosen, samples, deploy, extras);
    }

    ProjectSpec withSamples(boolean kept) {
        return new ProjectSpec(name, basePackage, versions, database, cache, broker, kept, deploy, extras);
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
