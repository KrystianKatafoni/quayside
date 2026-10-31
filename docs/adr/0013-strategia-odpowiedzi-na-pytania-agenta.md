# ADR 0013: Strategia odpowiedzi na pytania agenta

- **Status:** przyjęta (decyzja Krystiana 2026-10-19, wariant „Etapami”)
- **Data:** 2026-10-20
- **Dotyczy:** sessions:api, sessions:impl, terminal:*, mission-control:ui, app, notifications:ui (AM-411, AM-506, AM-507, AM-702)

## Kontekst

FR-S5 (priorytet S) wymaga odpowiedzi na pytanie agenta z panelu szczegółów albo z popupu, bez otwierania terminala. FR-K2 przypisuje do tego skrót `r`. Claude Code nie ma komendy `reply`. Dla sesji w tle `claude` 2.1.302 oferuje tylko `attach`, `logs`, `stop`, `rm`, `respawn` oraz `--bg --resume <id>`. Według pomocy `claude --bg --resume <id>` na sesji, która już działa, **startuje kopię** sesji. Sesja czekająca na odpowiedź działa, więc ta komenda nie odpowiada, tylko tworzy duplikat.

Do tej pory docs/09 zakładał w MVP odpowiedź przez ukryty attach i wpisanie tekstu. To jest kruche, bo aplikacja pisałaby do interfejsu tekstowego (TUI) Claude Code, którego stan i czas reakcji nie są kontraktem. Inaczej trzeba też obsłużyć pytanie tekstowe, pytanie z opcjami i prośbę o zgodę.

## Decyzja

Odpowiedź wchodzi etapami. AM-411 dostarczył podgląd ostatniego wyjścia sesji (`SessionMonitor.recentOutput`, skrót `space`) i nie zależy od strategii odpowiedzi. W MVP skrót `r` otwiera `claude attach <id>` w karcie terminala aplikacji z fokusem na polu odpowiedzi (AM-506, po AM-503 i AM-505). Użytkownik odpowiada w pełnym TUI, razem ze zgodami i wyborem opcji.

Odpowiedź bez terminala (FR-S5: pole w panelu i w popupie) wymaga najpierw spike'a ukrytego attach w PTY (AM-507, po AM-502). Spike mierzy niezawodność osobno dla pytania tekstowego, pytania z opcjami i prośby o zgodę. Przy dobrym wyniku AM-702 dostaje pole odpowiedzi w popupie, a panel dostaje szybką odpowiedź. Przy złym wyniku wracamy do decyzji o Agent SDK albo `claude -p --input-format stream-json` w nowym ADR. Odpowiedzi przez `claude --bg --resume` nie używamy.

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| **Etapami: `r` = attach w karcie terminala, później spike ukrytego attach** | Brak kruchości w MVP, pełne TUI ze zgodami; FR-S5 zależy od zmierzonego wyniku, nie od założenia | FR-S5 nie jest spełnione w MVP; terminal otwiera się przy odpowiedzi |
| Od razu ukryty attach w PTY (wpisanie tekstu i Enter) | Ta sama sesja, pole odpowiedzi w panelu i popupie od razu | Kruche: zależy od stanu i czasu reakcji TUI, każda wersja Claude Code może to zepsuć; trzy rodzaje pytań wymagają osobnej obsługi |
| `claude --bg --resume <id> "<odpowiedź>"` | Jedna komenda CLI, bez PTY | Na działającej sesji startuje kopię zamiast odpowiadać — odrzucone |
| Agent SDK albo `claude -p --input-format stream-json` jako osobny proces | Stabilny protokół, własny widok czatu, pełna kontrola nad zgodami | Inny backend niż sesje Agent View; runtime Node/Python lub duży protokół; dużo pracy i nowy ADR |
| Bez odpowiedzi w aplikacji (tylko ⏎ attach) | Najprostsze | Brak `r` z FR-K2 i brak drogi do FR-S5 |

## Konsekwencje

- AM-506 (#96) zmienia zakres: `r` otwiera attach w karcie terminala z fokusem na odpowiedzi. Zależy od AM-503 i AM-505, bez zmian w `sessions:api`.
- Nowe zadanie AM-507: spike ukrytego attach dla FR-S5 po AM-502. Wynik zapisujemy jako ADR uzupełniający ten albo zastępujący jego drugą część.
- `TerminalSession.send(text)` z AM-502 zostaje jako droga dla AM-507. Jeśli spike wymaga sesji bez widżetu, `terminal:api` dostanie wariant `open` bez komponentu (decyzja w AM-507).
- AM-702 (popupy) pokazuje pole odpowiedzi tylko przy dobrym wyniku AM-507. Inaczej popup ma akcję „Odpowiedz w terminalu”.
- Otwarta kwestia z docs/09 o odpowiedzi jest zamknięta. Ryzyko „brak `reply`” wskazuje ten ADR.
