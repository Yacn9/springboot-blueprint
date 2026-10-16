package com.acme.blueprint.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RewriteRulesTest {

    private static RewriteRules rulesFor(String name) {
        return new RewriteRules(ProjectSpec.defaultsFor(ProjectName.fromUserInput(name)));
    }

    private final RewriteRules rules = rulesFor("my-shop");

    @Test
    void rewritesThePackageInPaths() {
        assertThat(rules.path("modules/customers/impl/src/main/java/com/acme/blueprint/customers/domain/Customer.java"))
                .isEqualTo("modules/customers/impl/src/main/java/com/myshop/customers/domain/Customer.java");
    }

    @Test
    void renamesTheApplicationClassFiles() {
        assertThat(rules.path("app/src/main/java/com/acme/blueprint/BlueprintApplication.java"))
                .isEqualTo("app/src/main/java/com/myshop/MyShopApplication.java");
        assertThat(rules.path("app/src/test/java/com/acme/blueprint/BlueprintApplicationTests.java"))
                .isEqualTo("app/src/test/java/com/myshop/MyShopApplicationTests.java");
    }

    @Test
    void renamesThePrecompiledScriptPlugins() {
        assertThat(rules.path("build-logic/src/main/kotlin/blueprint.java-conventions.gradle.kts"))
                .isEqualTo("build-logic/src/main/kotlin/myshop.java-conventions.gradle.kts");
    }

    @Test
    void renamesThePlatformDefaultsFile() {
        assertThat(rules.path("shared/src/main/resources/config/blueprint-platform.yml"))
                .isEqualTo("shared/src/main/resources/config/myshop-platform.yml");
    }

    @Test
    void leavesUnrelatedPathsAlone() {
        assertThat(rules.path("gradle/wrapper/gradle-wrapper.jar")).isEqualTo("gradle/wrapper/gradle-wrapper.jar");
    }

    @Test
    void rewritesPackagesAndImports() {
        assertThat(rules.content("package com.acme.blueprint.customers;\nimport com.acme.blueprint.shared.SortMapping;", false))
                .isEqualTo("package com.myshop.customers;\nimport com.myshop.shared.SortMapping;");
    }

    @Test
    void rewritesPluginIdsAndTheProjectName() {
        assertThat(rules.content("id(\"blueprint.module-api\")", false)).isEqualTo("id(\"myshop.module-api\")");
        assertThat(rules.content("rootProject.name = \"modular-monolith-blueprint\"", false))
                .isEqualTo("rootProject.name = \"my-shop\"");
    }

    @Test
    void rewritesTheApplicationClassAndTheSystemProperty() {
        assertThat(rules.content("class BlueprintApplicationTests {}", false)).isEqualTo("class MyShopApplicationTests {}");
        assertThat(rules.content("systemProperty(\"blueprint.database-image\", x)", false))
                .isEqualTo("systemProperty(\"myshop.database-image\", x)");
    }

    @Test
    void rewritesAnyRemainingBlueprintInCode() {
        assertThat(rules.content("POSTGRES_DB: blueprint", false)).isEqualTo("POSTGRES_DB: myshop");
    }

    @Test
    void markdownKeepsItsProseButFollowsTheStructuralRenames() {
        String markdown = "A blueprint. See com.acme.blueprint.web, blueprint.module-impl and BlueprintApplication. Blueprint rules.";

        assertThat(rules.content(markdown, true))
                .isEqualTo("A blueprint. See com.myshop.web, myshop.module-impl and MyShopApplication. Blueprint rules.");
    }

    @Test
    void markdownPathsFollowThePackageToo() {
        assertThat(rules.content("`src/main/java/com/acme/blueprint/accounts/package-info.java`", true))
                .isEqualTo("`src/main/java/com/myshop/accounts/package-info.java`");
    }

    /** Output is never scanned again: a name containing "blueprint" must not be rewritten a second time. */
    @ParameterizedTest
    @CsvSource({
            "blueprint-demo,blueprintdemo,BlueprintDemo",
            "my-blueprint,myblueprint,MyBlueprint",
            "blueprint,blueprint,Blueprint",
            "acme,acme,Acme",
            "app,app,App"})
    void neverRewritesItsOwnOutput(String name, String shortName, String classPrefix) {
        RewriteRules awkward = rulesFor(name);
        String template = "package com.acme.blueprint.x; class BlueprintApplication; id(\"blueprint.module-api\"); "
                + "POSTGRES_DB: blueprint; name = \"modular-monolith-blueprint\"; blueprint-platform.yml";

        assertThat(awkward.content(template, false)).isEqualTo(
                "package com." + shortName + ".x; class " + classPrefix + "Application; id(\"" + shortName
                        + ".module-api\"); POSTGRES_DB: " + shortName + "; name = \"" + name + "\"; "
                        + shortName + "-platform.yml");
        assertThat(awkward.path("com/acme/blueprint/BlueprintApplication.java"))
                .isEqualTo("com/" + shortName + "/" + classPrefix + "Application.java");
    }
}
