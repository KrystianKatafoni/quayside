package dev.agentmanager.buildlogic

/** Coverage targets from docs/12-strategia-testow.md §7 and ENG-T1. */
object QualityThresholds {
    private val featureImpl = Regex("^:feature:[^:]+:impl$")

    /** Minimum line coverage in percent: 80 for feature impl, 90 for core modules, none elsewhere. */
    fun lineCoverage(projectPath: String): Int =
        when {
            featureImpl.matches(projectPath) -> 80
            projectPath.startsWith(":core:") -> 90
            else -> 0
        }
}
