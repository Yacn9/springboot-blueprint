package com.acme.blueprint.cli;

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
 * @param samples     whether the sample modules (users, notifications) are kept
 */
record ProjectSpec(ProjectName name, String basePackage, ProjectVersions versions, Database database,
        CacheProvider cache, boolean samples) {

    ProjectSpec(ProjectName name, String basePackage) {
        this(name, basePackage, null, Database.POSTGRESQL, CacheProvider.NONE, true);
    }

    /** The defaults for a project named only by its name. */
    static ProjectSpec defaultsFor(ProjectName name) {
        return new ProjectSpec(name, "com." + name.kebabCase().replace("-", ""));
    }

    ProjectSpec withVersions(ProjectVersions chosen) {
        return new ProjectSpec(name, basePackage, chosen, database, cache, samples);
    }

    ProjectSpec withDatabase(Database chosen) {
        return new ProjectSpec(name, basePackage, versions, chosen, cache, samples);
    }

    ProjectSpec withCache(CacheProvider chosen) {
        return new ProjectSpec(name, basePackage, versions, database, chosen, samples);
    }

    ProjectSpec withSamples(boolean kept) {
        return new ProjectSpec(name, basePackage, versions, database, cache, kept);
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
