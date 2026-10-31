package dev.quayside.agentruntime.api

class FakeHookInstallerContractTest : HookInstallerContract() {
    override fun create(): HookInstaller = FakeHookInstaller()
}
