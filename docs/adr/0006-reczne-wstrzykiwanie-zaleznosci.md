# ADR 0006: Ręczne wstrzykiwanie zależności

- **Status:** przyjęta
- **Data:** 2026-10-18
- **Dotyczy:** app, fabryki modułów

## Kontekst

Moduły `impl` mają być hermetyczne, a wiązanie widoczne w jednym miejscu.

## Decyzja

Bez frameworka DI. Każdy `impl` udostępnia jedną publiczną fabrykę `xxxModule(deps): XxxApi`. Moduł `app` wywołuje fabryki w `main` (composition root).

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| **Ręczne DI** | Błędy w czasie kompilacji, zero magii, łatwe testy | Trochę kodu w `app` |
| Koin | Mniej kodu wiązania | Błędy w runtime, łatwo ominąć granice modułów |
| Dagger/Hilt | Weryfikacja w kompilacji | Ciężkie, słabo wspierane na Desktop |

## Konsekwencje

Testy funkcjonalne składają aplikację tymi samymi fabrykami, podmieniając adaptery na fake'i.
