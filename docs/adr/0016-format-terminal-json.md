# ADR 0016: Format pliku `terminal.json` (schemat v1) i zasady stosowania ustawień terminala

- **Status:** przyjęta
- **Data:** 2026-10-31
- **Dotyczy:** `feature:terminal:api`, `feature:terminal:impl`, plik `terminal.json` w katalogu danych aplikacji

## Kontekst

AM-508 (FR-T7) utrwala ustawienia terminala. Plik przeżywa aktualizacje aplikacji, więc jego kształt to decyzja trwała (docs/11 §8: właściciel `terminal:impl`, `JsonStore` z `core:storage`). Ustawienia to wartości z domyślnymi (docs/06), a nie dane użytkownika jak lista projektów: utrata jednej wartości kosztuje mniej niż odłożenie całego pliku. JediTerm czyta większość ustawień (font, bufor historii) przy budowie widżetu. Powłoka `fish` nie rozumie `exec "$@"`, którego `openCommand` używa do `claude attach` i `claude logs` (AM-502).

## Decyzja

1. Koperta `JsonStore`, w `data` obiekt z płaskimi polami:

   ```json
   { "schemaVersion": 1,
     "data": { "shell": "LOGIN_DEFAULT", "startDirectory": "SESSION_WORKTREE", "fontFamily": null,
               "fontSize": 13, "scrollbackLines": 100000, "ligatures": true, "optionAsMeta": false } }
   ```

2. Serializer ręczny na `JsonElement` (jak ADR 0009, bez pluginu kompilatora). Nieznane klucze są ignorowane. **Brak klucza, zły typ, nieznana wartość enuma albo wartość spoza `TerminalSettingsRules` daje wartość domyślną tego pola**, nie uszkodzenie pliku. Plik, który nie jest obiektem JSON, jest uszkodzony (`terminal.json.broken`, jak w `JsonStore`).
3. Ustawienia działają **w nowych kartach i panelach**. Otwarte karty zostają przy ustawieniach z chwili otwarcia.
4. Wybrana powłoka (`shell`) dotyczy tylko kart powłoki (`openShell`, podział panelu). `openCommand` zawsze idzie przez powłokę logowania użytkownika przekazaną do modułu, bo potrzebuje składni POSIX (`exec "$@"`).

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| Domyślna wartość pola przy błędzie (wybrana) | Jedno złe pole nie kasuje reszty; dodanie pola w v1 nie wymaga migracji | Cicha korekta wartości (bez komunikatu) |
| Cały plik `.broken` przy złym polu (jak ADR 0009) | Spójne z `projects.json` | Utrata wszystkich ustawień przez jedną wartość |
| Ścieżka powłoki zamiast profilu (`/opt/homebrew/bin/fish`) | Dowolna powłoka | Walidacja ścieżki, plik z danymi maszyny; docs/06 wymienia tylko zsh, bash, fish |
| Stosowanie na żywo w otwartych kartach | Natychmiastowy efekt | JediTerm nie przebudowuje bufora historii; niespójny stan kart |

## Konsekwencje

- Nowe pole w v1: dodać z wartością domyślną, bez migracji. Zmiana znaczenia pola wymaga `schemaVersion = 2`.
- `fontFamily` może wskazywać font, którego nie ma w systemie: `terminal:impl` używa wtedy fontu domyślnego JediTerm i loguje stałą kategorię.
- Gdy `$SHELL` użytkownika to `fish`, `openCommand` nadal nie zadziała (stan sprzed AM-508); osobne zadanie, jeśli ktoś to zgłosi.
