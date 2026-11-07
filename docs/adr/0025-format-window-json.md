# ADR 0025: Plik `window.json`: układ okna (wysokość panelu terminala)

- **Status:** przyjęta (zatwierdził człowiek 2026-11-07, wariant A w planie AM-819)
- **Data:** 2026-11-07
- **Dotyczy:** `app` (AM-819), plik `window.json` w katalogu danych aplikacji, docs/11 §8

## Kontekst

AM-819 (FR-T2) pozwala przeciągnąć granicę między Mission Control a panelem terminala w trybie listy. Człowiek zdecydował 2026-11-06, że wysokość ma przetrwać ponowne uruchomienie, więc trzeba ją zapisać na dysku. Dziś żaden plik nie opisuje układu okna (rozmiar okna też nie jest zapisywany). Układ rysuje `app` (`QuaysideApp`), a docs/11 §8 przypisuje każdy plik jednemu modułowi. `terminal.json` należy do `terminal:impl`, a jego typ `TerminalSettings` leży w `terminal:api`.

## Decyzja

1. Nowy plik `window.json` w `~/Library/Application Support/Quayside/`, właściciel `app`. Koperta `JsonStore` z `core:storage`, schemat 1:

   ```json
   { "schemaVersion": 1, "data": { "terminalHeight": 412.5 } }
   ```

2. `terminalHeight` to wysokość panelu terminala w trybie listy w dp układu (po skali UI), liczba JSON. Zapisywane są tylko odstępstwa od wartości domyślnej (zasada z ADR 0024): wysokość równa domyślnej (320) nie ma klucza; brak klucza oznacza bieżącą domyślną.
3. Odczyt łagodny, jak ADR 0016: brak klucza, string, wartość nieliczbowa albo ≤ 0 daje domyślną; `data`, które nie jest obiektem, przenosi plik do `window.json.broken`. Bardzo duże wartości nie są błędem: przy rysowaniu wysokość jest ograniczana do miejsca w oknie.
4. Zapis tylko po zakończeniu przeciągania, nie przy każdym ruchu; zapis atomowy daje `JsonStore`.

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| A. `window.json` w `app` (wybrana) | Bez zmian `api`; jasny właściciel; miejsce na przyszły rozmiar i położenie okna | Drugi plik należący do `app` |
| B. Pole w `terminal.json` (`TerminalSettings`) | Jeden plik ustawień terminala | Zmiana `terminal:api`; układ okna miesza się z ustawieniami z dialogu terminala |
| C. Pole w `hook-offer.json` | Bez nowego pliku | Plik o innym znaczeniu (ADR 0020); mylący właściciel danych |
| D. `java.util.prefs` | Bez `JsonStore` | Drugi mechanizm trwałości; trudniejsze testy |
| E. Bez zapisu (tylko w pamięci) | Najprostsze | Sprzeczne z decyzją człowieka z 2026-11-06 |

## Konsekwencje

- docs/11 §8 dostaje wiersz `window.json` z właścicielem `app`.
- Nowe pole w v1 (np. rozmiar okna): z wartością domyślną, bez migracji. Zmiana znaczenia pola (np. ułamek wysokości zamiast dp) wymaga `schemaVersion = 2`.
- Zmiana domyślnej wysokości dociera do wszystkich, którzy nie przeciągnęli uchwytu, bez migracji.
- Wysokość w dp po skali UI: po zmianie skali UI panel zachowuje liczbę dp, więc rośnie lub maleje razem z resztą okna.
