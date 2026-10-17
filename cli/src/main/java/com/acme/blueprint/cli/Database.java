package com.acme.blueprint.cli;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The database a project runs on. PostgreSQL is the template as it is; any
 * other vendor is the template plus an <em>overlay</em> — the files under
 * {@code overlays/<id>/} replace or join the template's at the same paths — and
 * the version catalog's {@code database-*} entries rewritten.
 */
enum Database {

    POSTGRESQL("postgresql", "PostgreSQL", Map.of()),
    ORACLE("oracle", "Oracle", Map.of(
            "\"postgres:18-alpine\"", "\"gvenzl/oracle-free:23-slim-faststart\"",
            "\"org.postgresql:postgresql\"", "\"com.oracle.database.jdbc:ojdbc17\"",
            "\"org.flywaydb:flyway-database-postgresql\"", "\"org.flywaydb:flyway-database-oracle\"",
            "\"org.testcontainers:testcontainers-postgresql\"", "\"org.testcontainers:testcontainers-oracle-free\""));

    static final String OVERLAYS = "overlays/";

    private final String id;
    private final String displayName;
    /** The template's catalog values and what they become; checked against the real template by its tests. */
    private final Map<String, String> catalog;

    Database(String id, String displayName, Map<String, String> catalog) {
        this.id = id;
        this.displayName = displayName;
        this.catalog = catalog;
    }

    static List<String> ids() {
        return Arrays.stream(values()).map(database -> database.id).toList();
    }

    static Database of(String id) {
        return Arrays.stream(values()).filter(database -> database.id.equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown database: " + id));
    }

    String displayName() {
        return displayName;
    }

    /** Where this database's overlay lives in the template archive: {@code overlays/oracle/}. */
    String overlay() {
        return OVERLAYS + id + "/";
    }

    /** The version catalog with this database's image and libraries; any other file unchanged. */
    String pin(String path, String text) {
        if (!path.equals(ProjectVersions.CATALOG)) {
            return text;
        }
        String pinned = text;
        for (Map.Entry<String, String> entry : catalog.entrySet()) {
            pinned = pinned.replace(entry.getKey(), entry.getValue());
        }
        return pinned;
    }
}
