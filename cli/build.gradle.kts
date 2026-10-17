import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

plugins {
    application
}

group = "com.acme.blueprint"
version = "0.0.1"

dependencies {
    implementation("info.picocli:picocli:4.7.7")

    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "com.acme.blueprint.cli.BlueprintCli"
}

tasks.withType<JavaCompile>().configureEach {
    // Any JDK 17+ can run the CLI, whatever JDK builds it.
    options.release = 17
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:all")
}

// --- The template: the repository itself -----------------------------------

val repositoryRoot: File = layout.projectDirectory.dir("..").asFile
val templateZip: Provider<RegularFile> = layout.buildDirectory.file("template/template.zip")

// Never in a template, whatever git says: version control, tool state, build output, and everything
// that belongs to the blueprint's own distribution — this CLI, its installer and its release workflow.
val excludedDirectories = setOf(".git", ".github", ".gradle", ".kotlin", ".idea", ".vscode", ".claude", ".settings", "build", "out", "bin")
val excludedFileSuffixes = listOf(".iml", ".classpath", ".project")
val distributionOnly = setOf("cli", "install.sh", "jbang-catalog.json")

/** Whether a file or directory below the repository root is, by its place alone, part of the template. */
fun isInTemplate(path: File): Boolean {
    val segments = path.relativeTo(repositoryRoot).invariantSeparatorsPath.split('/')
    return segments.first() !in distributionOnly &&
        segments.none { it in excludedDirectories } &&
        excludedFileSuffixes.none { path.name.endsWith(it) }
}

/**
 * The files git would show: tracked, or untracked but not ignored — so a new
 * entry in .gitignore keeps its files out of the template without a second
 * edit here. Null when git is unavailable (a source tarball); the lists above
 * then decide alone.
 */
fun gitVisiblePaths(): Set<String>? =
    try {
        val git = ProcessBuilder("git", "ls-files", "-z", "--cached", "--others", "--exclude-standard")
            .directory(repositoryRoot)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        val listing = git.inputStream.readBytes().toString(Charsets.UTF_8)
        if (git.waitFor() == 0) listing.split('\u0000').filter { it.isNotEmpty() }.toSet() else null
    } catch (unavailable: java.io.IOException) {
        null
    }

val templateArchive = tasks.register("templateArchive") {
    group = "build"
    description = "Packs the repository, without build output and without the CLI's own distribution files, as the project template."
    outputs.file(templateZip)
    // Always repacked (it is a few hundred kilobytes): Gradle's file trees drop
    // .gitignore and .gitattributes, which the template needs, so inputs
    // cannot be declared reliably.
    outputs.upToDateWhen { false }
    doLast {
        val archive = templateZip.get().asFile
        archive.parentFile.mkdirs()
        val visibleToGit = gitVisiblePaths()
        ZipOutputStream(archive.outputStream().buffered()).use { zip ->
            repositoryRoot.walkTopDown()
                .onEnter { directory -> directory == repositoryRoot || isInTemplate(directory) }
                .filter { it.isFile && isInTemplate(it) }
                .filter { visibleToGit == null || it.relativeTo(repositoryRoot).invariantSeparatorsPath in visibleToGit }
                .sortedBy { it.relativeTo(repositoryRoot).invariantSeparatorsPath }
                .forEach { file ->
                    zip.putNextEntry(ZipEntry(file.relativeTo(repositoryRoot).invariantSeparatorsPath).apply { time = 0 })
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
    }
}

// --- One runnable jar: the CLI, picocli and the template --------------------

tasks.jar {
    archiveBaseName = "blueprint-cli"
    dependsOn(templateArchive)
    manifest {
        attributes("Main-Class" to application.mainClass, "Implementation-Version" to project.version)
    }
    from(templateArchive) { into("template") }
    from({ configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF.sig")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.test {
    useJUnitPlatform()
    dependsOn(templateArchive, tasks.jar)
    systemProperty("blueprint.template.zip", templateZip.get().asFile.absolutePath)
    // InstallScriptTest runs the real install.sh against the jar this build produced.
    val installScript = layout.projectDirectory.file("../install.sh")
    inputs.file(installScript).withPropertyName("installScript")
    systemProperty("blueprint.install.script", installScript.asFile.absolutePath)
    systemProperty("blueprint.cli.jar", tasks.jar.get().archiveFile.get().asFile.absolutePath)
}
