# ADR 0010: Rozpoznawanie hooków Agent Managera w `.claude/settings*.json`

- **Status:** przyjęta
- **Data:** 2026-10-18
- **Dotyczy:** projects:impl (`DirectoryInspector`, AM-303), agent-runtime:impl (`HookInstaller`, AM-602)

## Kontekst

FR-P2 wymaga, żeby przy dodawaniu katalogu pokazać, czy są w nim nasze hooki. Pliki to `.claude/settings.json` i `.claude/settings.local.json`. `HookInstaller.status(HookScope.Project(dir))` z `agent-runtime:api` opisuje tylko `settings.local.json`, a jego implementacja powstanie dopiero w AM-602. Nasze hooki trzeba więc odróżnić od cudzych w dwóch modułach, które nie widzą nawzajem swoich `impl`. `agent-runtime:api` nie może zawierać typów JSON.

## Decyzja

Hook jest „nasz”, gdy handler w `hooks.<Zdarzenie>[*].hooks[*]` spełnia wszystkie warunki:

- `type` równa się `"http"`,
- `url` ma schemat `http`, host `127.0.0.1` albo `localhost` i ścieżkę `/hooks`; port może być dowolny, bo jest konfigurowalny,
- `headers` ma klucz `X-AM-Token` (wielkość liter nie ma znaczenia).

Zdarzenie liczy się tylko wtedy, gdy jego nazwa odpowiada `HookKind`: `Notification`, `Stop`, `StopFailure`, `SessionStart`, `SessionEnd`. `matcher` i inne pola są pomijane. Host `[::1]` i ścieżka `/hooks/` (z ukośnikiem na końcu) są celowo odrzucane, bo AM-602 ich nie zapisuje; AM-602 ma dzielić z `OurHooksTest` tę samą tabelę przypadków, żeby obie implementacje odrzucały je tak samo. `"hooks": null` traktujemy jak „`hooks` nie jest obiektem”, czyli plik nieczytelny (`Err` → `UNREADABLE`), a nie brak hooków. AM-602 zapisuje hooki w kształcie z docs/03, który spełnia tę regułę, a `HookInstaller.status` i `uninstall` stosują tę samą regułę. `projects:impl` czyta oba pliki sam, tylko do odczytu, i łączy wynik w `HooksPresence`.

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| Reguła w ADR, implementowana w obu `impl` (wybrana) | Bez zmian `api`, AM-303 nie czeka na AM-602 | Dwie implementacje tej samej reguły; zgodność pilnują testy tablicowe na tych samych przykładach |
| `projects:impl` woła `HookInstaller.status` | Jedna implementacja | Nie obejmuje `settings.json`; implementacji jeszcze nie ma; rozszerzenie `HookScope` to zmiana `agent-runtime:api` |
| Rozpoznawanie tylko po URL | Prościej | Pomyłki z innymi narzędziami nasłuchującymi lokalnie na `/hooks` |
| Własne pole-znacznik w handlerze (np. `"am": true`) | Jednoznaczne | Claude Code może odrzucać nieznane pola; poza oficjalnym schematem |

## Konsekwencje

Zmiana kształtu zapisywanych hooków (np. inny nagłówek tokenu) wymaga nowego ADR i zmian w obu modułach w tym samym PR. AM-602 dodaje do swoich testów wiersze tablicy z planu AM-303 (`OurHooksTest`). Jeśli powstanie zadanie przenoszące czystą regułę do `agent-runtime:api` (nazwy zdarzeń i stałe bez typów JSON), ten ADR zostanie zastąpiony.
