# ADR 0026: Format `projects.json`: schemat 2 z podfolderami grupy

- **Status:** przyjęta (zatwierdził człowiek 2026-11-07)
- **Data:** 2026-11-07
- **Dotyczy:** `feature:projects:impl` (`ProjectListSerializer`, `ProjectListMigration`, `ProjectsModule`), `feature:projects:api` (`Project.subfolders`), plik `projects.json`; rozszerza ADR 0009 (przyjęta, bez zmian w jej treści)

## Kontekst

AM-306 (FR-P7) pokazuje projekt jako grupę z wybranymi podfolderami (np. `plant-puls/` z `soft/` i `marketing/`). Wybór podfolderów musi przetrwać restart, więc wpis projektu w `projects.json` dostaje nowe pole. ADR 0009 mówi, że zmiana pól wpisu wymaga `schemaVersion = 2` i migracji. Schemat 1 ignoruje nieznane klucze, więc starsza wersja aplikacji odczytałaby nowy klucz bez błędu, ale przy pierwszym zapisie po cichu by go usunęła.

## Decyzja

1. Schemat pliku rośnie do 2. Wpis projektu ma opcjonalny klucz `subfolders`: tablicę nazw bezpośrednich podkatalogów (`["marketing", "soft"]`), bez powtórzeń, posortowaną bez wielkości liter.
2. Klucz jest zapisywany tylko, gdy lista jest niepusta (zapis tylko odstępstw, jak ADR 0024). Brak klucza = projekt bez grupy.
3. Migracja 1 → 2 nie zmienia danych (wpisy schematu 1 są poprawnymi wpisami schematu 2).
4. Plik z błędnym `subfolders` (nie tablica, element nie napis, pusta nazwa, `/`, `.`, `..`, nazwa z kropką na początku, znak sterujący, nazwa dłuższa niż 80 code pointów, powtórzenie) jest uszkodzony i trafia do `projects.json.broken`, jak inne błędne wpisy w ADR 0009. Serializer nie sprawdza dysku: brak katalogu na dysku nie psuje pliku.
5. Plik nowszy niż schemat 2 daje `NEWER_SCHEMA` (bez zapisu), jak dotąd.

```json
{ "schemaVersion": 2,
  "data": { "projects": [
    { "id": "3f6c…", "dir": "/Users/jan/plant-puls", "displayName": "plant-puls", "subfolders": ["marketing", "soft"] },
    { "id": "9a1d…", "dir": "/Users/jan/quayside", "displayName": "quayside" } ] } }
```

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| A. Schemat 2, klucz tylko gdy niepusty (wybrana) | Starsza wersja nie skasuje podfolderów (widzi `NEWER_SCHEMA`); plik bez grup wygląda prawie jak dziś | Po migracji starsza wersja nie zapisze listy projektów |
| B. Schemat 1 z nowym kluczem | Bez migracji; starsza wersja czyta listę | Starsza wersja przy zapisie po cichu usuwa podfoldery |
| C. Osobny plik `groups.json` | `projects.json` bez zmian | Dane jednego projektu w dwóch plikach; usuwanie projektu musi sprzątać dwa pliki; brak atomowości między nimi |

## Konsekwencje

- Powrót do starszej wersji aplikacji po migracji daje pustą listę projektów i zablokowane komendy (`NEWER_SCHEMA`) do czasu ponownej aktualizacji. Plik po migracji nie ma kopii schematu 1.
- Kolejne opcjonalne pola wpisu (np. stan zwinięcia grupy) wymagają decyzji, czy to nowy schemat; zapisywane są tylko odstępstwa od domyślnych.
- Nazwy podfolderów zawierają fragmenty ścieżek: nie trafiają do logów ani raportów błędów (ENG-C8).
