---
name: reviewer-workflow
description: Jak reviewer bezpiecznie robi mutacje i weryfikuje fonty/dane w review Quayside (kopia rsync w scratchpadzie, skrypt z przywracaniem z pamięci, pułapki grep)
metadata:
  type: feedback
---

Mutacje rób wyłącznie w kopii: `rsync -a --exclude .git --exclude build --exclude .gradle --exclude .kotlin <repo>/ <scratchpad>/mut/`, skrypt Pythona podmienia fragment, uruchamia `./gradlew :<moduł>:test -q --offline`, przywraca treść z pamięci w `finally`.

**Why:** incydent z lekcji CLAUDE.md (31 obcych `.kt` skopiowanych do `src/main`); review nie może zmieniać plików repo.

**How to apply:**

- Przed wnioskiem sprawdź bazę kopii (zielona) i jedną zabitą mutację z pełnym wyjściem — przy ciepłym daemonie „BUILD FAILED in 1s” to prawdziwy błąd testu, nie kompilacji.
- Mutacje równoważne (np. `>=` → `>` przy porównaniu double z progiem) nie są uwagą blokującą.
- Weryfikacja `SOURCES.md` fontów: `grep -F` (nazwy plików mają `[wght]`, zwykły grep traktuje to jak klasę znaków i daje fałszywe rozjazdy); porównaj z `curl` z URL przypiętego commita.
- Porównanie katalogu z „Quayside Themes Documentation.md”: skrypt parsujący bloki CSS (`--bg-main`… `--shadow`, `rgba` → `#RRGGBB/NN`) przeciw wierszom Kotlina.

Powiązane: [[recurring-review-findings]]
