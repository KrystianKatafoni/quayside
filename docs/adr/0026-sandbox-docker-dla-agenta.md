# ADR 0026: Sandbox Docker dla agenta (szkic, spike)

- **Status:** proponowana (spike; nic nie jest zaimplementowane)
- **Data:** 2026-11-07
- **Dotyczy:** `core:process` (nowy runner), `terminal:impl` (start PTY), `agent-runtime:impl` (hooki, token), `mission-control:ui` (wybór przy starcie), przyszłe zadanie AM-8xx; pomysł nr 4 w [IDEAS.md](../IDEAS.md)

## Kontekst

Człowiek chce uruchamiać agenta Claude Code z pełnymi uprawnieniami (`--dangerously-skip-permissions`), bez pytań o zgodę, ale tak, żeby błąd lub prompt injection nie dotknął reszty Maca. Kontener Docker ogranicza szkody do zamontowanego katalogu projektu i tokenów, które sami do niego wpuścimy. Kontener musi mieć git (commit, push, pull).

Jak jest dziś (stan kodu):

- „Nowy agent” (`NewAgentDialog`, `AgentRuntime.start(StartSpec)`) to `claude --bg`: **supervisor Claude Code działa na hoście** i sam trzyma proces agenta. Wymaga promptu, zwraca krótkie ID sesji; potem `claude attach <id>`, `claude logs`, stop i respawn idą przez supervisora (ADR 0002).
- Zwykłej sesji interaktywnej `claude` Quayside nie tworzy osobnym poleceniem: użytkownik może ją wpisać w karcie `zsh` (FR-T1). Taka sesja nie jest „agentem w tle”, więc nie ma ID od supervisora.
- Hooki HTTP trafiają na `http://127.0.0.1:<port>/hooks` z nagłówkiem `X-AM-Token` (`AM_HOOK_TOKEN`); serwer (`KtorHookServer`) słucha tylko na `127.0.0.1`, a `OurHandlers`/`OurHooks` rozpoznają jako własne tylko hosty `127.0.0.1` i `localhost` (ADR 0004, 0010).
- PTY startuje `PtyLauncher` (pty4j) z `argv`, katalogiem i środowiskiem; `LoginShellProcessRunner` w `core:process` uruchamia programy do końca przez login shell.

## Decyzja (kierunek do potwierdzenia przez człowieka)

1. **Sandbox to osobny rodzaj sesji, nie zwykła opcja „Nowego agenta”.** Supervisor `--bg` jest na hoście, więc `claude --bg` w kontenerze albo nie widziałby kontenera (agent biegnie na hoście), albo wymagałby drugiego supervisora w kontenerze. Zamiast tego sandbox uruchamia **interaktywne `claude --dangerously-skip-permissions` jako PTY w `docker run -it --rm`**. W oknie „Nowy agent” pojawia się wybór „Gdzie: Host | Sandbox (Docker)”; dla Sandbox pole promptu jest opcjonalne (prompt idzie jako argument `claude`). Karta to zwykła karta PTY.
2. **Stan w Mission Control tylko z hooków.** Sesja w kontenerze nie ma ID supervisora, więc nie ma `attach`, `logs`, stopu ani respawnu przez `claude`. Zatrzymanie to `docker stop <nazwa>`; Quayside nadaje kontenerowi nazwę `quayside-<id>` i etykiety `quayside.session`, `quayside.project`.
3. **Izolacja domyślna:** tylko katalog projektu (rw), brak `--privileged`, brak montowania gniazda Dockera, `--cap-drop=ALL`, `--security-opt=no-new-privileges`, użytkownik niebędący rootem, `~/.ssh` i reszta `$HOME` nie są montowane. Sieć zostaje włączona (potrzebna API i git); ograniczanie wyjścia to osobna decyzja na później.
4. **Konfiguracja przez wolumen, nie przez `~/.claude` hosta.** Osobny nazwany wolumen `quayside-claude` na `/home/agent/.claude` (logowanie, historia, ustawienia sandboxa). Pierwsze logowanie robi człowiek w kontenerze (`/login`); alternatywa to `CLAUDE_CODE_OAUTH_TOKEN` z Keychaina przekazany przez `-e` (bez wartości w argv: `-e NAZWA` bierze ją ze środowiska procesu `docker`).
5. **Git przez HTTPS z fine-grained tokenem** ograniczonym do jednego repo: `GH_TOKEN` jako zmienna środowiska + `gh auth setup-git` w entrypoincie. Tożsamość: `GIT_AUTHOR_NAME/EMAIL` i `GIT_COMMITTER_*` z `git config` hosta (zamiast montowania `~/.gitconfig`, który może zawierać helpery i aliasy wykonujące kod na hoście). SSH (`SSH_AUTH_SOCK`) tylko jako świadomie włączana opcja, domyślnie wyłączona, bo agent z dostępem do agenta SSH ma dostęp do wszystkich kluczy.
6. **Hooki:** w obrazie działa `socat TCP-LISTEN:<port>,bind=127.0.0.1,fork TCP:host.docker.internal:<port>`, więc adres `http://127.0.0.1:<port>/hooks` w ustawieniach hooków zostaje bez zmian i `OurHandlers` nie trzeba uczyć nowego hosta. `AM_HOOK_TOKEN` jedzie jako `-e AM_HOOK_TOKEN` (wartość w środowisku procesu `docker`, nie w argv). Serwer hooków zostaje na `127.0.0.1` hosta; Docker Desktop (i OrbStack) kierują `host.docker.internal` na pętlę zwrotną hosta, co trzeba potwierdzić ręcznie (patrz niżej).
7. **Worktree git:** katalog roboczy worktree ma plik `.git` wskazujący na główne repo (`gitdir: /abs/path/.git/worktrees/x`). Kontener musi zamontować **główny `.git` pod tą samą ścieżką bezwzględną** (np. `-v /Users/u/p/.git:/Users/u/p/.git`), a także sam worktree pod jego oryginalną ścieżką (`-v $WT:$WT -w $WT`). Montowanie pod tymi samymi ścieżkami co na hoście jest prostsze niż przepisywanie `gitdir`. Zwykłe repo: jeden mount katalogu projektu.
8. **Implementacja (kiedyś):** `DockerCommandBuilder` (czysta funkcja: spec → `argv` + `env`, w pełni testowalna na fake'ach) w nowym module lub w `core:process`, wywołanie przez ten sam `PtyLauncher` (`argv = ["docker", "run", "-it", …]`), `docker info`/`docker image inspect` przez `ProcessRunner` do wykrycia, czy Docker działa. Port w `api` (np. `SandboxLauncher`) powstaje dopiero z zadaniem, po pytaniu do człowieka o zmiany `api` innych feature.

## Szkic do ręcznej weryfikacji (nie jest w repo jako kod)

Obraz `quayside-sandbox` (węzeł 22, `git`, `gh`, `socat`, `claude` z npm, użytkownik `agent`, entrypoint):

```text
entrypoint.sh:
  socat TCP-LISTEN:${AM_HOOK_PORT},bind=127.0.0.1,fork,reuseaddr TCP:host.docker.internal:${AM_HOOK_PORT} &
  [ -n "$GH_TOKEN" ] && gh auth setup-git
  exec claude --dangerously-skip-permissions "$@"
```

```text
docker run -it --rm --name quayside-<id> \
  --label quayside.session=<id> --cap-drop=ALL --security-opt no-new-privileges \
  --add-host host.docker.internal:host-gateway \
  -v <projekt>:<projekt> -w <projekt> \
  -v quayside-claude:/home/agent/.claude \
  -e AM_HOOK_TOKEN -e AM_HOOK_PORT=<port> -e GH_TOKEN \
  -e GIT_AUTHOR_NAME -e GIT_AUTHOR_EMAIL -e GIT_COMMITTER_NAME -e GIT_COMMITTER_EMAIL \
  quayside-sandbox [prompt]
```

Nowy `ShellCommand`/`PtyLauncher` dostaje te `-e NAZWA` bez wartości w `argv`; same wartości są w `env` procesu (`ShellCommand.toString` już je ukrywa).

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| A. Interaktywny `claude` w `docker run -it` jako osobny rodzaj sesji (wybrana) | Proste; działa z PTY, który już mamy; brak drugiego supervisora; stan z hooków | Brak `attach`, `logs`, stopu przez `claude`; sesja nie jest „agentem w tle” |
| B. `claude --bg` z supervisorem w kontenerze | Pełne `attach`/`logs`/stop jak dziś | Supervisor w kontenerze trzeba zainstalować, uruchomić i połączyć; `claude attach` z hosta go nie zobaczy; dużo niewiadomych |
| C. Opcja „Sandbox” w oknie „Nowy agent” dla `--bg` | Jedno miejsce startu | Pozorna: agent `--bg` biegnie na hoście i izolacja nie istnieje |
| D. Bez Dockera: `sandbox-exec`/uprawnienia Claude Code (sandbox wbudowany) | Brak zależności od Dockera, szybki start | Słabsza i mniej przewidywalna izolacja; nie rozwiązuje „bez pytań o zgodę” w pełni |
| E. Zdalna maszyna/VM | Najsilniejsza izolacja | Poza zakresem 1.0; koszt i konfiguracja |

## Ryzyka

- **Wymagany Docker na Macu** (Docker Desktop, OrbStack lub Colima); aplikacja musi wykryć brak lub wyłączony silnik i pokazać zrozumiały komunikat z linkiem do instalacji. Opcja jest ukryta lub nieaktywna bez Dockera.
- **Czas startu:** pierwszy `docker pull`/build to minuty, kolejne starty 1–3 s plus start `claude`; obraz trzeba aktualizować przy zmianie wersji `claude` (zgodność: `docs/compat.md`).
- **Brak narzędzi hosta:** w obrazie nie ma `brew`, Xcode, JDK, Gradle, Node projektu itd. Obraz bazowy musi być wybieralny per projekt (np. `.quayside/Dockerfile`), albo agent doinstaluje narzędzia sam; budowanie projektów macOS-owych w kontenerze Linux jest niemożliwe.
- **Wydajność plików:** bind mount na Macu (virtiofs/gRPC FUSE) jest wolny przy dużych repo i `node_modules`.
- **`--dangerously-skip-permissions` nie jest granicą bezpieczeństwa:** agent ma swobodę w mountach, w tokenie `GH_TOKEN` i w sieci (może wypchnąć kod lub wyciec sekrety z projektu). Dlatego token ograniczony do jednego repo, brak `.ssh`, brak sekretów hosta, jasny ekran zgody przy pierwszym użyciu (jak ADR 0020).
- **Tokeny w środowisku kontenera** są widoczne dla agenta; fine-grained token z wygasaniem i minimalnymi uprawnieniami (Contents, Pull requests) ogranicza szkodę.
- **Hooki przez `socat`:** zależą od `host.docker.internal` i od tego, że serwer na `127.0.0.1` jest osiągalny z silnika Dockera; przy innym silniku może trzeba nasłuchiwać też na interfejsie Dockera (zmiana ADR 0004, wtedy z tokenem jako jedyną ochroną).
- **Sprzątanie:** kontenery `--rm` znikają po wyjściu, ale zabicie aplikacji może zostawić działający kontener; przy starcie Quayside listuje `docker ps --filter label=quayside.session` i proponuje zatrzymanie.

## Do ręcznego sprawdzenia na Macu (nie da się w chmurze)

1. Czy `curl http://host.docker.internal:<port>/hooks` z kontenera dociera do serwera słuchającego tylko na `127.0.0.1` (Docker Desktop, OrbStack, Colima).
2. Czy `claude --dangerously-skip-permissions` w kontenerze jako użytkownik nie-root startuje bez ostrzeżeń (część wersji odmawia tej flagi jako root).
3. Czy logowanie do wolumenu `quayside-claude` przetrwa restart kontenera.
4. Czy PTY pty4j z `docker run -it` poprawnie przekazuje zmianę rozmiaru okna (SIGWINCH) i kolory.
5. Czy `git push` przez `GH_TOKEN` + `gh auth setup-git` działa, a w worktree git widzi główne `.git` pod tą samą ścieżką.
6. Czas zimnego i ciepłego startu oraz wydajność `git status` na dużym repo przez bind mount.

## Konsekwencje

- Ten ADR nic nie zmienia w kodzie. Po akceptacji: wpis w `ROADMAP.md` (po konceptach UI i wyborze obrazu), zmiana `NewAgentDialog` (wybór „Gdzie”), nowy port w `api` po pytaniu do człowieka, ADR o zgodzie użytkownika i o formacie `sandbox.json` (zapis tylko odstępstw, ADR 0024).
- Ścieżka `claude --bg` zostaje bez zmian; sandbox i agent w tle mogą współistnieć w jednym projekcie.
- Otwarte pytania do człowieka: czy brak `attach`/`stop` przez `claude` jest akceptowalny dla wersji 1; czy obraz ma być jeden wspólny, czy per projekt; czy ograniczać wyjście sieciowe od razu.
