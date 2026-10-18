package com.acme.blueprint.cli;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The OWASP-style scans the security workflow can run ({@code overlays/scans/}),
 * each a job of its own: one left out loses its job and its line in the
 * workflow's header — and Dependency-Check its root build script.
 */
enum Scan {

    CODEQL("codeql", "CodeQL", "SAST: static analysis of the code", "codeql", "#   CodeQL "),
    DEPENDENCY_CHECK("dependency-check", "Dependency-Check", "SCA: libraries with known vulnerabilities",
            "dependency-check", "#   Dependency-Check "),
    ZAP("zap", "ZAP baseline", "DAST: probes the running application", "zap-baseline", "#   ZAP baseline ");

    static final String OVERLAY = Database.OVERLAYS + "scans/";
    static final String WORKFLOW = ".github/workflows/security.yml";
    /** The overlay's root build script, which only Dependency-Check needs. */
    static final String BUILD_SCRIPT = "build.gradle.kts";

    private final String id;
    private final String label;
    private final String note;
    private final String job;
    private final String headerLine;

    Scan(String id, String label, String note, String job, String headerLine) {
        this.id = id;
        this.label = label;
        this.note = note;
        this.job = job;
        this.headerLine = headerLine;
    }

    static List<String> ids() {
        return Arrays.stream(values()).map(scan -> scan.id).toList();
    }

    static Scan of(String id) {
        return Arrays.stream(values()).filter(scan -> scan.id.equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown scan: " + id));
    }

    String id() {
        return id;
    }

    Choice choice() {
        return Choice.of(id, label, note);
    }

    /** The security workflow without the jobs, and header lines, of the scans not chosen; any other file unchanged. */
    static String pin(Set<Scan> chosen, String path, String text) {
        if (!path.equals(WORKFLOW)) {
            return text;
        }
        String workflow = text;
        for (Scan scan : values()) {
            if (!chosen.contains(scan)) {
                workflow = withoutJob(workflow.replaceAll("(?m)^" + Pattern.quote(scan.headerLine) + ".*\n", ""), scan.job);
            }
        }
        return workflow;
    }

    /** Removes a job: from its name to the blank line before the next one, or to the end. */
    private static String withoutJob(String text, String job) {
        int start = text.indexOf("\n  " + job + ":\n");
        if (start < 0) {
            return text;
        }
        int end = text.indexOf("\n\n  ", start + 1);
        return end < 0 ? text.substring(0, start) : text.substring(0, start) + text.substring(end + 1);
    }
}
