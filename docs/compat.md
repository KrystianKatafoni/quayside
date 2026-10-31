# Kompatybilność z Claude Code

Agent Manager steruje supervisorem sesji Claude Code (Agent View, research preview). Ten plik aktualizuje skill `/compat-check` (co tydzień w GitHub Actions) albo człowiek po ręcznym teście.

| Pozycja | Wartość |
| --- | --- |
| Minimalna wersja (Agent View, `claude agents`) | 2.1.139 |
| Wersja, na której oparto dokumentację | 2.1.290 (dokumentacja Agent View, październik 2026) |
| Ostatnio przetestowana ręcznie | 2.1.302 (2026-10-18, Linux; tylko `claude agents --json [--all]` z sesją interaktywną, bez sesji w tle) |
| Ostatni automatyczny przegląd | — |

## Elementy, od których zależymy

| Element | Używany w | Na co uważać |
| --- | --- | --- |
| `claude agents --json [--all] [--cwd]` | `agent-runtime:impl` (parser; adapter nie używa `--cwd`, które jest dopiero od 2.1.141, i filtruje `under` sam) | pola `id`, `sessionId`, `name`, `cwd`, `state`, `status`, `waitingFor`; nowe wartości `state`. Zaobserwowane w 2.1.302 (sesja interaktywna): `pid`, `cwd`, `kind`, `startedAt` (epoch millis), `sessionId`, `name`, `status`; brak `id`, `state`, `waitingFor`. Pola sesji w tle wciąż niezweryfikowane nagraniem (fixtures: `feature/agent-runtime/impl/src/test/resources/fixtures/agents-json/`) |
| `claude --bg`, `--name`, `--agent`, `--model`, `--permission-mode`, `--resume` | `agent-runtime:impl` (`ClaudeCliAdapter`) | zmiany flag; adapter parsuje linię `backgrounded · <id> · <name>` ze stdout (potwierdzone nagraniem 2.1.302; baner `Starting background service…` idzie na stderr). W niezaufanym katalogu `--bg` kończy się kodem 1 i `Workspace not trusted…`. `--permission-mode` w 2.1.302 przyjmuje `acceptEdits`, `auto`, `bypassPermissions`, `manual`, `dontAsk`, `plan` — dla `DEFAULT` flaga jest pomijana. Prompt idzie po `--` |
| `claude attach / logs / stop / respawn / rm` | `agent-runtime:impl`, `terminal` | zmiany zachowania, nowe potwierdzenia. 2.1.302: `attach`/`logs` przyjmują `<id\|name>`, `stop`/`rm` `<id>`, `respawn` `<id>\|--all`; `rm` usuwa też worktree i może odmówić (`kept <id>` → `CommandFailed`). Nieznane id (nagranie 2.1.302): kod 1 i stderr `No job matching '<id>'` (dla `attach`, `logs`, `stop` z dopiskiem `. Run 'claude agents' to list running sessions.`) — adapter i tak sprawdza listę ponownie. `stop` sesji `done` zostawia stan `done`, `logs` zwraca surowy zrzut TUI z ANSI. Nagrania: `feature/agent-runtime/impl/src/test/resources/fixtures/cli/` |
| Hooki HTTP: `Notification`, `Stop`, `StopFailure`, `SessionStart`, `SessionEnd` | `agent-runtime:impl` (serwer), `sessions:impl` | nazwy pól (`session_id`, `last_assistant_message`), typy powiadomień |
| `claude --version` | `agent-runtime:impl` (`health()`, `ClaudeVersion`) | 2.1.302 wypisuje `2.1.302 (Claude Code)`, kod 0. Adapter czyta `x.y.z` z początku pierwszej niepustej linii (sufiks ignorowany) i porównuje z 2.1.139; wersja starsza albo nieczytelna kończy sprawdzanie |
| `claude daemon status` | `agent-runtime:impl` (`health()`, `DaemonStatus`; tylko informacyjnie) | 2.1.302: demon nie działa → kod 1, pierwsza linia `not running`; działa → kod 0 i linie `pid:     698`, `version: 2.1.302`, `uptime: …`, `origin: …`, sekcja `bg sessions:` z `control.sock: reachable`. Adapter uznaje demona za działającego tylko przy kodzie 0 i linii `pid: <cyfry>`. Flagi `--json` CLI nie zna (ignoruje z ostrzeżeniem), więc jej nie podajemy. Demon startuje na żądanie, więc „nie działa” nie jest problemem. Od której wersji istnieje podkomenda — niezweryfikowane |
| `disableAgentView`, `CLAUDE_CODE_DISABLE_AGENT_VIEW` | `agent-runtime:impl` (`health()`, `AgentViewProbe` na `claude agents --json`) | 2.1.302: kod 1, stderr `'claude agents --json' is disabled by CLAUDE_CODE_DISABLE_AGENT_VIEW.` albo `… is disabled by the 'disableAgentView' setting.` → `AgentViewDisabled`. Wykrywanie po treści komunikatu: inny tekst da `CommandFailed` zamiast `AgentViewDisabled` |

## Historia przeglądów

| Data | Wersja Claude Code | Wynik | Issue |
| --- | --- | --- | --- |
