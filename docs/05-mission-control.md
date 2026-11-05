# 05 · Mission Control — specyfikacja głównego ekranu

![Mission Control](screenshots/mockups-2026-11-04/01-mission-control.png)

## Układ

| Strefa | Zawartość |
| --- | --- |
| Nagłówek (44 px) | Ścieżka `agents / <projekt>`, pole palety ⌘K, przełącznik głosu (z nazwą głosu), dzwonek z licznikiem, przycisk **Nowy agent ⌘N** |
| Sidebar projektów (260 dp) | Nagłówek „PROJEKTY” z przyciskiem „+” (dodaje projekt), lista projektów z ikoną folderu w tonie projektu i pigułkami liczników czekających i pracujących, menu ⋯, widoki globalne z ikoną i pigułką liczby, zwijana ściągawka skrótów |
| Środek | Nagłówek projektu (tytuł, pigułka ścieżki, liczba sesji, przycisk **Nowy agent** z ⌘N), cztery kafelki liczników (czeka, pracuje, gotowe, błąd; zatrzymane nie są liczone), filtr Wszystkie/Aktywne/Zakończone jako segmenty, lista sesji (prawy klik lub ⋯ w wierszu otwiera menu akcji), panel terminala. Pod listą nie ma paska podpowiedzi klawiszy: skrót jest przy akcji |
| Panel szczegółów (300 dp) | Nazwa, pigułka stanu („Czeka na odpowiedź · 4 min”), PODSUMOWANIE, pola w ramce (worktree, ID, start, ostatnia aktywność), na dole przyciski: główna Odpowiedz (r), zwykłe Attach (⏎), Respawn i Podgląd, groźne osobno Zatrzymaj (x) i Usuń z listy |
| Pasek statusu (28 px) | Projekt, liczniki czekających i pracujących, status głosu i popupu, wersja supervisora |
| Popupy | Stos w wybranym rogu ekranu (domyślnie prawy dół); szczegóły w [07](07-powiadomienia.md) |

## Wiersz sesji

`kafelek stanu 38 dp · nazwa (mono) nad podsumowaniem · pigułka ostatniego zdarzenia/narzędzia · wiek`. W gęstości **Kompaktowej** (ustawienie Wyglądu, docs/08) wiersz ma kafelek 28 dp, odstęp pionowy 4 dp i jedną linię: podsumowanie jest wtedy tylko w panelu szczegółów, a stan nadal widać w kafelku i pigułce (nie tylko kolorem). Zaznaczony wiersz ma tło `sel` oraz przyciski Odpowiedz (gdy sesja czeka) i Attach (przycisk ikonowy); „⋯” otwiera menu akcji.

## Stany sesji

| Glif | Stan w UI | Kolor | `state` / `waitingFor` z `--json` | Hook |
| --- | --- | --- | --- | --- |
| `?` | Czeka na odpowiedź | warn | `blocked` / `input needed` | `Notification`: `agent_needs_input`, `idle_prompt` |
| `!` | Prosi o zgodę | warn | `blocked` / `permission prompt` | `Notification`: `permission_prompt` |
| `~` | Pracuje | work | `working` | `UserPromptSubmit`, `PreToolUse` |
| `✓` | Gotowe | ok | `done` | `Stop` (z `last_assistant_message`) |
| `✕` | Błąd | err | `failed` | `StopFailure` (np. `rate_limit`) |
| `○` | Zatrzymana | dim | `stopped` | `SessionEnd` |

## Mysz

Każdą akcję da się wykonać bez klawiatury ([ADR 0015](adr/0015-skroty-jako-dodatek.md)):

| Gest | Akcja |
| --- | --- |
| Klik w wiersz | Zaznaczenie i panel szczegółów |
| Dwuklik w wiersz | Attach w terminalu |
| Prawy klik lub ⋯ w wierszu | Menu: Odpowiedz, Attach, Podgląd, Respawn, Zatrzymaj, Usuń |
| Przyciski w panelu szczegółów | Odpowiedz, Attach, Respawn, Zatrzymaj |
| Pole palety w nagłówku | Paleta komend (to samo co ⌘K) |

## Skróty klawiszowe

Skróty są przyspieszeniem dla wprawionych; każdy ma odpowiednik w menu albo palecie.

| Skrót | Akcja |
| --- | --- |
| ⌘K | Paleta komend |
| ⌘N | Nowy agent w bieżącym projekcie |
| ⌘⇧O / ⌘⌫ | Dodaj projekt / usuń projekt z narzędzia |
| g p | Przełącz projekt |
| j / k | Następna / poprzednia sesja |
| ⏎ | Attach w terminalu |
| r | Odpowiedz wybranej sesji |
| x | Zatrzymaj sesję |
| space | Podgląd ostatniego outputu |
| s | Podziel terminal |
| ⌘, | Ustawienia |

## Warianty wyglądu

Ten sam ekran działa z każdym schematem kolorów i każdą typografią. Dostępne motywy opisuje [08](08-wyglad.md) oraz [Quayside Themes Documentation.md](Quayside%20Themes%20Documentation.md).
