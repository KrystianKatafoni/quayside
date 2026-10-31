# Changelog

Format: [Keep a Changelog](https://keepachangelog.com/pl/1.1.0/), wersjonowanie: [SemVer](https://semver.org/lang/pl/). Wpisy generuje `/release` z Conventional Commits.

## [Unreleased]

### Added

- Dokumentacja koncepcyjna, plan wdrożenia i roadmapa.
- Konfiguracja agenta Claude Code: CLAUDE.md, subagenci, skille, hooki i uprawnienia.
- Automatyzacja GitHub: CI, `@claude`, review PR, cotygodniowy przegląd kompatybilności, wydania.
- Build Gradle 9.7 (wrapper, JDK 21), katalog wersji i convention plugins `am.kotlin`, `am.feature.api|impl|ui` z testami TestKit.
- Moduł `core:domain`: identyfikatory, `Outcome`, `Clock`, `Logger`, `AppDispatchers`, `ShellCommand`.
- Moduł `core:testing`: `TestClock`, `testDispatchers`, `RecordingLogger`, buildery danych testowych.
- Reguły zależności modułów w Gradle oraz testy architektury Konsist (`tests:architecture`).
- Bramka jakości: Spotless/ktlint, detekt 2.0, progi pokrycia Kover i szybki task `quickCheck` dla hooka `Stop`.
- Moduł `core:process`: `ProcessRunner` i `LoginShellProcessRunner` (login shell, timeouty, ucinanie wyjścia) z `FakeProcessRunner` w `testFixtures`.
- Moduł `core:storage`: `JsonStore` z atomowym zapisem, wersją schematu, migracjami i odkładaniem uszkodzonych plików jako `*.broken`.
- Moduł `app`: okno Compose Desktop z pustym Mission Control, composition root `CoreDeps` i logowanie przez SLF4J (`./gradlew :app:run`).
- Moduł `feature:agent-runtime:api`: kontrakt `AgentRuntime`, `AgentEventSource` i `HookInstaller` z fake'ami i testami kontraktowymi w `testFixtures`.
- Moduł `feature:agent-runtime:impl`: parser `claude agents --json` i `ClaudeCliAdapter` (lista, start w tle, stop, respawn, usuwanie, logi, attach) z fałszywym `claude` do testów.
- Health check `ClaudeCliAdapter.health()`: wersja Claude Code ≥ 2.1.139, włączony Agent View i stan supervisora (`claude --version`, `claude daemon status`, `claude agents --json`).
- Moduł `feature:projects:api`: kontrakty `ProjectsApi` i `ProjectInspector`, reguły `ProjectInputRules` oraz fake'i z testami kontraktowymi w `testFixtures`.
- Moduł `feature:projects:impl`: `JsonProjectStore` utrwala listę projektów w `projects.json` (schemat v1, ADR 0009) przez `JsonStore`, z fabryką `projectsModule(...)`.
- `DirectoryInspector` w `feature:projects:impl`: sprawdzenie katalogu przed dodaniem (gałąź git bez blokad, `CLAUDE.md`, aktywne sesje, nasze hooki w `.claude/settings*.json` wg ADR 0010) z fabryką `projectInspectorModule(...)`.
- Sidebar projektów w oknie aplikacji (`feature:projects:ui`): dodawanie folderu z dysku (⌘⇧O) z raportem inspektora, usuwanie z potwierdzeniem (⌘⌫) i wyborem, czy zatrzymać aktywne sesje, baner przy problemie z `projects.json`.
- Moduł `feature:sessions:api`: kontrakty `SessionMonitor` (snapshot sesji, zdarzenia) i `SessionCommands` (start, stop, `stopAllIn`, respawn, usuwanie), reguły przypisania sesji do projektu i liczniki, z `FakeSessions` i testami kontraktowymi w `testFixtures`.
- `SessionStateReducer` w `feature:sessions:impl`: czysta funkcja wyliczająca snapshot sesji i zdarzenia z kolejnych odczytów `claude agents --json` (linia bazowa bez powiadomień, zdarzenia tylko przy zmianie stanu, `Stale` przy błędzie odczytu).
- `PollingSessionMonitor` w `feature:sessions:impl`: monitor sesji odpytujący `claude agents --json` co 2 s, z wydłużaniem przerwy po błędach (do 15 s) i snapshotem `Stale` przy błędach odczytu.
- Komendy sesji w `feature:sessions:impl` (start, stop, respawn, usuwanie, `stopAllIn`) na `AgentRuntime` oraz `SessionsApi` w `feature:sessions:api`: fabryka `sessionsModule(...)` zwraca monitor i komendy na wspólnym stanie, a skutek komendy jest w snapshocie, zanim komenda wróci.
- Moduł `feature:mission-control:ui`: Mission Control pokazuje sesje zaznaczonego projektu z filtrem (wszystkie, aktywne, zakończone), panelem szczegółów, paskiem trybu i banerem nieaktualnego snapshotu.
- Sidebar projektów (`feature:projects:ui`): zmiana nazwy projektu, liczniki czekających i pracujących sesji przy każdym projekcie oraz usuwanie projektu z zatrzymaniem jego sesji przez `stopAllIn`.
- Moduły `feature:command-palette:api` i `feature:command-palette:impl`: komendy i skróty niezależne od Compose, `CommandRegistry` z fabryką `commandPaletteModule(...)`, wyszukiwanie `CommandMatcher` (bez wielkości liter i polskich znaków), `FakeCommandRegistry` i testy kontraktowe w `testFixtures`.
- Paleta komend ⌘K (`feature:command-palette:ui`): wyszukiwanie bez polskich znaków, ↑/↓, Enter, Esc, komendy projektów i „Przejdź do projektu”; w menu okna „Zmień nazwę projektu…” i „Widok → Paleta komend…”.
- Skróty j/k (następna/poprzednia sesja) i g p (przełącz projekt) w oknie głównym; komendy palety „Filtr: Wszystkie/Aktywne/Zakończone” (FR-K2, FR-S9).
- Akcje sesji w Mission Control: skrót x (zatrzymaj), przyciski Zatrzymaj/Respawn/Usuń z listy w panelu szczegółów (usuwanie z potwierdzeniem) oraz komendy palety „Zatrzymaj sesję”, „Respawn sesji”, „Usuń sesję z listy” (FR-K2, FR-S4).
- Widoki globalne „Czeka na mnie”, „Pracuje”, „Błędy” pod sidebarem projektów (liczniki ze wszystkich projektów, kolumna projektu w liście) i komendy palety „Widok: …”; wybranie widoku czyści zaznaczenie projektu, a w widoku znikają komendy „Filtr: …” (FR-S9).
- Moduły `feature:terminal:api` i `feature:terminal:impl`: `TerminalApi` otwiera login shell w katalogu projektu i komendy (np. `claude attach`) w sesjach PTY z JediTerm, `send(text)`, zamykanie bez procesów-sierot; ⌘K nie czyści już bufora terminala.
- Podgląd outputu sesji (`space`, przycisk „Podgląd”, komenda palety „Podgląd outputu”): panel pod listą pokazuje ogon `claude logs` wybranej sesji i odświeża go co 3 s. Zmiana w `feature:sessions:api`: `SessionMonitor.recentOutput(id)` i typ `RecentOutput` (każda implementacja `SessionMonitor` musi dodać metodę, ENG-I6); odpowiedź na pytanie agenta (`r`) przechodzi do AM-506.
- Terminal w oknie głównym (`feature:terminal:ui`): karty terminala per projekt, podział na panele (⌘D / ⌘⇧D, przełączanie ⌘⌥←/→), maksymalizacja panelu (⌘⇧↵) i nowa karta powłoki (⌘T).
- Szukanie w scrollbacku terminala (⌘F): wielkość liter, regex, podświetlenia wszystkich trafień, ⏎ starsze / ⇧⏎ nowsze, Esc zamyka pasek (FR-T3).
- ⏎ i komenda palety „Attach sesji w terminalu” otwierają `claude attach` zaznaczonej sesji w nowej karcie terminala jej projektu; przyciski panelu Mission Control nie przejmują fokusu po kliknięciu.
- r i komenda palety „Odpowiedz w terminalu” otwierają `claude attach` zaznaczonej sesji, która czeka na użytkownika (pytanie albo zgoda), w nowej karcie terminala projektu; odpowiedź wpisuje się w pełnym TUI (ADR 0013). Dla sesji, która nie czeka, `r` nic nie robi.
- Karty attach: ⏎, `r` i komenda palety pokazują istniejącą kartę attach sesji i dają jej fokus zamiast otwierać drugą kartę i drugi proces `claude attach`; karta po zakończonym procesie attach jest zastępowana w tym samym miejscu; kropka stanu sesji przy karcie attach (kolory jak w Mission Control).

### Changed

- API `feature:terminal:api`: `TerminalSession` ma nowe pole `search` (`TerminalSearch`, `SearchQuery`, `ScrollbackMatcher`) — zmiana kontraktu wg ENG-I6.
