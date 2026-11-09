package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SnapshotStatus
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.ZoneOffset

/** Texts of `i18n/missioncontrol.properties` (NFR-10): every key Mission Control can show exists and is filled in. */
internal class MissionControlStringsTest {
    @Test
    fun `every text key has a text`() {
        MissionControlStrings.keys shouldContainAll allKeys()
        allKeys().forEach { key -> withClue(key) { MissionControlStrings[key].shouldNotBeBlank() } }
    }

    @Test
    fun `old mode bar keys are gone`() {
        MissionControlStrings.keys shouldNotContain "modeBar.waiting"
        MissionControlStrings.keys shouldNotContain "modeBar.working"
        MissionControlStrings["modeBar.waiting.one"] shouldContain "{0}"
    }

    @Test
    fun `the new agent button and start show their shortcuts`() {
        MissionControlStrings["newAgent.button"] shouldBe "Nowy agent" // the key cap comes from SessionKeyCaps
        MissionControlStrings["newAgent.start"] shouldContain "Uruchom"
        MissionControlStrings["newAgent.start"] shouldContain "⌘⏎"
    }

    @Test
    fun `the bypass warning says the agent acts without asking`() {
        MissionControlStrings["newAgent.bypassWarning"] shouldContain "bez pytania"
    }

    @Test
    fun `arguments are filled into the text`() {
        argumentTexts().forEach { text ->
            withClue(text) { text.args.forEach { MissionControlStrings.format(text) shouldContain it } }
        }
    }

    @Test
    fun `every stale text starts with the time of the last listing`() {
        RuntimeProblem.entries.forEach { problem ->
            val text = Freshness.of(SnapshotStatus.Stale(T0, problem), ZoneOffset.UTC)
            withClue(problem) { MissionControlStrings.format(text) shouldStartWith "Nieaktualne od 09:00:00" }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixedTexts", "previewTexts", "newAgentTexts")
    fun `fixed texts say what the plan says`(
        key: String,
        expected: String,
    ) {
        MissionControlStrings[key] shouldBe expected
    }

    companion object {
        /** Fixed texts of the composables (not from [StatusLook], [LastEvent], [AgeText] or [Freshness]). */
        val UI_KEYS: Set<String> =
            setOf(
                "empty.noProjects",
                "empty.noProjects.hint",
                "empty.noSelection",
                "header.sessions",
                "filter.all",
                "filter.active",
                "filter.finished",
                "list.loading",
                "details.none",
                "details.worktree",
                "details.id",
                "details.startedAt",
                "details.lastActivity",
                "modeBar.waiting.one",
                "modeBar.waiting.few",
                "modeBar.waiting.many",
                "modeBar.working.one",
                "modeBar.working.few",
                "modeBar.working.many",
                "action.stop",
                "action.respawn",
                "action.stop.busy",
                "action.respawn.busy",
                "action.stop.failed",
                "action.respawn.failed",
                "action.remove",
                "action.remove.busy",
                "action.remove.failed",
                "confirmRemove.title",
                "confirmRemove.hint",
                "confirmRemove.confirm",
                "dialog.cancel",
                "view.waiting",
                "view.working",
                "view.failed",
                "row.noProject",
                "row.project",
                "header.global",
                "preview.title",
                "preview.loading",
                "preview.empty",
                "preview.failed",
                "preview.close",
                "action.attach",
                "action.reply",
                "row.menu",
                "shortcuts.toggle",
                "shortcuts.title",
                "shortcuts.navigate",
                "shortcuts.attach",
                "shortcuts.reply",
                "shortcuts.stop",
                "shortcuts.preview",
                "shortcuts.project",
                "shortcuts.palette",
                "action.preview",
                "newAgent.title",
                "newAgent.button",
                "newAgent.project",
                "newAgent.project.none",
                "newAgent.prompt",
                "newAgent.name",
                "newAgent.agent",
                "newAgent.agent.hint",
                "newAgent.model",
                "newAgent.model.default",
                "newAgent.permission",
                "newAgent.permission.default",
                "newAgent.permission.acceptEdits",
                "newAgent.permission.plan",
                "newAgent.permission.bypass",
                "newAgent.bypassWarning",
                "newAgent.start",
                "newAgent.problem.noProject",
                "newAgent.problem.emptyPrompt",
                "newAgent.problem.promptTooLong",
                "newAgent.problem.nameInvalid",
                "newAgent.problem.agentInvalid",
            )

        private val errors: List<SessionError> =
            listOf(
                SessionError.UnknownSession(SessionId("a")),
                SessionError.SessionActive(SessionId("a")),
                SessionError.UnknownProject(ProjectId("p")),
                SessionError.StopIncomplete(stopped = 0, failed = listOf(SessionId("a"))),
            ) + RuntimeProblem.entries.map { SessionError.Runtime(it) }

        private val states =
            listOf(
                SessionState.Working,
                SessionState.AwaitingInput("q"),
                SessionState.AwaitingPermission("Bash"),
                SessionState.AwaitingPermission(null),
                SessionState.Done,
                SessionState.Failed,
                SessionState.Stopped,
            )

        private val statuses: List<SnapshotStatus> =
            listOf(SnapshotStatus.Loading, SnapshotStatus.Fresh(T0)) +
                RuntimeProblem.entries.flatMap { listOf(SnapshotStatus.Stale(T0, it), SnapshotStatus.Stale(null, it)) }

        private val ages = listOf(0L, 5L, 120L, 7_200L, 172_800L)

        private fun generated(): List<UiText> =
            states.map { UiText(StatusLook.of(it).key) } +
                states.map(LastEvent::of) +
                ages.map { AgeText.of(T0, T0.plusSeconds(it)) } +
                statuses.map { Freshness.of(it, ZoneOffset.UTC) } +
                statuses.map { Freshness.of(it, ZoneOffset.UTC, short = true) } +
                errors.map(SessionActionMessages::reason) +
                errors.map(NewAgentRules::failureOf)

        private fun argumentTexts(): List<UiText> =
            generated().filter { it.args.isNotEmpty() } +
                listOf(
                    UiText("header.sessions", listOf("3")),
                    UiText("modeBar.waiting.few", listOf("2")),
                    UiText("modeBar.working.many", listOf("4")),
                    UiText("header.global", listOf("3")),
                    UiText("row.project", listOf("Quayside")),
                    UiText("preview.title", listOf("fix-login")),
                )

        fun allKeys(): Set<String> = generated().map { it.key }.toSet() + UI_KEYS

        @JvmStatic
        @Suppress("LongMethod")
        fun fixedTexts(): List<Arguments> =
            listOf(
                arguments("empty.noProjects", "Brak projektów"),
                arguments("empty.noSelection", "Wybierz projekt"),
                arguments("view.waiting", "Czeka na mnie"),
                arguments("view.working", "Pracuje"),
                arguments("view.failed", "Błędy"),
                arguments("row.noProject", "bez projektu"),
                arguments("filter.all", "Wszystkie"),
                arguments("filter.active", "Aktywne"),
                arguments("filter.finished", "Zakończone"),
                arguments("action.respawn", "Respawn"),
                arguments("action.attach", "Attach"),
                arguments("action.reply", "Odpowiedz"),
                arguments("shortcuts.toggle", "? skróty klawiszowe"),
                arguments("modeBar.waiting.one", "{0} czeka"),
                arguments("modeBar.waiting.few", "{0} czekają"),
                arguments("modeBar.waiting.many", "{0} czeka"),
                arguments("modeBar.working.one", "{0} pracuje"),
                arguments("modeBar.working.few", "{0} pracują"),
                arguments("modeBar.working.many", "{0} pracuje"),
                arguments("action.stop.busy", "Zatrzymywanie…"),
                arguments("action.respawn.busy", "Respawn…"),
                arguments("action.remove", "Usuń z listy"),
                arguments("action.remove.busy", "Usuwanie…"),
                arguments("action.remove.failed", "Nie udało się usunąć sesji z listy"),
                arguments("confirmRemove.title", "Usunąć sesję z listy?"),
                arguments("confirmRemove.hint", "Sesja zniknie z listy Claude Code. Pliki projektu zostają."),
                arguments("confirmRemove.confirm", "Usuń z listy"),
                arguments("dialog.cancel", "Anuluj"),
                arguments("list.loading", "Wczytywanie sesji…"),
                arguments("details.none", "Wybierz sesję"),
                arguments("fresh.live", "na żywo"),
                arguments("fresh.loading", "wczytywanie"),
                arguments("fresh.short.stale", "nieaktualne od {0}"),
            )

        @JvmStatic
        fun newAgentTexts(): List<Arguments> =
            listOf(
                arguments("newAgent.title", "Nowy agent"),
                arguments("newAgent.permission.default", "Domyślny"),
                arguments("newAgent.permission.acceptEdits", "Akceptuj edycje"),
                arguments("newAgent.permission.plan", "Tylko plan"),
                arguments("newAgent.permission.bypass", "Pomiń zgody"),
                arguments("newAgent.model.default", "Domyślny"),
            )

        @JvmStatic
        fun previewTexts(): List<Arguments> =
            listOf(
                arguments("preview.title", "Podgląd · {0}"),
                arguments("preview.loading", "Wczytywanie outputu…"),
                arguments("preview.empty", "Brak outputu"),
                arguments("preview.failed", "Nie udało się wczytać outputu"),
                arguments("preview.close", "Zamknij  space"),
                arguments("action.preview", "Podgląd  space"),
            )
    }
}
