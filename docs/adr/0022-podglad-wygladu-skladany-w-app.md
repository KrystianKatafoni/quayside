# ADR 0022: Podgląd ekranu Wygląd składany w `app`

- **Status:** proponowana
- **Data:** 2026-11-05
- **Dotyczy:** `appearance:ui`, `app`, `core:design-system` (`AmTheme.density`), `mission-control:ui` (zawartość podglądu, wiersze sesji)

## Kontekst

FR-A3 i docs/08 wymagają podglądu na żywo „prawdziwego ekranu Mission Control”. Ekran Wygląd leży w `appearance:ui`, a Mission Control w `mission-control:ui`. Reguła 1 z docs/11 §3 pozwala modułowi `ui` widzieć tylko `api` i `core:*`, więc `appearance:ui` nie może wołać `MissionControl(...)`. Podgląd nie może też używać danych użytkownika ani wykonywać akcji na prawdziwych sesjach.

## Decyzja

`appearance:ui` przyjmuje podgląd jako slot `preview: @Composable () -> Unit` i sam otacza go szkicem wyglądu (`AmTheme` z kolorami, kształtem, typografią i gęstością listy szkicu, `LocalDensity` ze skalą S/M/L). Gęstość listy przechodzi przez `AmTheme.density` z `core:design-system`, więc wiersze sesji w `mission-control:ui` reagują na nią bez zależności od `appearance`. Zawartość slotu składa `app`: prawdziwy `MissionControl` z osobnym `MissionControlController` nad przykładowymi danymi w pamięci (`SessionsApi` i `ProjectsApi` tylko do odczytu, stały zegar). Podgląd nie przyjmuje kliknięć ani fokusu. Ten sam wzorzec co ADR 0011: składanie między feature należy do `app`.

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| A. Slot w `appearance:ui`, treść z `app` (wybrana) | Prawdziwy ekran, granice modułów bez zmian, dane przykładowe pokazują wszystkie statusy | Kod danych przykładowych w `app` (ok. 100 linii) |
| B. Zależność `appearance:ui` → `mission-control:ui` | Mniej kodu w `app` | Łamie regułę 1 (ui → ui) |
| C. Własna makieta ekranu w `appearance:ui` | Brak zależności | Nie jest „prawdziwym ekranem”, rozjeżdża się po każdej zmianie Mission Control |
| D. Szkic stosowany od razu do całego okna | Najmniej kodu | Pusta lista sesji nic nie pokazuje; łatwo zostawić aplikację w nieczytelnym motywie |

## Konsekwencje

- Każda zmiana konstruktora `MissionControlController` dotyka też danych podglądu w `app` (test okna to wykryje).
- Terminal (JediTerm w `SwingPanel`) nie wchodzi do podglądu: rysuje się nad Compose. Kolory terminala pokazuje próbka w Compose z `termBg`/`termText` i fontem terminala.
- Kolejne ekrany ustawień z podglądem innego feature używają tego samego slotu.
