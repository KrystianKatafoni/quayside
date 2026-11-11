# ADR 0033: Ręczne przypisanie sesji do podfolderu w `session-history.json`

- **Status:** przyjęta
- **Data:** 2026-11-11
- **Dotyczy:** `feature:sessions:api` (`SessionCommands.assignPlace`, `PlaceAssignment`, `Session.assignedDir`, `SessionHistorySettings.places`), `feature:sessions:impl` (`SessionPlaces`, `SessionHistorySettingsSerializer`, `SessionsState`), `feature:projects:ui`, `feature:mission-control:ui`, plik `session-history.json`, ADR 0024, ADR 0032

## Kontekst

Miejsce sesji w grupie projektu (FR-P7: podfolder albo „folder główny”) wylicza się dziś w UI z `Session.cwd` przez `ProjectGroups.subfolderOf`. Dla sesji z dysku `cwd` to katalog startu (pierwszy bezwzględny `cwd`, ADR 0032), więc sesja, która zaczęła w `/p` i przeszła do `/p/soft`, ląduje w „folderze głównym”. Odczyt całego pliku sesji nie wchodzi w grę (ADR 0032, opcja D), a `cwdChanged` to tylko dolne ograniczenie. Użytkownik musi móc poprawić miejsce ręcznie (FR-P9, issue #172), a poprawka musi przetrwać restart, kolejne skany i wznowienie sesji (AM-313: id zmienia się z UUID na 8 znaków). `sessions:api` nie widzi `projects:api` (docs/11 §3), więc typy przypisania nie mogą używać `SubfolderName` ani `Project`. Żaden wydany build nie zapisał jeszcze `session-history.json` (CHANGELOG ma tylko `[Unreleased]`).

## Decyzja

1. **Miejsce zapisu:** nowy klucz `places` w `session-history.json`, schemat zostaje 1 (stare buildy pomijają nieznane klucze, a plik nie był jeszcze wydany). W `sessions:api` pole `SessionHistorySettings.places: Map<SessionId, StoredPlace>`, jak `hidden`.
2. **Klucz wpisu:** UUID rozmowy, gdy jest znany (sesja z dysku zawsze; sesja supervisora, gdy ma `conversationId`), w przeciwnym razie krótkie id supervisora. Odczyt sprawdza najpierw UUID, potem krótkie id, więc przypisanie przetrwa wznowienie (AM-313).
3. **Format:** tylko odstępstwa (ADR 0024): brak przypisania = brak wpisu, pusta mapa = brak klucza `places`. Wpis to obiekt z `project` (`ProjectId`) i opcjonalnym `subfolder` (dokładna nazwa z `Project.subfolders`); brak `subfolder` = „folder główny”. Klucze posortowane. Przykład: `"places": {"0b6f…": {"project": "p1", "subfolder": "soft"}, "7c5dcf5d": {"project": "p1"}}`. Wpis bez `project`, z pustym `subfolder`, z separatorem ścieżki, `.` lub `..` w `subfolder`: plik odłożony jako `.broken` (jak inne złe wartości w ADR 0032).
4. **Kolejność odczytu:** przy każdej publikacji migawki `sessions:impl` liczy `Session.assignedDir` z wpisu, gdy wpis jest ważny: projekt sesji (z `cwd`, `SessionRules.projectFor`) = `project` wpisu, a `subfolder` jest dokładnie równy jednej z `Project.subfolders` i jest bezpośrednim dzieckiem `Project.dir`. Ważny wpis wygrywa z `cwd`; nieważny jest pomijany (miejsce znowu wynika z `cwd`). Przypisanie nigdy nie zmienia projektu sesji, tylko miejsce w grupie. UI liczy miejsce z `Session.placeDir` (`assignedDir ?: cwd`).
5. **Podfolder zniknął z grupy albo projekt usunięto:** wpis zostaje w pliku i nie działa; gdy ten sam podfolder wróci do tego samego projektu, przypisanie znowu działa. Projekt dodany ponownie dostaje nowy `ProjectId`, więc stary wpis już nie pasuje. Quayside nie czyści wpisów samo (jak `hidden`, ADR 0032).
6. **Cofnięcie:** wybór „Automatycznie” (`assignPlace(id, null)`) usuwa wpis z pliku; bez wpisu nic nie jest zapisywane.
7. **Logi:** id sesji i rodzaj przypisania (`root`, `subfolder`, `auto`), nigdy nazwa podfolderu ani ścieżka (ENG-C8).

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| A. Klucz `places` w `session-history.json` (wybrana) | Jeden plik i jedno repo funkcji `sessions`; wzór `hidden`; bez nowego repo w `api` | Plik „ustawień” trzyma też dane per sesja; wpisy widoczne w `SessionHistorySettings` |
| B. Osobny plik `session-places.json` | Czysty podział ustawień i danych per sesja | Nowe repo i fabryka w `sessions:api`, drugi `JsonStore`, więcej kodu |
| C. Przypisanie w `projects.json` przy projekcie | Wpis znika razem z projektem | Właściciel pliku to `projects:impl`; migracja ADR 0026; `sessions` musiałoby pisać cudzy plik |
| D. Klucz zawsze = `Session.id` | Najprostsze | Przypisanie ginie przy wznowieniu sesji z dysku (UUID → 8 znaków) |
| E. Zapis ścieżki zamiast nazwy podfolderu | Bez sprawdzania `Project.subfolders` | Ścieżka w pliku (dane użytkownika), działa też po usunięciu podfolderu z grupy |

## Konsekwencje

- `Session` dostaje pole `assignedDir` i właściwość `placeDir`; każde miejsce w UI, które liczy podfolder z `cwd`, przechodzi na `placeDir` (grep po `subfolderOf(` i `SessionPlace.of(`).
- Zmiana `SessionCommands` (nowa metoda) łamie wszystkie implementacje interfejsu: `FakeSessions`, `PreviewSamples` w `app`, fake'i testów w `mission-control:ui`.
- Zdarzenia (`SessionEvent`) niosą sesję z tym samym `assignedDir` co migawka.
- Lista `places` rośnie z każdym przypisaniem; czyszczenie wpisów bez sesji nie jest częścią tej decyzji.
- Schemat 1 bez migracji jest poprawny tylko przed pierwszym wydaniem z `session-history.json`; po wydaniu zmiana znaczenia `places` wymaga schematu 2.
