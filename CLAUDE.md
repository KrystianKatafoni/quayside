# CLAUDE.md — Quayside

Natywna aplikacja macOS (Kotlin + Compose Desktop) do zarządzania lokalnymi agentami Claude Code. Ten plik czyta każda sesja Claude Code w repo. Trzymaj go krótkim; szczegóły są w `docs/`.

## Gdzie co jest

| Potrzebujesz | Plik |
| --- | --- |
| **Stan projektu, środowisko, kontekst (czytaj na starcie)** | `docs/15-stan-projektu.md` |
| Co budujemy (wymagania FR/NFR) | `docs/02-wymagania.md` |
| Jak budujemy (zasady ENG-xx, DoD) | `docs/10-wymagania-inzynierskie.md` |
| Moduły, API, reguły zależności | `docs/11-koncepcja-techniczna.md` |
| Jak testujemy | `docs/12-strategia-testow.md` |
| Kolejność prac | `docs/ROADMAP.md` |
| Proces dostarczania, wydania | `docs/13-plan-wdrozenia.md`, `docs/14-agent-claude-code.md` |
| Decyzje architektoniczne | `docs/adr/` |
| Plan bieżącego zadania | `docs/plans/AM-XXX.md` |

## Złote zasady

1. **Jedno zadanie z roadmapy naraz**, na własnej gałęzi, zakończone jednym PR. Nie dokładaj „przy okazji” zmian spoza planu.
2. **Granice modułów są święte.** `ui` → `api`; `impl` → `api`; `api` → `core:domain`; `impl` widzi tylko `app`. Klasy w `impl` są `internal` (poza fabryką modułu). Zmiana `api` innego feature = najpierw pytanie do człowieka.
3. **Najpierw testy**, potem kod. Każda nowa logika ma test jednostkowy, każdy interfejs `api` test kontraktowy, każda funkcja użytkownika test funkcjonalny.
4. **Bez prawdziwego Claude Code, sieci i zegara systemowego w testach.** Fake'i z `testFixtures`, fałszywy `claude` na `PATH`, `runTest` i `TestClock`.
5. **Błędy przez `Outcome`**, nie wyjątki, na granicach modułów. Zakazane: `!!`, `GlobalScope`, `runBlocking` poza `main`/testami, `println`.
6. **Nigdy nie pushuj do `main`, nie używaj `--force`, `--no-verify` ani `gh pr merge`.** Scala człowiek. Hooki to blokują; nie próbuj ich obchodzić. Jedyny wyjątek: po rebase własnej gałęzi zadania (`feat/*`, `fix/*`, `chore/*`, `docs/*`) wolno `git push --force-with-lease`.
7. **Nie zmieniaj konfiguracji agenta** (`.claude/settings.json`, `.claude/hooks/`) bez wyraźnej prośby człowieka.

## Polecenia

```bash
./gradlew check                          # pełna bramka (format, detekt, architektura, testy, pokrycie)
./gradlew :feature:<x>:impl:test         # testy jednego modułu
./gradlew :tests:architecture:test       # reguły modułów (Konsist; pominięte = moduł jeszcze nie istnieje)
./gradlew :tests:functional:test         # scenariusze UI
./gradlew quickCheck                     # szybka bramka: format + detekt + architektura (hook Stop)
./gradlew spotlessApply                  # formatowanie
./gradlew :app:run                       # uruchomienie aplikacji
```

Gradle 9.7 z wrapperem i JDK 21 (toolchain). Convention plugins: `am.kotlin` (z `am.quality`: Spotless/ktlint, detekt, Kover), `am.feature.api`, `am.feature.impl`, `am.feature.ui`, `am.app` w `build-logic/`; reguły detekt w `config/detekt/detekt.yml`; wersje tylko w `gradle/libs.versions.toml`. Repozytoria deklaruje wyłącznie `settings.gradle.kts`.

## Git i GitHub

- Gałęzie: `feat/AM-XXX-opis`, `fix/…`, `chore/…`, `docs/…`, `release/vX.Y.Z`.
- Commity: Conventional Commits **po angielsku** (`feat(sessions): add state reducer`), stopka `Refs: #N`. Małe, logiczne commity.
- PR: opis **po polsku** wg `.github/pull_request_template.md`, `Closes #N`. Wysyłaj przez `/ship`.
- Kod, nazwy i KDoc po angielsku. Dokumentacja w `docs/` po polsku.

## Agenci i skille w tym repo

- `claude --agent am-lead` — sesja prowadząca, deleguje do subagentów: `architect`, `test-engineer`, `implementer`, `reviewer`, `shipper`, `release-manager`, `maintainer`. Planuje Opus (`am-lead`, `architect`); testy, implementację, bramkę, push i PR robi Sonnet (`test-engineer`, `implementer`, `shipper`).
- Skille: `/deliver [AM-XXX]`, `/ship`, `/new-module <feature>`, `/adr <tytuł>`, `/review-pr <nr>`, `/release`, `/compat-check`, `/triage <opis>`, `/roadmap-sync`.

## Definition of Done (skrót)

`./gradlew check` zielony · testy na każdym poziomie, którego dotyczy zmiana · KDoc dla nowego `api` · ADR dla decyzji · plan w `docs/plans/` · wpis w roadmapie zaktualizowany · PR wymienia zmienione moduły.

## Lekcje

Krótkie wnioski z review i błędów agenta, dopisywane przez PR (jedna linia każdy).

- W sesjach Claude Code w chmurze GitHub działa tylko przez REST: zamiast `gh pr create` / `gh issue create|close|comment` używaj `gh api repos/KrystianKatafoni/quayside/{pulls,issues}…` (GraphQL jest zablokowany).
- W sesjach w chmurze Maven Central i repozytoria Gradle bywają niedostępne (allowlista sieci); wtedy weryfikacją buildu jest CI w PR.
- Komunikaty wyjątków kotlinx.serialization cytują fragment wejścia — nie wolno ich logować ani zwracać w błędach; używaj stałych kategorii powodu.
- Przy `allWarningsAsErrors` nie używaj przestarzałego API: w Compose MP 1.12 testy UI importują `androidx.compose.ui.test.v2.runComposeUiTest`. Źródła Compose (`*-desktop`) są na repo1.maven.org, więc API da się sprawdzić bez `dl.google.com`.
- Gdy detekt (TooManyFunctions, CyclomaticComplexMethod) wymusza pliki spoza planu, dopisz je do sekcji „Moduły” planu w tym samym PR; wpis w CHANGELOG `[Unreleased]` dodawaj razem z kodem funkcji widocznej dla użytkownika.
- `data class` z polem-lambdą (np. `Command.run`) nigdy nie jest równa kopii, więc `stateIn` emituje przy każdej zmianie źródła; efekty uboczne w łańcuchu (logi) deduplikuj osobno.
- `runCurrent` i `flatMapLatest` są eksperymentalne — przy `allWarningsAsErrors` potrzebny `@OptIn(ExperimentalCoroutinesApi::class)`.
- Plany z nowym composable i kontrolerem zaniżają rozmiar o ok. 30% (ktlint rozbija wywołania z nazwanymi argumentami); przy szacunku > 300 linii zaplanuj podział od razu; przy kopiowaniu kontrolek z innego modułu dolicz importy.
- Stan po `stateIn` bywa o krok w tyle przy szybkich klawiszach — gdy plan mówi, skąd czytać stan (np. bieżące `selection` w `update`), kod musi to odwzorować albo plan trzeba poprawić w tym samym PR.
- Gdy w trakcie zadania dochodzi nowy wariant (np. Remove obok Stop/Respawn), przejrzyj KDoc publicznego API i komentarze pod kątem starych wyliczeń („both”, „X or Y”) — testy tego nie złapią.
- Strażnik bez obserwowalnego skutku w UI daje test „zawsze zielony” — sprawdź, czy test padnie po usunięciu tej linii, albo opisz strażnik w KDoc jako ochronę przed opóźnionym `stateIn`.
- `@Timeout` w JUnit 5 domyślnie (SAME_THREAD) tylko przerywa wątek testu; natywny odczyt (PTY, socket) na to nie reaguje — przy blokujących odczytach używaj `threadMode = SEPARATE_THREAD`.
- Composable bliskie limitu LongMethod (30) przekraczają go po dodaniu jednego przycisku lub nazwanych argumentów rozbitych przez ktlint; gdy detekt nie działa lokalnie, zmierz treść zmienionych funkcji przed oddaniem.
- Skróty okna bez ⌘ (space, ⏎) zjada sfokusowany `clickable`/`selectable` (Compose traktuje Spacebar i Enter jak klik, a klik myszą prosi o fokus); `focusProperties { canFocus = false }` nie wystarczy na rodzicu, bo oddaje on fokus pierwszemu fokusowalnemu dziecku — dzieci (np. przycisk menu w wierszu) też muszą być niefokusowalne.
- `SwingPanel` rysuje się nad Compose i zjada skróty przy fokusie: palety i popupy nad komponentem Swing wymagają `compose.interop.blending=true`, a skróty aplikacji — `KeyEventDispatcher` przed komponentem (JediTerm ma np. ⌘K jako „Clear Buffer”).
- Otwieranie zasobu w `suspend` przez kilka `withContext` gubi go przy anulowaniu (prompt cancellation): start, budowę i rejestrację opakuj w `NonCancellable` albo sprzątaj przy `CancellationException`.
- `catching` łapie tylko `Exception`; błędy natywnych bibliotek (`LinkageError`) łap jawnie tam, gdzie KDoc obiecuje kategorię błędu.
- pty4j zgłasza zakończenie procesu dopiero po odczycie wyjścia PTY, a login shell na macOS uruchamia `path_helper` — testy PTY czytają wyjście przed `waitFor` i filtrują potomków po komendzie.
- `@file:OptIn` działa tylko w swoim pliku: pomocnik testów (TestKit) wołający `runCurrent` potrzebuje własnego OptIn, inaczej `-Werror` zatrzyma kompilację testów, czego lokalnie bez Compose nie widać.
- Detekt LongParameterList nie zgłosił funkcji z 6–7 parametrami (także composable i pomocników testów UI) — ENG-C2 (≤ 5) sprawdzaj ręcznie przy dokładaniu slotów do composable okna.
- Detekt obejmuje też testy (detektTest): tabele `@MethodSource` > 30 linii potrzebują `@Suppress("LongMethod")`; ReturnCount (max 2) działa w `src/main`, ale nie w `testFixtures`.
- Kroki kolejkowane za jobem (`join`) anuluj całym łańcuchem albo sprawdzaj licznik pokolenia po `join` — anulowanie tylko ostatniego joba zostawia wcześniejsze przy życiu.
- `awk 'length>120'` liczy bajty, więc znaki jak ⏎ zawyżają długość linii; ktlint liczy znaki.
- Fokusowalny `Popup` dialogu zabiera fokus z `Box` KEYS, więc test okna „skrót nic nie robi przy dialogu” przechodzi bez strażników w komendach — strażniki dialogu sprawdzaj testem jednostkowym, a test okna opisuj jako blokadę przez Popup.
- W testach Compose Desktop `performClick` daje fokus klikalnemu elementowi jak klik myszą, więc `assertIsNotFocused()` po kliknięciu dowodzi `canFocus = false`; gdy kliknięcie usuwa węzeł (np. baner), fokus sprawdzaj przez Tab.
- Zwolnienie blokady (zbiór kluczy w trakcie, flaga `pending`) w `finally` wymaga testu ścieżki błędu — po nieudanym starcie kolejne wywołanie znowu otwiera; inaczej przeniesienie zwolnienia do gałęzi `Ok` nie wywróci żadnego testu.
- `@Suppress("ForbiddenMethodCall")` nie zwalnia z ENG-C3: `runBlocking` tylko w `main`.
- ktlint nie zgłasza nieużywanych importów — po przeniesieniu kodu między plikami sprawdź je ręcznie.
- Kolory kopiowane z innego modułu `ui` przenoszą jego błędy kontrastu — przy kopiowaniu sprawdź NFR-6 (≥ 4.5:1).
- Test porzuconej grupy i zwolnienia chłodzenia w `VoiceNotifier` wymagał mutacji, by udowodnić, że pada; metoda testowa zwracająca wartość (np. `Boolean` z `runBlocking`) jest po cichu pomijana przez JUnit — testy mają zwracać `Unit`.
- Asercja `shouldContain "1"` na komunikacie z zakresem „1–10” jest zawsze prawdziwa („10” zawiera „1”) — asercję zakresu pisz pełnym fragmentem „od 1 do N”.
- Test sortowania bez wielkości liter musi mieć dane, dla których wynik różni się od sortowania ASCII (np. bob, Anna, Carl); inaczej mutacja `.lowercase()` przeżywa.
- Gdy szkic obejmuje wszystkie pola, test „zapis zaczyna od current” przestaje być obserwowalny — usuń go świadomie i opisz w planie, nie przepisuj na „zawsze zielony”.
- Check Gradle nie obejmuje lintu Markdown, a CI ma „Docs lint”: przed pushem zmian w `.md` uruchom lokalnie `npx --yes markdownlint-cli2@0.18.1` (musi dać 0 błędów).
- Test skoku po literze i skrótów klawiszowych potrzebuje danych z małą literą oraz klawisza z modyfikatorem, inaczej mutacje `ignoreCase` i `!isMetaPressed` przeżywają.
- `value class` nullable w parametrach `@MethodSource` JUnit odpakowuje do `String?` — przekazuj `String?` i opakowuj w teście.
- Test progu liczonego w code pointach zamiast znaków UTF-16 musi mieć dane, w których oba sposoby dają różny wynik (np. 45 emoji przed spacją), inaczej mutacja przeżywa; dopisanie wierszy do tabeli `@MethodSource` łatwo przekracza LongMethod (30) — wydziel funkcję źródła.
- `claude -p` z niezaufaną treścią (wiadomość agenta) dziedziczy narzędzia, MCP i uprawnienia użytkownika — zawsze `--tools ""`, `--strict-mcp-config`, `--disable-slash-commands` i wyłączone hooki.
- `booleanOrNull` z kotlinx.serialization przyjmuje też string `"true"` — sprawdzaj `isString` przy ścisłym parsowaniu JSON.
- Czyszczenie wpisów mapy per rozmowa przy globalnym liczniku tur to higiena pamięci, nieobserwowalna testem — nie dopisuj testów „zawsze zielonych”.
- Dialog zgody na zmianę konfiguracji użytkownika nie może dawać fokusu startowego przyciskowi zapisującemu (Space i Enter działają jak klik na sfokusowanym `clickable`): fokus startowy na „Zamknij/Pomiń”, po akcji zostaje na „Zamknij”, wskaźnik fokusu jest widoczny.
- Strażnik „wynik po zamknięciu odrzucony” wymaga testu z ponownym otwarciem okna, bo samo `state == null` zasłania brak strażnika.
- Stała z kodu (np. port) wpisana literalnie w tekście i w teście ukrywa rozjazd — przekazuj ją parametrem tekstu (`{0}`), a parametr liczbowy jako String, by MessageFormat nie dał „47,321”.
- `ChoiceField`/lista wyboru z zawijaniem strzałkami stawia ostatnią wartość obok domyślnej — wartość groźna (np. „Pomiń zgody”/bypassPermissions) nie może być osiągalna jedną strzałką od wartości domyślnej: strzałki nie zawijają, klik może.
- Kolory kopiowane z innego modułu `ui` (`Colors` MC) nie dawały 4.5:1 dla `secondary`, tonu błędu i ramki fokusu — okno z formularzem dostaje własne kolory z testem kontrastu (jak `HookColors`/`NewAgentColors`).
- Strażnik w kontrolerze, którego UI i tak nie dopuszcza (np. `Edit` przy `busy` — kontrolki wyłączone), wymaga testu jednostkowego kontrolera, inaczej mutacja przeżywa.
- Warunkowy zapis domyślnej sekcji w JSON (`if (value.x != X())`) wymaga testu, że domyślne ustawienia nie zapisują sekcji; round trip przechodzi także po mutacji `if (true)`.
- Czas lokalny czytaj przy każdym zdarzeniu (`zone: () -> ZoneId`), nie raz przy starcie; test potrzebuje dwóch stref albo zmiany strefy w trakcie.
- Test „kolejności filtrów” musi być obserwowalny: niezależne predykaty tego samego kanału nie mają kolejności, więc nazywaj test „łączy się z” i testuj przecięcia.
- Zmiana koordynowana z równoległymi subagentami na tych samych plikach (np. `NotificationSettingsSerializer.kt`) grozi przywróceniem starej wersji z kopii zapasowej — mutacje rób w osobnej kopii roboczej lub przez `git stash`, nie `cp` kopii; skrypty mutacji nie mogą kopiować plików z katalogów pomocniczych poprzednich zadań do `src/main` (incydent: 31 obcych `.kt`).
- Test reguł wyprowadzenia (mieszanie kolorów) musi mieć dane o różnych kolorach źródłowych (bg ≠ panel, text ≠ accent ≠ warn), inaczej mutacje „zły argument miksu” przeżywają, a audyt kontrastu tego nie złapie.
- `require` w publicznym API koloru/tokenów to błąd programisty — KDoc musi mieć `@throws IllegalArgumentException`, a dane od użytkownika idą przez `parse(): T?`/Outcome (ENG-I1/I4).
- Kolory wybierane w composable (`LocalTextColor`, ramki) wymagają testu UI: czyste funkcje kolorów nie wykrywają, że komponent ich nie używa; test pikselowy `captureToImage()` węzła clickable nie pokaże ramki rysowanej poza nim — rób zrzut opakowania z tłem.
- Wskaźnik fokusu nie może graniczyć z kolorem wypełnienia, którego kontrastu audyt nie liczy (np. text/accent < 3:1): rezerwuj stały odstęp, by ramka graniczyła z tłem kontenera; tabela zasobów (ścieżki fontów) musi być sprawdzana względem dysku, inaczej literówka wychodzi dopiero w runtime.
- Zasoby zewnętrzne z sumami kontrolnymi (fonty, licencje): oznacz `-text` w `.gitattributes`; `* text=auto eol=lf` zmienia CRLF w licencjach i sumy z SOURCES.md przestają pasować; sprawdzaj sumy z blobów git (`git show HEAD:<plik> | shasum -a 256`), nie z dysku.
- Przekazanie tokenu kształtu/cienia do API Compose wydziel do `internal` funkcji i przypnij testem każde pole — test pikselowy „jest ciemniej” nie wykryje zamiany offsetu, blur ani spread.
- Funkcja wyprowadzająca, która rzuca dla części wejść (brak trójki statusów dla akcentu o odcieniu ok. 350–10°), potrzebuje testu ścieżki błędu i zdania w KDoc, że wyjątek dotyczy tylko stałych katalogu (dla theme.json — `Outcome`).
- Audyt kontrastu musi obejmować pary faktycznie rysowane na ekranach (muted i statusy na sel/warnBg, nie tylko bg/panel) — inaczej wybór innego motywu w AM-803 psuje NFR-6 bez czerwonego testu.
- Zmiana ekranu na nowe tokeny: kolory przekazywane do composable (kropka statusu, pasek popupu, glif tonu) mają testy pikselowe, a „przy okazji” formatowanie obcych plików (ktlint) cofnij — nie należy do zadania.
- `SwingPanel(background = …)` jest `@Deprecated` w Compose MP 1.12 (`allWarningsAsErrors`): tło ustaw na komponencie w `factory`.
- Gdy kod odchodzi od planu (parametry fabryki, podział na pliki), popraw plan w tym samym PR — review wyłapuje rozjazd plan-kod.
- AWT zwraca dla pliku SemiBold osobną rodzinę (np. „IBM Plex Mono SemiBold”) — asercje rodzin fontów rób po tablicy `name` pliku, nie po nazwie rodziny Compose.
- `remember(measurer.measure(...))` musi mieć w kluczu `measurer` i gęstość — zmiana skali ekranu zmienia metryki tekstu; `clearAndSetSemantics` nie ukrywa tekstu w drzewie niescalonym (`useUnmergedTree`), więc test „bez węzła semantyki” wymusza rysowanie tekstu przez `drawText`.
- Nazwa sekcji mockupu z zadania może różnić się od sekcji w HTML (np. „PODSUMOWANIE” vs kafelki liczników) — sprawdź układ w pliku mockupu, nie po nazwie.
- Wybór sesji spoza paska bocznego (popup Attach, nowy agent) wysyła `ProjectsIntent.Select` przed `MissionControlIntent.SelectSession`; samo `SelectSession` rozjeżdża pasek z MC, a ponowny klik wybranego projektu nic nie robi (StateFlow nie emituje tej samej wartości). Logika dopisana w `WindowControllers` (kolektory, `follow`) potrzebuje odpowiednika w `WindowWorld`, inaczej nie ma testu okna.
- Fonty wybieralne w UI rejestruj synchronicznie w `main` przed `application {}` i to wszystkie z katalogu, nie tylko bieżący: `JediTermTerminalApi.installedFonts` jest `lazy` (liczone przy pierwszej karcie), więc font zarejestrowany później jest dla terminala niewidoczny.
- Korekta kontrastu w wyprowadzeniu katalogu (jedna reguła `readableOn`) zamiast ręcznych wartości per motyw — nowy motyw z AM-804/905 przechodzi audyt bez dopisywania liczb; mutacje stałej liczby przebiegów udowadniają, czy pętla jest potrzebna.
- Popup Compose nad `SwingPanel` nie dostaje kliknięć (kontener interop leży nad warstwą popupu): osłona trafień `contains()=false` na czas modalu (`ModalShield` + `AmModalGate`); samo `compose.interop.blending` tego nie naprawia. Test wymaga prawdziwego `ComposeWindow` i zdarzeń wysłanych do `focusOwner()` (np. `KeyEvent` Esc), nie samej asercji „fokus opuścił widget”.
- Oddanie fokusu terminalowi po zamknięciu modalu musi pamiętać poprzedniego właściciela fokusu (`focusReturnTarget`): w trybie listy terminal leży pod Mission Control i bezwarunkowe oddanie fokusu kieruje ⏎ i spację do shella.
- Log lub strażnik w loaderze stojący za serializerem, który już filtruje wartości, jest w produkcji martwy, choć test na `InMemoryJsonStore` (bez serializera) jest zielony — takie zachowanie testuj na prawdziwym `JsonStorage`.
- Sprzątanie pliku tymczasowego przy atomowym zapisie testuj celem, który psuje dopiero `move` (np. istniejący niepusty katalog); test z katalogiem tylko do odczytu pada przed utworzeniem pliku i nic nie dowodzi.
- Zmiana wartości domyślnej w `api` przy pliku, który zapisywał wszystkie pola, wymaga migracji schematu i decyzji o formacie (ADR); nowe formaty zapisują od początku tylko odstępstwa od domyślnych (ADR 0024).
- Test „zły kształt nie psuje migracji” jest zawsze zielony, gdy serializer i tak odrzuca dane (oba kończą się plikiem `.broken`) – nazwij go po skutku i sprawdź plik `.broken`.
