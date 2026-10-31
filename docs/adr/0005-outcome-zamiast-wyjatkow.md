# ADR 0005: Typowane `Outcome` zamiast wyjątków na granicach modułów

- **Status:** przyjęta
- **Data:** 2026-10-18
- **Dotyczy:** wszystkie moduły `api`

## Kontekst

Błędy z CLI, plików i sieci muszą być obsłużone w UI w przewidywalny sposób. Wyjątki przechodzące między modułami ukrywają kontrakt.

## Decyzja

Granice modułów zwracają `Outcome<T, E>` z błędami jako `sealed interface`. Wyjątki istnieją tylko wewnątrz adapterów i są tam mapowane na błędy domenowe.

## Konsekwencje

Kompilator wymusza obsługę błędów w `when`. Każdy nowy rodzaj błędu to zmiana `api`, widoczna w review i testach kontraktowych.
