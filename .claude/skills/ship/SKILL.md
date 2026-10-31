---
name: ship
description: Zamienia bieżące zmiany na gałęzi w porządne commity (Conventional Commits), pushuje gałąź i otwiera lub aktualizuje pull request z opisem wg szablonu repo. Użyj, gdy praca na gałęzi jest gotowa do przeglądu albo człowiek prosi o commit, push lub PR.
argument-hint: "[draft]"
allowed-tools: Bash(git *), Bash(gh *), Bash(./gradlew *), Read, Grep, Glob, Edit, Write
---

# /ship — commity, push i pull request

Tryb: `$ARGUMENTS` (`draft` = PR jako szkic).

## Stan

- Gałąź: !`git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "?"`
- Zmiany: !`git status --short 2>/dev/null | head -30 || true`
- Commity względem main: !`git log --oneline main..HEAD 2>/dev/null | head -20 || true`

## 1 · Bezpieczeństwo

- Na `main` lub `master`: **stop**. Utwórz gałąź (`feat/AM-XXX-opis`) i przenieś na nią zmiany (`git switch -c …`).
- Nie commituj sekretów: przejrzyj `git diff` pod kątem tokenów, haseł, plików `.env`, `*.p12`.

## 2 · Bramka

Jeśli istnieje `./gradlew`: `./gradlew check`. Czerwono → nie wysyłaj; napraw albo zgłoś człowiekowi. (Wyjątek: `draft` z adnotacją w opisie PR, co nie przechodzi.)

## 3 · Commity

Podziel zmiany na logiczne commity (np. plan i dokumentacja, testy, implementacja). Dla każdego:

```text
<typ>(<moduł>): <opis w trybie rozkazującym, po angielsku, ≤ 72 znaki>

<dlaczego, nie co — 1–3 linie, opcjonalnie>

Refs: #<issue>        (lub Refs: AM-XXX, gdy brak issue)
```

Typy: `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci`, `chore`, `perf`. Moduł = nazwa feature lub `core`, `app`, `agent`, `roadmap`. Nigdy `--no-verify`, nigdy `--amend` na wypchniętych commitach.

## 4 · Push

`git push -u origin <gałąź>`.

## 5 · Pull request

Treść z `.github/pull_request_template.md`, wypełniona po polsku:

- **Zadanie**: `AM-XXX` + link do planu `docs/plans/AM-XXX.md`.
- **Co się zmienia**: 2–5 punktów z perspektywy zachowania aplikacji.
- **Zmienione moduły**: lista; uzasadnij każdy spoza planu.
- **Testy**: co dodano na każdym poziomie i wynik `./gradlew check`.
- **Checklista DoD**: odhacz tylko to, co faktycznie zrobione.
- `Closes #<issue>`.

Zapisz treść do pliku tymczasowego i:

```bash
gh pr create --base main --head <gałąź> --title "<tytuł jak pierwszy commit>" --body-file <plik> [--draft] --label "area:<feature>"
```

Jeśli PR już istnieje (`gh pr view`), zaktualizuj opis (`gh pr edit --body-file`) i dodaj komentarz z podsumowaniem nowych commitów.

## 6 · Wynik

Zwróć: link do PR, listę commitów, status bramki. Nie scalaj PR.
