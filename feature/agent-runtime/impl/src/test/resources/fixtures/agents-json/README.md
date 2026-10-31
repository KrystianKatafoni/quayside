# Fixtures `claude agents --json`

Wejścia dla `AgentsJsonParser` (AM-202) i, od AM-203, dla `AgentRuntimeContract` na adapterze CLI.

- `recorded/` — wyłącznie prawdziwe odpowiedzi Claude Code po anonimizacji. Nie edytuj ich ręcznie poza regułami anonimizacji.
- `synthetic/` — przypadki napisane ręcznie; każdy ma w kolumnie „Uwagi” źródło, z którego pochodzą pola.

Test `AgentsJsonFixturesTest` sprawdza, że każdy plik z tego katalogu jest wymieniony w tabeli poniżej i że nagrania są zanonimizowane.

| Plik | Źródło | Polecenie | Wersja `claude` | Data | System | Uwagi |
| --- | --- | --- | --- | --- | --- | --- |
| `recorded/2.1.302-interactive-only.json` | recorded | `claude agents --json` | 2.1.302 | 2026-10-18 | Linux (kontener sesji Claude Code w chmurze) | Tylko bieżąca sesja interaktywna: brak `id`, `state`, `waitingFor`; `status: "busy"`, `startedAt` w milisekundach. |
| `recorded/2.1.302-all-interactive-only.json` | recorded | `claude agents --json --all` | 2.1.302 | 2026-10-18 | Linux (kontener sesji Claude Code w chmurze) | Identyczne z wywołaniem bez `--all`: brak sesji w tle. |
| `recorded/2.1.302-all-background-working.json` | recorded | `claude agents --json --all` | 2.1.302 | 2026-10-18 | macOS 15.7.3 (Darwin 24.6.0) | Sesja 2 tuż po `claude --bg --permission-mode manual`: `state: "working"`, `status: "busy"`, jest `pid`. Obok sesja w tle z innego projektu (`done`, bez `pid` i `status`) i dwie interaktywne. Kolejność pól różna dla `working` i `done`. |
| `recorded/2.1.302-all-background-blocked-permission.json` | recorded | `claude agents --json --all` | 2.1.302 | 2026-10-18 | macOS 15.7.3 (Darwin 24.6.0) | Sesja 2 czeka na zgodę na `touch`: `state: "blocked"`, `waitingFor: "permission prompt"`, `status: "waiting"`. |
| `recorded/2.1.302-all-background-done.json` | recorded | `claude agents --json --all` | 2.1.302 | 2026-10-18 | macOS 15.7.3 (Darwin 24.6.0) | Sesja 1 skończyła turę: `state: "done"`, `status: "idle"`, proces nadal żyje (jest `pid`). |
| `recorded/2.1.302-all-background-done-after-stop.json` | recorded | `claude agents --json --all` | 2.1.302 | 2026-10-18 | macOS 15.7.3 (Darwin 24.6.0) | Po `claude stop` sesji `done`: stan zostaje `done` (nie `stopped`), znika `pid` i `status`, a `startedAt` zmienia się (o 725 ms wcześniej niż przed `stop`). |
| `recorded/2.1.302-all-background-respawned.json` | recorded | `claude agents --json --all` | 2.1.302 | 2026-10-18 | macOS 15.7.3 (Darwin 24.6.0) | Po `claude respawn` zatrzymanej sesji 1: znów `pid` i `status: "idle"`, `state: "done"`, nowe `startedAt` (czas respawn). Ten sam `id` i `sessionId`. |
| `recorded/2.1.302-all-background-stopped.json` | recorded | `claude agents --json --all` | 2.1.302 | 2026-10-18 | macOS 15.7.3 (Darwin 24.6.0) | Po `claude stop` sesji `blocked`: `state: "stopped"`, bez `pid`, `status` i `waitingFor`; `startedAt` też się zmienia. |
| `recorded/2.1.302-background-stopped-hidden.json` | recorded | `claude agents --json` | 2.1.302 | 2026-10-18 | macOS 15.7.3 (Darwin 24.6.0) | Ten sam moment co `all-background-stopped`, bez `--all`: znikają sesje `stopped` i `done` bez procesu; zostają tylko interaktywne. |
| `recorded/2.1.302-all-background-removed.json` | recorded | `claude agents --json --all` | 2.1.302 | 2026-10-18 | macOS 15.7.3 (Darwin 24.6.0) | Po `claude rm` obu nagrywanych sesji: nie ma ich na liście nawet z `--all`. |
| `synthetic/background-all-phases.json` | synthetic | — | — | 2026-10-18 | — | Po jednej sesji na fazę, `blocked` z `permission prompt` i `input needed`. Pola `id`, `state`, `waitingFor` z docs/compat.md i docs/05 „Stany sesji”; `kind: "background"` zgadnięte (pytanie 3 planu AM-202); `pid`, `startedAt`, `sessionId`, `name` w kształcie z nagrania 2.1.302. |
| `synthetic/mixed-interactive-and-background.json` | synthetic | — | — | 2026-10-18 | — | Sesje interaktywne (jak w nagraniu 2.1.302, jedna z `id`) przeplecione sesjami w tle (jak `background-all-phases.json`). |
| `synthetic/unknown-fields-and-values.json` | synthetic | — | — | 2026-10-18 | — | Nowe pola (`model`, `worktree`, `tags`, `costUsd`, `futureFlag`, `nested`), `state: "hibernating"`, `waitingFor: "oauth"` — tolerancja NFR-8, docs/compat.md. |
| `synthetic/partial-entries.json` | synthetic | — | — | 2026-10-18 | — | Brak `id`, względny `cwd`, element nie-obiekt, powtórzone `id`, `startedAt` jako ISO-8601, sekundy i śmieć. Reguły z planu AM-202, tabela „Pola → RuntimeSession”. |
| `synthetic/empty.json` | synthetic | — | — | 2026-10-18 | — | `[]`: supervisor bez sesji. |
| `synthetic/not-an-array.json` | synthetic | — | — | 2026-10-18 | — | Poprawny JSON, ale obiekt zamiast tablicy. |
| `synthetic/malformed.txt` | synthetic | — | — | 2026-10-18 | — | Obcięty dokument (niepoprawny JSON). |

## Anonimizacja nagrań

Przed dodaniem pliku do `recorded/` podmień:

| Pole | Wartość po anonimizacji |
| --- | --- |
| `cwd` | `/Users/test/projects/agent-manager` |
| `sessionId` | `00000000-0000-4000-8000-00000000000N` (N = kolejny numer w pliku) |
| `name` | `agent-manager-N` |
| `pid` | `4242` |
| `id` | `0000000N` (to samo N co w `sessionId`; w oryginale `id` = pierwsze 8 znaków `sessionId`) |
| `cwd` innego projektu | `/Users/test/projects/other` |
| `startedAt` | bez zmian (nie jest wrażliwe) |

Test `recorded fixtures are anonymized` odrzuca nagranie, w którym jest `/home/`, `/root`, `/Users/` inne niż `/Users/test/` albo UUID bez prefiksu `00000000-0000-4000-8000-`.

## Jak nagrać nowe wyjście

```bash
claude --version
claude agents --json        > recorded/<wersja>-<opis>.json
claude agents --json --all  > recorded/<wersja>-all-<opis>.json
```

Zanonimizuj według tabeli wyżej, dopisz wiersz do tabeli plików i dodaj przypadek w `AgentsJsonFixturesTest`.
