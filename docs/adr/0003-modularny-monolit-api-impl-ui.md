# ADR 0003: Modularny monolit z podziałem api / impl / ui

- **Status:** przyjęta
- **Data:** 2026-10-18
- **Dotyczy:** struktura repo, build-logic, wszystkie feature

## Kontekst

Wymaganie nadrzędne: zmiana w jednym miejscu nie wymusza zmian w innych. Kod będzie pisany w dużej mierze przez agentów, więc granice muszą być wymuszane automatycznie, a nie tylko opisane.

## Decyzja

Każdy feature to moduły Gradle `api` (kontrakt), `impl` (wszystko `internal` poza fabryką) i `ui`. `impl` widzi tylko `app`. Reguły wymuszają convention plugins w `build-logic` i testy Konsist. Szczegóły: [11](../11-koncepcja-techniczna.md).

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| Jeden moduł z pakietami | Prosty build | Granice tylko umowne, łatwo je złamać |
| **api / impl / ui** | Granice wymuszone przez kompilator i testy | Więcej modułów i boilerplate fabryk |
| Mikroaplikacje / pluginy | Pełna izolacja | Przerost dla aplikacji desktopowej |

## Konsekwencje

Nowy feature zakładamy skillem `/new-module`, który generuje trzy moduły zgodne z konwencją. Zmiana `api` innego feature wymaga planu i zgody człowieka.
