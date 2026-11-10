# Fixtures plików sesji Claude Code

Wejścia dla `SessionFiles` i `SessionFileReader` (AM-311): pliki `<claudeHome>/projects/<zakodowany cwd>/<uuid>.jsonl`.

- `synthetic/` — wyłącznie pliki napisane ręcznie. Kształt wpisów odtworzono z agregatów raportu pomiaru
  (`SessionFilesOnDiskMeasurement`, M1–M8), nie z prawdziwych plików.
- Nie ma katalogu `recorded/`: żaden fragment prawdziwego pliku `.jsonl` nie trafia do repozytorium (decyzja człowieka
  z 2026-11-10, plan AM-311). Test `no real session file is recorded` pilnuje, żeby go nie dodać.
- Wpisy z `sessionId` mają go równego `00000000-0000-4000-8000-000000000001` (nie mają go `summary`, `file-history-snapshot`
  i `not-jsonl.txt`), ścieżki są pod `/Users/test/projects/quayside`, czasy z 2026-01-01, a tekst to `redacted`
  (poza zdaniami użytymi jako tytuły).

Test `SessionFilesFixturesTest` sprawdza, że każdy plik z tego katalogu jest wymieniony w tabeli poniżej, że fixtures
nie zawierają danych osobowych oraz że `SessionFiles` daje dla każdego oczekiwany wynik.

| Plik | Źródło | Uwagi |
| --- | --- | --- |
| `synthetic/interactive-single-cwd.jsonl` | synthetic | Zwykła sesja: `summary` bez `cwd` na początku (w 2.1.322 prawdziwe pliki zaczynają od `last-prompt`, `queue-operation` lub `mode`), komenda `/model`, wpis `isMeta`, potem pierwsza prawdziwa wiadomość; jedno `cwd`; ostatni wpis 30 minut po pierwszym. |
| `synthetic/background-first-entry-has-cwd.jsonl` | synthetic | Pierwszy wpis ma `cwd`, `sessionId` i `timestamp`. Pomiar M1 (2.1.322) nie znalazł takiego pliku wśród 132 prawdziwych (pierwszy wpis nigdy nie ma `cwd`); przypadek zostaje jako dozwolony wariant. |
| `synthetic/cwd-changes.jsonl` | synthetic | Sesja zaczyna w korzeniu projektu i przechodzi do `soft/`: `startCwd` to korzeń, `cwdChanged`. |
| `synthetic/starts-with-command.jsonl` | synthetic | Dwa pierwsze wpisy `user` to komenda `<command-name>` i `isMeta`; tytuł z trzeciego. |
| `synthetic/sidechain.jsonl` | synthetic | `isSidechain: true` od pierwszego wpisu: nie jest sesją do wylistowania. |
| `synthetic/subfolder.jsonl` | synthetic | Sesja uruchomiona w podfolderze `soft/`: `startCwd` to podfolder. |
| `synthetic/no-user-message.jsonl` | synthetic | Tylko migawka plików i wpis `assistant`: poprawna sesja bez tytułu. |
| `synthetic/not-jsonl.txt` | synthetic | Tekst bez linii JSON. |
| `synthetic/leading-metadata.jsonl` | synthetic | Kształt z pomiaru M1 (2.1.322): sześć wpisów bez `cwd` (`last-prompt`, `custom-title`, `agent-name`, `mode`, `permission-mode`, `session-owner`), pierwsze `cwd` i `timestamp` dopiero we wpisie 7. |
