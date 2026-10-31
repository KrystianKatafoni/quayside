# ADR 0011: Komendy palety składa `app`, wyszukiwanie w `command-palette:api`

- **Status:** przyjęta
- **Data:** 2026-10-19
- **Dotyczy:** command-palette:api, command-palette:impl, command-palette:ui, app, projects:ui, mission-control:ui (AM-406, AM-408, AM-409)

## Kontekst

FR-K1 wymaga palety ⌘K ze wszystkimi akcjami, a FR-K2 skrótów w stylu vima. Akcje należą do różnych feature'ów (projekty, sesje, później terminal, powiadomienia, wygląd). Według docs/11 §3 `ui` nie widzi innego `ui`, a `api` nie może używać Compose. Szkic w docs/11 §4 ma w `Command` pole Compose `KeyShortcut?`, co łamie ENG-I8 i regułę Konsist. Według docs/11 §2 wyszukiwanie rozmyte jest w `impl`, a tego modułu `ui` nie widzi.

## Decyzja

1. Listę komend buduje `app` (composition root) z publicznych intencji kontrolerów `ui` (`ProjectsIntent`, `MissionControlIntent`) i rejestruje ją w `CommandRegistry`. Moduły `ui` innych feature'ów nie zależą od `command-palette:api`. Tytuły komend to te same teksty co w menu okna.
2. Skrót to własny typ `Shortcut`/`KeyChord` w `command-palette:api`. Mapowanie z Compose `KeyEvent` jest w `command-palette:ui`. Skróty z ⌘ obsługuje `MenuBar`, a skróty bez ⌘ obsługuje `Window.onKeyEvent` i działają tylko wtedy, gdy nie ma otwartego dialogu ani palety.
3. Wyszukiwanie to czysta reguła `CommandMatcher` w `command-palette:api` (wzór: `ProjectInputRules`). W `impl` zostaje tylko rejestr.

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| Komendy w `app` (wybrana) | feature'y nie znają palety; teksty wspólne z menu okna; mniej zależności | `app` rośnie z każdym feature; `ui` musi wystawić publiczne intencje |
| Każdy `ui` wystawia `CommandProvider` | komendy i teksty przy właścicielu | zależność każdego `ui` od `command-palette:api`; teksty menu i palety zdublowane |
| Wyszukiwanie w `impl` za interfejsem `CommandSearch` | zgodne z docs/11 §2 | fake w `testFixtures` musi powtarzać algorytm albo testy `ui` dostają inne wyniki |

## Konsekwencje

- Każdy kolejny feature z akcjami (terminal, powiadomienia, wygląd) wystawia publiczne intencje kontrolera, a `app` dopisuje komendy w `AppCommands`.
- Trzeba zaktualizować docs/11: w §2 wyszukiwanie przenieść do `api`, a w §4 `Command.shortcut: Shortcut?` zamiast `KeyShortcut?`.
- Jeśli `AppCommands` przekroczy limity detektu, dzielimy go na pliki per feature w `app`, bez zmiany tej decyzji.
