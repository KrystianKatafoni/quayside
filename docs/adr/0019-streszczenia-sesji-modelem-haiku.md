# ADR 0019: Streszczenia sesji modelem Haiku

- **Status:** proponowana
- **Data:** 2026-11-02
- **Dotyczy:** summaries, agent-runtime, sessions

## Kontekst

FR-S7 dopuszcza „opcjonalnie streszczenie modelem klasy Haiku”. AM-604 część A robi podsumowanie bez modelu: pierwsze 1–2 zdania z `last_assistant_message` hooka `Stop`. Lepszy opis daje `claude -p --model haiku` po każdym `Stop` (tak robi Agent View, docs/00). To jednak:

- uruchamia nowy proces `claude` i kosztuje tokeny przy każdej turze każdej sesji,
- wysyła treść odpowiedzi agenta do modelu drugi raz,
- trwa sekundy, więc nie może blokować kolektora hooków w `sessions:impl`,
- sam wywołałby nasze hooki (`Stop`) i mógłby zapętlić streszczanie,
- wymaga przełącznika i jego zapisu (otwarta kwestia w docs/09).

ADR 0002 mówi, że wszystkie wywołania `claude` są w jednym adapterze w `agent-runtime:impl`.

## Decyzja

Decyzje człowieka z 2026-11-02:

1. **Port w `agent-runtime:api`.** Nowy mały interfejs `OneShotPrompt { suspend fun ask(request: PromptRequest): Outcome<String, RuntimeError> }` (ENG-I2), z implementacją `ClaudePrintAdapter` w `agent-runtime:impl`: `claude -p --model <model> --output-format json --settings {"disableAllHooks":true} --no-session-persistence -- <prompt>`. Prompt idzie w argv, bo `ProcessRunner` nie ma stdin (tak samo jak prompt w `claude --bg`); `PromptRequest.toString` i błędy go nie zawierają.
2. **Domyślnie wyłączone.** Przełącznik `useModel` w nowym pliku `summaries.json` (właściciel `summaries:impl`, `JsonStore`, `schemaVersion: 1`, wzór `notifications.json`). Brak pliku albo plik uszkodzony = wyłączone. Plik czytany raz przy starcie; bez ekranu w UI (FR-S7 go nie wymaga), ekran ustawień to osobne zadanie.
3. **Dwa etapy.** Podsumowanie bez modelu (`Summarizer`) pojawia się od razu, a `SummaryRefiner` (Haiku) zastępuje je w tle. Najwyżej jedno wywołanie modelu naraz, nowszy `Stop` tej samej rozmowy anuluje starsze, wynik starszej tury jest odrzucany. Każdy błąd modelu zostawia podsumowanie bez modelu.
4. Streszczane są tylko rozmowy z bieżącej listy sesji supervisora; to druga ochrona przed pętlą.

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| Port w `agent-runtime:api` (wybrana) | Zgodne z ADR 0002: jeden adapter i jedne fixtures fałszywego `claude` | Zmiana `api` innego feature (zatwierdzona) |
| `summaries:impl` woła `claude -p` przez `ProcessRunner` | Zmiana tylko w `summaries` | Łamie ADR 0002: druga znajomość flag CLI |
| Bezpośrednie API Anthropic (HTTP) | Bez procesu, szybciej | Klucz API, sieć, nowa biblioteka |
| Bez modelu (tylko część A) | Zero kosztu i ryzyka | Słabsze opisy przy długich odpowiedziach |

## Konsekwencje

- Nowy plik konfiguracji `summaries.json` w tabeli docs/11 §8; nowy port w docs/11 §4.
- Flagi `-p`, `--output-format json`, `--settings`, `--no-session-persistence` trafiają do `docs/compat.md` i `/compat-check`.
- Testy bez sieci i bez prawdziwego `claude`: fałszywy `claude` w katalogu tymczasowym (kontrakt `OneShotPrompt`), `FakeOneShotPrompt` w `summaries`.
- Prywatność: treść wiadomości trafia do modelu tylko po `useModel: true`; nigdy do logów. W argv jest widoczna w `ps` dla tego samego użytkownika.
- Koszt: najwyżej jedno wywołanie Haiku na turę sesji.
