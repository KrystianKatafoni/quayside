package dev.quayside.projects.impl

import dev.quayside.core.storage.StoreSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test

/** Schema 1 to 2 of `projects.json` (ADR 0026): entries of schema 1 are valid entries of schema 2. */
class ProjectListMigrationTest {
    private fun data(text: String): JsonObject = Json.parseToJsonElement(text) as JsonObject

    private fun migrate(data: JsonObject): JsonObject = ProjectListMigration.migrations().single().migrate(data)

    @Test
    fun `current schema is 2`() {
        ProjectListMigration.SCHEMA_VERSION shouldBe 2
    }

    @Test
    fun `one migration starts at schema 1`() {
        ProjectListMigration.migrations() shouldHaveSize 1
        ProjectListMigration.migrations().single().fromVersion shouldBe 1
    }

    @Test
    fun `the migrations fit the schema version of a store spec`() {
        StoreSpec(
            fileName = "projects.json",
            serializer = ProjectListSerializer,
            default = emptyList(),
            schemaVersion = ProjectListMigration.SCHEMA_VERSION,
            migrations = ProjectListMigration.migrations(),
        )
    }

    @Test
    fun `v1 entries migrate unchanged`() {
        val v1 =
            data(
                """{"projects": [
                    {"id": "p1", "dir": "/a", "displayName": "A"},
                    {"id": "p2", "dir": "/b", "displayName": "B"}
                ]}""",
            )

        migrate(v1) shouldBe v1
    }

    @Test
    fun `v1 with an unknown subfolders key keeps it`() {
        val v1 = data("""{"projects": [{"id": "p1", "dir": "/a", "displayName": "A", "subfolders": ["soft"]}]}""")

        migrate(v1) shouldBe v1
    }

    @Test
    fun `data of an unexpected shape is returned as it is`() {
        val odd = data("""{"projects": "not an array", "other": 1}""")

        migrate(odd) shouldBe odd
    }
}
