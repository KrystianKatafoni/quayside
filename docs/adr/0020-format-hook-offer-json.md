# ADR 0020: Plik `hook-offer.json`: zapamiętanie odmowy instalacji hooków

- **Status:** proponowana
- **Data:** 2026-11-02
- **Dotyczy:** `app` (AM-607), plik `hook-offer.json` w katalogu danych aplikacji, docs/11 §8

## Kontekst

AM-607 (NFR-5) proponuje przy starcie instalację hooków w `~/.claude/settings.json`. Gdy użytkownik wybierze „Pomiń” albo odinstaluje hooki, aplikacja nie powinna pytać przy każdym starcie. Odmowę trzeba więc zapisać na dysku. docs/11 §8 przypisuje każdy plik jednemu modułowi, a dotąd żaden plik nie należy do `app`. `agent-runtime:api` (`HookInstaller`) nie ma miejsca na taką preferencję, a jej dodanie to zmiana `api`.

## Decyzja

Wariant wybrał człowiek 2026-11-02 (plan AM-607); status zmieni się na „przyjęta” przy scaleniu PR.

1. Nowy plik `hook-offer.json` w `~/Library/Application Support/Quayside/`, właściciel `app` (composition root, który pokazuje propozycję). Koperta `JsonStore` z `core:storage`:

   ```json
   { "schemaVersion": 1, "data": { "dismissed": true } }
   ```

2. Serializer ręczny na `JsonElement`, jak `summaries.json` (ADR 0019). Brak pliku, brak klucza, zły typ albo plik `.broken` oznacza `dismissed = false`: aplikacja zapyta ponownie, co jest bezpieczne, bo pytanie niczego nie zmienia.
3. `dismissed = true` zapisują: przycisk „Pomiń” w propozycji przy starcie oraz udane „Odinstaluj”. Komenda palety „Hooki Claude Code” działa zawsze, niezależnie od pliku.

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| Plik `hook-offer.json` w `app` (wybrana) | Bez zmian `api`; mały, jasny właściciel | Pierwszy plik należący do `app` |
| Bez zapisu: pytanie przy każdym starcie, dopóki hooki nie są zainstalowane | Bez nowego pliku i ADR | Natrętne dla kogoś, kto świadomie odmówił |
| Preferencja w `agent-runtime` (nowy typ w `api`, plik w `impl`) | Wszystko o hookach w jednym feature | Zmiana `api` innego feature; preferencja UI w module adaptera |
| `java.util.prefs` | Bez `JsonStore` | Drugi mechanizm trwałości obok `JsonStore`; trudniejsze testy |

## Konsekwencje

- docs/11 §8 dostaje wiersz `hook-offer.json` z właścicielem `app`.
- Nowe pole w v1: z wartością domyślną, bez migracji.
- Gdy powstanie ekran ustawień aplikacji, plik może przejść do jego modułu; wtedy nowy ADR zastąpi ten.
