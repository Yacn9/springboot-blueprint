import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.kotlin.dsl.withType

/**
 * Fails the build at configuration time when this project declares a project
 * dependency that [isAllowed] rejects — before anything compiles, with a
 * message pointing at the offending line.
 */
fun Project.restrictProjectDependencies(rule: String, isAllowed: (targetPath: String) -> Boolean) {
    val projectPath = path
    configurations.configureEach {
        val configurationName = name
        dependencies.withType<ProjectDependency>().configureEach {
            check(isAllowed(path)) {
                "$projectPath must not depend on $path (configuration '$configurationName'). $rule"
            }
        }
    }
}
