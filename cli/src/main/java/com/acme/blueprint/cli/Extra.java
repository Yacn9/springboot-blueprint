package com.acme.blueprint.cli;

import java.util.Arrays;
import java.util.List;

/**
 * The yes-or-no parts of a project. {@link #COMPOSE} is the template as it is
 * (answering no removes it); the others add an overlay ({@code overlays/<id>/})
 * and the edits in {@link Extras}.
 */
enum Extra {

    COMPOSE("compose", "Docker Compose for the local database and cache (bootRun starts it)?", true),
    CI("ci", "A GitHub Actions workflow that builds and tests every push?", true),
    SECURITY("security", "Secure the API with JWTs (OAuth2 resource server)?", false),
    OBSERVABILITY("observability", "Observability: Prometheus metrics and OpenTelemetry tracing?", true),
    DEPENDABOT("dependabot", "Dependabot for dependency updates?", true);

    private final String id;
    private final String question;
    private final boolean byDefault;

    Extra(String id, String question, boolean byDefault) {
        this.id = id;
        this.question = question;
        this.byDefault = byDefault;
    }

    static List<String> ids() {
        return Arrays.stream(values()).map(extra -> extra.id).toList();
    }

    String id() {
        return id;
    }

    String question() {
        return question;
    }

    /** What Enter means. */
    boolean byDefault() {
        return byDefault;
    }

    String overlay() {
        return Database.OVERLAYS + id + "/";
    }
}
