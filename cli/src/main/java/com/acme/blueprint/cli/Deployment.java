package com.acme.blueprint.cli;

import java.util.Arrays;
import java.util.List;

/**
 * Where the project is meant to run. Docker adds the Dockerfile (and, with
 * compose, the application as a compose service); Kubernetes adds a Helm
 * chart on top of the Docker image.
 */
enum Deployment {

    NONE("none", List.of()),
    DOCKER("docker", List.of("deploy-docker")),
    KUBERNETES("kubernetes", List.of("deploy-docker", "deploy-kubernetes"));

    private final String id;
    private final List<String> overlays;

    Deployment(String id, List<String> overlays) {
        this.id = id;
        this.overlays = overlays;
    }

    static List<String> ids() {
        return Arrays.stream(values()).map(deployment -> deployment.id).toList();
    }

    static Deployment of(String id) {
        return Arrays.stream(values()).filter(deployment -> deployment.id.equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown deployment: " + id));
    }

    String id() {
        return id;
    }

    /** As the menu shows it. */
    Choice choice() {
        return switch (this) {
            case NONE -> Choice.of(id, "None");
            case DOCKER -> Choice.of(id, "Docker", "a Dockerfile, and the app in compose");
            case KUBERNETES -> Choice.of(id, "Kubernetes", "Docker plus a Helm chart");
        };
    }

    boolean containerised() {
        return this != NONE;
    }

    List<String> overlays() {
        return overlays.stream().map(name -> Database.OVERLAYS + name + "/").toList();
    }
}
