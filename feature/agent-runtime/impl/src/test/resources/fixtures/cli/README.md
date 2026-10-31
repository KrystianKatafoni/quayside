# Fixtures poleceń `claude` poza `agents --json`

Prawdziwe wyjścia `claude --bg`, `logs`, `stop`, `respawn`, `rm` i `attach` dla `ClaudeCliAdapter` (AM-203) i fałszywego `claude` (`fake-claude/fake-claude.sh`). Listy sesji z tych samych nagrań są w `../agents-json/recorded/`.

Każde nagranie to trzy pliki o wspólnym prefiksie: `.stdout`, `.stderr` i `.exit` (kod wyjścia w jednej linii). Pusty plik = brak wyjścia na tym strumieniu.

Nagrano 2026-10-18 na macOS 15.7.3 (Darwin 24.6.0), `claude` 2.1.302, w katalogu repo (zaufany workspace). Sesja 1: `claude --bg --name <n> -- "<prompt z poleceniem date>"` w domyślnym trybie użytkownika (`auto`), przeszła working → done bez blokady. Sesja 2: `--permission-mode manual` i prompt z `touch`, zatrzymana w `blocked` (zgody nie udzielono, plik nie powstał).

| Prefiks w `recorded/` | Polecenie | Kod | Uwagi |
| --- | --- | --- | --- |
| `2.1.302-bg` | `claude --bg --name <n> -- <prompt>` | 0 | stdout: `backgrounded · <id> · <name>` i cztery linie podpowiedzi; stderr: `Starting background service…` (tylko gdy usługa startuje). |
| `2.1.302-bg-permission-mode-manual` | `claude --bg --name <n> --permission-mode manual -- <prompt>` | 0 | Jak `bg`. |
| `2.1.302-bg-untrusted-workspace` | `claude --bg …` w katalogu bez akceptacji zaufania | 1 | stdout pusty; stderr: `Workspace not trusted. Run \`claude\` in <cwd> once and accept the trust prompt, then retry.` Adapter tego jeszcze nie rozpoznaje. |
| `2.1.302-logs-done` | `claude logs <id>` sesji `done` | 0 | Surowy zrzut TUI z sekwencjami ANSI (czyszczenie ekranu, pozycjonowanie kursora, kolory), nie zwykły tekst. Zawiera też pasek statusu i panel użytkownika. |
| `2.1.302-stop-done` | `claude stop <id>` sesji `done` | 0 | stdout `stopped <id>`; stan na liście zostaje `done`. |
| `2.1.302-stop-blocked` | `claude stop <id>` sesji `blocked` | 0 | stdout `stopped <id>`; stan na liście `stopped`. |
| `2.1.302-respawn-stopped` | `claude respawn <id>` po `stop` | 0 | stdout `respawned <id>`. |
| `2.1.302-rm-live` | `claude rm <id>` sesji z żywym procesem (po respawn) | 0 | stdout `removed <id>`, bez pytania o potwierdzenie. |
| `2.1.302-rm-stopped` | `claude rm <id>` sesji `stopped` | 0 | stdout `removed <id>`. |
| `2.1.302-logs-unknown-id` | `claude logs deadbeef` | 1 | stderr: `No job matching 'deadbeef'. Run 'claude agents' to list running sessions.` |
| `2.1.302-stop-unknown-id` | `claude stop deadbeef` | 1 | Jak `logs`. |
| `2.1.302-attach-unknown-id` | `claude attach deadbeef` | 1 | Jak `logs`. |
| `2.1.302-respawn-unknown-id` | `claude respawn deadbeef` | 1 | stderr: `No job matching 'deadbeef'` (bez drugiego zdania). |
| `2.1.302-rm-unknown-id` | `claude rm deadbeef` | 1 | Jak `respawn`. |

Nie nagrano: fazy `failed`, `rm` z odmową (`kept`, worktree), `attach` istniejącej sesji (interaktywne).

## Anonimizacja

| Oryginał | Po anonimizacji |
| --- | --- |
| `id` sesji | `0000000N`, jak w `../agents-json/recorded/` |
| `--name` | `agent-manager-N` |
| katalog niezaufany | `/Users/test/projects/untrusted` |
| w `logs`: użytkownik i host, `~/…` | `test@test-host`, `~/projects/agent-manager` |
| w `logs`: model, plan, limity użycia, koszt, wynik `date` | `Model`, `Plan`, `0%`, `$0.00`, `12:00:00` |
