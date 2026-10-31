import dev.quayside.buildlogic.ModuleRules
import dev.quayside.buildlogic.enforceModuleRule

// Public contract of a feature (and core modules): explicit visibility everywhere,
// contracts and fakes in testFixtures, dependencies only on shared core modules.
plugins {
    id("am.kotlin")
    `java-test-fixtures`
}

kotlin {
    explicitApi()
}

enforceModuleRule("api depends only on core:domain, core:design-tokens, core:testing", ModuleRules::violatesApiRule)
