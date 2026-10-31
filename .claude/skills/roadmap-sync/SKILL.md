---
name: roadmap-sync
description: Synchronizuje docs/ROADMAP.md z GitHubem — etykiety, milestone'y M0–M9, issue dla każdego zadania, numery issue w roadmapie i statusy [x] dla zamkniętych issue. Użyj po scaleniu PR, przed planowaniem i przy pierwszej konfiguracji repo.
argument-hint: "[--dry-run] [--only AM-XXX]"
allowed-tools: Bash(python3 scripts/roadmap_sync.py*), Bash(git *), Read
---

# /roadmap-sync

1. Najpierw podgląd: `python3 scripts/roadmap_sync.py --dry-run $ARGUMENTS`. Pokaż człowiekowi, co zostanie utworzone lub zmienione.
2. Jeśli podgląd wygląda dobrze (lub człowiek wcześniej o to poprosił): `python3 scripts/roadmap_sync.py $ARGUMENTS`.
3. Skrypt zmienia tylko `docs/ROADMAP.md` (pole `issue:` i statusy). Jeśli plik się zmienił:
   - na gałęzi zadania: dołącz zmianę do bieżącego PR,
   - na `main`: gałąź `chore/roadmap-sync-<data>` i `/ship`.
4. Zwróć: liczbę utworzonych issue, milestone'ów, etykiet i zmienionych statusów.
