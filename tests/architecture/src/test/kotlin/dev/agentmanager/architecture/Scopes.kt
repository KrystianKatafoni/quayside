package dev.agentmanager.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import org.junit.jupiter.api.Assumptions.assumeTrue

/** Production sources of the whole repository, grouped by the module they live in. */
internal object Scopes {
    private val mainFiles: List<KoFileDeclaration> by lazy {
        Konsist.scopeFromProject().files.filter {
            it.path.contains("/src/main/kotlin/") &&
                !it.path.contains("/build-logic/")
        }
    }

    fun main(): List<KoFileDeclaration> = mainFiles

    fun coreDomain(): List<KoFileDeclaration> = mainFiles.filter { it.path.contains("/core/domain/src/main/") }

    fun featureApi(): List<KoFileDeclaration> = mainFiles.filter { FEATURE_API.containsMatchIn(it.path) }

    fun featureImpl(): List<KoFileDeclaration> = mainFiles.filter { FEATURE_IMPL.containsMatchIn(it.path) }

    private val FEATURE_API = Regex("/feature/[^/]+/api/src/main/")
    private val FEATURE_IMPL = Regex("/feature/[^/]+/impl/src/main/")
}

/** Rules for modules that do not exist yet are reported as skipped instead of passing silently. */
internal fun <T> List<T>.requireModules(what: String): List<T> =
    also {
        assumeTrue(isNotEmpty(), "No $what yet")
    }
