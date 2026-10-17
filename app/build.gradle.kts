plugins {
    id("blueprint.spring-boot-application")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.modulith:spring-modulith-api")

    // Modules are runtime-only: :app wires them together but cannot compile
    // against a single class inside them.
    runtimeOnly(project(":users-impl"))
    runtimeOnly(project(":notifications-impl"))

    runtimeOnly("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("org.springframework.modulith:spring-modulith-actuator")
    runtimeOnly(libs.springdoc.webmvc.ui)

    // Starts compose.yaml (the database) on bootRun and wires the DataSource to it.
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation(project(":shared")) {
        capabilities { requireFeature("test-database") }
    }
    testImplementation("org.awaitility:awaitility")
}

tasks.bootRun {
    // compose.yaml lives at the repository root.
    workingDir = rootDir
}
