# ADR 0032: Odczyt plików sesji Claude Code i plik `session-history.json`

- **Status:** proponowana
- **Data:** 2026-11-10
- **Dotyczy:** `feature:agent-runtime:api` (`SessionHistory`), `feature:agent-runtime:impl` (`FileSessionHistory`, `SessionFiles`, `SessionFileReader`), `feature:sessions:api` (`SessionOrigin`, `SessionHistorySettings`), `feature:sessions:impl`, plik `session-history.json`, `docs/compat.md`

## Kontekst

FR-P9: użytkownik, który od tygodni pracuje w folderze i instaluje Quayside, chce widzieć stare sesje projektu. Supervisor (`claude agents --json --all`) zna tylko sesje w tle; pomiar AM-311 na Claude Code 2.1.322: 20 sesji supervisora wobec 132 plików `<claudeHome>/projects/<zakodowany cwd>/<uuid>.jsonl`. Format plików jest nieudokumentowany, pliki mają do 163 MB i zawierają całe rozmowy (dane użytkownika, ENG-C8). Wiedza o formatach Claude Code należy do `agent-runtime` (docs/11 §10), a `sessions:impl` nie widzi `internal` z `agent-runtime:impl`. Limity wieku i liczby wybiera użytkownik, więc trzeba je zapisać.

## Decyzja

1. **Port tylko do odczytu w `agent-runtime:api`** (wariant A, decyzja człowieka 2026-11-10): `SessionHistory.list(dirs, since)`. Implementacja czyta najwyżej 64 KiB z początku i 64 KiB z końca pliku, filtruje wiek po `mtime` przed otwarciem pliku, zawęża katalogi przez stratne kodowanie nazw (`SessionFiles.candidates`), a przynależność rozstrzyga po pierwszym bezwzględnym `cwd` (katalog startu). Wyniki trzyma w pamięci podręcznej po (ścieżka, rozmiar, `mtime`); błąd odczytu pliku nie trafia do pamięci podręcznej, a wpisy usuniętych plików i katalogów, które przestały być kandydatami, są czyszczone. Port nie ma metody zapisu, przenoszenia ani usuwania; Quayside nigdy nie zmienia plików sesji.
2. **Sesja z dysku w `sessions:api`** ma `origin = DISK`, `id` = UUID rozmowy (pełny, więc nie koliduje z 8-znakowym id supervisora) i zawsze `SessionState.Stopped`. Gdy supervisor zna tę rozmowę (`conversationId` = UUID albo `id` = 8 pierwszych znaków UUID), wygrywa wpis supervisora. Komendy poza „Usuń” zwracają `UnknownSession` bez wołania CLI.
3. **Skan:** przy starcie, po zmianie katalogów projektów, po zmianie ustawień i co 60 s. Błąd skanu zostawia ostatni wynik i nie oznacza migawki jako nieaktualnej (baner dotyczy supervisora).
4. **`session-history.json`**, schemat 1, w katalogu ustawień aplikacji, przez `JsonStore` (`core:storage`). Zapisuje tylko odstępstwa od domyślnych (ADR 0024): `maxAge` (`DAYS_7` | `DAYS_90` | `UNLIMITED`; brak = 30 dni), `maxPerProject` (`50` | `1000`; brak = 200), `hidden` (posortowane UUID sesji ukrytych przez „Usuń”; brak = pusty; decyzja człowieka do AM-312, pytanie 3a: „Usuń” przy sesji z dysku ukrywa ją na stałe i nigdy nie kasuje pliku). Nieznana wartość: plik odłożony jako `.broken`, domyślne. Plik ze schematem nowszym: `NEWER_SCHEMA`, bez zapisu.
5. **Logi:** tylko liczby i stałe kategorie (pominięte pliki per `SessionFileProblem`), nigdy ścieżka, UUID w kontekście tytułu ani tekst z pliku.

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| A. Port w `agent-runtime:api`, parser w `agent-runtime:impl` (wybrana) | Zgodne z docs/11 §10; parser z AM-311 bez przenoszenia; fake w `testFixtures` | Zmiana `api` dwóch feature |
| B. Parser w `sessions:impl` (obok `TranscriptTail`) | Mniej zmian `api` | Wiedza o formacie Claude Code w dwóch modułach; przeniesienie kodu i fixtures AM-311 |
| C. Tylko `claude agents --json --all` | Bez nieudokumentowanego formatu | Nie zna sesji interaktywnych; FR-P9 niespełnione |
| D. Czytanie całych plików | Pełne dane (wszystkie `cwd`) | Do 163 MB na plik; koszt i dane użytkownika w pamięci |
| E. Limity zapisywane przy projekcie w `projects.json` | Jeden plik mniej | Migracja `projects.json` (ADR 0026); limity są globalne |

## Konsekwencje

- Zmiana formatu plików sesji w Claude Code daje pustą historię, nie awarię; wykrywa ją `/compat-check` (wiersz w `docs/compat.md`).
- Sesja interaktywna otwarta teraz w terminalu wygląda jak zatrzymana (plakietka „z dysku”); AM-313 musi uwzględnić R4 z `docs/compat.md`.
- `cwdChanged` jest dolnym ograniczeniem (środek pliku nie jest czytany); ręczne poprawki przypisania robi AM-314 w osobnym pliku albo w tym (osobny ADR).
- Lista `hidden` rośnie z każdym „Usuń”; ponieważ plik sesji zostaje, usunięcie wpisu z `hidden` przywraca sesję. Czyszczenie wpisów bez pliku nie jest częścią tej decyzji.
- `claudeHome` jest parametrem fabryki; obsługa `CLAUDE_CONFIG_DIR` i wielu kont (ADR 0029) to osobna zmiana.
