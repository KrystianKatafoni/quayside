# ADR 0035: Format `accounts.json` i katalog nowego konta

- **Status:** proponowana
- **Data:** 2026-11-12
- **Dotyczy:** `feature:accounts:api`, `feature:accounts:impl` (`AccountsSerializer`, `JsonAccountsRepo`, `FileAccountSetup`), plik `accounts.json`, katalog `~/.claude-quayside/<id>/`, katalog `accounts-backup/`, `docs/11` §8
- **Rozszerza:** [ADR 0029](0029-wiele-kont-claude.md) (punkty 1 i 4; nie zastępuje go), stosuje [ADR 0024](0024-format-notifications-json.md) (zapis tylko odstępstw)

## Kontekst

AM-1002 wprowadza listę kont Claude (FR-L1, FR-L2, FR-L9). ADR 0029 ustala, co plik zawiera, ale nie ustala jego kształtu. Format pliku jest decyzją trwałą (docs/11 §8). Dodatkowe ograniczenia:

- Konto domyślne to zawsze `~/.claude`. Ścieżka zależy od katalogu domowego, więc nie może być stałą w pliku. Konto domyślne uruchamiamy zawsze bez `CLAUDE_CONFIG_DIR` (pomiar AM-1001, `docs/compat.md`).
- Kolejne zadania M10 dopiszą pola: `current` (AM-1010), `proposeSwitch` (AM-1006), próg ostrzeżenia o zużyciu (AM-1008).
- AM-1001 potwierdził, że dowiązanie `projects/` → `~/.claude/projects/` przetrwało pracę Claude Code i że rozmowę da się wznowić z drugiego konta. Nie potwierdził bezpiecznych zapisów równoległych ani odczytu pamięci automatycznej na obu kontach.

## Decyzja

1. **Plik** `~/Library/Application Support/Quayside/accounts.json`, właściciel `accounts:impl`, przez `JsonStore` (`schemaVersion` 1). Zapisujemy tylko odstępstwa od domyślnych. Brak pliku albo pusty obiekt `data` oznacza: tylko konto domyślne, włączone i w automacie, chłodzenie 5 h. Otwarcie aplikacji bez zmian niczego nie zapisuje.
2. **Kształt `data`:**

   ```json
   {
     "accounts": [
       { "id": "praca", "label": "Praca", "configDir": "/Users/x/.claude-quayside/praca" },
       { "id": "default", "enabled": false },
       { "id": "prywatne", "label": "Prywatne", "configDir": "/Users/x/.claude-work", "inAutomation": false }
     ],
     "cooldownMinutes": 120
   }
   ```

   - Kolejność tablicy `accounts` to kolejność automatu. Gdy w tablicy nie ma wpisu `default`, konto domyślne stoi na pierwszym miejscu.
   - Wpis `default` nie ma `configDir`, bo ścieżka zawsze wynika z katalogu domowego. Wpis powstaje tylko wtedy, gdy konto domyślne ma odstępstwo: wyłączone, poza automatem, własna nazwa albo pozycja inna niż pierwsza.
   - Wpis konta dodatkowego ma zawsze `id`, `label` i bezwzględny `configDir`. Pola `enabled` i `inAutomation` zapisujemy tylko wtedy, gdy mają wartość `false`.
   - `id` to slug `[a-z0-9-]{1,32}` z nazwy konta. Wartości `default` i `auto` są zarezerwowane. `auto` odrzuca jedna reguła, `AccountRules.validate` (`AccountProblem.RESERVED_ID`): przy `update` repozytorium i przy odczycie pliku (wpis `auto` daje plik `.broken`).
   - `cooldownMinutes` zapisujemy tylko wtedy, gdy różni się od 300. Dozwolony zakres to 60–1440.
3. **Odczyt.** Nieznane klucze są pomijane. Wartość złego typu, `id` spoza wzorca, powtórzone `id` lub `configDir`, wpis `default` z `configDir` i `cooldownMinutes` spoza zakresu dają `SerializationException` ze stałą kategorią (bez cytowania wejścia). Wtedy `JsonStore` odkłada plik jako `accounts.json.broken` i używa domyślnych. Plik nowszy niż schemat 1 daje `NewerSchema`: aplikacja nic nie zapisuje.
4. **Pola dopisywane później** (`current`, `proposeSwitch`, próg zużycia) są opcjonalne i mają wartości domyślne, więc nie zmieniają numeru schematu (jak `sound` w ADR 0024, punkt 3). Zmiana znaczenia istniejącego pola wymaga schematu 2 i migracji.
5. **Katalog nowego konta** to `~/.claude-quayside/<id>/`, tworzony z prawami `rwx------`. W środku powstaje bezwzględne dowiązanie `projects` → `~/.claude/projects` (gdy cel nie istnieje, Quayside tworzy pusty katalog). Dowiązanie tworzymy tylko wtedy, gdy `NewAccount.shareProjects` ma wartość `true`. Decyzja człowieka 1a (plan AM-1002): dowiązanie jest domyślnie włączone (`shareProjects = true`), a KDoc i ten ADR ostrzegają o niepotwierdzonych zapisach równoległych (patrz „Konsekwencje”). Potem, według wyboru, Quayside jednorazowo kopiuje z `~/.claude` pliki `CLAUDE.md` i `settings.json` oraz katalogi `agents/`, `skills/` i `commands/`. Dowiązania symboliczne w kopiowanych katalogach kopiujemy jako dowiązania. Nigdy nie kopiujemy `plugins/`, `.claude.json`, `.credentials.json`, `daemon/` ani `jobs/`. Gdy któryś krok się nie uda, katalog nowego konta jest usuwany w całości (przed operacją nie istniał), a plik `accounts.json` się nie zmienia.
6. **Wskazanie istniejącego katalogu** tylko dopisuje konto do pliku. W katalogu niczego nie tworzymy ani nie kopiujemy. Katalog `~/.claude`, także przez dowiązanie lub z końcowym `/`, jest odrzucany (porównanie po `toRealPath`).
7. **Kopia dla istniejącego konta** („Skopiuj ustawienia z konta domyślnego…”) scala pliki według ścieżki względnej. Plik, który już istnieje i ma inną treść, przed nadpisaniem trafia do `accounts-backup/<epochMillis>-<id>/<ścieżka względna>` w katalogu danych aplikacji, z prawami `rw-------`. Pliki o tej samej treści pomijamy. Plików, których nie ma na koncie domyślnym, nie usuwamy. Najpierw robimy wszystkie kopie zapasowe, dopiero potem cokolwiek nadpisujemy. Gdy kopia zapasowa się nie uda, nic nie zostaje nadpisane. Gdy na koncie pod ścieżką nadpisywanego pliku jest katalog, operacja kończy się błędem `DiskFailed(BACKUP)` przed jakimkolwiek zapisem (nie zastępujemy katalogu plikiem). Każdy zapis idzie przez plik tymczasowy w tym samym katalogu i `ATOMIC_MOVE`. Przywracanie z kopii zapasowej to osobne zadanie (NFR-5: ścieżkę kopii pokazuje okno „Konta”, AM-1005).

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| A. Tablica kont w kolejności automatu, `default` bez ścieżki i tylko przy odstępstwie (wybrana) | Kolejność w jednym miejscu; plik przenośny między katalogami domowymi; pusty plik dla jednego konta | Wpis `default` ma inny kształt niż pozostałe |
| B. Osobne `order: [id…]` i mapa `accounts` po `id` | Proste scalanie | Dwa źródła prawdy (wpis bez pozycji, pozycja bez wpisu) |
| C. Zapis wszystkich pól wszystkich kont | Prosty serializer | Łamie ADR 0024; zmiana domyślnych wymaga migracji |
| D. Kopia dla istniejącego konta zastępuje cały katalog (`skills/` itd.) | Wynik identyczny z kontem domyślnym | Usuwa własne skille i komendy konta (są w kopii zapasowej, ale to zaskoczenie) |

## Konsekwencje

- `docs/11` §8 dostaje wiersze `accounts.json` i `accounts-backup/`.
- Starsza wersja aplikacji pomija klucze dopisane później, a przy zapisie je gubi. Przed wydaniem 1.0 to akceptowalne, bo cały M10 wchodzi do jednego wydania.
- **Ryzyko wspólnego `projects/`:** zapisy dwóch supervisorów do tego samego katalogu i odczyt pamięci automatycznej na obu kontach nie są potwierdzone (AM-1001, punkt 7). Przy jednym koncie Claude Code też uruchamia kilka procesów, które piszą do `projects/<cwd>/`, każda sesja do własnego pliku. Wspólne dowiązanie nie dodaje więc nowego wzorca zapisu, poza wznowieniem tej samej rozmowy na innym koncie (AM-1007). Pomiar trzeba dokończyć przed AM-1007. Jeśli wypadnie źle, wyłączamy `shareProjects` i AM-1007 kopiuje transkrypt.
- `claude rm` na koncie z dowiązaniem zostawia w `~/.claude/projects` mały plik `cost-state` (AM-1001). Quayside go nie sprząta.
- Konto wskazane jako istniejący katalog nie ma wspólnego `projects/`, więc AM-1007 potrzebuje dla niego kopii transkryptu.
