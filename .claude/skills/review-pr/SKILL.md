---
name: review-pr
description: Przegląd pull requesta względem wymagań inżynierskich (docs/10), planu zadania i reguł modułów (docs/11). Lokalnie zwraca raport; z flagą --comment publikuje go jako komentarz w PR (używane przez workflow claude-review.yml). Użyj, gdy trzeba ocenić PR.
argument-hint: "<numer-PR> [--comment]"
allowed-tools: Bash(gh pr diff *), Bash(gh pr view *), Bash(gh pr comment *), Read, Grep, Glob
---

# /review-pr

Argumenty: `$ARGUMENTS` (numer PR, opcjonalnie `--comment`).

## Dane

1. `gh pr view <nr> --json title,body,files,headRefName,labels` — zadanie AM-XXX z tytułu, gałęzi lub opisu.
2. `gh pr diff <nr>` — pełny diff.
3. Plan `docs/plans/AM-XXX.md` (jeśli jest w PR lub w repo), wymagania z linii zadania w `docs/ROADMAP.md`.

## Ocena

Zastosuj checklistę i format odpowiedzi z `.claude/agents/reviewer.md` (zakres, granice modułów, interfejsy, clean code, testy, bezpieczeństwo, dokumentacja). Zasady:

- Blokujące tylko łamanie wymagań, brak testów, ryzyko bezpieczeństwa lub błąd logiki z konkretnym miejscem `plik:linia`.
- PR-y zmieniające tylko dokumentację lub konfigurację: sprawdź spójność z docs/10–14 i czy nie luzują zabezpieczeń agenta (uprawnienia, hooki).
- Maks. 10 punktów łącznie; zacznij od najważniejszych.

## Publikacja

- Bez `--comment`: zwróć raport w odpowiedzi.
- Z `--comment`: zapisz raport do pliku i `gh pr comment <nr> --body-file <plik>`. Nagłówek: `### Review (Claude) — WERDYKT`. Nie zatwierdzaj i nie odrzucaj PR formalnie.
