# ADR 0023: Format pliku `theme.json` (schemat v1) i wykrywanie trybu jasny/ciemny macOS

- **Status:** proponowana
- **Data:** 2026-11-05
- **Dotyczy:** `feature:appearance:api`, `feature:appearance:impl`, `app`, plik `theme.json` w katalogu danych aplikacji, `docs/08-wyglad.md`

## Kontekst

AM-804 (FR-A5, FR-A6) utrwala ustawienia wyglądu, dodaje przełączanie motywu dzień/noc razem z trybem macOS oraz import i eksport pliku `theme.json`. Plik przeżywa aktualizacje i jest wymieniany między użytkownikami, więc jego kształt to decyzja trwała (docs/11 §8: właściciel `appearance:impl`). docs/08 pokazuje szkic bez koperty, z sekcją `terminal` (font i rozmiar terminala mają już właściciela: `terminal.json`, ADR 0016) i z `customSchemes`. JDK 21 nie ma API do odczytu trybu wyglądu systemu; JNA jest w aplikacji tylko przechodnio (pty4j), a ADR 0018 (JNA dla CoreAudio) jest wciąż proponowana.

## Decyzja

1. Koperta `JsonStore`, w `data` płaskie pola z nazwami enumów jak w `terminal.json`:

   ```json
   { "schemaVersion": 1,
     "data": { "scheme": "plex", "typography": "plex", "uiScale": "M", "density": "COMFORTABLE",
               "followSystem": false, "dayScheme": "paper", "nightScheme": "plex" } }
   ```

2. Odczyt przy starcie jest łagodny jak ADR 0016: brak klucza, zły typ, nieznana nazwa enuma albo id spoza katalogu daje wartość domyślną pola; plik, który nie jest obiektem, idzie do `theme.json.broken`. Import pliku wybranego przez użytkownika jest ścisły: obecne, ale złe pole to błąd `InvalidValue`; brak pola daje wartość domyślną. Eksport zapisuje dokładnie ten format, więc wyeksportowany plik można też położyć jako `theme.json`.
3. Bez sekcji `terminal` (jedno źródło prawdy: `terminal.json`) i bez `customSchemes` (własne schematy to osobna decyzja z walidacją NFR-6; dodanie pola w v1 nie wymaga migracji).
4. Tryb macOS: `defaults read -g AppleInterfaceStyle` przez `ProcessRunner` z `core:process` (limit 2 s), odpytywany co 5 s tylko przy `followSystem = true`; wyjście z kodem 0 i słowem „Dark” w ostatniej niepustej linii = ciemny; kod 0 z innym słowem albo kod 1 (brak klucza) = jasny; błąd odczytu (inny kod, limit czasu, brak programu) nie zmienia ostatniego znanego trybu. Pierwszy odczyt odbywa się w fabryce modułu, przed pierwszym oknem.

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| A. `defaults` przez `ProcessRunner` co 5 s (wybrana) | Bez nowej zależności; testy z `FakeProcessRunner` | Powłoka logowania przy każdym odpytaniu; opóźnienie do 5 s |
| B. JNA + `CFPreferencesCopyAppValue` co 1 s | Tani odczyt, małe opóźnienie | Nowa bezpośrednia zależność i kod natywny (jak ADR 0018) |
| C. JNA + obserwacja `AppleInterfaceThemeChangedNotification` | Natychmiastowa reakcja, brak odpytywania | Callback Objective-C przez JNA, najtrudniejsze testy |
| Sekcja `terminal` w `theme.json` (docs/08) | Jeden plik do wymiany | Dwa źródła prawdy dla fontu terminala |
| Plik bez koperty (docs/08) | Krótszy plik | Brak wersji schematu: nie da się odróżnić nowszego pliku |

## Konsekwencje

- docs/08 (sekcja `theme.json`) dostaje ten format; docs/11 §4 opisuje `applied` i `ThemeFiles`.
- Nowe pole w v1: dodać z wartością domyślną, bez migracji. Zmiana znaczenia pola wymaga `schemaVersion = 2`.
- Gdy tryb „Automatycznie” w macOS nie aktualizuje `AppleInterfaceStyle` albo koszt odpytywania okaże się zauważalny, przechodzimy na opcję B (nowy ADR).
- Pierwszy odczyt trybu jest w `runBlocking` w `main`, więc przy ciężkim profilu powłoki może opóźnić pierwsze okno do 2 s; przy profilu wolniejszym niż 2 s tryb się nie wczyta (jeden WARN) i obowiązuje zapisany `scheme` do pierwszego udanego odczytu.
- Własne schematy kolorów (FR-A6, część „własny schemat”) wymagają osobnego zadania: katalog zmienny w czasie i walidacja kontrastu importowanych kolorów.
