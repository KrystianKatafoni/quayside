# Fixtures poleceń `claude` poza `agents --json`

Prawdziwe wyjścia `claude --bg`, `logs`, `stop`, `respawn`, `rm` i `attach` dla `ClaudeCliAdapter` (AM-203) i fałszywego `claude` (`fake-claude/fake-claude.sh`). Listy sesji z tych samych nagrań są w `../agents-json/recorded/`.

Każde nagranie to trzy pliki o wspólnym prefiksie: `.stdout`, `.stderr` i `.exit` (kod wyjścia w jednej linii). Pusty plik = brak wyjścia na tym strumieniu.

Nagrania `2.1.302-*` powstały 2026-10-18 na macOS 15.7.3 (Darwin 24.6.0), `claude` 2.1.302, w katalogu repo (zaufany workspace). Sesja 1: `claude --bg --name <n> -- "<prompt z poleceniem date>"` w domyślnym trybie użytkownika (`auto`), przeszła working → done bez blokady. Sesja 2: `--permission-mode manual` i prompt z `touch`, zatrzymana w `blocked` (zgody nie udzielono, plik nie powstał).

| Prefiks w `recorded/` | Polecenie | Kod | Uwagi |
| --- | --- | --- | --- |
| `2.1.302-bg` | `claude --bg --name <n> -- <prompt>` | 0 | stdout: `backgrounded · <id> · <name>` i cztery linie podpowiedzi; stderr: `Starting background service…` (tylko gdy usługa startuje). |
| `2.1.302-bg-permission-mode-manual` | `claude --bg --name <n> --permission-mode manual -- <prompt>` | 0 | Jak `bg`. |
| `2.1.302-bg-untrusted-workspace` | `claude --bg …` w katalogu bez akceptacji zaufania | 1 | stdout pusty; stderr: `Workspace not trusted. Run \`claude\` in <cwd> once and accept the trust prompt, then retry.` Adapter zamienia to na `RuntimeError.WorkspaceNotTrusted` (AM-309). |
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
| `2.1.322-bg-resume-original-dir` | `claude --bg --model haiku --resume <uuid> -- <prompt>` w katalogu rozmowy | 0 | R1 (AM-311). Ten sam `sessionId`, `id` = pierwsze 8 znaków UUID, stdout bez nazwy; transkrypt dopisany do istniejącego pliku, nowy plik nie powstaje. |
| `2.1.322-bg-resume-subfolder` | to samo w podfolderze katalogu rozmowy | 0 | R2. Jak R1; `cwd` sesji w supervisorze to podfolder, a nowe wpisy transkryptu mają `cwd` podfolderu (plik zostaje w katalogu projektu). |
| `2.1.322-bg-resume-other-dir` | to samo w innym, zaufanym katalogu | 0 | R3. Wbrew hipotezie rozmowa jest znaleziona (po UUID, niezależnie od katalogu) i wznowiona z historią; `cwd` sesji to katalog uruchomienia. |
| `2.1.322-bg-resume-open-elsewhere` | to samo, gdy rozmowa jest otwarta w interaktywnym `claude --resume` | 0 | R4. Nie ma dwóch piszących: powstaje kopia z nowym UUID (nowy plik); stderr: `note: session … is open in another Claude Code process, so this started a copy as …. The original conversation is unchanged.` |
| `2.1.322-bg-resume-job-with-flags` | to samo z flagami (`--model`, prompt), gdy zadanie tej rozmowy już jest w supervisorze (zatrzymane) | 0 | R5. Powstaje kopia z nowym UUID; stderr: `note: background session … keeps its own saved options, so the flags you passed started a copy as …. Without flags, the same command continues … itself.` |
| `2.1.322-bg-resume-job-no-flags` | `claude --bg --resume <uuid>` bez flag i bez promptu, to samo zadanie zatrzymane | 0 | R5. Budzi to samo zadanie (to samo `id`, ten sam plik); stdout z nazwą i `(idle — send a prompt to start)`; stderr: `note: woke session … with its saved options (--model).` Efekt jak `respawn`. |
| `2.1.322-bg-resume-unknown-session` | `claude --bg --resume <nieistniejący uuid> -- <prompt>` | 0 | Kod 0 i `backgrounded`; dopiero potem zadanie przechodzi w `failed` (brak transkryptu, brak pliku). Błędu nie ma w wyjściu polecenia. |
| `2.1.322-bg-resume-job-prompt-only` | `claude --bg --resume <uuid> -- <prompt>` bez flag, to samo zadanie zatrzymane | 0 | R5. Budzi to samo zadanie (bez kopii), prompt trafia do niego; stderr: `note: woke session … with its saved options (--model).` |
| `2.1.322-bg-resume-no-prompt` | `claude --bg --resume <uuid>` bez promptu i flag, sesja spoza supervisora | 0 | stdout `backgrounded · <id> (idle — send a prompt to start)`; zadanie czeka na prompt, transkrypt bez zmian. |
| `2.1.322-account-*` | `claude auth status` (konto domyślne bez `CLAUDE_CONFIG_DIR`, konto dodatkowe zalogowane i wylogowane, `--text`), `claude --bg` na koncie dodatkowym, `--resume` z innego konta, `--resume` w katalogu niezaufanym na koncie docelowym | 0 / 1 | AM-1001, konto testowe w `~/.claude-quayside/spike`. E-mail, organizacja, UUID i ścieżki zredagowane; lista sesji konta: `../agents-json/recorded/2.1.322-account-b-one-bg-session.json`. Wnioski w `docs/compat.md`, „Wiele kont”. |

Nagrania `2.1.322-bg-resume-*` (AM-311, 2026-11-10, macOS 15.7, `claude` 2.1.322) pochodzą wyłącznie z sesji testowych w osobnych katalogach, `--model haiku`; zaufanie katalogów nadano interaktywnym `claude`. Dla `--resume` w tych samych warunkach `respawn` daje `respawned <id>` jak w 2.1.302 (nie nagrano ponownie).

Nie nagrano: fazy `failed`, `rm` z odmową (`kept`, worktree), `attach` istniejącej sesji (interaktywne).

## Anonimizacja

| Oryginał | Po anonimizacji |
| --- | --- |
| `id` sesji | `0000000N`, jak w `../agents-json/recorded/` |
| `--name` | `agent-manager-N` |
| katalog niezaufany | `/Users/test/projects/untrusted` |
| w `logs`: użytkownik i host, `~/…` | `test@test-host`, `~/projects/agent-manager` |
| w `logs`: model, plan, limity użycia, koszt, wynik `date` | `Model`, `Plan`, `0%`, `$0.00`, `12:00:00` |
