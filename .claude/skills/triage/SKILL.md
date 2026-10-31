---
name: triage
description: Zamienia opis problemu lub pomysłu (z rozmowy, logu, zrzutu) w dobrze opisane issue GitHub — typ, moduł, kroki odtworzenia, priorytet, etykiety. Użyj, gdy człowiek zgłasza błąd, zachowanie do poprawy lub nową potrzebę.
argument-hint: "<opis problemu>"
allowed-tools: Bash(gh issue *), Bash(gh label *), Read, Grep, Glob
---

# /triage

Zgłoszenie: `$ARGUMENTS`

Deleguj do subagenta `maintainer` (sekcja „Triage zgłoszeń”) albo wykonaj sam:

1. Ustal typ: `type:bug` (działało inaczej niż w wymaganiach), `type:feat` (nowa potrzeba), `type:chore`.
2. Ustal moduł na podstawie `docs/11-koncepcja-techniczna.md` (np. powiadomienia → `area:notifications`). Gdy niepewne: `area:unknown` i pytanie w treści.
3. Dla błędu: oczekiwane vs faktyczne zachowanie, kroki odtworzenia, wersja aplikacji i Claude Code, logi (bez danych wrażliwych). Brakujące informacje → zadaj człowiekowi maks. 2 pytania przed utworzeniem issue.
4. Priorytet: `P1` blokuje pracę z agentami, `P2` utrudnia, `P3` kosmetyka.
5. Sprawdź duplikaty: `gh issue list --search "<słowa kluczowe>" --state all`.
6. Utwórz issue szablonem z `.github/ISSUE_TEMPLATE/` (`gh issue create --title … --body-file … --label …`).
7. Dla `type:feat` zaproponuj linię do `docs/ROADMAP.md` (ID w odpowiednim etapie), ale nie zmieniaj roadmapy bez zgody.

Zwróć link do issue i jednozdaniowe podsumowanie.
