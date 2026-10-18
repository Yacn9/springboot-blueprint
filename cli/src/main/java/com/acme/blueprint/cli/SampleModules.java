package com.acme.blueprint.cli;

import java.util.List;
import java.util.Optional;

/**
 * The sample modules — users and notifications, everything under
 * {@code modules/} — and what else changes when a project keeps fewer: the
 * build stops naming the ones left out, the end-to-end test and ArchUnit
 * settings come from an overlay ({@code overlays/no-samples/} without any,
 * {@code overlays/users-only/} with users alone), and the README follows.
 * Notifications alone is not possible: it compiles against users' contract.
 */
final class SampleModules {

    static final String USERS = "users";
    static final String NOTIFICATIONS = "notifications";
    /** In the order the build names them. */
    static final List<String> ALL = List.of(USERS, NOTIFICATIONS);

    static final String MODULES = "modules/";
    static final String OVERLAY = Database.OVERLAYS + "no-samples/";
    static final String USERS_ONLY_OVERLAY = Database.OVERLAYS + "users-only/";

    private static final String SETTINGS = "settings.gradle.kts";
    private static final String APP_BUILD = "app/build.gradle.kts";
    private static final String MODULE_INCLUDES = "businessModule(\"users\")\nbusinessModule(\"notifications\")\n";
    private static final String MODULE_DEPENDENCIES =
            "    runtimeOnly(project(\":users-impl\"))\n    runtimeOnly(project(\":notifications-impl\"))\n";
    private static final String README_LAYOUT = """
            modules/users/api    :users-api      public contract (plain Java)
            modules/users/impl   :users-impl     domain · application · infrastructure · web
            modules/notifications/…  :notifications-api / :notifications-impl
            """;
    private static final String README_SLICE_START = "Try it:";
    private static final String README_SLICE_END = "lists the\nsteps.\n";
    private static final String README_NOTIFICATIONS_LAYOUT = "modules/notifications/…  :notifications-api / :notifications-impl\n";
    private static final String README_NOTIFICATIONS_TRY =
            "curl \"localhost:8080/api/notifications?userId=<id from the response>\"   # the welcome message\n";
    private static final String README_NOTIFICATIONS_SLICE = """
            `users` is the full reference slice — copy its shape. `notifications`
            shows both ways modules talk: it reacts to `UserRegistered` events and
            calls `UserDirectory` synchronously. Replace or delete both once you have
            your own modules;""";

    private SampleModules() {
    }

    /** Whether a template file under {@code modules/} stays: its module is kept, and its tests if they are wanted. */
    static boolean keeps(ProjectSpec spec, String path) {
        if (!path.startsWith(MODULES)) {
            return true;
        }
        String module = path.substring(MODULES.length()).split("/", 2)[0];
        return spec.modules().contains(module) && (spec.tested(module) || !path.contains("/src/test/"));
    }

    /** The overlay for the modules kept, if they need one. */
    static Optional<String> overlay(ProjectSpec spec) {
        if (!spec.samples()) {
            return Optional.of(OVERLAY);
        }
        return spec.modules().contains(NOTIFICATIONS) ? Optional.empty() : Optional.of(USERS_ONLY_OVERLAY);
    }

    /** The file without the modules left out; any other file unchanged. Checked against the real template by its tests. */
    static String remove(ProjectSpec spec, String path, String text) {
        if (!spec.samples()) {
            return switch (path) {
                case SETTINGS -> text.replace(MODULE_INCLUDES,
                        "// Add a module with businessModule(\"<name>\") — see \"Adding a New Module\" in docs/architecture.md.\n");
                // Modules bring :shared (the database, cache and broker libraries); without any, the application needs it itself.
                case APP_BUILD -> text.replace(MODULE_DEPENDENCIES,
                        "    // runtimeOnly(project(\":<name>-impl\")) for each module.\n    runtimeOnly(project(\":shared\"))\n");
                default -> text;
            };
        }
        String result = text;
        for (String module : ALL) {
            if (!spec.modules().contains(module)) {
                result = switch (path) {
                    case SETTINGS -> result.replace("businessModule(\"" + module + "\")\n", "");
                    case APP_BUILD -> result.replace("    runtimeOnly(project(\":" + module + "-impl\"))\n", "");
                    default -> result;
                };
            }
        }
        return result;
    }

    /** The generated README for the modules kept. */
    static String readme(ProjectSpec spec, String text) {
        if (spec.modules().contains(NOTIFICATIONS)) {
            return text;
        }
        if (spec.samples()) {
            return text.replace(README_NOTIFICATIONS_LAYOUT, "")
                    .replace(README_NOTIFICATIONS_TRY, "")
                    .replace(README_NOTIFICATIONS_SLICE,
                            "`users` is the full reference slice — copy its shape. Replace or delete it\nonce you have your own modules;");
        }
        String layout = text.replace(README_LAYOUT, "modules/                 business modules — none yet\n");
        int start = layout.indexOf(README_SLICE_START);
        int end = layout.indexOf(README_SLICE_END);
        if (start < 0 || end < 0) {
            return layout;
        }
        return layout.substring(0, start)
                + "There are no business modules yet: \"Adding a New Module\" in the architecture\ndocument lists the steps.\n"
                + layout.substring(end + README_SLICE_END.length());
    }
}
