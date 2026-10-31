# ADR 0007: Strategia testów

- **Status:** przyjęta
- **Data:** 2026-10-18
- **Dotyczy:** wszystkie moduły, CI

## Kontekst

Kod piszą w dużej mierze agenci. Testy muszą wykrywać regresje i łamanie granic modułów bez uruchamiania prawdziwego Claude Code.

## Decyzja

Piramida: testy architektury (Konsist), jednostkowe na czystych rdzeniach, kontraktowe dla każdego `api` (fake i prawdziwa implementacja), integracyjne adapterów, funkcjonalne przez UI na fake'owym runtime, E2E tylko nocą. Szczegóły: [12](../12-strategia-testow.md).

## Konsekwencje

Bramka PR to `./gradlew check`. Hook `Stop` w Claude Code uruchamia szybką część bramki po zmianach w Kotlinie, a `/deliver` pełną przed wysłaniem PR.
