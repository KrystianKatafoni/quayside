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
- Scenariusze T i O zapisują plik. W domyślnym trybie zgód może to wywołać prośbę o zgodę i fałszywe porażki. Rozważyć `--permission-mode acceptEdits` tylko dla T i O (Z zostaje w trybie domyślnym). Decyzja przed krokiem 2.
- Odpowiedź przez Esc przy odmowie może zostawić sesję czekającą na tekst. Kalibracja porównuje opcję 3 z Esc.
- `SpikeClaude` duplikuje parser `agents --json` z `agent-runtime:impl` (zależność impl → impl jest zakazana).

## Ograniczenia pomiaru

- Wyścig „widoczna karta attach” sprawdzamy tylko ręcznie.
- Wyścig „sesja przestaje czekać” jest przybliżony przez `stopAndRemove` przed attach. To „attach do usuniętej sesji”, a nie TOCTOU.

## Wyniki

Wypełni PR `docs/AM-507-wyniki`.

| Rodzaj | Próby | Sukcesy | Szkodliwe | p50 | p95 | Wpisania w wyścigach | Czyste odłączenia | Wybrany wariant | Wersja `claude` |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T tekst | | | | | | | | | |
| O opcje | | | | | | | | | |
| Z zgoda | | | | | | | | | |

## Decyzja

Do wypełnienia po pomiarze (PR `docs/AM-507-wyniki`, `Closes #101`).
