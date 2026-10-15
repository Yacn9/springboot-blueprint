package com.acme.blueprint;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Spring Modulith's view of the whole system: no cycles between modules and
 * no module reaching into another's internal packages. Gradle already blocks
 * most violations at compile time; this catches the rest (for example, an
 * -api project that starts referencing an internal type through reflection
 * or a misplaced package).
 */
class ModularityTests {

    private static final ApplicationModules MODULES = ApplicationModules.of(BlueprintApplication.class);

    @Test
    void modulesRespectTheirBoundaries() {
        MODULES.verify();
    }

    /** Writes C4/PlantUML component diagrams and module canvases to build/spring-modulith-docs. */
    @Test
    void writesModuleDocumentation() {
        new Documenter(MODULES).writeDocumentation();
    }
}
