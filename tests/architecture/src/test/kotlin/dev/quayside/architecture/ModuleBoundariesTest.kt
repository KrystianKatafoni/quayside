package dev.quayside.architecture

import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Test

/** Rules from docs/11-koncepcja-techniczna.md §3 that Gradle alone cannot enforce. */
class ModuleBoundariesTest {
    private val terminalLibraries = listOf("com.jediterm.", "com.pty4j.")
    private val forbiddenInContracts = listOf("java.io.", "java.net.", "java.lang.ProcessBuilder", "androidx.compose.")

    @Test
    fun `core domain has no IO, process or Compose imports`() {
        Scopes.coreDomain().requireModules("core:domain sources").assertFalse { file ->
            file.imports.any { import -> forbiddenInContracts.any { import.name.startsWith(it) } }
        }
    }

    @Test
    fun `feature api modules have no IO, process or Compose imports`() {
        Scopes.featureApi().requireModules("feature api modules").assertFalse { file ->
            file.imports.any { import -> forbiddenInContracts.any { import.name.startsWith(it) } }
        }
    }

    @Test
    fun `package follows the module path`() {
        Scopes.main().requireModules("modules").assertTrue { file ->
            file.packagee?.name?.startsWith(expectedPackagePrefix(file.path)) ?: true
        }
    }

    @Test
    fun `impl exposes only its module factory`() {
        Scopes
            .featureImpl()
            .requireModules("feature impl modules")
            .flatMap {
                it.classes() + it.interfaces() +
                    it.objects()
            }.assertTrue { it.hasInternalModifier || it.hasPrivateModifier }
    }

    @Test
    fun `adapters live only in impl modules`() {
        Scopes
            .main()
            .flatMap { it.classes() }
            .withNameEndingWith("Adapter")
            .requireModules("adapters")
            .assertTrue { it.path.contains("/impl/src/main/") }
    }

    @Test
    fun `objects hold no mutable state`() {
        Scopes.main().flatMap { it.objects() }.requireModules("objects").assertFalse { obj ->
            obj.properties().any { it.isVar }
        }
    }

    @Test
    fun `jediterm and pty4j are imported only in terminal impl`() {
        Scopes.main().requireModules("modules").assertTrue { file ->
            val usesTerminalLibs =
                file.imports.any { import -> terminalLibraries.any { import.name.startsWith(it) } }
            !usesTerminalLibs || file.path.contains("/feature/terminal/impl/src/main/")
        }
    }

    private fun expectedPackagePrefix(path: String): String {
        val module = MODULE_PATH.find(path)?.groupValues?.get(1) ?: return "dev.quayside"
        val segments = module.split('/').map { it.replace("-", "") }
        return "dev.quayside." +
            if (segments.first() == "feature") segments.drop(1).joinToString(".") else segments.joinToString(".")
    }

    private companion object {
        val MODULE_PATH = Regex("/((?:core|feature|tests)/[^/]+(?:/(?:api|impl|ui))?)/src/main/")
    }
}
