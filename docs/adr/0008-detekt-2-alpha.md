# ADR 0008: detekt 2.0 (alfa) jako analiza statyczna

- **Status:** przyjęta
- **Data:** 2026-10-18
- **Dotyczy:** `build-logic` (`am.quality`), bramka CI, hook `Stop`

## Kontekst

ENG-C1 wymaga detekt z zerową liczbą naruszeń. Projekt używa Kotlin 2.4. Stabilna linia detekt 1.23.x jest zbudowana na Kotlin 2.0 i odmawia pracy z nowszym kompilatorem w buildzie. Linia 2.0 (`dev.detekt`, obecnie 2.0.0-alpha.6) jest zbudowana na Kotlin 2.4.10.

## Decyzja

Używamy detekt 2.0.0-alpha.6 z konfiguracją `config/detekt/detekt.yml` nałożoną na domyślną. Bramka i `quickCheck` uruchamiają `detektMain` i `detektTest`, czyli analizę z rozwiązywaniem typów, bo bez niej część reguł (np. `ForbiddenMethodCall`, `InjectDispatcher`) nic nie zgłasza.

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| detekt 2.0 alfa | Działa z Kotlin 2.4, reguły z rozwiązywaniem typów | Wersja alfa: możliwe zmiany nazw reguł i API pluginu |
| detekt 1.23 | Stabilny | Niezgodny z Kotlin 2.4 w buildzie |
| Bez detekt, tylko ktlint | Prostota | Brak limitów złożoności i zakazów z ENG-C2/C3 |

## Konsekwencje

Dependabot i `maintainer` śledzą wydania detekt 2.x. Przy przejściu na wersję stabilną trzeba sprawdzić nazwy reguł w `detekt.yml` (walidacja konfiguracji przerywa build przy nieznanym kluczu). Jeśli alfa zablokuje pracę, tymczasowo wyłączamy detekt z `check` w osobnym PR z odniesieniem do tego ADR.
