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

1. **Konto Quayside = etykieta + katalog konfiguracji.** Konto domyślne to `~/.claude` (zawsze istnieje, nie da się go usunąć). Dodatkowe konta tworzymy w `~/.claude-quayside/<slug>/` (decyzja człowieka 2026-11-09) albo wskazujemy istniejący katalog (np. `~/.claude-work`). Quayside **nie przechowuje sekretów**: login trzyma Claude Code w Keychain per katalog. Plik `accounts.json` zawiera tylko `id`, `label`, `configDir`, `enabled` („Aktywne”), `inAutomation` („W automacie”: konto może być celem przełączenia), kolejność, bieżące konto (`current`), przełącznik „Proponuj przełączenie po limicie” i czas chłodzenia po limicie. Zapisujemy tylko odstępstwa od domyślnych (ADR 0024).
2. **Logowanie przez terminal, nie przez Quayside.** „Dodaj konto” otwiera kartę PTY z `claude auth login`, uruchomionym ze środowiskiem `CLAUDE_CONFIG_DIR=<katalog>`. Stan zalogowania sprawdza `claude auth status` (format do nagrania w spike'u). Aplikacja nigdy nie widzi tokenu.
3. **Jeden `AgentRuntime` na konto, złożone w `app`.** Fabryka `agent-runtime:impl` dostaje `configDir` i dokłada `CLAUDE_CONFIG_DIR` do środowiska każdego wywołania `claude`, w tym `attachCommand` (`ShellCommand.env`). Każda sesja ma konto, na którym działa: to wynika z Claude Code (supervisor, stan i limit są per katalog), nie jest wyborem Quayside. `RuntimeSession` i sesja w `sessions:api` dostają `AccountId`. Klucz sesji w Quayside to para (konto, krótkie ID). Złożenie list z wielu supervisorów robi `sessions:impl`. Polling idzie per konto, a wyniki się scala. Konto sesji jest widoczne jako plakietka; po przeniesieniu sesja pokazuje historię kont („A → B”).
4. **Wspólna jest tylko historia rozmów i pamięć projektów** (decyzje człowieka 2026-11-09). Katalog nowego konta dostaje jedno dowiązanie: `projects/` → `~/.claude/projects/`. W nim są transkrypty i pamięć automatyczna, rozdzielone per projekt i podprojekt (`projects/<cwd>/`), więc nic nie miesza się między projektami. **Osobne per konto** zostają: globalny `CLAUDE.md`, `agents/`, `skills/`, `commands/`, `settings.json`, login (Keychain, `.credentials.json`), stan supervisora (`daemon/`, `jobs/`), dane konta i zaufane foldery. Nowe konto startuje więc bez Twoich globalnych instrukcji, skilli i subagentów. Rzeczy projektowe w repo (`CLAUDE.md` i katalog `.claude` projektu) działają na każdym koncie bez zmian, bo leżą w katalogu projektu. Skutki:
   - przeniesienie sesji nie wymaga kopiowania transkryptu (punkt 6);
   - hooki globalne instalujemy w `settings.json` każdego aktywnego konta, z osobną zgodą i kopią zapasową (NFR-5).

   **Do potwierdzenia w spike'u:** czy dwa supervisory mogą bezpiecznie pisać do wspólnego `projects/` i czy pamięć automatyczna jest czytana na obu kontach. Jeśli nie, wracamy do kopiowania transkryptu przy przeniesieniu.
5. **Automatyka reaguje na limit i domyślnie pyta o zgodę** (decyzje człowieka 2026-11-09). Czysta funkcja `AccountSelector.choose(accounts, statuses, preferred)` wybiera konto dla „Nowego agenta” (gdy w oknie wybrano „Auto”), dla streszczeń Haiku (`claude -p`) i jako propozycję celu przełączenia; przy równym stanie wybiera konto z mniejszym zużyciem (punkt 7). Gdy `StopFailure` z `rate_limit` przyjdzie dla sesji na koncie X:
   - konto X dostaje stan `Limited(until)`. `until` to czas odnowienia z punktu 7, a gdy go nie znamy, czas chłodzenia: domyślnie 5 h, konfigurowalny w oknie „Konta” (zakres np. 1–24 h). Drugi limit tego samego konta w ciągu 24 h bez znanego czasu odnowienia (prawdopodobnie limit tygodniowy) daje stan „limit do ręcznego odblokowania”. Konto z limitem można odblokować ręcznie. `authentication_failed` daje `LoggedOut`;
   - jeśli włączone jest „Proponuj przełączenie po limicie” (domyślnie tak), Quayside pokazuje **jedno okno zgody na konto, nie na sesję** (decyzja człowieka 2026-11-09): „Konto X osiągnęło limit. Przenieść przerwane sesje na Y?” z listą sesji przerwanych limitem. Limit przerywa zwykle kilka sesji w krótkim odstępie, więc kolejne `rate_limit` z konta X dopisują sesje do już otwartego okna zamiast otwierać nowe; okno pojawia się po krótkiej zwłoce (ok. 5 s) po pierwszym zdarzeniu, żeby zebrać sesje. Gdy limit trafi dwa konta, są najwyżej dwa okna (po jednym na konto). Po zamknięciu okna kolejne `rate_limit` z tego samego konta w czasie trwania limitu nie otwierają go ponownie, tylko dają przycisk „Przenieś sesję na to konto” przy sesji (punkt 6). **Domyślnie żadna sesja nie jest zaznaczona.** Wyłączone ustawienie oznacza tylko powiadomienie, bez okna;
   - **wyjątek: sesja z włączonym „Przełączaj konto automatycznie”** przenosi się bez pytania, z powiadomieniem „Sesja S kontynuuje na koncie Y”. To ustawienie jest per sesja, domyślnie wyłączone; ustawia się je w „Nowym agencie” i w panelu szczegółów, menu wiersza i palecie;
   - automatyka nie rusza sesji na kontach bez limitu ani sesji, które pracują, czekają albo się skończyły.
6. **Przenoszone są tylko sesje przerwane limitem** (decyzja człowieka 2026-11-09: największa wartość, najmniejsze ryzyko). Sesji, które pracują, czekają na odpowiedź lub zgodę, są zakończone albo mają inny błąd, wersja 1 nie przenosi. Przeniesienie zawsze wymaga potwierdzenia, z wyjątkiem sesji z „Przełączaj konto automatycznie” (punkt 5). Drogi przeniesienia:
   - okno zgody po limicie (punkt 5);
   - **przycisk „Przenieś sesję na to konto”** przy sesji przerwanej limitem, której konto różni się od bieżącego konta (ikona w górnym pasku). Przycisk jest w panelu szczegółów i w menu wiersza, a w palecie jako „Przenieś sesję na konto <bieżące>”. Typowy przypadek: zamknąłeś okno zgody, a potem przełączyłeś bieżące konto. Klik pokazuje krótkie potwierdzenie z nazwą sesji i obu kont;
   - akcja „Przenieś na konto…” w menu wiersza i palecie, z wyborem konta.

   Mechanizm i zasady:
   - **Mechanizm:** sesja przerwana limitem już nie pracuje, więc nie trzeba jej zatrzymywać w trakcie tury. Quayside uruchamia `claude --bg --resume <uuid>` z `CLAUDE_CONFIG_DIR` Y, w tym samym `cwd` (także worktree), ze stałym promptem kontynuacji („Kontynuuj przerwaną pracę.”, tekst w zasobach). Dzięki wspólnemu `projects/` (punkt 4) transkrypt jest już na miejscu; jeśli spike tego nie potwierdzi, kopiujemy `projects/<cwd>/<uuid>.jsonl` do katalogu Y.
   - **Koszt:** wznowiona rozmowa nie ma na koncie Y pamięci podręcznej promptów, więc pierwsza tura przetwarza cały kontekst i zużywa więcej limitu Y. Okno zgody i potwierdzenie przycisku pokazują to jednym zdaniem.
   - **Stara sesja zostaje na koncie X,** ukryta na liście i powiązana z nową (historia kont „X → Y”). Nigdy nie robimy `claude rm` w X, bo `rm` usuwa worktree, na którym pracuje kontynuacja.
   - **Bez pętli:** na jedno zdarzenie limitu przypada najwyżej jedno automatyczne przeniesienie sesji, a konto z limitem nie jest celem, dopóki limit trwa. Gdy wszystkie konta mają limit, nic nie przenosimy, tylko pokazujemy powiadomienie „Wszystkie konta mają limit” z najbliższym czasem odnowienia.
   - **Ryzyko spike'u:** jeśli AM-1001 wykaże, że `--resume` na innym koncie nie działa (np. supervisor tworzy nowy worktree), plan wraca do człowieka przed AM-1007. Wariant zapasowy: nowa sesja na Y z promptem zawierającym podsumowanie przerwanej pracy (`last_assistant_message`, ścieżka worktree).
7. **Dokładne zużycie limitu w wersji 1** (decyzja człowieka 2026-11-09). Jedyne udokumentowane źródło zużycia to JSON paska stanu Claude Code (`statusLine`): `rate_limits.five_hour` i `rate_limits.seven_day` z `used_percentage` i `resets_at`. Quayside za zgodą (NFR-5, z kopią zapasową) ustawia w `settings.json` każdego aktywnego konta własny skrypt paska stanu. Skrypt:
   - wysyła `rate_limits` do serwera hooków Quayside na `127.0.0.1` z tokenem (jak hooki, ADR 0004/0017), w tle i z krótkim limitem czasu, żeby nie spowalniać paska;
   - jeśli konto miało już własny `statusLine`, uruchamia go i przekazuje jego wynik bez zmian, więc pasek wygląda jak wcześniej; bez własnego pokazuje pusty wiersz;
   - nie zapisuje niczego na dysk i nie wysyła treści rozmowy (tylko konto, procenty i czasy).

   Quayside trzyma ostatni odczyt per konto (procent 5 h i 7 dni, czasy odnowienia, czas odczytu) i używa go do: pasków zużycia w oknie „Konta” i na liście pod ikoną; `Limited(until = resets_at)` zamiast stałych 5 h; ostrzeżenia przy progu (domyślnie 90%, konfigurowalny) „Konto X ma 90% limitu 5 h, odnowienie 14:30”; preferowania mniej zużytego konta w `AccountSelector`. Odczyt starszy niż czas odnowienia traktujemy jako nieaktualny. **Do potwierdzenia w spike'u:** czy skrypt paska stanu działa też dla sesji w tle (`--bg`), czy tylko w interaktywnym TUI i po `attach`. Jeśli tylko w TUI, odczyty pojawiają się rzadziej, a 5 h chłodzenia zostaje jako zapas.

## Widok kont (prośba człowieka 2026-11-09)

Osobne okno „Konta”, otwierane z menu aplikacji, z palety ⌘K („Konta…”) i z plakietki konta w panelu szczegółów sesji. W oknie są:

- **Lista kont** w kolejności używanej przez automatykę (przeciąganie albo przyciski ↑/↓). Każdy wiersz pokazuje:
  - nazwę i katalog;
  - stan logowania: zalogowane (e-mail i organizacja z `claude auth status`, jeśli są), wylogowane, wygasłe;
  - stan limitu: dostępne albo „limit do HH:MM” z przyciskiem „Odblokuj”, oraz paski zużycia 5 h i 7 dni z czasem odnowienia i wiekiem odczytu (punkt 7);
  - liczbę działających sesji.
- **Dodaj konto:** pole nazwy, z której powstaje `~/.claude-quayside/<nazwa>/`, albo „Wskaż istniejący katalog…”. Potem od razu otwiera się logowanie.
- **Zaloguj / Zaloguj ponownie:** otwiera kartę terminala z `claude auth login` dla katalogu konta. Po zamknięciu karty stan konta odświeża się sam. Jest też akcja **Wyloguj** (`claude auth logout` dla tego katalogu), poprzedzona pytaniem.
- **Aktywne (przełącznik):** konto aktywne można wybrać w „Nowym agencie”. Nieaktywne nie jest używane do niczego nowego, ale jego istniejące sesje są dalej widoczne i odpytywane, dopóki jakieś działają.
- **W automacie (przełącznik per konto):** czy konto może być celem przełączenia: proponowanym w oknie zgody po limicie i używanym przez „Automatycznie” oraz przez sesje z „Przełączaj konto automatycznie” (FR-L4, FR-L5). Przełącznik jest niedostępny dla konta nieaktywnego albo wylogowanego.
- **Usuń konto:** usuwa konto z listy Quayside. Katalog i login w Keychain zostają na dysku; w dialogu jest dodatkowy wybór „Wyloguj też z Claude Code”. Gdy na koncie działają sesje, dialog daje wybór „zatrzymaj sesje i usuń” albo „anuluj”, jak FR-P4. Konta domyślnego `~/.claude` nie da się usunąć, można je tylko wyłączyć z automatu.
- **Ustawienia automatyki** na dole okna: „Proponuj przełączenie po limicie” (domyślnie włączone), czas chłodzenia po limicie bez znanego czasu odnowienia (domyślnie 5 h), próg ostrzeżenia o zużyciu (domyślnie 90%) i przełącznik „Odczytuj zużycie limitu” (instalacja skryptu paska stanu za zgodą, punkt 7).

Każda akcja ma widoczną kontrolkę i komendę w palecie (FR-K1, FR-K3). Okno używa `AmTheme` i ma własny test kontrastu (NFR-6, Lekcje). Fokus startowy w dialogach usuwania i wylogowania stoi na „Anuluj” (Lekcje: dialog zgody).

## Przełącznik kont w górnym pasku (prośba człowieka 2026-11-09)

**Ikona konta** stoi w prawym górnym rogu górnego paska okna (`WindowTopBar`), na prawo od przycisku „Nowy agent ⌘N”, jak awatar użytkownika. Pokazuje inicjały **bieżącego konta** albo literę „A”, gdy wybrano „Automatycznie”. Ma kropkę stanu (ostrzeżenie: limit, błąd: wylogowane), więc stan nie jest przekazywany samym kolorem (NFR-6). Ma etykietę dostępności „Konto: <nazwa>” i, jak cały pasek, nie bierze fokusu.

**Bieżące konto** to konto, na którym startują nowi agenci, gdy w „Nowym agencie” nie wybrano innego, i na którym idą streszczenia. Wartość zapisuje się w `accounts.json` (pole `current`: `auto` albo `id` konta).

**Klik w ikonę otwiera listę pod ikoną** (popup przez `AmModalGate`, bo musi działać nad `SwingPanel` terminala; Lekcje). Na liście są:

- **„Automatycznie”**: bieżące konto wybiera `AccountSelector` według kolejności i stanu kont.
- **Aktywne konta**: nazwa, e-mail, stan (dostępne, „limit do HH:MM”, wylogowane), paski zużycia 5 h i 7 dni (punkt 7) i znacznik ✓ przy bieżącym. Klik w konto dostępne ustawia je jako bieżące; to **ręczny przełącznik**. Konto wylogowane w tym miejscu otwiera logowanie, a konto z limitem pyta, czy przełączyć mimo limitu.
- **„Zarządzaj kontami…”**: otwiera okno „Konta”.

Lista obsługuje klawiaturę (↑↓ ⏎ Esc) jak `AmDropdown`. Ten sam wybór jest w palecie ⌘K: „Przełącz konto: <nazwa>” i „Przełącz konto: automatycznie”.

**Każde przełączenie bieżącego konta wymaga potwierdzenia** (decyzja człowieka 2026-11-09). Wybór konta z listy, palety albo „Automatycznie” otwiera krótkie okno: „Przełączyć na konto Y? Nowe sesje będą startować na koncie Y”. Okno nie przenosi sesji. Sesje przerwane limitem na innym koncie dostają po przełączeniu przycisk „Przenieś sesję na to konto” (punkt 6). Fokus startowy stoi na „Anuluj”, a Space i Enter nie zatwierdzają okna przypadkiem (Lekcje: dialog zgody). Ikona od razu pokazuje nowe konto.

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| A. `CLAUDE_CONFIG_DIR` per konto + wspólne `projects/` przez dowiązanie (wybrana) | Wspierane i opisane w dokumentacji; Keychain rozdzielony; Quayside nie trzyma sekretów; wspólna historia i pamięć projektów | Osobny supervisor i ustawienia na konto; hooki w każdym katalogu; dowiązanie trzeba potwierdzić w spike'u |
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
5. Czy `claude --bg --resume <uuid> -- <prompt>` z `CLAUDE_CONFIG_DIR` konta Y wznawia rozmowę zaczętą na koncie X (wspólne `projects/` albo skopiowany transkrypt): czy zachowuje `sessionId` czy tworzy nowy, czy w sesji z worktree używa istniejącego worktree (a nie tworzy nowego), czy potrzebne są inne pliki niż `.jsonl` (np. `todos`, `file-history`). Wynik decyduje o AM-1007 (punkt 6).
6. Czy dwa demony nie kolidują (gniazda, porty, `~/.claude.json` poza katalogiem).
7. Wspólne `projects/` (punkt 4): czy dowiązanie przetrwa pracę Claude Code, czy dwa supervisory piszą bezpiecznie do tego samego katalogu i czy pamięć automatyczna jest czytana na obu kontach.
8. Skrypt `statusLine` (punkt 7): czy działa dla sesji w tle (`--bg`), czy tylko w interaktywnym TUI i po `attach`; jak często jest wołany; czy `rate_limits` jest obecne dla każdego rodzaju subskrypcji; jak zachowuje się istniejący `statusLine` użytkownika uruchamiany przez nasz skrypt.

## Podział na zadania

Kamień milowy **M10 · Wiele kont Claude** w [ROADMAP.md](../ROADMAP.md), przed wydaniem 1.0 (decyzja człowieka 2026-11-09; AM-905 zależy od AM-1007, AM-1008 też wchodzi przed 1.0 — decyzja człowieka 2026-11-09):

| ID | Zadanie | Moduły | Zależy |
| --- | --- | --- | --- |
| AM-1001 | Spike: nagrania CLI dla dwóch katalogów konfiguracji i wspólnego `projects/` (punkty 1–8 wyżej), wpis w `docs/compat.md` | docs, `agent-runtime:impl` (fixtures) | — |
| AM-1002 | `accounts:api/impl`: `Account`, `AccountId`, `AccountStatus`, `accounts.json`, czysty `AccountSelector`, tworzenie katalogu konta z dowiązaniem `projects/` (wg wyniku AM-1001), test kontraktowy i fake | `accounts:api`, `accounts:impl`, `app` | AM-1001 |
| AM-1003 | `agent-runtime` per konto: `CLAUDE_CONFIG_DIR` w środowisku, `AccountId` w `RuntimeSession`, fabryka z `configDir`; `sessions` scala listy wielu kont (klucz konto+ID) | `agent-runtime:api/impl`, `sessions:api/impl`, `app` | AM-1002 |
| AM-1004 | Hooki globalne w `<configDir>/settings.json` każdego aktywnego konta, z osobną zgodą i kopią zapasową | `agent-runtime:impl`, `app` | AM-1003 |
| AM-1005 | Okno „Konta” (sekcja „Widok kont”): lista z kolejnością, stanem logowania i limitu, dodaj, zaloguj/wyloguj w karcie terminala, aktywne, w automacie, usuń, odblokuj, główny przełącznik automatyki i czas chłodzenia | `accounts:ui`, `accounts:impl`, `app` | AM-1003 |
| AM-1009 | Konto w reszcie aplikacji: wybór konta (Auto/aktywne konta) w „Nowym agencie”, plakietka konta w panelu szczegółów i wierszu sesji, komendy palety | `mission-control:ui`, `app` | AM-1005 |
| AM-1010 | Ikona konta w prawym górnym rogu i lista pod nią: bieżące konto, „Automatycznie”, ręczny przełącznik z krótkim oknem potwierdzenia (bez przenoszenia sesji), „Zarządzaj kontami…”, komendy palety „Przełącz konto” | `accounts:api`, `accounts:impl`, `accounts:ui`, `app` | AM-1005 |
| AM-1006 | Automatyka: `rate_limit` → `Limited(until)` (czas odnowienia albo chłodzenie 5 h, drugi limit w 24 h → do ręcznego odblokowania), `authentication_failed` → `LoggedOut`, wybór konta dla nowych sesji i streszczeń, ustawienie sesji „Przełączaj konto automatycznie” (domyślnie wyłączone), powiadomienia | `accounts:impl`, `summaries:impl`, `notifications`, `mission-control:ui`, `app` | AM-1004, AM-1009, AM-1010 |
| AM-1007 | Przenoszenie sesji przerwanych limitem (punkt 6): jedno okno zgody na zdarzenie limitu (domyślnie żadna sesja), przycisk „Przenieś sesję na to konto” po przełączeniu bieżącego konta, akcja „Przenieś na konto…”, automatyczne przeniesienie sesji z „Przełączaj konto automatycznie”, `--resume` z promptem kontynuacji, powiązanie starej i nowej sesji, ochrona przed pętlą; port w `agent-runtime:api` po pytaniu do człowieka | `agent-runtime:api/impl`, `sessions:api/impl`, `accounts:impl`, `accounts:ui`, `mission-control:ui`, `app` | AM-1006, wynik AM-1001 |
| AM-1008 | Dokładne zużycie limitu (punkt 7): skrypt `statusLine` per konto za zgodą (z zachowaniem własnego paska użytkownika), odbiór `rate_limits` w serwerze hooków, paski zużycia w oknie „Konta” i na liście pod ikoną, `Limited(until = resets_at)`, ostrzeżenie przy progu, preferowanie mniej zużytego konta | `accounts:api/impl/ui`, `agent-runtime:impl`, `app` | AM-1006 |

Wymagania: [02-wymagania.md](../02-wymagania.md), sekcja „Konta” (FR-L1–FR-L10).

## Konsekwencje

- Ten ADR nic nie zmienia w kodzie. Pomysł przeniesiono z `IDEAS.md` do `02-wymagania.md` (FR-L1–FR-L6) i `ROADMAP.md` (M10).
- Zmiany `agent-runtime:api` i `sessions:api` (AM-1003) wymagają zgody człowieka i osobnego ADR o formacie `accounts.json`.
- Sesje bez konta (wszystko sprzed zmiany) należą do konta domyślnego, więc po aktualizacji nic nie znika z listy.
- Otwarte pytania do człowieka:
  1. ~~Czy wersja 1 ma przełączać tylko nowe sesje?~~ Rozstrzygnięte 2026-11-09: wersja 1 przenosi tylko sesje przerwane limitem, zawsze za zgodą (okno po limicie, domyślnie bez zaznaczonych sesji, albo przycisk „Przenieś sesję na to konto”); bez pytania tylko sesje z „Przełączaj konto automatycznie” (punkty 5 i 6). Sesji, które pracują lub czekają, nie przenosimy.
  2. ~~Gdzie tworzyć katalogi nowych kont?~~ Rozstrzygnięte 2026-11-09: `~/.claude-quayside/<slug>/` (punkt 1).
  3. ~~Chłodzenie po limicie?~~ Rozstrzygnięte 2026-11-09: domyślnie 5 h, konfigurowalne w oknie „Konta” (punkt 5); dokładny `resets_at` przez `statusLine` wchodzi do wersji 1 (AM-1008, punkt 7), a 5 h zostaje jako zapas, gdy odczytu nie ma.
  4. ~~Czy M10 wchodzi przed 1.0?~~ Rozstrzygnięte 2026-11-09: tak, przed 1.0 (poza AM-1008).
