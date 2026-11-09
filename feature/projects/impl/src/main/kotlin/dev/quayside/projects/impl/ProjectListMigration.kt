package dev.quayside.projects.impl

import dev.quayside.core.storage.Migration

/** Schema of `projects.json` (ADR 0026): version 2 added the optional `subfolders` key of an entry. */
internal object ProjectListMigration {
    const val SCHEMA_VERSION = 2

    /** The migrations from schema 1 up to [SCHEMA_VERSION]; 1 to 2 changes no data. */
    fun migrations(): List<Migration> = listOf(Migration(fromVersion = 1) { data -> data })
}
