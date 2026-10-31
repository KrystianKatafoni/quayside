package dev.agentmanager.projects.api

import java.nio.file.Path

class FakeProjectsApiContractTest : ProjectsApiContract() {
    override fun create(tempDir: Path): ProjectsApi =
        FakeProjectsApi(pathKind = PathKind::onDisk, realPath = PathKind::realPathOnDisk)
}
