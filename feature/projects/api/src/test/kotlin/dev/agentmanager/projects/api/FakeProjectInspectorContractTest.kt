package dev.agentmanager.projects.api

import java.nio.file.Path

class FakeProjectInspectorContractTest : ProjectInspectorContract() {
    override fun create(
        tempDir: Path,
        world: InspectionWorld,
    ): ProjectInspector =
        FakeProjectInspector(world = world, pathKind = PathKind::onDisk, realPath = PathKind::realPathOnDisk)
}
