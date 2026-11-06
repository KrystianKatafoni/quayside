# Changelog

Format: [Keep a Changelog](https://keepachangelog.com/pl/1.1.0/), wersjonowanie: [SemVer](https://semver.org/lang/pl/). Wpisy generuje `/release` z Conventional Commits.

## [Unreleased]

### Fixed

- Dialogi i paleta komend nad otwartą kartą terminala: „Anuluj” i inne przyciski reagują na kliknięcie, a Esc zamyka okno; po zamknięciu fokus wraca do terminala tylko, jeśli w nim był (AM-803).

### Added

- Hooki Claude Code per projekt: okno „Hooki Claude Code” ma listę wierszy „Globalnie” i każdy dodany projekt; „Zainstaluj” i „Odinstaluj” działają na zaznaczonym wierszu i zapisują hooki projektu w `.claude/settings.local.json`; projekt bez folderu pokazuje „Folder projektu nie istnieje.”, a Quayside nie zmienia `.gitignore` projektu (AM-609).
- Okno „Ustawienia podsumowań” (Widok → Ustawienia podsumowań… i paleta komend): przełącznik streszczeń modelem Haiku zapisywany w `summaries.json`; zmiana działa od następnego uruchomienia aplikacji, o czym okno informuje (AM-606).
- Lista rozwijana `AmDropdown` zamiast pól z ‹ › (powłoka i katalog startowy terminala; projekt, model i tryb uprawnień w „Nowy agent”; głos i popup w ustawieniach powiadomień): klik, ⏎, spacja i strzałki otwierają listę pod polem (nad nim, gdy brak miejsca), ↑↓ przesuwają bez zawijania, ⏎ wybiera, Esc zamyka tylko listę; „Pomiń zgody” wymaga otwarcia listy, trzech ↓ i ⏎; dłuższe listy (głosy) mają 8 wierszy i przewijanie (AM-817).
- Kanał „Dźwięk” powiadomień gra: krótki dźwięk systemowy (Glass, Ping, Basso, Hero, Pop, Submarine, Tink wg rodzaju zdarzenia), najwyżej jeden na 2 s, wyciszany w ciszy nocnej i Skupieniu. „Agent skończył turę” ma teraz domyślnie popup i dźwięk Hero; `notifications.json` zapisuje tylko odstępstwa od domyślnych, a migracja do schematu 2 przenosi stare pliki na nową domyślną; starsza wersja aplikacji odczyta taki plik jako nowszy i nie zapisze ustawień powiadomień (AM-812, ADR 0024).
- Wygląd zapisuje się w `theme.json` i przeżywa restart. Przełącznik „Dzień i noc z macOS” w oknie Wygląd: osobny motyw na dzień i na noc (segment „Dzień | Noc” nad listą), zmiana trybu macOS przełącza motyw w ciągu ok. 5 s; przyciski „Importuj…” i „Eksportuj…” wczytują szkic z pliku `theme.json` i zapisują go do pliku (import zapisuje się dopiero po „Zastosuj”) (AM-804).
- Ekran „Wygląd” (Widok → Wygląd… i paleta komend): lista 29 motywów z próbką kolorów i plakietką trybu, lista 22 typografii z podpowiedzią motywu, rozmiar interfejsu S/M/L, gęstość listy sesji (Kompaktowa / Komfortowa), font i rozmiar terminala (8–32) oraz podgląd na żywo na prawdziwym ekranie Mission Control z przykładowymi sesjami; „Zastosuj” zmienia kolory, kształt, fonty i skalę okna, a nowe karty terminala dostają kolory i font wybranego motywu (AM-803).
- Kompaktowa gęstość listy sesji: kafelek 28 dp, mniejszy odstęp i jedna linia (podsumowanie tylko w panelu szczegółów) (AM-803).

- Przebudowa Mission Control i paska bocznego wg mockupów z 2026-11-04: „+” w nagłówku PROJEKTY, ikony i pigułki liczników, kafelki liczników strony projektu, wiersze z kafelkiem stanu, panel szczegółów z hierarchią przycisków (główny, zwykłe, groźne), skróty przy akcjach; `AmButton` z rodzajem `DANGER`, ikoną i skrótem (AM-809).
- Tryb terminala (przycisk „Tryb terminala” na pasku kart albo komenda palety): wąski pasek ikon projektów, pasek sesji nad terminalem i „Wróć do listy sesji”; karty z ikoną stanu i przyciskami ikonowymi (AM-809).

- Terminal w kolorach i foncie motywu: tło i tekst z `termBg`/`termText`, font z typografii motywu (IBM Plex Mono dla Plex Violet, rodziny dołączone do aplikacji rejestrowane dla JediTerm), bez białej ramki wokół terminala (AM-807).
- Nowy domyślny wygląd Plex Violet z fontami IBM Plex: wszystkie ekrany (Mission Control, sidebar, terminal, powiadomienia, paleta, okna dialogowe) czytają kolory, kształt i typografię z `AmTheme`; terminal (JediTerm) zostaje w starych kolorach (AM-806).
- Cisza nocna i wyjątki projektów w `notifications.json` (ustawiane w pliku do czasu ekranu w AM-706; cisza domyślnie wyłączona; profile projektu: domyślny / tylko błędy / wyciszony, osobny głos projektu) (AM-705).
- Nowy agent: okno startu agenta z programu (⌘N, komenda palety „Nowy agent…”, menu Projekty → Nowy agent… oraz przycisk „Nowy agent ⌘N” w nagłówku strony projektu); pola: projekt, prompt, nazwa, subagent, model i tryb uprawnień; start dopiero po „Uruchom” lub ⌘⏎ (AM-608).
- Okno „Hooki Claude Code” (Widok → Hooki Claude Code… i paleta komend) oraz propozycja instalacji hooków przy pierwszym uruchomieniu: status, instalacja i odinstalowanie tylko globalnie w `~/.claude/settings.json`, po zgodzie użytkownika, z kopią zapasową przed zmianą; „Pomiń” zapamiętuje wybór w `hook-offer.json` (AM-607).
- Streszczenie sesji modelem Haiku, włączane przez `useModel` w `summaries.json` (domyślnie wyłączone, działa po restarcie): po turze krótkie podsumowanie z ostatniej wiadomości zastępuje model uruchomiony przez `claude -p` bez narzędzi i MCP (AM-604, część B).
- Podsumowanie sesji po zakończeniu tury: najwyżej 2 zdania z ostatniej wiadomości agenta, bez Markdown (AM-604, część A).
- Sekcja „Głos” w ustawieniach powiadomień: wybór głosu z listy zainstalowanych (polskie na początku, litera przeskakuje do głosu), tempo i głośność, 7 komunikatów z przyciskiem „Przywróć domyślny” oraz przełączniki czytania pytania, trybu tylko w tle i wyciszenia przy mikrofonie (to ostatnie jeszcze nie działa, czeka na ADR 0018); działa od razu po zapisaniu (AM-704, część B2).
- Sekcja „Popup” w ustawieniach powiadomień: pozycja (6 miejsc), czas wyświetlania, maksymalna liczba popupów naraz (1–10) i styl; działa od razu po zapisaniu, także przy wyłączonych popupach (AM-704, część B1).
- Ekran „Ustawienia powiadomień” (Widok → Ustawienia powiadomień… i paleta komend): przełączniki Popupy i Głos, macierz 7 rodzajów × 4 kanały i próg minut dla długiej pracy sesji; zmiany działają od razu po zapisaniu (AM-704, część A).
- Głos powiadomień: kanał VOICE mówi przez macOS `say` wybranym głosem, tempem i głośnością, z szablonem wiadomości na rodzaj, najwyżej 1 komunikatem na 20 s (kolejne łączone, np. „3 agenty czekają”) i ciszą, gdy okno aplikacji jest aktywne; sekcja `speech` w `notifications.json` (AM-703).
- Popupy powiadomień: okno zawsze na wierzchu w rogu ekranu ze stosem kart (pasek koloru wg rodzaju, projekt · sesja, pytanie) i akcjami Otwórz (attach), Wycisz 15 min i Zamknij; nie kradnie fokusu terminalowi (AM-702).
- Dzwonek terminala (BEL) zamienia się w powiadomienie (rodzaj `TERMINAL_BELL`, domyślnie popup); przełącznik „Dzwonek terminala pokazuje powiadomienie” w ustawieniach terminala (AM-509).
- Moduły `feature:notifications:api|impl`: `decide()` i ustawienia powiadomień w `notifications.json` (AM-701).
- Procesy `claude --bg` i `respawn` dostają `AM_HOOK_TOKEN` z Keychain, więc hooki HTTP przechodzą uwierzytelnienie serwera hooków (AM-605).
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
- Ustawienia terminala w `terminal.json` (ADR 0016): powłoka (`zsh`, `bash`, `fish`), katalog startowy nowej powłoki (worktree zaznaczonej sesji, projekt, katalog domowy), font, rozmiar, scrollback (domyślnie 100 000 wierszy), ligatury (domyślnie włączone) i Option jako Meta (domyślnie wyłączone); działają w nowych kartach i panelach. Okno „Ustawienia terminala…” (komenda palety `terminal.settings` i menu Terminal) zmienia je i zapisuje do `terminal.json`; błędne pole pokazuje komunikat, a błąd zapisu lub uszkodzony plik ustawień — informację w oknie (AM-508).
- Karty attach: ⏎, `r` i komenda palety pokazują istniejącą kartę attach sesji i dają jej fokus zamiast otwierać drugą kartę i drugi proces `claude attach`; karta po zakończonym procesie attach jest zastępowana w tym samym miejscu; kropka stanu sesji przy karcie attach (kolory jak w Mission Control).
- Obsługa myszą w Mission Control (AM-511, ADR 0015): przyciski „Attach” i „Odpowiedz” w panelu szczegółów, menu wiersza sesji (prawy klik albo „⋯”: attach, odpowiedz, podgląd, zatrzymaj, respawn, usuń z listy), dwuklik na wierszu = attach i zwijana ściągawka skrótów („Skróty” w pasku statusu).
- Serwer hooków w `feature:agent-runtime:impl` (Ktor CIO, `127.0.0.1`, token `X-AM-Token` z Keychain przez `security`): hooki `Notification`, `Stop`, `StopFailure` i `SessionEnd` trafiają do `AgentEventSource` (fabryka `hookEventSourceModule`, ADR 0017).
- Instalator hooków w `feature:agent-runtime:impl` (AM-602, NFR-5): `hookInstallerModule` dopisuje nasze handlery HTTP do `~/.claude/settings.json` albo `.claude/settings.local.json` projektu, zawsze po kopii zapasowej, usuwa tylko nasze handlery albo przywraca kopię; obcych ustawień i hooków nie rusza.

### Changed

- Kolory 23 z 29 motywów poprawione tak, by ekrany spełniały kontrast 4,5:1 (tekst) i 3:1 (znaki): `warn`, `ok`, `err`, `work`, `muted` i w `oatmeal-light` `accent` są minimalnie ściemnione lub rozjaśnione tam, gdzie wcześniej zawodziły; motywy `plex`, `graphite`, `phosphor`, `synth`, `amber` i `contrast` bez zmian (AM-803).
- Nowa nazwa aplikacji: Quayside (wcześniej Agent Manager). Dane aplikacji leżą w `~/Library/Application Support/Quayside/`; listę projektów ze starego katalogu `AgentManager/` trzeba przenieść ręcznie.
- API `feature:terminal:api`: `TerminalSession` ma nowe pole `search` (`TerminalSearch`, `SearchQuery`, `ScrollbackMatcher`) — zmiana kontraktu wg ENG-I6.
