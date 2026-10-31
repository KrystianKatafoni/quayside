# 00. Research: Claude Code Agent Manager na macOS

> Dokument źródłowy z 18.10.2026 (Krystian), przeniesiony z claude.ai do repo. **Zapis historyczny:** research rekomendował SwiftUI, a ostatecznie wybraliśmy Kotlin + Compose Desktop ([ADR 0001](adr/0001-kotlin-compose-desktop.md)). Aktualna architektura: [11-koncepcja-techniczna.md](11-koncepcja-techniczna.md). Stan na dziś: [15-stan-projektu.md](15-stan-projektu.md).

## TL;DR

Zbuduj natywną aplikację SwiftUI (macOS 15+), która jest cienką nakładką GUI na oficjalny supervisor sesji Claude Code, a nie własnym orkiestratorem procesów.

- **Backend za darmo:** od v2.1.139 Claude Code ma supervisor sesji w tle (`claude --bg`, `claude agents --json`, `claude attach/stop/logs/respawn`). Sesje żyją bez terminala, przeżywają zamknięcie aplikacji i sen Maca.
- **Status:** polling `claude agents --json --all` co 2–3 s daje stany `working / blocked / done / failed / stopped` oraz `waitingFor` (np. `permission prompt`). Hooki HTTP (`Notification`, `Stop`) dają zdarzenia natychmiast.
- **Terminal:** SwiftTerm `LocalProcessTerminalView` uruchamiający `claude attach <id>` lub zwykły login shell w katalogu projektu.
- **Podsumowanie:** `last_assistant_message` z hooka `Stop` + opcjonalnie krótki prompt do Haiku przez `claude -p`.
- **Czas do MVP:** realnie 2–4 weekendy dla jednej osoby znającej Swifta; dla Ciebie (Kotlin) alternatywą jest Compose Desktop + pty4j + JediTerm.

## Kluczowe odkrycie: supervisor sesji jako backend

Najtrudniejszą część — trzymanie wielu agentów przy życiu niezależnie od okna — Claude Code już robi. [Agent View](https://code.claude.com/docs/en/agent-view) (research preview od maja 2026, v2.1.139+) uruchamia każdą sesję jako osobny proces pod per-userowym supervisorem. Twoja aplikacja tylko wywołuje CLI i czyta JSON.

| Potrzeba w Twojej aplikacji | Komenda CLI |
| --- | --- |
| Start nowego agenta w projekcie | `cd <projekt> && claude --bg --name "<nazwa>" "<prompt>"` |
| Start z konkretnym subagentem | `claude --agent <nazwa> --bg "<prompt>"` |
| Wznowienie istniejącej rozmowy w tle | `claude --resume <session-uuid> --bg "<prompt>"` |
| Lista sesji ze stanem | `claude agents --json --all [--cwd <projekt>]` |
| Podgląd ostatniego outputu | `claude logs <id>` |
| Interaktywny terminal sesji | `claude attach <id>` |
| Stop | `claude stop <id>` |
| Restart / wznowienie | `claude respawn <id>` |
| Usunięcie z listy | `claude rm <id>` |
| Zadanie shellowe w tle (np. testy) | `claude --bg --exec 'pytest -x'` |
| Stan supervisora | `claude daemon status` |

Pola zwracane przez `--json`: `id`, `sessionId`, `name`, `cwd`, `kind`, `startedAt`, `state`, `pid`, `status` (`busy / waiting / idle`) i `waitingFor`. Dokumentacja wprost zaleca to wyjście zamiast parsowania `~/.claude/jobs/<id>/state.json`, które nie jest stabilnym API.

Korzyści: sesje przeżywają zamknięcie Twojej aplikacji, auto-update Claude Code i uśpienie Maca; worktree per sesja jest tworzony automatycznie; status PR jest śledzony. Ryzyko: to research preview — flagi i format mogą się zmieniać, więc warstwę CLI trzymaj za jednym interfejsem w kodzie.

## Skąd brać stan i podsumowanie

Łącz dwa źródła: polling `claude agents --json` jako prawdę o stanie i hooki HTTP jako natychmiastowe zdarzenia. Polling naprawia wszystko, co hooki przegapią (np. gdy aplikacja była zamknięta).

| Stan w UI | Sygnał z `--json` | Sygnał z hooka |
| --- | --- | --- |
| Pracuje | `state: working`, `status: busy` | `UserPromptSubmit`, `PreToolUse` |
| Czeka na zgodę | `state: blocked`, `waitingFor: permission prompt` | `Notification` z typem `permission_prompt` |
| Czeka na odpowiedź | `state: blocked`, `waitingFor: input needed` | `Notification` z typem `agent_needs_input` lub `idle_prompt` |
| Skończył turę | `state: done` | `Stop` (zawiera `last_assistant_message`) |
| Błąd | `state: failed` | `StopFailure` (np. `rate_limit`, `authentication_failed`) |
| Zatrzymany | `state: stopped` | `SessionEnd` |

**Hooki HTTP.** Claude Code potrafi wysłać JSON zdarzenia jako POST na URL (`"type": "http"`). Aplikacja wystawia mały serwer na `127.0.0.1` (np. port 47321), a w `~/.claude/settings.json` dopisujesz hooki dla `Notification`, `Stop`, `StopFailure`, `SessionStart`, `SessionEnd`. Każde zdarzenie niesie `session_id`, `cwd` i `transcript_path`, więc łatwo je przypiąć do projektu. Dodaj nagłówek z tokenem przez `allowedEnvVars`, żeby inne procesy nie podszywały się pod hooki.

**Podsumowanie sesji — trzy poziomy:**

1. Najtańszy: `last_assistant_message` z hooka `Stop`, przycięty do 1–2 zdań.
2. Średni: `claude logs <id>` dla sesji, która właśnie pracuje.
3. Najlepszy: po każdym `Stop` wywołaj `claude -p --model haiku --output-format json "Streść w jednym zdaniu, co agent zrobił i czego potrzebuje: …"` — tak robi sam Agent View, który generuje opisy wierszy modelem klasy Haiku po zakończeniu tury.

**Historia i odkrywanie projektów.** Transkrypty leżą w `~/.claude/projects/<zakodowana-ścieżka>/<session-uuid>.jsonl`; opcode i Kanban Code właśnie stąd budują listę projektów i sesji. Traktuj ten format jako nieoficjalny i czytaj go tylko do historii, nie do stanu na żywo.

## Porównanie stacków

Na Sequoię najlepszy jest SwiftUI + SwiftTerm; Compose Desktop to rozsądna opcja, jeśli chcesz zostać w Kotlinie.

| Stack | Terminal | Plusy | Minusy | Przykład w praktyce |
| --- | --- | --- | --- | --- |
| **SwiftUI + SwiftTerm** (rekomendacja) | `LocalProcessTerminalView` — gotowy PTY + emulator xterm | Natywny wygląd, mały binarny plik, menu bar, powiadomienia `UserNotifications`, niskie zużycie RAM | Nowy język dla Ciebie; SwiftTerm to widok AppKit, w SwiftUI trzeba go owinąć `NSViewRepresentable` | [Wrangle](https://github.com/J-Krush/wrangle) (macOS 15, SwiftTerm + Claude Code), [Kanban Code](https://github.com/langwatch/kanban-code/wiki) |
| **Compose Desktop (Kotlin)** | JediTerm (terminal z IDE JetBrains) + pty4j | Twój język i ekosystem, współdzielenie kodu z PlantPulse | Większy pakiet (JVM), mniej natywny wygląd, integracja JediTerm z Compose przez `SwingPanel` | — |
| **Tauri 2 (Rust + web)** | xterm.js + `portable-pty` | Mały pakiet, UI w React/Svelte | Dwa języki (Rust + TS), WebView zamiast natywnych kontrolek | [opcode](https://github.com/winfunc/opcode) |
| **Wails (Go + web)** | xterm.js + tmux | Prosty backend w Go | tmux jako zależność | [Agent Session Manager Desktop](https://github.com/izll/agent-session-manager-desktop) |
| **Electron** | xterm.js + node-pty | Najwięcej przykładów, łatwe Agent SDK w TS | Ciężki (setki MB RAM), najmniej natywny | — |

SwiftTerm sam o sobie mówi, że lepiej obsługuje Unicode niż Terminal.app i iTerm2, a w repo jest przykładowa aplikacja macOS z większością funkcji Terminal.app. Uwaga: w issue #330 opisano problem z wysokością ramki `LocalProcessTerminalView` w SwiftUI na Sonomie — przetestuj layout wcześnie.

## Architektura aplikacji

Aplikacja nie trzyma żadnych procesów agentów: steruje nimi przez CLI, a zdarzenia odbiera hookami.

Diagram architektury (wersja docelowa, Kotlin): [screenshots/30-architektura.png](screenshots/30-architektura.png).

Gdy aplikacja jest zamknięta, agenci dalej pracują w supervisorze; po ponownym otwarciu pierwszy `claude agents --json` odtwarza pełny stan. Moduły w kodzie: `ProjectStore`, `ClaudeCLI` (jedyne miejsce wywołujące `claude`), `SessionMonitor` (polling + scalanie zdarzeń), `HookServer`, `TerminalTabs`, `Summarizer`.

## Mapowanie wymagań na mechanizmy

Każde z Twoich wymagań da się zrealizować bez modyfikowania Claude Code.

| Wymaganie | Jak to zrobić | Uwagi |
| --- | --- | --- |
| Dodawanie lokalnych projektów | `NSOpenPanel` z wyborem katalogu; zapis ścieżki w SwiftData / JSON; opcjonalnie import z `~/.claude/projects/` | Zapisz security-scoped bookmark, jeśli kiedyś włączysz sandbox |
| Wykonywanie poleceń jak w terminalu | Zakładka z SwiftTerm uruchamiającym `/bin/zsh -l` w katalogu projektu | Login shell daje PATH z `.zprofile` (nvm, brew) |
| Interakcja z agentem | Ta sama zakładka z `claude attach <id>` | Pełne TUI Claude Code, zgody na narzędzia, `/model` itd. |
| Szybka odpowiedź bez terminala | Pole tekstowe → odpowiedź przez attach albo `claude --resume <uuid> --bg "<odpowiedź>"` | Brak oficjalnej komendy `reply`; w Agent View odpowiedź idzie przez peek panel |
| Start agenta | Formularz: projekt, nazwa, prompt, opcjonalnie subagent, model, tryb uprawnień → `claude --bg …` | Sesja sama przenosi się do worktree przed edycją plików |
| Stop | `claude stop <id>` | `claude rm <id>` usuwa wiersz i (bezpiecznie) worktree |
| Czy działa / czy czeka | `state`, `status`, `waitingFor` z `--json` + hooki | Badge na ikonie w Docku + powiadomienie macOS przy `blocked` |
| Krótkie podsumowanie | `last_assistant_message` z hooka `Stop` lub streszczenie Haiku | Cache per sesja, odświeżany tylko po `Stop` |

**Wariant zaawansowany: Agent SDK.** Jeśli kiedyś zechcesz pełnej kontroli (własne okno czatu zamiast TUI, własne zatwierdzanie narzędzi), użyj [Agent SDK](https://code.claude.com/docs/en/agent-sdk/sessions) w TypeScript lub Pythonie jako sidecar procesu. `query()` przyjmuje `resume`, `continue` i fork sesji; eksperymentalne V2 API (`createSession`) usunięto w wersji 0.3.142, więc nie opieraj się na starych przykładach. Alternatywa bez SDK: `claude -p --input-format stream-json --output-format stream-json --verbose` daje dwukierunkowy protokół JSONL po stdin/stdout. Dla MVP to zbędna komplikacja.

## Pułapki macOS Sequoia

Najczęstszy błąd to aplikacja GUI, która nie widzi `claude` w PATH.

- **PATH.** Aplikacje uruchomione z Findera nie dziedziczą PATH z `.zshrc`. Wywołuj CLI przez `/bin/zsh -l -c 'claude agents --json'` albo raz odczytaj `which claude` z login shella i zapamiętaj pełną ścieżkę.
- **Bez App Sandbox.** Aplikacja uruchamia procesy potomne i czyta `~/.claude`, więc wyłącz sandbox i dystrybuuj poza Mac App Store: podpis Developer ID + notaryzacja. Do użytku prywatnego wystarczy podpis lokalny.
- **Uprawnienia TCC.** Jeśli projekty leżą w `~/Documents`, `~/Desktop` lub `~/Downloads`, macOS zapyta o dostęp — osobno dla Twojej aplikacji i osobno dla procesu supervisora Claude Code. Najprościej trzymać repozytoria np. w `~/dev`.
- **Sieć lokalna.** Na macOS 15+ sesje w tle potrzebują zgody „Local Network”, żeby łączyć się z hostami w LAN — istotne dla PlantPulse i PLC.
- **Autoryzacja.** Supervisor używa zapisanych poświadczeń z `/login`. Jeśli dispatch zwraca `Could not resolve authentication method`, zrestartuj go: `claude daemon stop --any --keep-workers`, potem `claude agents`.
- **Wersja Claude Code.** Sprawdzaj `claude --version` przy starcie i pokaż ostrzeżenie poniżej v2.1.139 (brak Agent View) — a najlepiej poniżej wersji, na której testowałeś.
- **Wyłączony Agent View.** Ustawienie `disableAgentView` lub zmienna `CLAUDE_CODE_DISABLE_AGENT_VIEW` (np. w firmowym MDM) całkowicie odbiera backend — wtedy jedyną drogą jest Agent SDK.

## Istniejące narzędzia do inspiracji

Warto przejrzeć ich kod, zanim zaczniesz — część może wręcz pokryć Twoje potrzeby.

| Narzędzie | Technologia | Co podpatrzeć |
| --- | --- | --- |
| [Kanban Code](https://github.com/langwatch/kanban-code/wiki) | Natywny macOS, SwiftTerm, tmux | Odkrywanie sesji z `~/.claude/projects/`, terminal SwiftTerm per karta; wymaga macOS 26, więc nie ruszy na Sequoi |
| [Agent Session Manager Desktop](https://github.com/izll/agent-session-manager-desktop) | Wails + Svelte + xterm.js | Panel agentów `--bg`, attach w zakładce, podgląd logów, stop |
| [opcode](https://github.com/winfunc/opcode) (dawniej Claudia) | Tauri 2 | Przeglądarka projektów, historia sesji, agenci w tle |
| [Agent Sessions](https://github.com/jazzyalex/agent-sessions) | Natywny macOS | Wyszukiwanie w historii sesji, licznik zużycia limitu |
| [Wrangle](https://github.com/J-Krush/wrangle) | SwiftUI + SwiftTerm, macOS 15 | Konfiguracja `LocalProcessTerminalView` uruchamiającego `claude` |
| [Agent Manager](https://github.com/marinsokol5/agent-manager) | macOS menu bar + CLI | Izolowane konta przez `CLAUDE_CONFIG_DIR` |

Zanim zaczniesz budować, sprawdź samo `claude agents` w terminalu przez kilka dni — może się okazać, że brakuje Ci tylko widoku per projekt i powiadomień, a nie całej aplikacji.

## Plan MVP w etapach

Każdy etap kończy się czymś, czego możesz realnie używać.

1. **Szkielet i CLI.** Projekt Xcode (SwiftUI, macOS 15, bez sandboxa). Klasa `ClaudeCLI` uruchamiająca komendy przez login shell i dekodująca `claude agents --json` do struktur `Codable`. Test: lista sesji w konsoli.
2. **Projekty.** Sidebar z listą projektów (`NavigationSplitView`), dodawanie przez `NSOpenPanel`, zapis w SwiftData.
3. **Widok sesji.** Tabela sesji projektu (`--cwd`), polling co 2–3 s, kolorowe stany, przyciski Start / Stop / Respawn / Usuń.
4. **Terminal.** Zakładki z SwiftTerm: shell projektu oraz `claude attach <id>` dla wybranej sesji.
5. **Zdarzenia.** Lokalny serwer HTTP (np. Swifter lub Network.framework), automatyczne dopisanie hooków do `~/.claude/settings.json` (z backupem), powiadomienia macOS przy `blocked` i `done`, badge w Docku.
6. **Podsumowania.** Zapis `last_assistant_message`, opcjonalne streszczenie Haiku, widok szczegółów sesji z historią z transkryptu.
7. **Szlif.** Ikona w pasku menu z licznikiem czekających agentów, skróty klawiszowe, obsługa braku/za starej wersji Claude Code.

Otwarte pytania do decyzji:

- [ ] Swift czy Kotlin/Compose Desktop?
- [ ] Czy aplikacja ma modyfikować globalne `~/.claude/settings.json`, czy tylko `.claude/settings.local.json` w dodanych projektach?
- [ ] Czy potrzebujesz własnego okna czatu (wtedy Agent SDK), czy wystarczy TUI przez `claude attach`?

## Źródła

- [Claude Code — Manage multiple agents with agent view](https://code.claude.com/docs/en/agent-view)
- [Claude Code — Hooks reference](https://code.claude.com/docs/en/hooks)
- [Claude Code — Headless mode](https://docs.claude.com/en/docs/claude-code/headless)
- [Agent SDK — Sessions](https://code.claude.com/docs/de/agent-sdk/sessions.md)
- [Agent SDK — usunięte V2 session API](https://code.claude.com/docs/de/agent-sdk/typescript-v2-preview.md)
- [SwiftTerm](https://github.com/krzyzanowskim/SwiftTerm)
- [SwiftTerm issue #330 (wysokość ramki w SwiftUI)](https://www.github.com/migueldeicaza/SwiftTerm/issues/330)
- [Wrangle](https://github.com/J-Krush/wrangle)
- [Kanban Code](https://github.com/langwatch/kanban-code/wiki)
- [Agent Session Manager Desktop](https://github.com/izll/agent-session-manager-desktop)
- [opcode](https://github.com/winfunc/opcode)
- [Agent Sessions](https://github.com/jazzyalex/agent-sessions)
- [Agent Manager](https://github.com/marinsokol5/agent-manager)
