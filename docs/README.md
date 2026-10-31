# Quayside — dokumentacja projektu

> Wersja 0.1 · 18.10.2026 · status: koncepcja zatwierdzona, etap M0 (fundament repo)
> Nazwa: **Quayside** (wcześniej Agent Manager; projekt w Claude: *AgentToolMac*)

Quayside to natywna aplikacja desktopowa na macOS 15 Sequoia do zarządzania lokalnymi agentami Claude Code. Zbiera w jednym oknie projekty, sesje agentów, ich stan i terminal, a gdy agent czegoś potrzebuje, daje znać popupem i głosem.

Aplikacja **nie uruchamia własnego orkiestratora**. Steruje oficjalnym supervisorem sesji w tle Claude Code (`claude --bg`, `claude agents --json`, `claude attach/stop/logs/respawn`) i odbiera zdarzenia przez hooki HTTP.

![Mission Control — wariant Plex Violet](screenshots/10-mc-plex-violet.png)

## Spis treści

| Plik | Zawartość |
| --- | --- |
| [00-research.md](00-research.md) | Research wyjściowy: supervisor Claude Code jako backend, hooki, porównanie stacków, istniejące narzędzia (zapis historyczny) |
| [01-wizja-i-zakres.md](01-wizja-i-zakres.md) | Problem, użytkownik, cele, czego nie robimy |
| [02-wymagania.md](02-wymagania.md) | Wymagania funkcjonalne (FR) i niefunkcjonalne (NFR) z priorytetami |
| [03-architektura.md](03-architektura.md) | Stack (Kotlin + Compose Desktop), integracja z Claude Code, moduły, model danych |
| [04-koncepcje-ux.md](04-koncepcje-ux.md) | 5 koncepcji UX i uzasadnienie wyboru |
| [05-mission-control.md](05-mission-control.md) | Specyfikacja głównego ekranu, stany sesji, skróty |
| [06-terminal.md](06-terminal.md) | Tryb terminala: karty, panele, bloki komend, ustawienia |
| [07-powiadomienia.md](07-powiadomienia.md) | Powiadomienia popup i głosowe, konfiguracja |
| [08-wyglad.md](08-wyglad.md) | 9 schematów kolorów, 8 typografii, tokeny, `theme.json` |
| [09-plan-i-otwarte-kwestie.md](09-plan-i-otwarte-kwestie.md) | Plan MVP, ryzyka, otwarte decyzje |
| [10-wymagania-inzynierskie.md](10-wymagania-inzynierskie.md) | Zasady budowy: moduły, hermetyczność, interfejsy, clean code, testy, Definition of Done |
| [11-koncepcja-techniczna.md](11-koncepcja-techniczna.md) | Koncepcja techniczna: mapa modułów, reguły zależności, kontrakty API, przepływ danych, izolacja zmian |
| [12-strategia-testow.md](12-strategia-testow.md) | Piramida testów: jednostkowe, kontraktowe, integracyjne, funkcjonalne, architektury; bramka CI |
| [13-plan-wdrozenia.md](13-plan-wdrozenia.md) | Plan wdrożenia: etapy M0–M9, cykl dostarczania, gałęzie, wydania, utrzymanie, ryzyka |
| [14-agent-claude-code.md](14-agent-claude-code.md) | Agent Claude Code budujący i utrzymujący aplikację: role, skille, hooki, uprawnienia, GitHub Actions |
| [15-stan-projektu.md](15-stan-projektu.md) | Notatka przekazania: stan prac, gdzie i jak pracujemy, ustalenia o środowisku, pułapki |
| [ROADMAP.md](ROADMAP.md) | Zadania AM-XXX z zależnościami i statusami, źródło prawdy dla agenta |
| [compat.md](compat.md) | Zgodność z wersjami Claude Code i historia przeglądów |
| [adr/](adr/) | Decyzje architektoniczne (ADR) |
| [plans/](plans/) | Plany zadań tworzone przez agenta `architect` |
| [screenshots/](screenshots/) | 17 zrzutów mockupów (PNG, 1440 px × 1,5), diagram architektury i diagram modułów |
| [Agent-Manager-podsumowanie.pdf](Agent-Manager-podsumowanie.pdf) | 13-stronicowe podsumowanie całej koncepcji, z koncepcją techniczną |

## Decyzje w skrócie

| Obszar | Decyzja |
| --- | --- |
| Platforma | macOS 15 Sequoia+, Apple Silicon |
| Język i UI | Kotlin + Compose Multiplatform (Desktop, JVM) |
| Terminal | JediTerm (terminal z IDE JetBrains) + pty4j |
| Backend agentów | Supervisor Claude Code (Agent View, v2.1.139+) wywoływany przez CLI |
| Zdarzenia | Hooki HTTP Claude Code → lokalny serwer w aplikacji (`127.0.0.1`) |
| Główny ekran | Mission Control w stylu Keyboard-first (paleta ⌘K, skróty j/k, pasek trybu) |
| Wygląd | 9 schematów kolorów × 8 typografii, wybierane niezależnie |
| Architektura kodu | Modularny monolit: feature jako `api` / `impl` / `ui`, porty i adaptery, jeden composition root, reguły zależności wymuszane przez Gradle i Konsist |
| Testy | Jednostkowe, kontraktowe, integracyjne, funkcjonalne (Compose UI) i architektury; bramka `./gradlew check` |
| Powiadomienia | Własny popup (konfigurowalny) + głos macOS (`say`, np. Zosia) |

## Źródła materiałów

- Interaktywna kanwa z mockupami (claude.ai, artefakt „Agent Manager — 5 koncepcji UX”), strony *Koncepcje* i *Mission Control*.
- Dokument researchu „Claude Code Agent Manager na macOS — research” (claude.ai).
- Zrzuty w `screenshots/` są renderami tych mockupów z 18.10.2026.
