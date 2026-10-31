package dev.agentmanager.terminal.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Grouping of tabs by project (plan AM-503, decision 1). */
class TabGroupsTest {
    @Test
    fun `tabs are grouped by project in order of first opening`() {
        val tabs = listOf(aTab(1, BETA), aTab(2, ALPHA), aTab(3, BETA), aTab(4, ALPHA))

        val groups = TabGroups.of(tabs)

        groups.map { it.project.id } shouldBe listOf(BETA.id, ALPHA.id)
        groups.map { g -> g.tabs.map { it.id.value } } shouldBe listOf(listOf(1, 3), listOf(2, 4))
    }

    @Test
    fun `a new tab of a known project joins its group`() {
        val before = TabGroups.of(listOf(aTab(1, ALPHA), aTab(2, BETA)))
        before.size shouldBe 2

        val after = TabGroups.of(listOf(aTab(1, ALPHA), aTab(2, BETA), aTab(3, ALPHA)))

        after.map { it.project.id } shouldBe listOf(ALPHA.id, BETA.id)
        after.first().tabs.map { it.id.value } shouldBe listOf(1, 3)
    }

    @Test
    fun `the group name comes from the first tab`() {
        val renamed = ALPHA.copy(name = "Renamed")

        val groups = TabGroups.of(listOf(aTab(1, ALPHA), aTab(2, renamed)))

        groups.single().project.name shouldBe ALPHA.name
    }

    @Test
    fun `no tabs means no groups`() {
        TabGroups.of(emptyList()) shouldBe emptyList()
    }
}
