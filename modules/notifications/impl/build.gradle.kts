plugins {
    id("blueprint.module-impl")
}

dependencies {
    api(project(":notifications-api"))

    // Notifications depends on users — never the other way round.
    implementation(project(":users-api"))
}
