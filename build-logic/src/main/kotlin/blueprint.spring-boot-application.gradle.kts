// The single deployable. Only :app applies this plugin.
plugins {
    id("blueprint.java-conventions")
    id("org.springframework.boot")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    // Boot's developmentOnly configuration does not extend `implementation`,
    // so it needs the BOM of its own.
    developmentOnly(platform(libs.findLibrary("spring-boot-dependencies").get()))
}
