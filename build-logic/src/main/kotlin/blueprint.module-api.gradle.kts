// A module's published contract: the only project other modules may compile
// against. It stays plain Java — no Spring, no Jakarta, no Hibernate — so a
// contract can never leak an implementation technology to its consumers.
// ArchitectureRulesTests in :app fails the build if one creeps in.
plugins {
    id("blueprint.java-conventions")
    `java-library`
}

// A contract may build on other contracts, never on an implementation — that
// would put internals on every consumer's compile classpath.
restrictProjectDependencies("A module contract may only depend on other -api projects.") { target ->
    target.endsWith("-api")
}
