import dev.agentmanager.buildlogic.ModuleRules
import dev.agentmanager.buildlogic.enforceModuleRule

// Implementation of a feature. Visible only to :app; never depends on another impl or any ui.
plugins {
    id("am.kotlin")
}

enforceModuleRule("impl never depends on impl or ui", ModuleRules::violatesImplRule)
