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
- Rozmiar diffu z nowymi plikami: NIE używaj `git add -N .` + `git reset` — `reset` zdejmuje z indeksu wcześniejsze `git rm` autora (AM-806: musiałem przywracać `git rm --cached`). Licz `git diff HEAD --numstat` dla śledzonych i `wc -l` dla `git ls-files -o --exclude-standard`.
- Sondy (np. wypisanie par kontrastu prawdziwego motywu z katalogu) pisz jako tymczasowy test w kopii, wynik do pliku w scratchpadzie; usuń przed mutacjami.
- Źródła Compose do weryfikacji twierdzeń (np. `SwingWindow` przekazuje `currentCompositionLocalContext`, granice `SemanticsNode`): `curl -sfLO https://repo1.maven.org/maven2/org/jetbrains/compose/ui/ui-desktop/<wersja>/ui-desktop-<wersja>-sources.jar`.
- Testy z prawdziwym `ComposeWindow` działają na tym Macu i na CI macos-15 (nie headless, ok. 9 s). Sonda Esc: `onEdt { focusOwner()?.dispatchEvent(KeyEvent(owner, KEY_PRESSED, ..., VK_ESCAPE, CHAR_UNDEFINED)) }`; plik `probe.txt` w scratchpadzie bywa zostawiony przez autora — przenieś go przed sondą.

Powiązane: [[recurring-review-findings]]
