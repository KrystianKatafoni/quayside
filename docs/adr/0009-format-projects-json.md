# ADR 0009: Format pliku `projects.json` (schemat v1)

- **Status:** przyjęta
- **Data:** 2026-10-18
- **Dotyczy:** `feature:projects:impl`, plik `projects.json` w katalogu danych aplikacji

## Kontekst

AM-302 utrwala listę projektów. Plik przeżywa aktualizacje aplikacji, więc jego kształt to decyzja trwała: każda zmiana oznacza migrację `vN → vN+1` w `JsonStore` (`core:storage`, AM-107). `Migration` działa na `JsonObject`, a w repo nie ma pluginu kompilatora kotlinx.serialization (AM-107 używa tylko gotowych `KSerializer`).

## Decyzja

Koperta `JsonStore` (`{"schemaVersion": 1, "data": …}`), a w `data` **obiekt** z tablicą projektów w kolejności dodania:

```json
{ "schemaVersion": 1,
  "data": { "projects": [ { "id": "3f6c…-uuid", "dir": "/Users/jan/projects/agent-manager", "displayName": "agent-manager" } ] } }
```

`id` to losowy UUID (string, nieprzezroczysty), `dir` to ścieżka rzeczywista (`toRealPath()`) jako napis absolutny, `displayName` to nazwa po walidacji. Nieznane klucze są ignorowane (zgodność w przód w obrębie v1). Plik z błędnym wpisem (brak pola, pusty `id`, ścieżka względna, powtórzony `id` lub `dir`) jest w całości uszkodzony i trafia do `projects.json.broken`. Serializer jest pisany ręcznie na `JsonElement` (`JsonEncoder`/`JsonDecoder`), bez pluginu kompilatora.

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| `data` jako obiekt `{ "projects": [...] }` (wybrana) | migracje na `JsonObject` działają, miejsce na przyszłe pola listy | o jeden poziom więcej |
| `data` jako goła tablica | prościej | `Migration` z AM-107 nie przyjmie tablicy; migracja v1 → v2 niemożliwa bez zmiany `core:storage` |
| `@Serializable` + plugin kompilatora | mniej kodu, standard | zmiana `build-logic` i katalogu wersji poza modułem zadania; walidacja wpisów i tak ręczna |
| pomijanie błędnych wpisów zamiast `.broken` | nie traci reszty listy | ciche gubienie danych bez komunikatu w UI; plik pisze tylko aplikacja, więc błędny wpis = uszkodzenie |

## Konsekwencje

- Zmiana pól wpisu wymaga `schemaVersion = 2` i migracji w `projects:impl` (docs/11 §10).
- Plik zawiera ścieżki z nazwą użytkownika: nie trafia do logów ani raportów błędów (ENG-C8).
- Gdy pierwszy kolejny moduł będzie potrzebował `@Serializable`, warto rozważyć plugin kompilatora w `build-logic` (osobny ADR); format pliku się od tego nie zmienia.
