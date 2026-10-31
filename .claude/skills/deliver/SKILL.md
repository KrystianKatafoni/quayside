---
name: deliver
description: Dowozi jedno zadanie z docs/ROADMAP.md od wyboru do otwartego PR — issue, gałąź, plan (architect), testy (test-engineer), implementacja (implementer), review (reviewer), bramka, commity, push i PR (shipper). Użyj, gdy człowiek prosi o realizację kolejnego lub wskazanego zadania z roadmapy.
argument-hint: "[AM-XXX]"
allowed-tools: Bash(git *), Bash(gh *), Bash(./gradlew *), Bash(python3 scripts/*), Read, Grep, Glob, Edit, Write
---

# /deliver — dowiezienie zadania z roadmapy

Zadanie: `$ARGUMENTS` (puste = następne dostępne).

**Podział modeli.** Główna sesja (Opus) wybiera zadanie, zleca plan `architect` (Opus), sprawdza plan i zakres, rozmawia z człowiekiem. Testy, implementację, bramkę, push i PR wykonują subagenci na Sonnecie: `test-engineer`, `implementer`, `shipper`. Główna sesja nie pisze kodu, nie uruchamia `./gradlew check` i nie robi commitów; błędy odsyła do właściwego subagenta.

## Stan wejściowy

- Gałąź: !`git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "?"`
- Zmiany: !`git status --short 2>/dev/null | head -15 || true`
- W toku: !`grep -E '^- \[~\] AM-[0-9]' docs/ROADMAP.md 2>/dev/null | head -5 || true`
- Do zrobienia: !`grep -E '^- \[ \] AM-[0-9]' docs/ROADMAP.md 2>/dev/null | head -8 || true`

## Krok 0 · Warunki

1. Brak niezatwierdzonych zmian. Jeśli są: zapytaj człowieka (dokończyć je, odłożyć, porzucić). Nie usuwaj ich sam.
2. `git switch main && git pull --ff-only`.
3. Jeśli istnieje wpis `[~]` (otwarty PR), zapytaj, czy kontynuować tamten PR zamiast zaczynać nowe zadanie.

## Krok 1 · Wybór zadania

- Z argumentem: weź wpis o tym ID. Bez argumentu: **pierwszy** wpis `[ ]`, którego wszystkie ID z pola `zależy:` mają `[x]`.
- Pokaż człowiekowi wybraną linię i jednym zdaniem, co powstanie. Jeśli zadanie jest niejasne albo za duże (~400+ linii kodu), zaproponuj doprecyzowanie lub podział i **zatrzymaj się**.
- Issue: jeśli pole `issue:` to `—`, uruchom `python3 scripts/roadmap_sync.py --only AM-XXX`, który utworzy issue i wpisze jego numer.

## Krok 2 · Gałąź

`git switch -c feat/AM-XXX-<krótki-opis-kebab>` (dla poprawek `fix/…`, dla zadań porządkowych `chore/…`).

## Krok 3 · Plan → subagent `architect`

Przekaż: ID, pełną linię z roadmapy, wymagania z pola `wymagania:`. Oczekuj pliku `docs/plans/AM-XXX.md` i listy pytań blokujących.

- Pytania blokujące (zmiana `api` innego feature, nowy ADR, zmiana zakresu) → przedstaw człowiekowi i **czekaj**.
- Przeczytaj plan sam. Odrzuć go, jeśli lista modułów jest szersza, niż wymaga zadanie.

## Krok 4 · Testy → subagent `test-engineer`

Przekaż ścieżkę planu. Oczekiwany wynik: testy kompilują się i nie przechodzą z powodu brakującej implementacji.

## Krok 5 · Implementacja → subagent `implementer`

Przekaż ścieżkę planu i listę czerwonych testów. Oczekiwany wynik: zielone `./gradlew check`.

## Krok 6 · Zakres (sam)

`git diff --stat main...` — tylko moduły z planu (+ `docs/plans`). Zmiany poza planem → wróć do `implementer` z prośbą o ich cofnięcie albo uzasadnienie. Nie uruchamiaj tu `./gradlew check`; zielony wynik raportuje `implementer`, a przed wysyłką sprawdza go `shipper`.

## Krok 7 · Review → subagent `reviewer`

Przekaż ID i ścieżkę planu. Uwagi **blokujące** wróć do `implementer` / `test-engineer` i powtórz kroki 6–7. Sugestie zachowaj dla `shipper` jako „do rozważenia”.

## Krok 8 · Bramka, dokumentacja i wysyłka → subagent `shipper`

Przekaż: ID, ścieżkę planu, numer issue, werdykt review z sugestiami, decyzje z raportu `implementer` warte opisania w PR oraz wniosek do sekcji „Lekcje”, jeśli review go przyniosło. `shipper` uruchamia `./gradlew check`, aktualizuje roadmapę (`[~]`) i `CHANGELOG.md`, a potem `/ship`.

- Bramka czerwona → `shipper` zwraca błędy bez poprawiania. Przekaż je `implementer` (lub `test-engineer`, jeśli błąd jest w teście) i ponów krok 8. Maks. 3 rundy, potem eskalacja do człowieka z opisem.

## Krok 9 · Wynik

Zwróć człowiekowi: link do PR, wynik bramki, werdykt review, otwarte kwestie, następne zadanie z roadmapy.

Nigdy nie scalaj PR i nie pushuj do `main`.
