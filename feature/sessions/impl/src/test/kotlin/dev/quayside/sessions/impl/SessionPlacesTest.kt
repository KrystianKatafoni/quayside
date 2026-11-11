package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.SubfolderName
import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import dev.quayside.sessions.api.PlaceAssignment
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.StoredPlace
import dev.quayside.sessions.api.aSession
import dev.quayside.sessions.api.pastSession
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path

/** The pure rules of the manual place (AM-314, ADR 0033): what `assignedDir` a saved place gives, and its key. */
class SessionPlacesTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("resolveTable")
    fun `resolve gives assignedDir by the saved place`(
        @Suppress("UNUSED_PARAMETER") title: String,
        stored: StoredPlace?,
        session: Session,
        projects: List<Project>,
        expected: Path?,
    ) {
        SessionPlaces.resolve(stored, session, projects) shouldBe expected
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validateTable")
    fun `validate accepts the root and exact direct children only`(
        @Suppress("UNUSED_PARAMETER") title: String,
        place: PlaceAssignment,
        project: Project,
        expected: Boolean,
    ) {
        SessionPlaces.validate(place, project) shouldBe expected
    }

    @Test
    fun `disk session is keyed by its uuid`() {
        val disk = pastSession(id = UUID)

        SessionPlaces.key(disk, conversation = null) shouldBe SessionId(UUID)
        SessionPlaces.key(disk, ConversationId(UUID)) shouldBe SessionId(UUID)
    }

    @Test
    fun `supervisor session with a conversation is keyed by the uuid`() {
        SessionPlaces.key(aSession(id = SHORT), ConversationId(UUID)) shouldBe SessionId(UUID)
    }

    @Test
    fun `supervisor session without a conversation is keyed by its id`() {
        SessionPlaces.key(aSession(id = SHORT), conversation = null) shouldBe SessionId(SHORT)
    }

    @Test
    fun `uuid entry wins over the short id entry`() {
        val places = mapOf(SessionId(UUID) to SOFT_PLACE, SessionId(SHORT) to ROOT_PLACE)

        SessionPlaces.find(places, aSession(id = SHORT), ConversationId(UUID)) shouldBe SOFT_PLACE
    }

    @Test
    fun `short id entry is used when there is no uuid entry`() {
        val places = mapOf(SessionId(SHORT) to ROOT_PLACE)

        SessionPlaces.find(places, aSession(id = SHORT), ConversationId(UUID)) shouldBe ROOT_PLACE
        SessionPlaces.find(places, aSession(id = SHORT), conversation = null) shouldBe ROOT_PLACE
    }

    @Test
    fun `no entry gives no place`() {
        val places = mapOf(SessionId("other000") to ROOT_PLACE)

        SessionPlaces.find(places, aSession(id = SHORT), ConversationId(UUID)) shouldBe null
        SessionPlaces.find(places, pastSession(id = UUID), conversation = null) shouldBe null
    }

    @Test
    fun `a disk session ignores an entry under a short id`() {
        val places = mapOf(SessionId(UUID.take(SHORT_LENGTH)) to ROOT_PLACE)

        SessionPlaces.find(places, pastSession(id = UUID), conversation = null) shouldBe null
    }

    companion object {
        private const val UUID = "0b6f0000-0000-4000-8000-000000000000"
        private const val SHORT = "7c5dcf5d"
        private const val SHORT_LENGTH = 8
        private val DIR: Path = Path.of("/p")
        private val P1 = ProjectId("p1")
        private val SOFT_PLACE = StoredPlace(P1, PlaceAssignment.Subfolder("soft"))
        private val ROOT_PLACE = StoredPlace(P1, PlaceAssignment.ProjectRoot)
        private val group = aProject(id = "p1", dir = DIR, subfolders = subfolderNames("soft", "docs"))
        private val plain = aProject(id = "p1", dir = DIR)
        private val session = aSession(id = SHORT, project = P1, cwd = DIR.resolve("marketing"))

        private fun strange(name: String) = aProject(id = "p1", dir = DIR, subfolders = listOf(SubfolderName(name)))

        @JvmStatic
        @Suppress("LongMethod")
        fun resolveTable(): List<Arguments> =
            listOf(
                arguments("no entry", null, session, listOf(group), null),
                arguments("a subfolder", SOFT_PLACE, session, listOf(group), DIR.resolve("soft")),
                arguments("the root", ROOT_PLACE, session, listOf(group), DIR),
                arguments("the root of a project without subfolders", ROOT_PLACE, session, listOf(plain), DIR),
                arguments(
                    "another project than the entry's",
                    StoredPlace(ProjectId("p2"), PlaceAssignment.Subfolder("soft")),
                    session,
                    listOf(group, aProject(id = "p2", dir = DIR.resolve("soft"), subfolders = subfolderNames("soft"))),
                    null,
                ),
                arguments(
                    "a nested project took the session",
                    SOFT_PLACE,
                    session.copy(project = ProjectId("nested")),
                    listOf(group, aProject(id = "nested", dir = DIR.resolve("marketing"))),
                    null,
                ),
                arguments("the subfolder left the group", SOFT_PLACE, session, listOf(plain), null),
                arguments(
                    "another letter case",
                    StoredPlace(P1, PlaceAssignment.Subfolder("Soft")),
                    session,
                    listOf(group),
                    null,
                ),
                arguments(
                    "a name with dots",
                    StoredPlace(P1, PlaceAssignment.Subfolder("../x")),
                    session,
                    listOf(strange("../x")),
                    null,
                ),
                arguments(
                    "a name with a separator",
                    StoredPlace(P1, PlaceAssignment.Subfolder("a/b")),
                    session,
                    listOf(strange("a/b")),
                    null,
                ),
                arguments("a session without a project", SOFT_PLACE, session.copy(project = null), listOf(group), null),
                arguments("the project is not registered any more", SOFT_PLACE, session, emptyList<Project>(), null),
            )

        @JvmStatic
        fun validateTable(): List<Arguments> =
            listOf(
                arguments("root", PlaceAssignment.ProjectRoot, group, true),
                arguments("root without subfolders", PlaceAssignment.ProjectRoot, plain, true),
                arguments("a subfolder", PlaceAssignment.Subfolder("docs"), group, true),
                arguments("another letter case", PlaceAssignment.Subfolder("Docs"), group, false),
                arguments("not in the group", PlaceAssignment.Subfolder("marketing"), group, false),
                arguments("dots", PlaceAssignment.Subfolder("../x"), strange("../x"), false),
                arguments("a separator", PlaceAssignment.Subfolder("a/b"), strange("a/b"), false),
            )
    }
}
