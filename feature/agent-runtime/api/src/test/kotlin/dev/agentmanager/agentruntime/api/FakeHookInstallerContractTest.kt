package dev.agentmanager.agentruntime.api

class FakeHookInstallerContractTest : HookInstallerContract() {
    override fun create(): HookInstaller = FakeHookInstaller()
}
