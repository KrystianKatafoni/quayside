# 02 · Wymagania

Priorytety: **M** = must (MVP), **S** = should (1.0), **C** = could (później).

## Wymagania funkcjonalne

### Projekty

| ID | Wymaganie | Prio |
| --- | --- | --- |
| FR-P1 | Dodanie projektu przez wybór katalogu (`NSOpenPanel`), skrót ⌘⇧O, przycisk w sidebarze i komenda w palecie | M |
| FR-P2 | Przy dodawaniu sprawdzenie katalogu: repo git i branch, obecność `CLAUDE.md`, liczba istniejących sesji, obecność hooków w `.claude/settings*.json` | S |
| FR-P3 | Usunięcie projektu z narzędzia (menu ⋯, ⌘⌫, komenda w palecie). Pliki, transkrypty i worktree **zostają na dysku** | M |
| FR-P4 | Jeśli w usuwanym projekcie działają sesje: wybór „zatrzymaj sesje i usuń” albo „usuń z listy, sesje działają dalej” | M |
| FR-P5 | Import projektów z historii `~/.claude/projects/` | C |
| FR-P6 | Zmiana nazwy wyświetlanej projektu, licznik sesji czekających i pracujących w sidebarze | S |

### Sesje agentów

| ID | Wymaganie | Prio |
| --- | --- | --- |
| FR-S1 | Lista sesji projektu ze stanem, nazwą, podsumowaniem, ostatnim zdarzeniem i wiekiem | M |
| FR-S2 | Stany: *czeka na odpowiedź*, *prosi o zgodę*, *pracuje*, *gotowe*, *błąd*, *zatrzymana* (mapowanie w [05](05-mission-control.md)) | M |
| FR-S3 | Start nowego agenta: projekt, prompt, opcjonalnie nazwa, subagent, model, tryb uprawnień (`claude --bg`) | M |
| FR-S4 | Stop (`claude stop`), Respawn (`claude respawn`), Usuń z listy (`claude rm`) | M |
| FR-S5 | Odpowiedź na pytanie agenta z panelu szczegółów lub popupu, bez otwierania terminala | S |
| FR-S6 | Pozwól/Odmów dla prośby o zgodę z popupu | S |
| FR-S7 | Krótkie podsumowanie sesji: `last_assistant_message` z hooka `Stop`, opcjonalnie streszczenie modelem klasy Haiku | M |
| FR-S8 | Panel szczegółów: worktree, model, tryb, ID, lista ostatnich zdarzeń hooków, link do PR | S |
| FR-S9 | Filtry: wszystkie / aktywne / zakończone oraz widoki globalne „czeka na mnie”, „pracuje”, „błędy” | S |

### Terminal

| ID | Wymaganie | Prio |
| --- | --- | --- |
| FR-T1 | Wbudowany emulator terminala z PTY: shell projektu (`zsh -l`) i `claude attach <id>` | M |
| FR-T2 | Karty pogrupowane per projekt, podział na panele (⌘D / ⌘⇧D), tryb pełnoekranowy | S |
| FR-T3 | Szukanie w scrollbacku (⌘F, wielkość liter, regex) | S |
| FR-T4 | Bloki komend z kodem wyjścia i czasem; akcje: uruchom ponownie, kopiuj jako Markdown, **wyślij do agenta** | C |
| FR-T5 | Ścieżki `plik:linia` klikalne, otwierane w IntelliJ IDEA | S |
| FR-T6 | Broadcast komendy do zaznaczonych paneli | C |
| FR-T7 | Ustawienia: powłoka, katalog startowy, font i rozmiar, scrollback, ligatury, Option jako Meta, dzwonek → powiadomienie | S |

### Powiadomienia

| ID | Wymaganie | Prio |
| --- | --- | --- |
| FR-N1 | Macierz zdarzenia × kanały (popup, głos, dźwięk, badge w Docku) konfigurowana przez użytkownika | M |
| FR-N2 | Zdarzenia: czeka na odpowiedź, prośba o zgodę, tura zakończona, błąd sesji, PR gotowy, praca dłuższa niż N min | M |
| FR-N3 | Popup: własny albo systemowy macOS, pozycja (6 miejsc), czas wyświetlania, maks. liczba naraz, styl, akcje w popupie | M |
| FR-N4 | Głos: wybór głosu macOS, tempo, głośność, szablony komunikatów ze zmiennymi `{sesja}`, `{projekt}`, `{pytanie}`, `{narzędzie}`, `{podsumowanie}` | M |
| FR-N5 | Grupowanie komunikatów głosowych (najwyżej 1 na 20 s), wyciszenie przy aktywnym mikrofonie, mówienie tylko w tle | S |
| FR-N6 | „Nie przeszkadzać” w godzinach i respektowanie trybu Skupienia macOS | S |
| FR-N7 | Wyjątki per projekt (profil: domyślny / tylko błędy / wyciszony, inny głos) | S |
| FR-N8 | „Wycisz sesję na 15 min” z popupu | C |

### Wygląd i obsługa

| ID | Wymaganie | Prio |
| --- | --- | --- |
| FR-A1 | 9 wbudowanych schematów kolorów, w tym 2 jasne i 1 wysokiego kontrastu | M |
| FR-A2 | 8 typografii (para font UI + font terminala), wybieranych **niezależnie** od schematu | M |
| FR-A3 | Podgląd na żywo w ustawieniach | S |
| FR-A4 | Rozmiar interfejsu S/M/L, gęstość listy, osobny font i rozmiar terminala | S |
| FR-A5 | Automatyczne przełączanie schematu dzień/noc razem z trybem macOS | S |
| FR-A6 | Własny schemat, import i eksport `theme.json` | C |
| FR-K1 | Paleta komend ⌘K obejmująca wszystkie akcje (sesje, projekty, ustawienia) | M |
| FR-K2 | Nawigacja klawiaturą: j/k, ⏎ attach, r odpowiedz, x stop, space podgląd, g p przełącz projekt | M |

## Wymagania niefunkcjonalne

| ID | Wymaganie |
| --- | --- |
| NFR-1 | macOS 15 Sequoia+, Apple Silicon; aplikacja podpisana Developer ID i notaryzowana; **bez App Sandbox** |
| NFR-2 | Stan sesji odświeżany co ≤ 3 s (polling), zdarzenia hooków widoczne w UI w ≤ 1 s |
| NFR-3 | Zamknięcie aplikacji nie zatrzymuje agentów. Po starcie pełny stan odtwarza się z `claude agents --json` |
| NFR-4 | Bezpieczeństwo hooków: serwer nasłuchuje tylko na `127.0.0.1`, wymaga tokenu w nagłówku (`allowedEnvVars`) |
| NFR-5 | Edycja `~/.claude/settings.json` tylko za zgodą użytkownika, z kopią zapasową i możliwością cofnięcia |
| NFR-6 | Kontrast tekstu ≥ 4.5:1 w każdym schemacie; pełna obsługa klawiaturą; etykiety dostępności na przyciskach ikonowych |
| NFR-7 | Zużycie RAM w spoczynku < 400 MB (JVM), start aplikacji < 3 s |
| NFR-8 | Warstwa CLI za jednym interfejsem (`ClaudeCli`), bo Agent View jest w research preview i jego format może się zmieniać |
| NFR-9 | Wykrycie wersji Claude Code przy starcie; ostrzeżenie poniżej v2.1.139 lub gdy Agent View jest wyłączony |
| NFR-10 | Interfejs po polsku; teksty w zasobach, gotowe na de/en |
| NFR-11 | Kod spełnia wymagania inżynierskie z [10-wymagania-inzynierskie.md](10-wymagania-inzynierskie.md): hermetyczne moduły, jasne interfejsy, clean code, testy jednostkowe i funkcjonalne |
