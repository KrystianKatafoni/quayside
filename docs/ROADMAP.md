# Roadmapa

Źródło prawdy o zakresie i kolejności prac. Czytają ją ludzie i agent (`/deliver`, `/roadmap-sync`).

**Format wpisu** (jedna linia, nie zmieniaj kolejności pól):

```text
- [ ] AM-XXX · Tytuł · moduły: <lista> · zależy: <ID|—> · wymagania: <FR/NFR/ENG> · issue: <#N|—>
```

- `[ ]` do zrobienia, `[~]` w toku (otwarty PR), `[x]` scalone do `main`.
- Agent bierze **pierwszy** wpis `[ ]`, którego wszystkie zależności są `[x]`.
- Zmiana zakresu = PR zmieniający ten plik, z uzasadnieniem w opisie.

## M0 · Fundament repozytorium

- [x] AM-001 · Import dokumentacji koncepcyjnej · moduły: docs · zależy: — · wymagania: — · issue: —
- [x] AM-002 · Agent Claude Code: CLAUDE.md, subagenci, skille, hooki, uprawnienia · moduły: .claude · zależy: AM-001 · wymagania: ENG-C10, ENG sekcja G · issue: —
- [x] AM-003 · Automatyzacja GitHub: CI, @claude, review PR, maintenance, release, szablony · moduły: .github, scripts · zależy: AM-001 · wymagania: ENG sekcja E · issue: —

## M1 · Szkielet i bramka jakości

- [x] AM-101 · Gradle wrapper, katalog wersji, convention plugins w build-logic · moduły: build-logic · zależy: AM-002 · wymagania: ENG-M1, ENG-M2 · issue: —
- [x] AM-102 · core:domain (ID, Outcome, Clock, Logger, ShellCommand) · moduły: core:domain · zależy: AM-101 · wymagania: ENG-M6, ENG-I3, ENG-I4 · issue: —
- [x] AM-103 · core:testing (TestClock, TestDispatchers, buildery) · moduły: core:testing · zależy: AM-102 · wymagania: ENG-T6 · issue: —
- [x] AM-104 · Testy architektury Konsist + reguła Gradle impl→impl · moduły: tests:architecture, build-logic · zależy: AM-101 · wymagania: ENG-M3–M8, ENG-T5 · issue: —
- [x] AM-105 · Spotless/ktlint, detekt, Kover, `./gradlew check` w CI na macOS, szybki task `quickCheck` dla hooka Stop · moduły: build-logic, .github · zależy: AM-104 · wymagania: ENG-C1, ENG sekcja E · issue: —
- [x] AM-106 · core:process: LoginShellProcessRunner (timeouty, ucinanie wyjścia) · moduły: core:process · zależy: AM-102 · wymagania: ENG-M9 · issue: —
- [x] AM-107 · core:storage: JsonStore (zapis atomowy, migracje, *.broken) · moduły: core:storage · zależy: AM-102 · wymagania: 11 §8 · issue: —
- [x] AM-108 · app: okno Compose, composition root, pusty Mission Control · moduły: app · zależy: AM-102 · wymagania: ENG-M8 · issue: —

## M2 · Integracja z Claude Code

- [x] AM-201 · agent-runtime:api + kontrakt AgentRuntime w testFixtures · moduły: agent-runtime:api · zależy: AM-103 · wymagania: ENG-I1–I8, ENG-T2 · issue: —
- [x] AM-202 · Parser `claude agents --json` + nagrane fixtures · moduły: agent-runtime:impl · zależy: AM-201 · wymagania: FR-S2, NFR-8 · issue: —
- [x] AM-203 · ClaudeCliAdapter + fałszywy `claude` do testów · moduły: agent-runtime:impl, agent-runtime:api (testFixtures), core:process · zależy: AM-202, AM-106 · wymagania: FR-S3, FR-S4 · issue: —
- [x] AM-204 · Health check: wersja ≥ 2.1.139, Agent View, supervisor · moduły: agent-runtime:impl · zależy: AM-203 · wymagania: NFR-9 · issue: —

## M3 · Projekty

- [x] AM-301 · projects:api + kontrakt · moduły: projects:api · zależy: AM-103 · wymagania: FR-P1, FR-P3 · issue: —
- [x] AM-302 · JsonProjectStore · moduły: projects:impl · zależy: AM-301, AM-107 · wymagania: FR-P1, FR-P3 · issue: —
- [x] AM-303 · DirectoryInspector (git, CLAUDE.md, sesje, hooki) · moduły: projects:impl · zależy: AM-302, AM-106 · wymagania: FR-P2 · issue: —
- [x] AM-304 · projects:ui: sidebar, dodaj z dysku, usuń z dialogiem · moduły: projects:ui, app · zależy: AM-302, AM-108 · wymagania: FR-P1, FR-P3, FR-P4 · issue: —
- [x] AM-305 · projects:ui: zmiana nazwy, liczniki sesji w sidebarze i usuwanie przez `stopAllIn` · moduły: projects:ui, app · zależy: AM-304, AM-404 · wymagania: FR-P6, FR-P4 · issue: —

## M4 · Sesje i Mission Control

- [x] AM-401 · sessions:api (SessionMonitor, SessionCommands) + kontrakty · moduły: sessions:api · zależy: AM-201, AM-301 · wymagania: ENG-I2 · issue: —
- [x] AM-402 · SessionStateReducer (czysty, tablicowe testy) · moduły: sessions:impl · zależy: AM-401 · wymagania: FR-S2 · issue: —
- [x] AM-403 · Pętla pollingu + snapshot · moduły: sessions:impl · zależy: AM-402, AM-203 · wymagania: FR-S1, NFR-2, NFR-3 · issue: —
- [x] AM-404 · Komendy: start, stop, respawn, remove, stopAllIn · moduły: sessions:impl · zależy: AM-403 · wymagania: FR-S3, FR-S4 · issue: —
- [x] AM-405 · mission-control:ui: lista sesji, panel szczegółów, pasek trybu · moduły: mission-control:ui, projects:ui, app · zależy: AM-404, AM-304 · wymagania: FR-S1, FR-S8, FR-S9 · issue: —
- [x] AM-406 · command-palette:api/impl: komendy, skróty, CommandRegistry, wyszukiwanie · moduły: command-palette:api, command-palette:impl · zależy: AM-405 · wymagania: FR-K1, FR-K2 (część) · issue: —
- [x] AM-407 · mission-control:ui: widoki globalne „czeka na mnie”, „pracuje”, „błędy” · moduły: mission-control:ui, projects:ui, app · zależy: AM-405 · wymagania: FR-S9 · issue: —
- [x] AM-408 · Paleta ⌘K i „Zmień nazwę projektu…” w menu okna · moduły: command-palette:ui, projects:ui, app · zależy: AM-406 · wymagania: FR-K1, FR-P6 · issue: —
- [x] AM-409 · Skróty j/k i g p · moduły: command-palette:ui, mission-control:ui, app · zależy: AM-408 · wymagania: FR-K2 (część), FR-S9 (komendy filtra) · issue: —
- [x] AM-410 · Akcje sesji: x stop, Zatrzymaj/Respawn/Usuń w panelu i palecie · moduły: mission-control:ui, app · zależy: AM-409 · wymagania: FR-K2 (część) · issue: —
- [x] AM-411 · sessions:api: recentOutput i podgląd space · moduły: sessions:api, sessions:impl, mission-control:ui, app · zależy: AM-410 · wymagania: FR-K2 (część) · issue: —

## M5 · Terminal

- [x] AM-501 · Spike: JediTerm w SwingPanel (fokus, skróty, HiDPI) · moduły: terminal:impl, app · zależy: AM-108 · wymagania: FR-T1 · issue: —
- [x] AM-502 · terminal:api/impl: shell i attach w PTY · moduły: terminal:api, terminal:impl, app (terminalSpike) · zależy: AM-501 · wymagania: FR-T1 · issue: —
- [x] AM-503 · terminal:ui: karty per projekt, panele, maksymalizacja · moduły: terminal:ui, app · zależy: AM-502 · wymagania: FR-T2 · issue: —
- [x] AM-504 · Szukanie w scrollbacku (⌘F, wielkość liter, regex) · moduły: terminal:* · zależy: AM-503 · wymagania: FR-T3 · issue: —
- [x] AM-505 · Skrót ⏎: attach wybranej sesji w terminalu (komenda palety i skrót) · moduły: mission-control:ui, app · zależy: AM-503, AM-409 · wymagania: FR-K2 (część) · issue: —
- [x] AM-506 · Skrót r: attach wybranej sesji w karcie terminala z fokusem na odpowiedzi (ADR 0013) · moduły: mission-control:ui, app · zależy: AM-503, AM-505, AM-411 · wymagania: FR-K2 (część) · issue: —
- [~] AM-507 · Spike: odpowiedź przez ukryty attach w PTY (pytanie tekstowe, opcje, zgoda), fallback Agent SDK (ADR 0013) · moduły: terminal:impl · zależy: AM-502 · wymagania: FR-S5 · issue: #1
- [ ] AM-508 · Ustawienia terminala: powłoka, katalog startowy, font i rozmiar, scrollback, ligatury, Option jako Meta (zapis i zastosowanie) · moduły: terminal:*, core:storage, app · zależy: AM-503 · wymagania: FR-T7 (bez dzwonka) · issue: #2
- [ ] AM-509 · Dzwonek terminala → powiadomienie · moduły: terminal:impl, notifications:impl, app · zależy: AM-508, AM-701 · wymagania: FR-T7 (dzwonek) · issue: #3
- [x] AM-510 · Karty attach: fokus istniejącej karty sesji i kropka stanu · moduły: terminal:ui, app · zależy: AM-505 · wymagania: FR-T2, FR-K2 (część) · issue: —

## M6 · Hooki i podsumowania

- [ ] AM-601 · KtorHookServer (127.0.0.1, token z Keychain) · moduły: agent-runtime:impl · zależy: AM-203 · wymagania: NFR-4 · issue: #4
- [ ] AM-602 · HookInstaller z backupem i odinstalowaniem · moduły: agent-runtime:impl · zależy: AM-601 · wymagania: NFR-5 · issue: #5
- [ ] AM-603 · Scalanie hooków z pollingiem w reducerze · moduły: sessions:impl · zależy: AM-601, AM-403 · wymagania: NFR-2 · issue: #6
- [ ] AM-604 · Summarizer (last_assistant_message, opcjonalnie Haiku) · moduły: summaries:* · zależy: AM-603 · wymagania: FR-S7 · issue: #7

## M7 · Powiadomienia

- [ ] AM-701 · NotificationPolicy `decide()` + ustawienia · moduły: notifications:api, notifications:impl · zależy: AM-603 · wymagania: FR-N1, FR-N2 · issue: #8
- [ ] AM-702 · Popupy (okno Compose, stos, akcje) · moduły: notifications:ui · zależy: AM-701 · wymagania: FR-N3, FR-S5, FR-S6 · issue: #10
- [ ] AM-703 · MacSayAdapter, szablony, grupowanie · moduły: notifications:impl · zależy: AM-701 · wymagania: FR-N4, FR-N5 · issue: #11
- [ ] AM-704 · Ekran ustawień powiadomień · moduły: notifications:ui · zależy: AM-702, AM-703 · wymagania: FR-N1–N4 · issue: #12
- [ ] AM-705 · Cisza nocna, tryb Skupienia, wyjątki per projekt · moduły: notifications:impl · zależy: AM-704 · wymagania: FR-N6, FR-N7 · issue: #13

## M8 · Wygląd

- [ ] AM-801 · core:design-tokens + katalog 9 schematów i 8 typografii (test kontrastu) · moduły: core:design-tokens, appearance:impl · zależy: AM-102 · wymagania: FR-A1, FR-A2, NFR-6 · issue: #14
- [ ] AM-802 · core:design-system: komponenty i AmTheme · moduły: core:design-system · zależy: AM-801 · wymagania: FR-A1 · issue: #15
- [ ] AM-803 · Ekran Wygląd z podglądem na żywo · moduły: appearance:ui · zależy: AM-802, AM-405 · wymagania: FR-A3, FR-A4 · issue: #17
- [ ] AM-804 · Dzień/noc z macOS, import/eksport theme.json · moduły: appearance:impl · zależy: AM-803 · wymagania: FR-A5, FR-A6 · issue: #18

## M9 · Wydanie 1.0

- [ ] AM-901 · Pakowanie .dmg (packageDmg), ikona, metadane · moduły: app, build-logic · zależy: AM-405 · wymagania: NFR-1 · issue: #19
- [ ] AM-902 · Podpis Developer ID i notaryzacja w CI · moduły: .github · zależy: AM-901 · wymagania: NFR-1 · issue: #20
- [ ] AM-903 · Badge w Docku i ikona w pasku menu · moduły: notifications:impl, app · zależy: AM-705 · wymagania: FR-N1 · issue: #21
- [ ] AM-904 · Komplet testów funkcjonalnych MVP (9 scenariuszy z 12 §4) · moduły: tests:functional · zależy: AM-705, AM-504 · wymagania: ENG-T4 · issue: #22
- [ ] AM-905 · Wydanie v1.0.0 · moduły: — · zależy: AM-902, AM-903, AM-904, AM-804 · wymagania: 09 kryteria MVP · issue: #23
