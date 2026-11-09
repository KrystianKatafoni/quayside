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
| FR-P7 | Grupa projektu: projekt z podfolderami (np. `plant-puls/` z `soft/`, `marketing/`) pokazany w sidebarze jako grupa z podpozycjami, filtr sesji po podfolderze, liczniki per podfolder | S |
| FR-P8 | Przegląd grupy projektu: po wybraniu grupy widok z każdym podprojektem i liczbą agentów, którzy na mnie czekają (oraz pracujących i z błędem), klik w podprojekt otwiera jego sesje | S |

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
| FR-A1 | 29 wbudowanych motywów ([08](08-wyglad.md)): 9 podstawowych schematów kolorów i 20 motywów z kształtem; 12 jasnych i 17 ciemnych, w tym 2 wysokiego kontrastu (1 jasny, 1 ciemny) | M |
| FR-A2 | 22 typografie (para font UI + font terminala): 8 podstawowych i 14 par domyślnych motywów, wybierane **niezależnie** od motywu; motyw tylko podpowiada swoją domyślną typografię | M |
| FR-A3 | Podgląd na żywo w ustawieniach | S |
| FR-A4 | Rozmiar interfejsu S/M/L, gęstość listy, osobny font i rozmiar terminala | S |
| FR-A5 | Automatyczne przełączanie schematu dzień/noc razem z trybem macOS | S |
| FR-A6 | Własny schemat, import i eksport `theme.json` | C |
| FR-A7 | Tokeny kształtu w motywie: promień, grubość ramki, cień, półprzezroczyste tło panelu (przybliżenie bez rozmycia) | S |
| FR-K1 | Paleta komend ⌘K obejmująca wszystkie akcje (sesje, projekty, ustawienia) | M |
| FR-K2 | Skróty jako przyspieszenie: j/k, ⏎ attach, r odpowiedz, x stop, space podgląd, g p przełącz projekt ([ADR 0015](adr/0015-skroty-jako-dodatek.md)) | S |
| FR-K3 | Każda akcja na sesji i projekcie ma widoczną kontrolkę: przycisk w panelu szczegółów albo pozycję menu kontekstowego wiersza (prawy klik, przycisk ⋯); podgląd i attach także kliknięciem | M |

### Konta

Plan i decyzje: [ADR 0029](adr/0029-wiele-kont-claude.md).

| ID | Wymaganie | Prio |
| --- | --- | --- |
| FR-L1 | Lista kont Claude (konto domyślne `~/.claude` + dodatkowe katalogi w `~/.claude-quayside/<nazwa>/`), dodanie przez logowanie w karcie terminala, zmiana nazwy, wyłączenie, usunięcie z listy bez kasowania katalogu | S |
| FR-L2 | Stan konta: dostępne, limit (od kiedy i do kiedy wg czasu chłodzenia, domyślnie 5 h, konfigurowalny; ręczne odblokowanie), wylogowane, nieznane | S |
| FR-L3 | Wybór konta przy starcie agenta: „Auto” albo konkretne; konto widoczne w panelu szczegółów | S |
| FR-L4 | Po `rate_limit` konto dostaje stan limitu; Quayside proponuje przełączenie w oknie zgody (lista sesji z tego konta, domyślnie żadna zaznaczona); ustawienie „Proponuj przełączenie po limicie” wyłącza okno (zostaje powiadomienie); nowe sesje i streszczenia w trybie „Automatycznie” omijają konta z limitem | S |
| FR-L5 | Przeniesienie sesji na inne konto, zawsze po potwierdzeniu: sesje pracujące, czekające i przerwane limitem (nie zakończone); sesja pracująca domyślnie po zakończeniu tury; stara sesja zatrzymana i powiązana z nową | S |
| FR-L6 | Zużycie limitu i czas odnowienia per konto | C |
| FR-L7 | Widok „Konta”: logowanie i wylogowanie wielu kont, dodawanie, usuwanie (bez kasowania katalogu), kolejność, przełączniki „Aktywne” i „W automacie” per konto, odblokowanie limitu, główny przełącznik automatyki i czas chłodzenia | S |
| FR-L8 | Ikona bieżącego konta w prawym górnym rogu okna; klik otwiera listę kont pod ikoną z ręcznym przełączeniem bieżącego konta (dla nowych sesji), opcją „Automatycznie” i wejściem do widoku „Konta”; każde przełączenie potwierdzane oknem z listą wszystkich sesji, które można przenieść (domyślnie żadna) | S |
| FR-L9 | Wspólny profil wszystkich kont: historia rozmów i pamięć wspólne dla całego Quayside, rozdzielone per projekt i podprojekt; wspólne instrukcje, skille, subagenci i ustawienia; osobny tylko login i stan supervisora | S |
| FR-L10 | Ustawienie sesji „Przełączaj konto automatycznie” (domyślnie wyłączone): po limicie sesja przenosi się bez pytania na następne dostępne konto, z powiadomieniem | S |

## Wymagania niefunkcjonalne

| ID | Wymaganie |
| --- | --- |
| NFR-1 | macOS 15 Sequoia+, Apple Silicon; aplikacja podpisana Developer ID i notaryzowana; **bez App Sandbox** |
| NFR-2 | Stan sesji odświeżany co ≤ 3 s (polling), zdarzenia hooków widoczne w UI w ≤ 1 s |
| NFR-3 | Zamknięcie aplikacji nie zatrzymuje agentów. Po starcie pełny stan odtwarza się z `claude agents --json` |
| NFR-4 | Bezpieczeństwo hooków: serwer nasłuchuje tylko na `127.0.0.1`, wymaga tokenu w nagłówku (`allowedEnvVars`) |
| NFR-5 | Edycja `~/.claude/settings.json` tylko za zgodą użytkownika, z kopią zapasową i możliwością cofnięcia |
| NFR-6 | Kontrast tekstu ≥ 4.5:1 w każdym schemacie; obsługa klawiaturą przez fokus i Tab (dostępność); etykiety dostępności na przyciskach ikonowych |
| NFR-7 | Zużycie RAM w spoczynku < 400 MB (JVM), start aplikacji < 3 s |
| NFR-8 | Warstwa CLI za jednym interfejsem (`ClaudeCli`), bo Agent View jest w research preview i jego format może się zmieniać |
| NFR-9 | Wykrycie wersji Claude Code przy starcie; ostrzeżenie poniżej v2.1.139 lub gdy Agent View jest wyłączony |
| NFR-10 | Interfejs po polsku; teksty w zasobach, gotowe na de/en |
| NFR-11 | Kod spełnia wymagania inżynierskie z [10-wymagania-inzynierskie.md](10-wymagania-inzynierskie.md): hermetyczne moduły, jasne interfejsy, clean code, testy jednostkowe i funkcjonalne |
