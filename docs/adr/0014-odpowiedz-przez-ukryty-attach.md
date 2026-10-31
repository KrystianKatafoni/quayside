# ADR 0014: Odpowiedź przez ukryty attach w PTY

- **Status:** proponowana (szkic ze spike'a AM-507; wyniki i decyzję dopisze PR `docs/AM-507-wyniki`, status zmienia człowiek)
- **Data:** 2026-10-20
- **Dotyczy:** `feature:terminal:impl` (spike), później `sessions`, `mission-control`, `notifications` (AM-702)

Uzupełnia drugą część ADR 0013 (nie edytujemy go) i opiera się na ADR 0012 (JediTerm i pty4j).

## Kontekst

FR-S5 wymaga odpowiedzi na pytanie agenta bez otwierania terminala, a FR-K2 przypisuje do tego skrót `r`. Claude Code nie ma komendy `reply`. Docs/09 wskazuje ryzyko „brak `reply`”. ADR 0013 wprowadził etapy: w MVP `r` otwiera attach w karcie terminala (AM-506), a pole odpowiedzi zależy od zmierzonej niezawodności ukrytego attach.

Interfejs tekstowy (TUI) Claude Code nie jest kontraktem. Jego stan, markery i czas reakcji mogą się zmienić z każdą wersją. Pytanie tekstowe, pytanie z opcjami i prośba o zgodę mają różne ekrany i różne klawisze, więc mierzymy je osobno.

## Warianty

| Wariant | Opis | Uwagi |
| --- | --- | --- |
| **Ukryty attach w PTY** | Aplikacja otwiera `claude attach <id>` bez widżetu, czeka na pytanie, wpisuje odpowiedź, sprawdza, że pytanie zniknęło, i odłącza klienta | Warianty klawiszy: tekst PASTE albo TYPE; wybór DIGIT albo ARROWS; odłączenie KEY albo HANGUP |
| Agent SDK albo `claude -p --input-format stream-json` | Osobny proces z protokołem | Wybór tylko przy złym wyniku wszystkich trzech rodzajów; osobny spike i ADR 0015 |
| `claude --bg --resume <id>` | Jedna komenda | Wykluczony: na działającej sesji startuje kopię |

## Protokół pomiaru

Pomiar na macOS, `claude` ≥ 2.1.302 (wersja trafia do raportu), model `haiku`. Każda próba to nowa sesja `--bg` w katalogu z `Files.createTempDirectory` (na macOS per-user `$TMPDIR`, 0700) i z losową nazwą sesji. Scenariusze: T (pytanie tekstowe), O (pytanie z opcjami), Z (prośba o zgodę). Sukces sprawdzamy po plikach, nie po ekranie.

| Krok | Próby | Sesje |
| --- | --- | --- |
| 1. `record`: ramki z prawdziwego TUI vs fixtures | 1 na rodzaj | 3 |
| 2. Kalibracja: 8 wariantów klawiszy i odłączenia | 3 na wariant | 24 |
| 3. Pomiar najlepszym wariantem | 10 na rodzaj | 30 |
| 4. Wyścigi | 3 na wyścig, 2 wyścigi | 6 |
| **Razem** | | **63 sesje haiku** |

Uruchomienie: `./gradlew :feature:terminal:impl:runReplySpike --args="<tryb> <parametry>"`. Tryby: `record kind=t|o|z`, `single kind=t|o|z`, `run`. Parametry `klucz=wartość`: `kinds` (np. `tzo`), `trials` (domyślnie 10), `race` (liczba wyścigów na rodzaj), `text=paste|type`, `choice=digit|arrows`, `detach=key|hangup`. Wynik trafia do `build/reply-spike/` (`trials.jsonl`, `summary.txt`, `spike.log`, w trybie `record` także `frame-*.txt`). Logi i raport nie zawierają tekstu ekranu ani odpowiedzi.

Werdykt próby: `Success`, `Failure` (kategoria: np. `NotReady`, `NotAcknowledged`, timeout) albo `Harmful` (zła opcja lub token, zgoda przy odmowie, sesja zatrzymana po odłączeniu, kopia sesji, wpisanie w wyścigu).

## Kryteria

| Ocena | Warunek (na rodzaj, 10 prób) | Skutek dla UI |
| --- | --- | --- |
| Dobry | 10/10, 0 szkodliwych, p95 ≤ 5 s, 0 wpisań w wyścigach | Pole odpowiedzi bez terminala |
| Graniczny | 9/10 bez szkodliwych albo 10/10 z p95 > 5 s | Pole odpowiedzi plus akcja „Odpowiedz w terminalu” przy `NotReady` |
| Zły | ≤ 8/10 albo jakikolwiek szkodliwy | Ten rodzaj zostaje w terminalu (AM-506) |

Gdy wszystkie trzy rodzaje są złe, następny krok to spike Agent SDK albo `stream-json` (ADR 0015).

**Uwaga statystyczna.** 10/10 nie gwarantuje niezawodności ≥ 95%. Dolna granica jednostronnego 95% przedziału ufności (Clopper–Pearson) wynosi przy 10/10 ok. 74%, więc „dobry” znaczy „brak sygnału awarii”. Ocenę graniczną albo złą bez szkodliwego wyniku doprecyzowujemy 10 dodatkowymi próbami; progi przy 20 próbach: dobry ≥ 19/20, graniczny 17–18/20, zły < 17/20. Liczbę prób i wersję `claude` zapisujemy w sekcji „Wyniki”. Pomiar powtarzamy po aktualizacji Claude Code.

## Ryzyka i kalibracja

Do sprawdzenia w krokach 1–2:

- Ekran bez widżetu (jediterm-core: `JediTerminal`, `JediEmulator`, własny `TerminalDisplay`) działa w testach, ale nie jest udokumentowaną drogą. Plan B: ukryty `JediTermWidget`.
- Markery TUI w `ScreenClassifier` są przybliżone. Spinner może łapać także punkty listy typu `* Coś…`. Krok 1 porównuje je z nagranymi ramkami.
- Klawisz odłączenia `claude attach` jest nieznany. `DETACH_KEY` (strzałka w lewo) to placeholder. Pole `cleanDetach` mierzy, czy klient odłączył się sam.
- Potwierdzenie przyjęcia tekstu (T): pole wejścia zostaje widoczne, więc potwierdzeniem jest `Busy`, `None` albo puste pole po wcześniej widzianym polu z tekstem (`TextFilled`).
- Zamknięcie ukrytego PTY po odłączeniu: na macOS pty4j przy zamykaniu PTY prawdopodobnie zabija całą grupę procesów klienta (test z potomkiem w tej samej grupie padał tylko w CI na macOS; fixture uruchamia go teraz w osobnej sesji, jak demon). Jeśli `claude attach` zostawia w swojej grupie proces potrzebny sesji, odłączenie może ją zabić. Każda próba sprawdza po odłączeniu, czy sesja żyje (`SESSION_LOST`).
- Reguła „sesja gotowa (DONE), a `waitingFor` = input” jest mapowana na `BLOCKED`. Do weryfikacji, ryzyko `STILL_BLOCKED`.
- Scenariusze T i O zapisują plik. W domyślnym trybie zgód może to wywołać prośbę o zgodę i fałszywe porażki. **Decyzja:** T i O startują z `--permission-mode acceptEdits`. Z startuje z jawnym `--permission-mode default`, bo w skonfigurowanym trybie użytkownika `touch` nie wywoływał prośby o zgodę i sesja kończyła się bez pytania (sprawdzone ręcznie przed pomiarem).
- Odpowiedź przez Esc przy odmowie może zostawić sesję czekającą na tekst. Kalibracja porównuje opcję 3 z Esc.
- `SpikeClaude` duplikuje parser `agents --json` z `agent-runtime:impl` (zależność impl → impl jest zakazana).

## Ograniczenia pomiaru

- Wyścig „widoczna karta attach” sprawdzamy tylko ręcznie.
- Wyścig „sesja przestaje czekać” jest przybliżony przez `stopAndRemove` przed attach. To „attach do usuniętej sesji”, a nie TOCTOU.

## Wyniki

Pomiar z 2026-10-31, `claude` 2.1.316, model `haiku`, macOS. Wariant każdego rodzaju wybrany w kalibracji; pomiar: 10 prób na rodzaj i 3 wyścigi na rodzaj, razem 39 sesji pomiarowych (plus 24 w kalibracji i 3 w kroku 1). Czasy to czas od wyświetlenia pytania do jego zniknięcia po wpisaniu odpowiedzi. Surowe wiersze (`trials.jsonl`, `calib-trials.jsonl`) zawierają tylko rodzaj, werdykt i czasy; zostają lokalnie w `build/reply-spike/`.

| Rodzaj | Próby | Sukcesy | Szkodliwe | p50 | p95 | Wpisania w wyścigach | Czyste odłączenia | Wybrany wariant | Wersja `claude` |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T tekst | 10 | 10 | 0 | 199 ms | 225 ms | 0 z 3 | 10 z 10 | `TextKeys.PASTE`, `Detach.HANGUP` | 2.1.316 |
| O opcje | 10 | 10 | 0 | 165 ms | 195 ms | 0 z 3 | 10 z 10 | `ChoiceKeys.DIGIT`, `Detach.HANGUP` | 2.1.316 |
| Z zgoda | 10 | 10 | 0 | 189 ms | 219 ms | 0 z 3 | 10 z 10 | `ChoiceKeys.DIGIT` (allow `1`, deny `Esc`), `Detach.HANGUP` | 2.1.316 |

Ocena według tabeli z sekcji „Kryteria”: **T Dobry, O Dobry, Z Dobry.**

Kalibracja (3 próby na wariant):

| Rodzaj | Wariant | Sukcesy | Czyste odłączenia |
| --- | --- | --- | --- |
| T | PASTE + HANGUP | 3/3 | 3/3 |
| T | TYPE + HANGUP | 2/3 (jedna porażka `no_effect`) | 3/3 |
| T | PASTE + KEY | 3/3 | 0/3 |
| O | DIGIT + HANGUP | 3/3 | 3/3 |
| O | ARROWS + HANGUP | 3/3 | 3/3 |
| Z | DIGIT + HANGUP | 3/3 | 3/3 |
| Z | ARROWS + HANGUP | 3/3 | 3/3 |
| Z | DIGIT + KEY | 3/3 | 0/3 |

Wnioski z kalibracji i kroku 1:

- Wklejenie (bracketed paste) jest pewniejsze niż pisanie znak po znaku i dwa razy szybsze. Cyfra i strzałki działają tak samo dobrze, cyfra jest prostsza.
- Odłączenie przez SIGHUP do klienta `attach` (`HANGUP`) jest czyste, sesja przeżywa. Klawisz odłączenia (placeholder, strzałka w lewo) nie odłączał klienta sam (0 czystych odłączeń).
- Markery ekranu z ręcznie zrobionych ramek nie pasowały do prawdziwego TUI i zostały poprawione na nagranych ramkach: pole wejścia to wiersz `❯` pod poziomą linią (dopełniony niełamliwymi spacjami); lista opcji pytania ma dodatkowe wiersze „Type something.” i „Chat about this”, których nie liczymy jako opcji; zgoda ma pytanie „Do you want to proceed?”, w którym opcja 2 zmienia ustawienia projektu (dlatego allow = `1`).
- Po pomiarze nie została żadna sesja (`claude agents --json --all`).

## Decyzja

Wszystkie trzy rodzaje pytań wyszły jako **Dobry**, więc odpowiedź przez ukryty `claude attach` w PTY jest przyjęta dla pytania tekstowego, pytania z opcjami i prośby o zgodę. Fallback Agent SDK / `stream-json` (ADR 0015) nie jest potrzebny.

Skutek dla UI (zgodnie z sekcją „Kryteria”): pole odpowiedzi bez terminala dla wszystkich trzech rodzajów. Zadanie wdrożeniowe wprowadza wariant `open` bez widżetu w `terminal:api` i `SessionReplies` w `sessions:api`, używa wariantów z tabeli (T: wklejenie, O i Z: cyfra, odłączenie przez `HANGUP`) i przenosi do `main` ekran bez widżetu, klawisze i klasyfikator razem z markerami z tego pomiaru. Zbiór źródeł `replySpike` usuwa to zadanie.

Zastrzeżenia:

- **10/10 to brak sygnału awarii, nie dowód.** Dolna granica 95% przedziału ufności wynosi ok. 74%. Przed wdrożeniem warto powtórzyć pomiar na 20 próbach (dobry ≥ 19/20), a zawsze po aktualizacji Claude Code. Markery ekranu są przypięte do 2.1.316.
- **Wyścigi pokryte częściowo** (sekcja „Ograniczenia pomiaru”): „widoczna karta attach” tylko ręcznie, „sesja przestaje czekać” jako attach do usuniętej sesji.
- **Z mierzono w jawnym trybie `default`**, a T i O w `acceptEdits`. Zachowanie w trybach użytkownika, które same zezwalają na polecenie, jest inne: sesja nie pyta, więc nie ma czego potwierdzać. Aplikacja musi odpowiadać tylko na pytanie, które jest na ekranie (klasyfikator to sprawdza przed wpisaniem).
- **Odstępstwo od protokołu:** `claude --bg` odmawia pracy w niezaufanym katalogu (`Workspace not trusted`), więc katalogi prób leżą w `~/quayside-spike-work` (owner-only, nadpisywalne `QUAYSIDE_SPIKE_WORK`), a nie w `$TMPDIR`. Człowiek zaufał tej jednej lokalizacji ręcznie. Sesje pomiarowe nie widzą CLAUDE.md ani hooków repo.
- **Wdrożenie w aplikacji** wymaga zaufanego katalogu sesji: ukryty attach działa na istniejących sesjach, więc nie dotyczy nowych katalogów, ale warto to potwierdzić w zadaniu wdrożeniowym.
