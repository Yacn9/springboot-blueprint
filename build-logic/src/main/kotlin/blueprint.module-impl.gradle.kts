// A module's implementation: domain, application, infrastructure and web.
// Nothing outside the module may compile against it — :app only pulls it in
// at runtime.
plugins {
    id("blueprint.java-conventions")
    `java-library`
}

// The dependency rule: an implementation sees other modules only through their
// -api projects. Declaring a dependency on another -impl fails the build.
restrictProjectDependencies(
    "A module implementation may only depend on :shared and on other modules' -api projects."
) { target -> target == ":shared" || target.endsWith("-api") }

dependencies {
    implementation(project(":shared"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.modulith:spring-modulith-events-api")

    testImplementation(testFixtures(project(":shared")))
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
}
