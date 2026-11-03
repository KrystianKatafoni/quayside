---
name: recurring-review-findings
description: Powtarzające się problemy w PR Quayside, które warto sprawdzać od razu (runBlocking z @Suppress poza main, rozjazd planu z API, kolejność błędów w fake'ach)
metadata:
  type: feedback
---

Sprawdzaj od razu przy każdym review:

- `runBlocking` w prywatnym pomocniku w `Main.kt` z `@Suppress("ForbiddenMethodCall")` zamiast w samym `main()` (AM-508). ENG-C3 dopuszcza tylko `main` i testy; suppress obchodzi detekt.
  **Why:** detekt tego nie łapie, gdy jest suppress; bramka zielona.
  **How to apply:** `grep -n "runBlocking\|ForbiddenMethodCall" app/src/main` i sprawdź, w której funkcji stoi.
- Sekcja „Zmiany API” planu rozjeżdża się z kodem po obejściu testu architektury (np. plan: `public class TerminalLaunch`, kod: `internal` + przeciążona fabryka). Test `impl exposes only its module factory` sprawdza tylko klasy/interfejsy/obiekty, nie funkcje.
  **How to apply:** porównaj sygnatury publicznych fabryk `impl` z planem i docs/11.
- Fake w `testFixtures` sprawdza `failure` przed walidacją, a impl po walidacji — inna odpowiedź na ten sam błędny update. Kontrakt tego nie łapie.

- Fabryka `impl` wpina `NoOpLogger` na sztywno, a test jednostkowy klasy sprawdza `warn` przez `RecordingLogger` (AM-703, `VoiceNotifier`). Test zielony, w produkcji log martwy.
  **How to apply:** `grep -n "NoOpLogger" feature/*/impl/src/main` i porównaj z planem („błąd X: warn”).
- Ścieżka „porzuć/wyczyść stan” (np. grupa głosu odrzucona przy aktywnym oknie) testowana tylko przez „nic nie powiedziano”; brak testu, że następne zdarzenie przechodzi od razu — mutacja zostawiająca flagę (`cooling`) przeżywa.
- Rozmiar PR: plany UI zaniżają o ok. 50% (AM-704 A: plan 390, pomiar ok. 600 netto z importami), a próg „gdy pomiar > X, przenieś Y” z planu bywa pominięty bez aktualizacji planu. Przeniesienie do części B tylko przesuwa nadmiar.
  **How to apply:** zmierz netto (awk bez pustych/KDoc) dla `src/main` + `git diff --numstat` w `app`; porównaj z progiem z sekcji „Rozmiar” planu.
- Kolory dialogów skopiowane z `terminal:ui`: `secondary` 0xFF8E8E93 (4.27:1) i `error` 0xFFFF453A (4.09:1) na `panel` 0xFF2C2C2E łamią NFR-6 (≥ 4.5:1); brak widocznego wskaźnika fokusu w kontrolkach dialogów.
- `repo.update { current -> (walidacja as? Ok)?.value ?: current }` po wcześniejszej walidacji: nieosiągalna gałąź, która przy przyszłej walidacji zależnej od `current` da ciche „sukces bez zmian”.

- Słabe asercje tekstu komunikatu: `message shouldContain "1"` przy zakresie 1–10 jest zawsze prawdziwe (bo „10” zawiera „1”) — mutacja `{min}` przeżywa (AM-704 B1, wcześniej A dla 1–1440). Oczekuj `shouldContain "od 1 do 10"`.
- Teksty UI z liczbami zachowania z `impl` (np. „8 s” = `PopupManager.TIMEOUT`, internal) — `ui` nie widzi stałej, dryf niewykrywalny testem; proponuj stałą w `api` albo co najmniej komentarz po obu stronach.
- `ChoiceField`/`TextSetting` w dialogach nie podają nazwy pola w semantyce (czytnik czyta tylko wartość); dotąd traktowane jako sugestia, bo NFR-6 wymaga etykiet tylko dla przycisków ikonowych.

- Przykłady „sortowanie bez wielkości liter” w planie/kontrakcie (`zosia`, `Anna`) dają ten sam wynik przy sortowaniu ASCII — mutacja `sortedBy { name }` przeżywa (AM-704 B2a). Wymagaj pary typu `anna`/`Bob`.
- Uzasadnienie usunięcia testu („byłby zawsze zielony”) weryfikuj ręcznie: w AM-704 B2a test „zapis od current” byłby CZERWONY (szkic nadpisuje wszystkie pola), czyli dokumentował zmianę semantyki, którą trzeba opisać w planie/KDoc.

- Ostatnia część zadania (PR z `Closes #N`) pomija `docs/ROADMAP.md` `[x]`, choć plan go wymienia (AM-704 B2b). Sprawdzaj `git status` pod kątem ROADMAP przy każdym zamykającym PR. To samo dla pierwszej części: `[~]` i CHANGELOG `[Unreleased]` brakowały w AM-604 A, choć plan je wymieniał w „Moduły A”.
- ENG-C2: docs/10 mówi „≤ 5 parametrów”, ale `config/detekt/detekt.yml` ma `allowedConstructorParameters: 6` — konstruktor z 6 parametrami przechodzi bramkę. Traktuj jako sugestię i wskaż rozjazd (AM-604 `SessionsState`).
- Limity długości (`<= MAX`) w czystych rdzeniach: sprawdzaj, czy tabela testów ma przypadek dokładnie na granicy dla każdej gałęzi porównania; w AM-604 granica „dwa zdania = 160” nie była przypięta (mutacja `<=`→`<` przeżywa).
- `remember { wartość z pierwszej kompozycji }` jako „zapamiętany zapis” (np. niezainstalowany głos zostaje na liście) bez testu „odejdź i wróć” — mutacja na bieżącą wartość przeżywa. Tak samo `ignoreCase` przy skoku literą, gdy wszystkie nazwy w fixtures są z wielkiej litery.
- AM-604 B powtórzyło brak ROADMAP `[x]` i CHANGELOG mimo planu („Moduły B”) — sprawdzaj w KAŻDEJ części, nie tylko ostatniej.
- Wywołanie modelu (`claude -p`) z treścią spoza kontroli (wiadomość agenta) dziedziczy ustawienia użytkownika: narzędzia, `permissions.allow`, MCP, globalny CLAUDE.md; cwd = home daje Read całego katalogu domowego bez pytania. Sprawdzaj ograniczenie narzędzi/tur i opis ryzyka w ADR (AM-604 B).
- `JsonPrimitive.booleanOrNull` przyjmuje też string `"true"` — „zły typ = wyłączone” wymaga `!isString`; testy z `"yes"` tego nie łapią.
- Twierdzenia KDoc o współbieżności („deadlock przy Unconfined”) weryfikuj: `Mutex.lock` zawiesza, nie blokuje, a szybka ścieżka `withLock` nie sprawdza anulowania (więc strażnik pokolenia po anulowaniu jest potrzebny i testowalny).
- Mapy per rozmowa (`turns`, `jobs`) bez czyszczenia: pytaj o wyciek; licznik pokolenia globalny pozwala czyścić bez ponownego użycia numerów.
- Zwolnienie flagi w `finally` wokół wywołania zwracającego `Outcome` (bez wyjątków) jest równoważne zwolnieniu po `try` — mutacja przeżywa; to obrona, nie błąd, ale opisz to w KDoc zamiast deklarować „sprawdzone testem”.

- Dialog zgody (AM-607 hooki): automatyczny fokus na pierwszym aktywnym przycisku = „Zainstaluj”, a `LaunchedEffect(first)` po akcji przenosi fokus na przeciwną akcję („Odinstaluj”); bez widocznego wskaźnika fokusu Space/Enter zmienia plik użytkownika bez świadomego kliknięcia. Przy zmianach plików użytkownika fokus startowy na „Pomiń/Zamknij”.
  **How to apply:** w dialogach z akcją zapisującą sprawdź `requestFocus` i jaki przycisk dostaje fokus po zmianie stanu.
- Stała z `Main.kt`/`impl` (port 47321) wpisana literalnie w `strings.properties` i literalnie w teście okna — test nie wykryje rozjazdu; oczekuj `{0}` albo testu z `$STAŁA` (ten sam wzorzec co „8 s”).

Powiązane: [[reviewer-workflow]]
