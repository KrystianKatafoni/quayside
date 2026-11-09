# ADR 0029: Wiele kont Claude

- **Status:** przyjęta (kierunek; szczegóły CLI potwierdza spike AM-1001, nic nie jest jeszcze zaimplementowane)
- **Data:** 2026-11-09
- **Dotyczy:** nowy feature `accounts` (api/impl/ui), `agent-runtime` (api i impl), `sessions` (api i impl), `summaries:impl`, `terminal` (attach), `mission-control:ui`, `app`; pomysł nr 1 w [IDEAS.md](../IDEAS.md)

## Kontekst

Człowiek chce mieć kilka kont Claude zalogowanych naraz. Gdy limit jednej subskrypcji się wyczerpie, aplikacja ma sama przejść na inne konto. Konta da się też wybierać ręcznie i automatykę można wyłączyć.

Co mówi dokumentacja Claude Code (code.claude.com, stan 2026-11-09):

- **Konto = katalog konfiguracji.** `CLAUDE_CONFIG_DIR` (domyślnie `~/.claude`) trzyma ustawienia, historię sesji i login. Dokumentacja wprost poleca go do „wielu kont obok siebie”. Na macOS wpis w Keychain jest kluczowany katalogiem, więc inny `CLAUDE_CONFIG_DIR` czyta inny wpis. Wyjątek: dwa logowania Console bez klucza API (profil Anthropic w `~/.config/anthropic`) się nie rozdzielają.
- **Osobny supervisor na katalog.** Agent View: „jeśli ustawisz `CLAUDE_CONFIG_DIR`, supervisor używa tego katalogu zamiast `~/.claude` i działa jako osobna instancja z własnymi sesjami”. Stan sesji (`daemon/roster.json`, `jobs/<id>/`) i transkrypty (`projects/`) leżą w katalogu konta. `claude agents --json` widzi więc tylko sesje swojego konta.
- **Wyczerpany limit widać w hooku.** `StopFailure` ma typ błędu `rate_limit`, a także `authentication_failed`, `account_on_hold`, `billing_error` i inne. Quayside już go parsuje (`HookPayloadParser` → `AgentEvent.Failed(kind)`). Hook nie podaje, kiedy limit się odnowi.
- **Zużycie z wyprzedzeniem jest dostępne tylko skryptowi paska stanu** (`statusLine`). Dostaje on JSON z `rate_limits.five_hour` / `seven_day`, czyli `used_percentage` i `resets_at`. CLI nie ma osobnej komendy, która by to zwracała.
- `CLAUDE_CODE_OAUTH_TOKEN` (`claude setup-token`, token roczny) ma pierwszeństwo przed Keychain, ale nie obsługuje Remote Control ani konektorów claude.ai.

Stan kodu: `ClaudeCliAdapter` woła `claude` bez `CLAUDE_CONFIG_DIR`. `SettingsFiles` zapisuje hooki globalne do `~/.claude/settings.json`. `AgentRuntime` zakłada jednego supervisora, a krótkie `SessionId` jest unikalne tylko w jego obrębie.

## Decyzja (kierunek do potwierdzenia przez człowieka)

1. **Konto Quayside = etykieta + katalog konfiguracji.** Konto domyślne to `~/.claude` (zawsze istnieje, nie da się go usunąć). Dodatkowe konta tworzymy w `~/.claude-quayside/<slug>/` (decyzja człowieka 2026-11-09) albo wskazujemy istniejący katalog (np. `~/.claude-work`). Quayside **nie przechowuje sekretów**: login trzyma Claude Code w Keychain per katalog. Plik `accounts.json` zawiera tylko `id`, `label`, `configDir`, `enabled` („Aktywne”), `inAutomation` („W automacie”), kolejność, przełącznik automatyki i czas chłodzenia po limicie. Zapisujemy tylko odstępstwa od domyślnych (ADR 0024).
2. **Logowanie przez terminal, nie przez Quayside.** „Dodaj konto” otwiera kartę PTY z `claude auth login`, uruchomionym ze środowiskiem `CLAUDE_CONFIG_DIR=<katalog>`. Stan zalogowania sprawdza `claude auth status` (format do nagrania w spike'u). Aplikacja nigdy nie widzi tokenu.
3. **Jeden `AgentRuntime` na konto, złożone w `app`.** Fabryka `agent-runtime:impl` dostaje `configDir` i dokłada `CLAUDE_CONFIG_DIR` do środowiska każdego wywołania `claude`, w tym `attachCommand` (`ShellCommand.env`). `RuntimeSession` i sesja w `sessions:api` dostają `AccountId`. Klucz sesji w Quayside to para (konto, krótkie ID). Złożenie list z wielu supervisorów robi `sessions:impl`. Polling idzie per konto, a wyniki się scala.
4. **Hooki per konto.** `HookInstaller` z zakresem globalnym instaluje się w `<configDir>/settings.json` każdego włączonego konta, z osobną zgodą i kopią zapasową (NFR-5). Zakres projektu (`.claude/settings.local.json`) jest wspólny dla wszystkich kont i nie wymaga zmian. Zdarzenie hooka łączymy z kontem przez `ConversationId` z pollingu, więc payload nie musi znać konta.
5. **Automatyka dotyczy nowych i istniejących sesji** (decyzja człowieka 2026-11-09). Czysta funkcja `AccountSelector.choose(accounts, statuses, preferred)` wybiera konto dla „Nowego agenta” (gdy w oknie wybrano „Auto”), dla streszczeń Haiku (`claude -p`) i dla kontynuacji (punkt 6). `StopFailure` z `rate_limit` oznacza konto jako `Limited(since)` na czas chłodzenia: domyślnie 5 h, konfigurowalny w oknie „Konta” (decyzja człowieka 2026-11-09; jedna wartość dla wszystkich kont, zakres np. 1–24 h). Konto z limitem można też odblokować ręcznie przed czasem. Status `authentication_failed` daje `LoggedOut`. Powiadomienie brzmi: „Konto X osiągnęło limit, sesja S kontynuuje na Y”.
6. **Sesja przerwana limitem kontynuuje na innym koncie, już w wersji 1** (decyzja człowieka 2026-11-09). Gdy automatyka jest włączona, a `StopFailure` z `rate_limit` przyjdzie dla sesji na koncie X, Quayside wybiera dostępne konto Y. Następnie kopiuje transkrypt `projects/<cwd>/<uuid>.jsonl` z katalogu X do katalogu Y i uruchamia `claude --bg --resume <uuid>` z `CLAUDE_CONFIG_DIR` Y, w tym samym `cwd` (także worktree) i ze stałym promptem kontynuacji („Kontynuuj przerwaną pracę.”, tekst w zasobach). Przy wyłączonej automatyce to samo robi ręczna akcja „Kontynuuj na koncie…” w panelu szczegółów, w menu wiersza i w palecie. Zasady:
   - **Stara sesja zostaje na koncie X** w stanie błędu, z dopiskiem „kontynuowana na Y” i powiązaniem z nową sesją. Nigdy nie robimy `claude rm` w X, bo `rm` usuwa worktree, na którym pracuje kontynuacja.
   - **Bez pętli.** Na jedno zdarzenie limitu przypada najwyżej jedna automatyczna kontynuacja, a konto, na którym sesja już trafiła na limit, nie jest wybierane ponownie w czasie chłodzenia. Gdy wszystkie konta mają limit, nic nie przełączamy, tylko pokazujemy powiadomienie „Wszystkie konta mają limit”.
   - **Sesja, która czeka na zgodę lub odpowiedź, nie jest przenoszona.** Przenosimy tylko sesje zakończone błędem `rate_limit`, a nie działające.
   - **Ryzyko spike'u.** Jeśli AM-1001 wykaże, że `--resume` między katalogami nie działa (np. supervisor tworzy nowy worktree albo nie czyta skopiowanego transkryptu), plan wraca do człowieka przed AM-1007. Wariant zapasowy: nowa sesja na Y z promptem zawierającym podsumowanie przerwanej pracy (`last_assistant_message`, ścieżka worktree).

## Widok kont (prośba człowieka 2026-11-09)

Osobne okno „Konta”, otwierane z menu aplikacji, z palety ⌘K („Konta…”) i z plakietki konta w panelu szczegółów sesji. W oknie są:

- **Lista kont** w kolejności używanej przez automatykę (przeciąganie albo przyciski ↑/↓). Każdy wiersz pokazuje:
  - nazwę i katalog;
  - stan logowania: zalogowane (e-mail i organizacja z `claude auth status`, jeśli są), wylogowane, wygasłe;
  - stan limitu: dostępne albo „limit do HH:MM” z przyciskiem „Odblokuj”;
  - liczbę działających sesji.
- **Dodaj konto:** pole nazwy, z której powstaje `~/.claude-quayside/<nazwa>/`, albo „Wskaż istniejący katalog…”. Potem od razu otwiera się logowanie.
- **Zaloguj / Zaloguj ponownie:** otwiera kartę terminala z `claude auth login` dla katalogu konta. Po zamknięciu karty stan konta odświeża się sam. Jest też akcja **Wyloguj** (`claude auth logout` dla tego katalogu), poprzedzona pytaniem.
- **Aktywne (przełącznik):** konto aktywne można wybrać w „Nowym agencie”. Nieaktywne nie jest używane do niczego nowego, ale jego istniejące sesje są dalej widoczne i odpytywane, dopóki jakieś działają.
- **W automacie (przełącznik per konto):** czy automatyka może na to konto przełączać nowe i przerwane sesje (FR-L4, FR-L5). Przełącznik jest niedostępny dla konta nieaktywnego albo wylogowanego.
- **Usuń konto:** usuwa konto z listy Quayside. Katalog i login w Keychain zostają na dysku; w dialogu jest dodatkowy wybór „Wyloguj też z Claude Code”. Gdy na koncie działają sesje, dialog daje wybór „zatrzymaj sesje i usuń” albo „anuluj”, jak FR-P4. Konta domyślnego `~/.claude` nie da się usunąć, można je tylko wyłączyć z automatu.
- **Ustawienia automatyki** na dole okna: główny przełącznik automatyki i czas chłodzenia po limicie (domyślnie 5 h).

Każda akcja ma widoczną kontrolkę i komendę w palecie (FR-K1, FR-K3). Okno używa `AmTheme` i ma własny test kontrastu (NFR-6, Lekcje). Fokus startowy w dialogach usuwania i wylogowania stoi na „Anuluj” (Lekcje: dialog zgody).

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| A. `CLAUDE_CONFIG_DIR` per konto (wybrana) | Wspierane i opisane w dokumentacji; Keychain rozdzielony; Quayside nie trzyma sekretów | Osobny supervisor, historia i ustawienia na konto; hooki trzeba instalować w każdym katalogu |
| B. `CLAUDE_CODE_OAUTH_TOKEN` z `setup-token` w Keychain Quayside, jeden katalog | Jeden supervisor, wspólna historia | Supervisor startuje raz z jednym środowiskiem, więc nie da się zmienić konta per sesja `--bg`; Quayside trzyma sekret; brak Remote Control i konektorów |
| C. Przełączanie logowania (`/logout` + `/login`) w jednym katalogu | Nic nowego w modelu | Ręczne, przerywa wszystkie sesje, nie spełnia „automatycznie” |
| D. Klucze API Console zamiast subskrypcji | Brak limitów subskrypcji | Inny model kosztów; nie o to prosi pomysł |

## Ryzyka

- **Warunki korzystania.** Kilka subskrypcji jednej osoby do rozłożenia pracy to co innego niż współdzielenie konta lub obchodzenie limitów. Człowiek musi sprawdzić, czy jego plany (np. konto prywatne i firmowe) na to pozwalają. Quayside tylko przełącza między kontami, które człowiek sam zalogował.
- **Zmiana łamiąca `sessions:api` i `agent-runtime:api`** (konto w sesji, klucz złożony). Dotyka wszystkich `ui`, które używają `SessionId`, w tym palety komend, MC, terminala i powiadomień. Wymaga pytania do człowieka (CLAUDE.md, zasada 2).
- **N supervisorów = N razy polling** `claude agents --json` co ≤ 3 s (NFR-2) i N procesów demona. Przy 2–3 kontach to akceptowalne. Konto wyłączone nie jest odpytywane.
- **Brak czasu odnowienia limitu w hooku.** Stałe chłodzenie jest zgrubne. Dokładny `resets_at` wymaga skryptu `statusLine` w katalogu konta. To zmienia konfigurację użytkownika, więc musi być osobną, świadomą opcją (NFR-5).
- **Pierwsze uruchomienie nowego katalogu** przechodzi przez onboarding Claude Code (motyw, zaufanie folderu). `claude --bg` w niezaufanym katalogu kończy się błędem `Workspace not trusted…` (`docs/compat.md`), więc zaufanie trzeba nadać per konto.
- **Krótkie ID z dwóch supervisorów mogą się zderzyć.** Dlatego klucz to para (konto, ID), a nie samo ID.

## Do ręcznego sprawdzenia na Macu (spike, nie da się w chmurze)

1. Czy `CLAUDE_CONFIG_DIR=X claude --bg …` startuje osobnego demona i czy `claude agents --json` z X widzi tylko jego sesje (nagranie do `fixtures/`).
2. Wyjście i kod `claude auth status` dla konta zalogowanego, wylogowanego i z wygasłym loginem.
3. Prawdziwy payload `StopFailure` z `rate_limit` (nazwa pola: `error_type` czy `error`, czy jest tam czas odnowienia).
4. Co robi sesja w tle po limicie: stan `failed` czy czekanie i wznowienie.
5. Czy `claude --bg --resume <uuid> -- <prompt>` działa po skopiowaniu transkryptu do innego katalogu konta: czy zachowuje `sessionId` czy tworzy nowy, czy w sesji z worktree używa istniejącego worktree (a nie tworzy nowego), czy potrzebne są inne pliki niż `.jsonl` (np. `todos`, `file-history`). Wynik decyduje o AM-1007 (punkt 6).
6. Czy dwa demony nie kolidują (gniazda, porty, `~/.claude.json` poza katalogiem).

## Podział na zadania

Kamień milowy **M10 · Wiele kont Claude** w [ROADMAP.md](../ROADMAP.md), przed wydaniem 1.0 (decyzja człowieka 2026-11-09; AM-905 zależy od AM-1007, AM-1008 jest po 1.0):

| ID | Zadanie | Moduły | Zależy |
| --- | --- | --- | --- |
| AM-1001 | Spike: nagrania CLI dla dwóch katalogów konfiguracji (punkty 1–6 wyżej), wpis w `docs/compat.md` | docs, `agent-runtime:impl` (fixtures) | — |
| AM-1002 | `accounts:api/impl`: `Account`, `AccountId`, `AccountStatus`, `accounts.json`, czysty `AccountSelector`, test kontraktowy i fake | `accounts:api`, `accounts:impl`, `app` | AM-1001 |
| AM-1003 | `agent-runtime` per konto: `CLAUDE_CONFIG_DIR` w środowisku, `AccountId` w `RuntimeSession`, fabryka z `configDir`; `sessions` scala listy wielu kont (klucz konto+ID) | `agent-runtime:api/impl`, `sessions:api/impl`, `app` | AM-1002 |
| AM-1004 | Hooki globalne w katalogu każdego konta, ze zgodą i kopią zapasową | `agent-runtime:impl`, `app` | AM-1003 |
| AM-1005 | Okno „Konta” (sekcja „Widok kont”): lista z kolejnością, stanem logowania i limitu, dodaj, zaloguj/wyloguj w karcie terminala, aktywne, w automacie, usuń, odblokuj, główny przełącznik automatyki i czas chłodzenia | `accounts:ui`, `accounts:impl`, `app` | AM-1003 |
| AM-1009 | Konto w reszcie aplikacji: wybór konta (Auto/aktywne konta) w „Nowym agencie”, plakietka konta w panelu szczegółów i wierszu sesji, komendy palety | `mission-control:ui`, `app` | AM-1005 |
| AM-1006 | Automatyka: `rate_limit` → `Limited`, wybór konta dla nowych sesji i streszczeń, powiadomienie o przełączeniu | `accounts:impl`, `summaries:impl`, `notifications`, `app` | AM-1004, AM-1009 |
| AM-1007 | Kontynuacja sesji przerwanej `rate_limit` na innym koncie: automatycznie (gdy automatyka włączona) i ręcznie („Kontynuuj na koncie…”), kopia transkryptu, `--resume` z promptem kontynuacji, powiązanie starej i nowej sesji, ochrona przed pętlą; port `continueOn(...)` w `agent-runtime:api` po pytaniu do człowieka | `agent-runtime:api/impl`, `sessions:api/impl`, `accounts:impl`, `mission-control:ui`, `app` | AM-1006, wynik AM-1001 |
| AM-1008 | (C) Dokładne zużycie i `resets_at` przez opcjonalny skrypt `statusLine` | `accounts:impl`, `agent-runtime:impl` | AM-1006 |

Wymagania: [02-wymagania.md](../02-wymagania.md), sekcja „Konta” (FR-L1–FR-L7).

## Konsekwencje

- Ten ADR nic nie zmienia w kodzie. Pomysł przeniesiono z `IDEAS.md` do `02-wymagania.md` (FR-L1–FR-L6) i `ROADMAP.md` (M10).
- Zmiany `agent-runtime:api` i `sessions:api` (AM-1003) wymagają zgody człowieka i osobnego ADR o formacie `accounts.json`.
- Sesje bez konta (wszystko sprzed zmiany) należą do konta domyślnego, więc po aktualizacji nic nie znika z listy.
- Otwarte pytania do człowieka:
  1. ~~Czy wersja 1 ma przełączać tylko nowe sesje?~~ Rozstrzygnięte 2026-11-09: wersja 1 przełącza też istniejące sesje jako kontynuację (punkty 5 i 6, AM-1007, FR-L5 = S).
  2. ~~Gdzie tworzyć katalogi nowych kont?~~ Rozstrzygnięte 2026-11-09: `~/.claude-quayside/<slug>/` (punkt 1).
  3. ~~Chłodzenie po limicie?~~ Rozstrzygnięte 2026-11-09: domyślnie 5 h, konfigurowalne w oknie „Konta” (punkt 5); dokładny `resets_at` przez `statusLine` zostaje opcją AM-1008 (C).
  4. ~~Czy M10 wchodzi przed 1.0?~~ Rozstrzygnięte 2026-11-09: tak, przed 1.0 (poza AM-1008).
