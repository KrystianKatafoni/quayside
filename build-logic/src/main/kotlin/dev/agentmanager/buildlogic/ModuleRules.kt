package dev.agentmanager.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

/** Module dependency rules from docs/11-koncepcja-techniczna.md §3, enforced when a dependency is declared. */
object ModuleRules {
    private val featureImplOrUi = Regex("^:feature:[^:]+:(impl|ui)$")
    private val featureImpl = Regex("^:feature:[^:]+:impl$")

    /** Projects an `api` (or core) module may depend on. */
    val allowedForApi: Set<String> = setOf(":core:domain", ":core:design-tokens", ":core:testing")

    /** `impl` -> never another feature's `impl` or any `ui` (ENG-M3). */
    fun violatesImplRule(target: String): Boolean = featureImplOrUi.matches(target)

    /** `ui` -> never an `impl`. */
    fun violatesUiRule(target: String): Boolean = featureImpl.matches(target)

    /** `api` and core modules -> only the shared core modules. */
    fun violatesApiRule(target: String): Boolean = target !in allowedForApi
}

/** Fails the build as soon as this project declares a project dependency that [violates] a rule. */
fun Project.enforceModuleRule(
    ruleName: String,
    violates: (String) -> Boolean,
) {
    val self = path
    configurations.configureEach {
        val configuration = name
        dependencies.withType(ProjectDependency::class.java).configureEach {
            val target = path
            if (target != self && violates(target)) {
                throw GradleException(
                    "Module rule '$ruleName' violated: $self -> $target (configuration '$configuration'). " +
                        "See docs/11-koncepcja-techniczna.md, section 3.",
                )
            }
        }
    }
}
