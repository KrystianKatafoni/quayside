# 12 · Strategia testów

Testy mają dawać pewność, że zmiana w jednym module nie zepsuła innego, i to **bez uruchamiania prawdziwego Claude Code**. Wymagania: [10 · sekcja D](10-wymagania-inzynierskie.md#d-testy).

## Piramida

| Poziom | Co testuje | Narzędzia | Gdzie | Uruchamiane |
| --- | --- | --- | --- | --- |
| Architektura | Reguły zależności, widoczność, nazewnictwo, zakazane importy | Konsist | `tests/architecture` | każdy build |
| Jednostkowe | Czyste rdzenie (reducery, polityki, parsery, walidatory, szablony), ViewModele | JUnit 5, Kotest assertions, Turbine | `*/src/test` | każdy build |
| Kontraktowe | Zgodność fake'ów i prawdziwych implementacji z interfejsem `api` | abstrakcyjne zestawy w `testFixtures` | `api/src/testFixtures` + `impl/src/test` | każdy build |
| Integracyjne | Adaptery na prawdziwych zasobach lokalnych | JUnit 5, Ktor client, pliki tymczasowe, fałszywy `claude` | `impl/src/integrationTest` | każdy PR |
| Funkcjonalne | Scenariusze użytkownika przez UI całej aplikacji na fake'owym runtime | Compose UI test (desktop), wirtualny czas | `tests/functional` | każdy PR |
| E2E | Pełny przepływ z prawdziwym Claude Code w repo testowym | JUnit 5 + tag `e2e` | `tests/e2e` | nocą / ręcznie |

## 1. Testy jednostkowe czystych rdzeni

Najwięcej wartości daje testowanie tablicowe funkcji decyzyjnych. Przykład dla reducera stanu sesji:

```kotlin
class SessionStateReducerTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `reduces runtime phase and hook events to session state`(case: ReducerCase) {
        val next = reduce(case.before, case.input, clock = TestClock(T0))
        next.stateOf(SESSION) shouldBe case.expected
    }

    companion object {
        @JvmStatic fun cases() = listOf(
            ReducerCase("hook NeedsInput → AwaitingInput", working(), needsInput("Milo czy Prosys?"), AwaitingInput("Milo czy Prosys?")),
            ReducerCase("polling BLOCKED+Permission wygrywa z brakiem hooka", working(), runtime(BLOCKED, WaitingFor.Permission), AwaitingPermission(null)),
            ReducerCase("polling WORKING po zgodzie czyści blokadę", awaitingPermission(), runtime(WORKING), Working),
            ReducerCase("TurnEnded zapisuje podsumowanie", working(), turnEnded("Chart gotowy, PR #87"), Done),
            ReducerCase("StopFailure → Failed z rodzajem", working(), failed("rate_limit"), Failed("rate_limit")),
        )
    }
}
```

Analogicznie testujemy: `decide()` w powiadomieniach (zdarzenie × ustawienia × cisza nocna × okno w tle → lista dostarczeń), renderowanie szablonów głosu (`{sesja}` → „opcua connector”), walidację katalogu projektu, parser `claude agents --json` na nagranych odpowiedziach (`fixtures/agents-json/*.json`, także z nieznanymi polami i nowymi stanami) oraz **kontrast każdego schematu kolorów** (test liczy WCAG dla par `text/bg`, `muted/bg`, `accentInk/accent` i wymaga ≥ 4.5:1).

ViewModele testujemy przez `Turbine`: intent → sekwencja `UiState`.

## 2. Testy kontraktowe

Każdy interfejs w `api` ma abstrakcyjny zestaw testów w `testFixtures`. Ten sam zestaw uruchamiamy na fake'u (używanym przez inne moduły w ich testach) i na prawdziwej implementacji. Dzięki temu fake nie „rozjeżdża się” z rzeczywistością.

```kotlin
// projects:api / testFixtures
abstract class ProjectsApiContract {
    abstract fun create(tempDir: Path): ProjectsApi

    @Test fun `added project appears in the list`() = runTest { /* … */ }
    @Test fun `remove never deletes files on disk`() = runTest { /* … */ }
    @Test fun `adding the same directory twice returns AlreadyAdded`() = runTest { /* … */ }
}

class FakeProjectsApiTest : ProjectsApiContract() { override fun create(tempDir: Path) = FakeProjectsApi() }
// projects:impl
class JsonProjectStoreContractTest : ProjectsApiContract() { override fun create(tempDir: Path) = projectsModule(testCore(tempDir)).api }
```

Dla `AgentRuntime` kontrakt działa na **fałszywym pliku wykonywalnym `claude`**: skrypt w katalogu tymczasowym, wstawiony na początek `PATH`, który na podstawie argumentów zwraca nagrane odpowiedzi i kody wyjścia (w tym: brak Agent View, stara wersja, timeout, śmieci na stdout). Test jest hermetyczny, a jednocześnie sprawdza prawdziwy `ClaudeCliAdapter` z prawdziwym `ProcessRunner`.

## 3. Testy integracyjne adapterów

| Adapter | Scenariusze |
| --- | --- |
| `KtorHookServer` | Prawdziwy POST na losowy port: poprawny token, brak tokenu (401), zły JSON, nieznany typ zdarzenia, 100 zdarzeń naraz bez utraty |
| `SettingsJsonHookInstaller` | Instalacja w kopii `settings.json` w katalogu tymczasowym: zachowanie cudzych hooków, backup, odinstalowanie, ponowna instalacja idempotentna |
| `JsonStore` | Zapis atomowy, migracja schematu v1→v2, uszkodzony plik → `*.broken` + domyślne |
| `MacSayAdapter` | Na fałszywym `say` na `PATH`: argumenty `-v`, `-r`, kolejkowanie, przerwanie |
| `LoginShellProcessRunner` | Timeout, duże wyjście (ucięcie), kod wyjścia, środowisko z `.zprofile` |
| JediTerm / pty4j | Uruchomienie `echo` w PTY, odczyt wyjścia, `resize`, zamknięcie bez wycieku procesu |

## 4. Testy funkcjonalne (akceptacyjne)

Uruchamiają **całą aplikację** złożoną jak w `app`, ale z fake'ami na brzegach: `FakeAgentRuntime` (skryptowalny), `FakeAgentEventSource`, `RecordingSpeech`, `RecordingSound`, `TestClock`. UI sterujemy przez Compose UI test (kliknięcia, klawisze, odczyt węzłów po `testTag` i semantyce). Każdy scenariusz odpowiada kryterium akceptacji z [09](09-plan-i-otwarte-kwestie.md) lub wymaganiu FR.

```kotlin
@Test fun `agent asks a question - popup and voice within 2 seconds, reply from popup`() = appTest {
    // Given
    givenProject("plantpulse-core")
    runtime.hasSession("7c5dcf5d", name = "opcua-connector", phase = WORKING)

    // When
    events.emit(needsInput(session = "7c5dcf5d", question = "Eclipse Milo czy Prosys SDK?"))
    advanceTimeBy(2.seconds)

    // Then
    onPopup("opcua-connector").assertIsDisplayed().assertTextContains("Milo czy Prosys")
    speech.spoken.single() shouldBe "Agent opcua connector w projekcie plantpulse-core czeka na odpowiedź"
    onSessionRow("opcua-connector").assertGlyph("?")

    onPopup("opcua-connector").typeReply("1, Milo").pressEnter()
    runtime.repliesTo("7c5dcf5d") shouldContainExactly listOf("1, Milo")
}
```

Zestaw minimalny dla MVP:

1. Dodanie projektu pokazuje jego sesje (FR-P1, FR-S1).
2. Usunięcie projektu z działającymi sesjami: obie ścieżki dialogu (FR-P3, FR-P4).
3. Start agenta z ⌘N i promptem → stan „pracuje” (FR-S3, FR-K2).
4. Pytanie agenta → popup + głos + glif `?`; odpowiedź z popupu (FR-N1–N4, FR-S5).
5. Prośba o zgodę → Pozwól z popupu (FR-S6).
6. Cisza nocna i tryb w tle blokują głos, ale nie popup (FR-N5, FR-N6).
7. Utrata hooka: polling sam koryguje stan w ≤ 3 s (NFR-2).
8. Zmiana schematu i typografii w ustawieniach zmienia cały UI bez restartu (FR-A1–A3).
9. Claude Code niezainstalowany lub za stary → baner z akcją naprawczą (NFR-9).

## 5. Testy architektury (Konsist)

```kotlin
// szkic — pakiet = moduł, np. dev.quayside.sessions.impl
private val implOrUi = Regex("""dev\.quayside\.([\w-]+)\.(impl|ui)\b.*""")

class ArchitectureTest {
    @Test fun `impl does not import impl or ui of another feature`() =
        Konsist.scopeFromProject().files
            .filter { it.packagee?.name.orEmpty().endsWith(".impl") }
            .assertFalse { file ->
                val ownFeature = file.packagee!!.name.split('.')[2]
                file.imports.any { imp -> implOrUi.matchEntire(imp.name)?.groupValues?.get(1)?.let { it != ownFeature } == true }
            }

    @Test fun `api and core domain have no IO, process or Compose imports`() = /* java.io, java.net, ProcessBuilder, androidx.compose */
    @Test fun `classes in impl are internal except module factories`() = /* … */
    @Test fun `adapters live only in impl`() = /* klasy *Adapter */
    @Test fun `no stateful singletons`() = /* object z var */
}
```

## 6. Zasady pisania testów

- **Fake'i zamiast mocków.** MockK tylko tam, gdzie fake byłby sztuczny (np. weryfikacja wywołania callbacku).
- Jeden test = jedno zachowanie; nazwy w backtickach opisują zachowanie, nie metodę.
- Struktura Given / When / Then; dane przez buildery z `core:testing` (`aSession(…)`, `aProject(…)`).
- Zero `Thread.sleep`, zero zegara systemowego: `runTest`, `advanceTimeBy`, `TestClock`.
- Zero sieci i zero prawdziwego Claude Code poza tagiem `e2e`.
- Test, który raz przechodzi, a raz nie, jest traktowany jak błąd produkcyjny.

## 7. Progi i bramka CI

| Etap pipeline'u (GitHub Actions, macOS) | Polecenie | Próg |
| --- | --- | --- |
| Format i analiza | `./gradlew spotlessCheck detekt` | 0 naruszeń |
| Architektura | `./gradlew :tests:architecture:test` | 100% reguł |
| Jednostkowe + kontraktowe | `./gradlew test` | wszystkie zielone |
| Integracyjne | `./gradlew integrationTest` | wszystkie zielone |
| Funkcjonalne | `./gradlew :tests:functional:test` | wszystkie zielone |
| Pokrycie | `./gradlew koverVerify` | ≥ 80% linii per `impl`, ≥ 90% w pakietach `*.core` (czyste rdzenie) |
| E2E (nocny) | `./gradlew :tests:e2e:test -Pe2e` | raport, bez blokowania PR |

Cała bramka to `./gradlew check`; ten sam task uruchamia lokalnie programista i agent Claude Code przed zakończeniem pracy.
