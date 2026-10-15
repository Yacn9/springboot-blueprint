package com.acme.blueprint;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import java.util.Set;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.CompositeArchRule;

import org.junit.jupiter.api.Test;

/**
 * Rules inside a module, which Spring Modulith does not check. Modulith
 * guards the boundaries between modules; these guard the layering within
 * one. Annotations are referenced by name so :app needs no compile-time
 * dependency on Spring MVC or JPA.
 */
class ArchitectureRulesTests {

    private static final String ROOT = "com.acme.blueprint";
    private static final String MODULE = ROOT + ".*";
    private static final String WEB = MODULE + ".web..";
    private static final String APPLICATION = MODULE + ".application..";
    private static final String DOMAIN = MODULE + ".domain..";
    private static final String INFRASTRUCTURE = MODULE + ".infrastructure..";

    /** Spring JDBC's template and object APIs, and the JDK's own. */
    private static final String[] JDBC_PACKAGES = {
            "java.sql..", "javax.sql..", "org.springframework.jdbc.core..", "org.springframework.jdbc.object.."};

    private static final Set<String> SQL_ANNOTATIONS = Set.of(
            "jakarta.persistence.NamedNativeQuery", "jakarta.persistence.NamedNativeQueries",
            "jakarta.persistence.NamedStoredProcedureQuery", "jakarta.persistence.NamedStoredProcedureQueries",
            "org.hibernate.annotations.NamedNativeQuery", "org.hibernate.annotations.NamedNativeQueries",
            "org.springframework.data.jpa.repository.NativeQuery",
            "org.springframework.data.jpa.repository.query.Procedure");
    private static final String SPRING_DATA_QUERY = "org.springframework.data.jpa.repository.Query";
    private static final Set<String> COLUMN_ANNOTATIONS = Set.of(
            "jakarta.persistence.Column", "jakarta.persistence.JoinColumn");

    /**
     * Test support — :shared's {@code testDatabase} jar is on :app's test
     * classpath, and DO_NOT_INCLUDE_TESTS only skips class directories — is
     * not production code, so a vendor-specific test helper may use JDBC.
     */
    private static final ImportOption EXCLUDING_TEST_SUPPORT = location -> !location.contains("/shared/testing/");

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(EXCLUDING_TEST_SUPPORT)
            .importPackages(ROOT);

    @Test
    void layersInsideEachModuleDependInwards() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Web").definedBy(WEB)
                .layer("Application").definedBy(APPLICATION)
                .layer("Domain").definedBy(DOMAIN)
                .layer("Infrastructure").definedBy(INFRASTRUCTURE)
                .whereLayer("Web").mayNotBeAccessedByAnyLayer()
                .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Web", "Infrastructure")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Web")
                .check(CLASSES);
    }

    /** The documented allow-list: the JDK, JPA mapping annotations and the project's own types. */
    @Test
    void domainUsesOnlyTheJdkJpaAndProjectTypes() {
        classes().that().resideInAPackage(DOMAIN)
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "jakarta.persistence..", ROOT + "..")
                .because("the domain must stay unit-testable without Spring, Hibernate or Jackson")
                .check(CLASSES);
    }

    /** Persistence is reached through ports; Page/Pageable (spring-data-commons) is the accepted exception. */
    @Test
    void applicationLayerDoesNotTouchPersistenceTechnology() {
        noClasses().that().resideInAPackage(APPLICATION)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.persistence..", "org.springframework.data.jpa..", "org.hibernate..")
                .check(CLASSES);
    }

    @Test
    void moduleContractsAreFrameworkFree() {
        classes().that().resideInAPackage(MODULE)
                .and().resideOutsideOfPackage(ROOT + ".shared")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", MODULE)
                .because("an -api project is a plain-Java contract: no frameworks, no other module's internals")
                .check(CLASSES);
    }

    /**
     * Use cases return live aggregates, but once they return the transaction is
     * over: a mutation made in a controller would show in the response and
     * never be saved. State changes belong in the application layer.
     */
    @Test
    void webLayerDoesNotMutateEntities() {
        noClasses().that().resideInAPackage(WEB)
                .should().callMethodWhere(describe("a state-changing (void) method of an entity",
                        (JavaMethodCall call) -> call.getTargetOwner().isAnnotatedWith("jakarta.persistence.Entity")
                                && call.getTarget().getRawReturnType().isEquivalentTo(void.class)))
                .check(CLASSES);
    }

    /**
     * SQL lives only in the Flyway migrations, so they are the only code that
     * changes with the database vendor. Java queries through JPQL, the Criteria
     * API or derived Spring Data queries, which Hibernate renders per dialect.
     * Hibernate's own SQL-fragment annotations ({@code @Formula},
     * {@code @SQLRestriction}, …) need no rule: entities live in {@code domain},
     * which may not use {@code org.hibernate}.
     */
    @Test
    void sqlLivesOnlyInMigrations() {
        var embedsSql = describe("SQL embedded in Java", ArchitectureRulesTests::declaresSql);

        CompositeArchRule.of(noClasses()
                        .should().dependOnClassesThat().resideInAnyPackage(JDBC_PACKAGES)
                        .orShould().callMethodWhere(describe("a call that runs SQL", ArchitectureRulesTests::runsSql))
                        .orShould().beAnnotatedWith(embedsSql))
                .and(noMethods().should().beAnnotatedWith(embedsSql))
                .and(noFields().should().beAnnotatedWith(embedsSql))
                .because("a statement written in Java is written for one database vendor")
                .check(CLASSES);
    }

    @Test
    void controllersLiveInTheWebLayer() {
        classes().that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should().resideInAPackage(WEB)
                .check(CLASSES);
    }

    @Test
    void entitiesLiveInTheDomain() {
        classes().that().areAnnotatedWith("jakarta.persistence.Entity")
                .should().resideInAPackage(DOMAIN)
                .check(CLASSES);
    }

    @Test
    void springDataRepositoriesStayInsideTheInfrastructureLayer() {
        classes().that().areAssignableTo("org.springframework.data.repository.Repository")
                .should().resideInAPackage(INFRASTRUCTURE)
                .andShould().notBePublic()
                .check(CLASSES);
    }

    /** Native or stored-procedure queries, {@code @Query(nativeQuery = true)} and a column's DDL type. */
    private static boolean declaresSql(JavaAnnotation<?> annotation) {
        String type = annotation.getRawType().getName();
        if (SQL_ANNOTATIONS.contains(type)) {
            return true;
        }
        if (type.equals(SPRING_DATA_QUERY)) {
            return Boolean.TRUE.equals(annotation.get("nativeQuery").orElse(false));
        }
        if (COLUMN_ANNOTATIONS.contains(type)) {
            return annotation.get("columnDefinition").filter(definition -> !"".equals(definition)).isPresent();
        }
        return false;
    }

    /**
     * Native and stored-procedure queries, raw JDBC work, and Criteria API calls
     * to database functions by name. A vendor function written inside a JPQL
     * string cannot be seen from here — that one is for code review.
     */
    private static boolean runsSql(JavaMethodCall call) {
        String method = call.getTarget().getName();
        JavaClass owner = call.getTargetOwner();
        return method.startsWith("createNative")
                || method.contains("StoredProcedure")
                || method.equals("function") && owner.isAssignableTo("jakarta.persistence.criteria.CriteriaBuilder")
                || (method.equals("doWork") || method.equals("doReturningWork"))
                        && owner.getPackageName().startsWith("org.hibernate");
    }
}
