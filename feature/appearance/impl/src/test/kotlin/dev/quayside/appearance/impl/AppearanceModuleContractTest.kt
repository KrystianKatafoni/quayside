package dev.quayside.appearance.impl

import dev.quayside.appearance.api.AppearanceApi
import dev.quayside.appearance.api.AppearanceApiContract

/** The API given out by `appearanceModule` passes the shared contract (ENG-T2). */
class AppearanceModuleContractTest : AppearanceApiContract() {
    override fun api(): AppearanceApi = appearanceModule()
}
