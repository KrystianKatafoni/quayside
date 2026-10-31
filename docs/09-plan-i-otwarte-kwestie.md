# 09 · Plan MVP, ryzyka i otwarte kwestie

## Plan w etapach

Każdy etap kończy się czymś, czego da się realnie używać.

| # | Etap | Zakres | Wynik |
| --- | --- | --- | --- |
| 0 | Spike techniczny | Compose Desktop + JediTerm w `SwingPanel`; wywołanie `claude agents --json` przez login shell; parsowanie do data class | Okno z terminalem i surową listą sesji |
| 1 | Szkielet i CLI | Repo wg [11](11-koncepcja-techniczna.md): `build-logic`, moduły `core:*`, puste `api`/`impl` feature, testy architektury Konsist, bramka CI (`./gradlew check`); `agent-runtime` z fałszywym `claude`, `SessionMonitor` z pollingiem | Lista sesji odświeżana co 2–3 s, zielony CI |
| 2 | Projekty | Sidebar, dodawanie z `JFileChooser`/`FileDialog` (wybór katalogu), walidacja, usuwanie z dialogiem, `projects.json` | FR-P1, P3, P4 |
| 3 | Mission Control | Lista sesji z glifami, panel szczegółów, start/stop/respawn/rm, paleta ⌘K, skróty j/k | FR-S1–S4, FR-K1–K2 |
| 4 | Terminal | Karty attach/zsh/logs, panele, szukanie, ustawienia terminala | FR-T1–T3, T7 |
| 5 | Hooki i podsumowania | `HookServer` (Ktor), instalator hooków z backupem, scalanie z pollingiem, `Summarizer` | FR-S7, NFR-4, NFR-5 |
| 6 | Powiadomienia | Własny popup, głos `say`, macierz reguł, ciche godziny, wyjątki per projekt | FR-N1–N7 |
| 7 | Wygląd | 9 schematów, 8 typografii (fonty w zasobach), ustawienia z podglądem, dzień/noc | FR-A1–A5 |
| 8 | Szlif i dystrybucja | Badge w Docku, ikona w pasku menu, odpowiedź i zgoda z popupu, podpis Developer ID, notaryzacja, `.dmg` | Wersja 1.0 |

## Ryzyka

| Ryzyko | Wpływ | Działanie |
| --- | --- | --- |
| Agent View jest w research preview; flagi i format `--json` mogą się zmienić | Wysoki | Cała integracja za `ClaudeCli`; testy kontraktowe na nagranych odpowiedziach; ostrzeżenie o nieznanej wersji |
| Agent View wyłączony (`disableAgentView`, MDM) | Wysoki | Wykrycie przy starcie; tryb ograniczony (tylko terminal) albo przyszły backend na Agent SDK |
| Odpowiedź i zgoda bez attach: brak oficjalnej komendy `reply` (`claude --bg --resume` na działającej sesji tworzy kopię) | Średni | [ADR 0013](adr/0013-strategia-odpowiedzi-na-pytania-agenta.md): MVP — `r` otwiera `claude attach` w karcie terminala (AM-506); odpowiedź bez terminala (FR-S5) dopiero po spike'u ukrytego attach (AM-507), przy złym wyniku Agent SDK |
| JediTerm w Compose (`SwingPanel`): fokus, skróty, HiDPI | Średni | Spike w etapie 0; plan B: osobne okno Swing dla terminala |
| Hooki HTTP z tokenem: zmienna środowiskowa musi dotrzeć do procesów supervisora | Średni | Sprawdzić dziedziczenie env przez supervisor; alternatywnie token w URL albo hook typu `command` z małym klientem |
| Edycja `~/.claude/settings.json` przez aplikację | Średni | Tylko za zgodą, backup, możliwość odinstalowania hooków jednym kliknięciem |
| Zużycie pamięci JVM | Niski | Profilowanie; limity heap; lazy terminali |

## Otwarte kwestie do decyzji

- [ ] Gdzie instalować hooki: globalnie w `~/.claude/settings.json` czy per projekt w `.claude/settings.local.json`?
- [x] Odpowiedź na pytanie agenta: wystarczy przez attach, czy potrzebny własny widok czatu (wtedy Agent SDK jako sidecar)? — rozstrzygnięte 2026-10-19 „Etapami” ([ADR 0013](adr/0013-strategia-odpowiedzi-na-pytania-agenta.md)): AM-411 podgląd (`space`), AM-506 `r` = attach w karcie terminala, AM-507 spike ukrytego attach dla FR-S5, fallback Agent SDK
- [ ] Streszczenia modelem Haiku: domyślnie włączone czy opcjonalne (koszt i limity)?
- [ ] Popup systemowy macOS: potrzebny w 1.0, czy wystarczy własny?
- [x] Nazwa produktu: Quayside (wybrana 2026-10-31, wcześniej robocza nazwa Agent Manager).
- [ ] Ikona produktu.
- [ ] Czy aplikacja ma też obsługiwać sesje interaktywne otwarte w zwykłym terminalu (po `/bg` pojawiają się w Agent View)?

## Kryteria akceptacji MVP (etapy 0–6)

1. Dodaję katalog projektu, widzę jego sesje w ≤ 3 s.
2. Startuję agenta z promptem i widzę stan *pracuje*.
3. Gdy agent zadaje pytanie, w ≤ 2 s pojawia się popup i komunikat głosowy.
4. Odpowiadam z popupu lub panelu, agent kontynuuje.
5. Zamykam aplikację: agent pracuje dalej; po ponownym otwarciu stan jest kompletny.
6. Usuwam projekt z listy, pliki i działające sesje zostają nietknięte (jeśli tak wybiorę).
