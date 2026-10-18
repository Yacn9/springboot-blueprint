package com.acme.blueprint.cli;

/**
 * The sample modules — users and notifications, everything under
 * {@code modules/} — and what else changes when a project starts without them:
 * the build stops naming them, the end-to-end test and ArchUnit settings come
 * from {@code overlays/no-samples/}, and the README says there are none yet.
 */
final class SampleModules {

    static final String MODULES = "modules/";
    static final String OVERLAY = Database.OVERLAYS + "no-samples/";

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

    private SampleModules() {
    }

    /** The file without the samples; any other file unchanged. Checked against the real template by its tests. */
    static String remove(String path, String text) {
        return switch (path) {
            case SETTINGS -> text.replace(MODULE_INCLUDES,
                    "// Add a module with businessModule(\"<name>\") — see \"Adding a New Module\" in docs/architecture.md.\n");
            // Modules bring :shared (the database, cache and broker libraries); without any, the application needs it itself.
            case APP_BUILD -> text.replace(MODULE_DEPENDENCIES,
                    "    // runtimeOnly(project(\":<name>-impl\")) for each module.\n    runtimeOnly(project(\":shared\"))\n");
            default -> text;
        };
    }

    /** The generated README without the samples. */
    static String readme(String text) {
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
