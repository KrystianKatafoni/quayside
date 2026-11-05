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
- ENG-C2 (stan 2026-11-03): docs/10 mówi „funkcja ≤ 5 parametrów, konstruktor ≤ 6” — zgodne z detekt `allowedConstructorParameters: 6`; funkcje i composable z 6+ parametrami sprawdzaj ręcznie (lekcja w CLAUDE.md).
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

- `ChoiceField` z zawijaniem (`floorMod`) stawia najgroźniejszą opcję obok domyślnej: w AM-608 jedna strzałka w lewo z „Domyślny” wybiera „Pomiń zgody” (bypassPermissions), a ostrzeżenie nie jest `liveRegion`. Test UI wręcz przypinał zawijanie.
  **How to apply:** przy listach trybów/uprawnień sprawdź sąsiadów wartości domyślnej po zawinięciu i czy ostrzeżenie trafia do czytnika.
- AM-608 znów bez CHANGELOG `[Unreleased]` i ROADMAP `[x]` mimo planu — sprawdzaj `git status CHANGELOG.md` na starcie każdego review.
- Strażnik `busy` w kontrolerze (np. `Edit` przy `busy`) bywa testowany tylko przez UI z wyłączonymi kontrolkami — mutacja strażnika przeżywa; oczekuj testu jednostkowego kontrolera.

- AM-705 A: znów brak `docs/ROADMAP.md` `[~]` mimo „Moduły A” — to już wzorzec w każdej części; sprawdzaj `git status docs/ROADMAP.md` jako pierwszy krok.
- Warunkowy zapis sekcji JSON („nie zapisuj domyślnej”, `if (x != X()) put(...)`) bywa bez testu — round trip przechodzi także bez warunku, mutacja przeżywa. Oczekuj testu `shouldNotContain "<klucz>"` dla domyślnych ustawień.
- Nazwy testów parametryzowanych zawężone do pierwszego przypadku (`wrong type of enabled throws` dla 6 pól) i asercja `shouldNotContain "yes"` sensowna tylko dla jednego wiersza — sugestia nazwy i asercji per wiersz.

- AM-705 B: znów brak CHANGELOG `[Unreleased]`, choć „Moduły B” planu go wymienia (plan zrzuca ROADMAP na shippera, CHANGELOG nie jest jasno przypisany) — pytaj, kto dopisuje, zanim zatwierdzisz.
- Test „kolejność filtrów” przy łańcuchu `.filter {}` z niezależnych predykatów jednego kanału: kolejność jest nieobserwowalna (filtry są przemienne), więc nazwa testu obiecuje więcej, niż sprawdza. Sugeruj zmianę nazwy na „profil łączy się z X”.
- Podwójny strażnik zapisu sekcji (warunek `any { != default }` przed `put` + `filterValues` w `encode`): test „tylko domyślne wpisy” sprawdza brak id, nie brak klucza sekcji — mutacja `any {…}` → `isNotEmpty()` przeżywa. Oczekuj `shouldNotContain "<sekcja>"`.
- Parametr opisowy testu parametryzowanego „użyty” asercją `label.isNotEmpty() shouldBe true` tylko dla detektu — asercja zawsze zielona; sugeruj `@Suppress("UnusedParameter")` albo `name = "{0}"` bez parametru.

- AM-801: znów ROADMAP `[ ]` mimo „Closes #N, roadmapa `[x]`” w planie (wcześniejsze PR-y miały commit `docs(roadmap): mark AM-XXX done` w gałęzi zadania).
- Dane testu tablicowego czystej funkcji mieszającej wejścia (np. `deriveTokens`: bg = panel, text = accent = warn) czynią źródło każdej reguły nieobserwowalnym — mutacja `mix(text)`→`mix(accent)` przeżywa. Wymagaj wejść rozróżnialnych po kanale (np. bg `#0000C8`, accent `#FF0000`, warn `#00FF00`).
  **Why:** plan sam dobrał „szare” dane; implementer je przepisał, testy zielone.
  **How to apply:** dla każdej reguły X = f(A, B) zapytaj, czy zamiana A na inną kolumnę zmienia oczekiwaną wartość.
- Korekta wartości w tabeli docs zostawia starą wartość cytowaną w innej sekcji tego samego pliku (AM-801: `paper` `warn` poprawione, „Luki 1” dalej każe brać `#B45309` z `paper`). Po korekcie grepuj starą wartość w `docs/`.
- Test fake'a „serwuje podany katalog” z argumentem równym domyślnemu (`FakeX(defaultCatalog())` vs `FakeX()`) jest zawsze zielony — oczekuj innej wartości i `shouldBeSameInstanceAs`.
- `require` w typach `api`/`core` bez `@throws` w KDoc (precedens w repo: `Shortcut`, `OneShotPrompt` mają `@throws IllegalArgumentException`). Wyjątek z warunku wstępnego na stałych to błąd programisty (OK wobec ENG-I4), ale musi być opisany; dane użytkownika (theme.json) wymagają `parse(): T?`.

- AM-802 (design system): test kontrastu czystych funkcji kolorów (`ComponentColors`) nie dowodzi, że composable ich używa — mutacje `LocalTextColor provides null` w `AmModeBar` i `FRAME_WIDTH = 0.dp` w `amFocusFrame` przeżyły. Oczekuj testu UI (np. `captureToImage` piksela ramki/tekstu) dla wskaźnika fokusu i koloru kontenera.
  **How to apply:** dla każdego „komponenty biorą kolory tylko stąd” wybierz jedną mutację w composable i sprawdź, czy któryś test pada.
- Ramka fokusu rysowana w granicach komponentu (przed `clip`/`background`) graniczy z tłem komponentu, nie kontenera: na przycisku głównym `text` na `accent` = 2.2:1 (plex i paper) — audyt AM-801 tej pary nie ma. Sprawdzaj, z jakim kolorem ramka faktycznie sąsiaduje.
- Tabela ścieżek zasobów w kodzie (`AmFonts.FILES`) a test iterujący pliki z dysku: literówka w ścieżce przeżywa (mutacja `Geist[wght].ttf` → `GeistTypo.ttf` zielona). Wymagaj testu „każda ścieżka z tabeli istnieje na classpath i tabela = pliki”.
- `!!` w testach przechodzi przez detektTest (UnsafeCallOnNullableType domyślnie wyłącza testy), a ENG-C3 go zakazuje — `grep -rn '!!' --include='*.kt' <moduł>/src`.
- AM-802: znów ROADMAP `[ ]` mimo planu (commit 4: `[x]`) — to samo co AM-801. AM-805 (2026-11-03): to samo, plan „commit 5: AM-805 `[x]`”, w diffie `[ ]`.
- AM-805: mapowanie tokenu na parametry Compose w composable (`ShadowSpec.toShadow()` w `AmPanel`) testowane tylko „cień jest / nie ma” pikselem — mutacje znaku/zera offsetu, `blur`, `spread` i koloru cienia przeżyły. Oczekuj testu czystej funkcji mapującej (internal) albo asymetrii pikseli (nad vs pod, kolor ≠ szary).
  **How to apply:** dla każdego pola tokenu przekazywanego do Compose zrób mutację „pole = 0 / stała” i sprawdź, czy coś pada.
- Compose `Modifier.dropShadow` rysuje cień POD treścią bez wycięcia wnętrza (inaczej niż CSS `box-shadow`) — przy półprzezroczystym panelu (`glass-light`) cień prześwituje; KDoc „outside the shape” jest wtedy nieprawdziwy. Sprawdzaj źródła (`ui-desktop`/`ui-graphics-desktop` sources z repo1.maven.org).
- `error()`/`@throws` w czystej funkcji wyprowadzającej z list kandydatów (`deriveStatus`): sprawdź brute-force, czy wyjątek jest osiągalny (czerwony akcent 350–10° nie ma trójki) — OK dla stałych katalogu, ale ścieżka błędu bez testu (mutacja `?: error` → domyślna trójka przeżyła) i funkcji nie wolno użyć dla danych użytkownika (AM-804) bez `Outcome`.
- AM-806 (2026-11-04): znów ROADMAP `[ ]` mimo planu (commit 8: `[x]`) — piąty raz z rzędu w M8.
- AM-806: zmiany „przy okazji” bez wymuszenia przez bramkę: przeformatowanie `Slf4jLogger.kt`, `TerminalCommands.kt` i `TerminalTabs.kt` (`if (tab ==\n null\n)`) — w kopii przywrócone do `HEAD` dały zielony `quickCheck`.
  **How to apply:** dla każdego pliku w diffie spoza listy „Moduły” planu przywróć go w kopii do `HEAD` i uruchom `quickCheck`; zielono = zbędna zmiana (blokująca: złota zasada 1).
- AM-806: test kontrastu par ekranów pokrywał pary z planu („muted i statusy tylko na bg/panel”), a ekrany kładą `muted` i statusy na `sel` (wybrany wiersz MC, licznik sidebara, kod wyjścia karty terminala, licznik widoku globalnego) i `muted` na `warnBg` (× w banerze). Na `plex` przechodzi (≥ 5,9:1), ale audyt i KDoc (`Tone.tint`: „tylko bg i panel”) kłamią; AM-803 otwiera inne motywy.
  **How to apply:** grepuj `if (selected) sel`/`selColor()` i `warnBgColor()`, potem kolory tekstów w środku tych kontenerów; porównaj z mapami w `ScreenContrastTest`.
- Granice węzła semantyki w Compose = zewnętrzny modyfikator *scalający* (`SemanticsNode.findCoordinatorToGetBounds`), więc `testTag` przed `amFocusFrame` + `clickable` w środku daje granice bez ramki; obejście `semantics(mergeDescendants = true) {}` zmienia tylko granice (nie właściwości). Lepsze miejsce: wewnątrz `AmButton`.
- AM-807 (2026-11-05): znów brak ROADMAP `[x]` i CHANGELOG `[Unreleased]` mimo commitu 4 planu — szósty raz w M8. Do tego plan nie poprawiony po odstępstwie (plan: `SwingPanel(background = …)`, kod: tło w `factory`, bo przeciążenie z `background` jest `@Deprecated` w CMP 1.12 i i tak ustawiało `it.background` na komponencie — zmiana równoważna).
- Przeniesienie własności zasobu (scope z `app` do fabryki modułu) zostawia w KDoc zdania o sytuacji już nieosiągalnej z zewnątrz („If that scope is cancelled…”) — sprawdzaj KDoc fabryki po każdej zmianie parametrów.
- `stateIn(scope, Eagerly, initial)` testowany na `UnconfinedTestDispatcher`: wartość `initial` jest natychmiast nadpisana, więc mutacja „initial bez mapowania” przeżywa; przypięcie wymaga `StandardTestDispatcher` bez `runCurrent`.
- AM-808 (2026-11-05): tekst rysowany przez `rememberTextMeasurer` + `remember(text, style) { measurer.measure(...) }` — klucz bez `measurer` (który zależy od `LocalDensity`), więc po przeniesieniu okna na ekran o innej skali (Retina ↔ zewnętrzny) układ jest w starych pikselach. Zwykle zamiast `drawText` wystarczy `AmText` w opakowaniu z `clearAndSetSemantics {}`.
  **How to apply:** `grep -n "rememberTextMeasurer\|measurer.measure" -A2` i sprawdź klucze `remember`.
- AM-809 (2026-11-05): `WindowWorld` składa okno bez `WindowControllers`, więc logika dopisana w `WindowControllers` (kolektor `hasTabs` → `mode.sync()`) nie ma żadnego testu okna — mutacja usuwająca ją przeżyła. Sprawdzaj każdy nowy `scope.launch`/kolektor w `WindowControllers` i czy `WindowWorld` go odtwarza.
- AM-809: wybór sesji spoza paska bocznego (popup Attach) wysłał tylko `MissionControlIntent.SelectSession` bez `ProjectsIntent.Select` — pasek boczny i MC pokazują różne projekty, a ponowny klik wybranego projektu nic nie robi (StateFlow nie emituje tej samej wartości). Wzorzec poprawny: `selectStartedSession` (AM-608). Potwierdzone sondą w kopii (`probe.txt`).
- AM-809: przeniesienie skrótów z tekstów (`action.attach=Attach  ⏎`) do `SessionKeyCaps` po cichu zabrało je z menu „⋯” (te same klucze). Przy usuwaniu fragmentu z klucza tekstu grepuj wszystkie miejsca użycia klucza.
- AM-809: plan wymienił `TabStatusLookTest`, kodu nie ma; testy pikselowe (kolor, różne kształty) nie przypinają, która ikona należy do stanu — zamiana WAITING↔DONE przeżyła. Testy „kształty parami różne” zawsze uzupełnij tabelą mapowania.
- AM-809: znów ROADMAP `[ ]` (siódmy raz w M8); `QuaysideApp` urósł z 7 do 10 parametrów pod istniejącym `@Suppress("LongParameterList")` — suppress nie zwalnia z ENG-C2.
- AM-809 (2. review): po refaktorze (kolektor `activeKey` z `QuaysideApp` → `WindowModeState.follow`) plan nadal opisywał stary mechanizm w akapicie „Układ”, a nowe testy okna nie trafiły do listy „Testy” — dopisanie „Decyzji podjętych w trakcie” nie wystarcza, sprawdzaj akapity opisowe. Mutacje równoważne (nie zgłaszać): kolejność `selectProject`/`SelectSession` (`Selection.onProject` to no-op dla tego samego projektu), warunek w `sync()` przy `distinctUntilChanged`. Nowy plik testu bywa `??` (bez intent-to-add) obok plików `A` — przypomnij o `git add`.
- AM-808: zadanie od leada nazwało sekcję mockupu („PODSUMOWANIE”), która w HTML jest czym innym (podsumowanie sesji); kafelki liczników są pod komentarzem `<!-- Liczniki -->`. Sprawdzaj sekcję w pliku, nie po nazwie z zadania.
- AM-803 (2026-11-05): fonty terminala rejestrowane w AWT po starcie (kolektor `looks` na Default) — `JediTermTerminalApi.installedFonts` jest `lazy`, więc font zarejestrowany po pierwszej karcie jest niewidoczny; do tego rejestrowano tylko font typografii, a nie font wybrany w „Font terminala”. Przy każdej zmianie fontów/terminala grepuj `installedFonts` i `registerFont`.
- AM-803: znów `@Suppress("LongParameterList")` na konstruktorze (AppModules 8) zamiast obiektu parametrów (wzorzec `TerminalDeps`/`NotificationDeps`); ROADMAP `[ ]` ósmy raz. Pętle „kilka przebiegów” (`ACCENT_PASSES`) i `FocusRequester` „na wszelki wypadek” (`PreviewBypass.before`) — sprawdzaj mutacją, czy nie są martwe. Test „odcień zachowany” przez porządek R>G>B jest zawsze zielony dla mieszania z bielą/czernią.
- AM-803 (2. review): po poprawce `ACCENT_PASSES = 1` nadal przeżywa wszystkie testy (impl, ScreenContrastTest, design-system), a KDoc twierdzi „jeden przebieg daje 2.92:1” — liczby z KDoc/planu uzasadniające pętlę weryfikuj mutacją. Etykiety `contentDescription` przycisków „−”/„+” (NFR-6) bez testu — mutacja usunięcia przeżywa. Plan po uproszczeniu kontrolera dalej mówił „zwolnienie w `finally`”. Mutacja `dropLast(1)` na liście fontów jest równoważna (ostatni = Menlo, systemowy) — testuj `drop(1)`.
- AM-803 (poprawka modali nad SwingPanel, 2026-11-05): test prawdziwego `ComposeWindow` sprawdzał tylko proxy (`focusOwner() !== area`, hit test), nie zgłoszone zachowanie (Esc/Anuluj) — mutacja fokusu na `JRootPane` przeżyła, a sonda z `KeyEvent(VK_ESCAPE)` do `focusOwner` ją zabija; stan `dismissed` w teście był martwy. Do tego `FocusActivePane` z kluczem `modal` oddawał fokus terminalowi po zamknięciu każdego modalu — w trybie listy terminal leży pod Mission Control, więc fokus listy przechodzi do powłoki (⏎ w shellu); oba warunki (`if (modal) return`, klucz `modal`) bez testu. Dopasowanie `SkiaLayer` po prefiksie nazwy łapie anonimowe `SkiaLayer$1` (backedLayer).
  **How to apply:** przy poprawkach fokusu AWT/Compose każ testować dokładnie zgłoszony klawisz/klik i sprawdzaj, czy „oddanie fokusu po zamknięciu” pamięta poprzedniego właściciela.
- AM-804 (2026-11-05): log „id spoza katalogu” w `loadStart` po serializerze, który już podmienia id na domyślne — w produkcji martwy (sonda z prawdziwym `JsonStorage`: 0 WARN), a test na `InMemoryJsonStore` omija serializer i jest zielony. Przy „loguj X przy odczycie” sprawdzaj, czy filtr nie stoi wcześniej w łańcuchu (serializer/decoder).
  **How to apply:** sonda w kopii: prawdziwy `JsonStorage` + plik z błędną wartością + `RecordingLogger`.
- AM-804: sprzątanie pliku tymczasowego w `finally` atomowego zapisu bez testu — test „katalog tylko do odczytu” nie tworzy pliku tymczasowego wcale. Ścieżka, która tworzy temp i pada na `move`: cel = niepusty katalog. Czyszczenie `notice` przy edycji też bez testu (mutacja przeżyła).
- AM-804: plan „nowe zadanie na własne schematy, jeśli decyzja 2 = tak” (decyzja zapadła), a ROADMAP bez nowego wpisu — przy decyzjach „wydzielić do osobnego zadania” grepuj ROADMAP.
- AM-812 (2026-11-05): ROADMAP bez `[x]` i bez wpisu na wydzielony zakres (wybór dźwięku/głośności, pytanie 2), a „Uwagi do M7” dalej mówią „`SOUND -> Unit`”. Plan „bez `notifications:ui`”, a zmieniono 3 testy ui + test okna app (zmiana domyślnej w `api` przepisuje fikstury w każdym module) — wymagaj listy w „Moduły”/„Odstępstwa”.
- AM-812: zmiana domyślnej w `api` przy pliku, który zapisywał wszystkie pola, wymusiła pierwszą migrację schematu i nową semantykę „zapis tylko odstępstw” — decyzja o formacie pliku w „Odstępstwach” bez ADR, choć plan mówił „ADR niepotrzebny”. Test „dane o złym kształcie nie psują migracji” jest zawsze zielony, gdy serializer i tak odrzuca te dane (oba wyjątki → `.broken` + domyślne).
- AM-812: kolejka odtwarzania na `Channel.UNLIMITED` liczy chłodzenie od wysłania, nie od startu odtwarzania (KDoc mówi „od startu”); przy zawieszonym odtwarzaczu (timeout 10 s) zaległości rosną i grają bez ponownego sprawdzenia ciszy nocnej.

Powiązane: [[reviewer-workflow]]
