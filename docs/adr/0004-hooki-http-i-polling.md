# ADR 0004: Zdarzenia z hooków HTTP + polling jako źródło prawdy

- **Status:** przyjęta
- **Data:** 2026-10-18
- **Dotyczy:** agent-runtime, sessions

## Kontekst

Użytkownik ma się dowiedzieć o czekającym agencie w ≤ 2 s. Polling `claude agents --json` jest pewny, ale wolny. Hooki są natychmiastowe, ale mogą zaginąć (np. gdy aplikacja jest zamknięta).

## Decyzja

Hooki HTTP (`Notification`, `Stop`, `StopFailure`, `SessionStart`, `SessionEnd`) na `127.0.0.1` z tokenem dają zdarzenia i treść. Polling co 2–3 s jest źródłem prawdy i koryguje stan w reducerze.

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| Tylko polling | Prosto | Opóźnienie, brak treści pytania |
| Tylko hooki | Natychmiast | Utracone zdarzenia = zły stan |
| Obserwacja plików `~/.claude/jobs` | Bez konfiguracji hooków | Nieoficjalny, niestabilny format |

## Konsekwencje

Instalator hooków musi robić kopię zapasową i umieć się odinstalować. Reducer łączy dane po `conversationId`.
