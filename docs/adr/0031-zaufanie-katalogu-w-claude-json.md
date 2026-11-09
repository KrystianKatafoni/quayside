# ADR 0031: Zaufanie katalogu przez zapis w `~/.claude.json`

- **Status:** proponowana
- **Data:** 2026-11-09
- **Dotyczy:** `agent-runtime` (api i impl), `sessions` (api i impl), `mission-control:ui`, `app`; zadanie AM-309, wymaganie FR-S3

## Kontekst

`claude --bg` (od 2.1.281) odmawia startu w katalogu, któremu użytkownik nie zaufał: kod 1 i na stderr ``Workspace not trusted. Run `claude` in <cwd> once and accept the trust prompt, then retry.`` (ścieżka po `toRealPath`, na macOS np. `/private/...`; nagranie `2.1.302-bg-untrusted-workspace`). Dziś okno „Nowy agent” pokazuje wtedy ogólny błąd `COMMAND_FAILED` z instrukcją ręczną (AM-608, decyzja 2): ⌘T, `claude`, akceptacja, ponowny start.

Zaufanie Claude Code trzyma w pliku konfiguracji użytkownika `~/.claude.json` (przy ustawionym `CLAUDE_CONFIG_DIR`: `<CLAUDE_CONFIG_DIR>/.claude.json`): obiekt `projects`, klucz = bezwzględna ścieżka katalogu, pole `hasTrustDialogAccepted` (boolean). Eksperyment człowieka i lead'a (2026-11-09, izolowany `CLAUDE_CONFIG_DIR`, plik z samym `hasCompletedOnboarding` i jednym wpisem): `false` → `Workspace not trusted`, `true` → `backgrounded · <id>`.

Ograniczenia:

- Plik nie jest nasz (docs/11 §8: jeden właściciel pliku). Ma ok. 263 KB, zawiera dane konta i jest zapisywany przez każdą działającą sesję Claude Code. Jego format nie jest opisany jako stabilny.
- Dialog zaufania w Claude Code to zabezpieczenie: katalog może zawierać `.claude/settings.json`, hooki i serwery MCP, które uruchomią się bez pytania.

## Decyzja

Opcja 2 (decyzja człowieka 2026-11-09): Quayside, **tylko po jawnej zgodzie użytkownika w dialogu**, ustawia `projects[<ścieżka rzeczywista>].hasTrustDialogAccepted = true` dla jednego katalogu i ponawia start.

1. **Rozpoznanie.** `ClaudeCliAdapter.start` zamienia kod ≠ 0 z linią stderr zaczynającą się od `Workspace not trusted` na `RuntimeError.WorkspaceNotTrusted` (bez ścieżki i bez stderr). `sessions` mapuje to na `RuntimeProblem.WORKSPACE_NOT_TRUSTED`.
2. **Port.** `WorkspaceTrust` w `agent-runtime:api` (feature, który już zna pliki Claude Code: `HookInstaller`), implementacja na pliku w `agent-runtime:impl` za fabryką `workspaceTrustModule(configHome, io, logger)`. `configHome` = katalog z `.claude.json` (dziś katalog domowy; w M10 katalog konta, ADR 0029).
3. **Zapis tylko dokładnie tego katalogu.** Klucz = `dir.toRealPath()`; katalog musi istnieć. Nigdy katalogi nadrzędne ani inne wpisy. Nowy wpis ma tylko `{"hasTrustDialogAccepted": true}` (eksperyment potwierdził, że to wystarcza). Wpis już `true` → brak zapisu.
4. **Bez zapisu przy wątpliwości.** Brak pliku, plik nie-UTF-8, nie-JSON, korzeń nie jest obiektem, `projects` albo wpis nie jest obiektem, pole nie jest booleanem JSON (także string `"true"`), plik większy niż 16 MiB albo będący dowiązaniem symbolicznym → typowany błąd (`TrustError`), plik nietknięty.
5. **Ponowna serializacja zamiast edycji tekstu.** Plik jest parsowany do drzewa JSON (kotlinx.serialization, już w `agent-runtime:impl`) i zapisywany w całości: kolejność kluczy zostaje, liczby zostają jako ten sam literał, nieznane pola i zagnieżdżone obiekty zostają, wcięcie 2 spacje, końcowy znak nowej linii jak w oryginale. Mogą się zmienić tylko odstępy i sposób zapisu znaków ucieczki w stringach (np. `ó` → `ó`), czyli nic znaczeniowego. Zduplikowane klucze obiektu sklejają się do ostatniego (jak w `JSON.parse`). Wartości bez cudzysłowu muszą być `true`, `false`, `null` albo liczbą JSON, a napis z samotnym surogatem nie daje się zapisać jako UTF-8; w obu przypadkach wynik to `CONFIG_INVALID` bez zapisu. Claude Code i tak przepisuje cały plik przy każdym zapisie, więc formatowanie nie jest stanem, który chronimy.
6. **Zapis atomowy z ponownym odczytem.** Plik tymczasowy w tym samym katalogu z uprawnieniami oryginału (zwykle 600), tuż przed `ATOMIC_MOVE` ponowny odczyt bajtów; gdy różnią się od odczytanych na początku, cała operacja powtarza się na świeżej treści (najwyżej 3 próby, potem `CONCURRENT_CHANGE`). Bez zapisu nieatomowego. Plik tymczasowy jest zawsze usuwany.
7. **Bez kopii zapasowej.** Odstępstwo od ducha NFR-5 (który dotyczy `settings.json`): przywrócenie starej kopii pliku zapisywanego na bieżąco przez działające sesje cofnęłoby ich nowsze zmiany, więc kopia byłaby groźniejsza niż sama zmiana. Kopia powielałaby też dane konta poza kontrolą Claude Code. Zmiana dotyczy jednego pola; cofnięcie opisuje `docs/compat.md` (ustawić `false` albo usunąć wpis).
8. **Ostatnia linia obrony: instrukcja ręczna.** Gdy zapis się nie uda albo po zapisie `claude --bg` nadal odmawia (inny `CLAUDE_CONFIG_DIR` w powłoce, cofnięcie wpisu przez działającą sesję, zmiana formatu), okno pokazuje instrukcję z AM-608 (⌘T, `claude`, akceptacja) i nie pyta drugi raz w tej samej próbie.
9. **Logi** zawierają tylko kategorie (`TrustError`, `RuntimeProblem`) i ID projektu; nigdy ścieżki, treści pliku ani komunikatów wyjątków kotlinx.serialization (cytują wejście).

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| 1. Przycisk „Otwórz terminal w katalogu” z `claude` (użytkownik akceptuje dialog Claude Code sam) | Nie dotyka cudzego pliku; zabezpieczenie Claude Code działa jak zaprojektowano; odporne na zmianę formatu | Kilka kroków ręcznych; sesja interaktywna do zamknięcia; zgłoszenie człowieka: zbyt uciążliwe przy każdym nowym katalogu (podfoldery grup, AM-308) |
| 2. Zapis `hasTrustDialogAccepted` po zgodzie w Quayside (wybrana) | Jedno kliknięcie; prompt nie ginie; start ponawia się sam | Obejście zabezpieczenia Claude Code (zgoda w naszym dialogu zamiast w jego); plik współdzielony z działającymi sesjami; format nieudokumentowany |
| 3. Uruchomienie `claude` w ukrytym PTY i wysłanie klawiszy akceptacji | Claude Code sam zapisuje plik | Zależność od układu ekranu TUI, kruche przy każdej wersji; trudne do testowania; nadal obejście dialogu |
| 4. Edycja bajtowo-wierna (zamiana fragmentu tekstu) | Formatowanie nietknięte | Wymaga własnego lokalizatora zakresów JSON; ryzyko uszkodzenia pliku większe niż zysk, bo Claude Code i tak przepisuje plik w całości |
| 5. Zapis z kopią zapasową w `Application Support` | Zgodne z literą NFR-5 | Przywrócenie kopii cofa cudze zmiany; powiela dane konta (punkt 7) |

## Ryzyka

- **Obejście zabezpieczenia.** Zaufanie uruchamia ustawienia, hooki i MCP z katalogu. Dialog Quayside mówi to wprost, ma fokus startowy na „Anuluj” i nie da się go zatwierdzić Space, Enter ani ⌘⏎ zaraz po otwarciu.
- **Dziedziczenie zaufania.** Zainstalowany Claude Code przy sprawdzaniu zaufania idzie w górę po katalogach nadrzędnych, więc zaufanie katalogu działa też na wszystkie jego podkatalogi; dlatego ostrzeżenie w dialogu mówi „z tego katalogu i jego podkatalogów”. Własny klucz Claude Code to raczej korzeń repozytorium git, a nasz klucz `toRealPath` działa dzięki przeszukiwaniu w górę. Gdy Claude Code mimo zapisu nadal odmawia, kończy się to `TrustIneffective` (punkt 8).
- **Zapisy równoległe.** Między ponownym odczytem a `move` zostaje okno rzędu milisekund, w którym cudzy zapis może zginąć. Działająca sesja może też później zapisać plik ze swojego stanu w pamięci i cofnąć nasz wpis. Skutek drugiego: start znów odmawia, okno pokazuje instrukcję ręczną (punkt 8). Nie sprawdzono, czy Claude Code używa blokady pliku (np. `~/.claude.json.lock`); jeśli tak, follow-up: respektować ją.
- **Format.** Zmiana nazwy pola, struktury `projects` albo klucza (np. ścieżka bez `toRealPath`) zepsuje funkcję bez czerwonego testu. `docs/compat.md` opisuje ręczne sprawdzenie po aktualizacji Claude Code.
- **Wiele kont (ADR 0029).** Zaufanie jest per katalog konfiguracji; fabryka dostaje `configHome`, więc AM-1003 złoży jeden `WorkspaceTrust` na konto.

## Konsekwencje

- Nowe typy w `agent-runtime:api` (`WorkspaceTrust`, `TrustError`, `RuntimeError.WorkspaceNotTrusted`) i `sessions:api` (`RuntimeProblem.WORKSPACE_NOT_TRUSTED`); `mission-control:ui` zależy od `agent-runtime:api`.
- docs/11 §4 i §8: port i zasada, że `~/.claude.json` nie ma właściciela w Quayside, a jedynym zapisem jest `WorkspaceTrust` po zgodzie.
- Nowy wiersz w `docs/compat.md`; `/compat-check` sprawdza go przy każdej nowej wersji Claude Code.
