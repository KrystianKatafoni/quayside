# ADR 0002: Supervisor Claude Code przez CLI jako backend agentów

- **Status:** przyjęta
- **Data:** 2026-10-18
- **Dotyczy:** agent-runtime, sessions

## Kontekst

Agenci muszą działać niezależnie od okna aplikacji. Claude Code od v2.1.139 ma supervisor sesji w tle (Agent View) sterowany przez `claude --bg`, `claude agents --json`, `attach`, `stop`, `respawn`, `rm`.

## Decyzja

Aplikacja nie hostuje procesów agentów. Steruje supervisorem przez CLI za portem `AgentRuntime`, a stan czyta z `claude agents --json`.

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| **CLI + supervisor** | Sesje przeżywają zamknięcie aplikacji; mało kodu | Agent View w research preview; brak komendy `reply` |
| Agent SDK jako sidecar | Pełna kontrola, własny czat | Własny cykl życia procesów, więcej kodu |
| Własne PTY z `claude` | Pełna kontrola terminala | Sesje giną z aplikacją |

## Konsekwencje

Wszystkie wywołania w jednym adapterze, testy kontraktowe na fixtures, cotygodniowy `/compat-check`. Przejście na Agent SDK = nowa implementacja `AgentRuntime` bez zmian w innych modułach.
