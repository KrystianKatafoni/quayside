---
name: compat-check
description: Sprawdza, czy nowe wersje Claude Code zmieniły coś, od czego zależy Agent Manager (claude agents --json, --bg, attach/stop/respawn/rm, hooki, Agent View) i zakłada issue z etykietą compat, gdy tak. Uruchamiany co tydzień przez workflow maintenance.yml oraz na żądanie.
allowed-tools: Bash(claude --version), Bash(gh issue *), Bash(gh label *), Read, Grep, Glob, WebFetch
---

# /compat-check

- Wersja Claude Code w tym środowisku: !`claude --version 2>/dev/null || echo "nieznana"`
- Odniesienie: `docs/compat.md` (minimalna wersja, ostatnio przetestowana, historia przeglądów).

## Kroki

1. Pobierz changelog: `https://raw.githubusercontent.com/anthropics/claude-code/main/CHANGELOG.md`. Weź wpisy nowsze niż ostatni przegląd w `docs/compat.md` (lub z ostatnich 30 dni, gdy brak historii).
2. Wybierz wpisy dotyczące elementów z tabeli „Elementy, od których zależymy” w `docs/compat.md`: Agent View, `claude agents`, `--bg`, `attach`, `logs`, `stop`, `respawn`, `rm`, `daemon`, hooki (`Notification`, `Stop`, `StopFailure`, `SessionStart`, `SessionEnd`, pola wejścia), `disableAgentView`.
3. Dla każdej istotnej zmiany oceń wpływ: brak / wymaga nowych fixtures / zmiana parsera lub adaptera / zmiana `api` (rzadkie, wymaga ADR).
4. Jeśli są zmiany z wpływem: sprawdź `gh issue list --label compat --state open`, żeby nie dublować, i utwórz issue:
   - tytuł `compat: Claude Code <wersja> — <krótki opis>`,
   - treść: cytowane (krótko) wpisy changelogu z linkiem, wpływ na moduły, propozycja zadania do roadmapy, nowe fixtures do nagrania,
   - etykiety `compat`, `area:agent-runtime` (utwórz brakujące przez `gh label create`).
5. Zwróć podsumowanie: wersja, liczba przejrzanych wpisów, utworzone issues lub „bez zmian wpływających na Agent Manager”.

Lokalnie (nie w CI) możesz dodatkowo zaktualizować tabelę „Historia przeglądów” w `docs/compat.md` i wysłać to przez `/ship`.
