# 10 · Wymagania inżynierskie

Ten dokument określa, **jak** budujemy Quayside. Jest wiążący dla każdego kodu w repozytorium, niezależnie od tego, czy pisze go człowiek, czy agent Claude Code. Szczegóły architektury: [11-koncepcja-techniczna.md](11-koncepcja-techniczna.md). Szczegóły testów: [12-strategia-testow.md](12-strategia-testow.md).

**Cel nadrzędny: zmiana w jednym miejscu nie wymusza zmian w innych.** Każde wymaganie poniżej służy temu celowi albo czytelności kodu.

## A. Modułowość i hermetyczność

| ID | Wymaganie | Jak weryfikujemy |
| --- | --- | --- |
| ENG-M1 | Aplikacja jest **modularnym monolitem**: osobne moduły Gradle per funkcja (feature) i per warstwa wspólna (core) | Struktura repo, `settings.gradle.kts` |
| ENG-M2 | Każdy feature ma trzy moduły: **`api`** (kontrakt), **`impl`** (implementacja), **`ui`** (ekrany Compose). Feature bez UI nie ma modułu `ui` | Konwencja nazw, test Konsist |
| ENG-M3 | Moduł `impl` **nie jest widoczny** dla żadnego innego modułu poza `app` | Reguła Gradle w `build-logic` + test Konsist |
| ENG-M4 | Wszystkie klasy w `impl` są `internal`. Jedynym publicznym elementem jest **fabryka modułu** (np. `fun projectsModule(deps): ProjectsApi`) | Test Konsist |
| ENG-M5 | Moduł `api` zależy tylko od `core:domain`. Zależność `api → api` innego feature jest wyjątkiem i wymaga ADR | Test Konsist + przegląd |
| ENG-M6 | `core:domain` to czysty Kotlin: bez Compose, bez IO, bez bibliotek zewnętrznych poza `kotlinx.coroutines-core` i `kotlinx.serialization` (adnotacje) | Test Konsist (zakazane importy) |
| ENG-M7 | Brak zależności cyklicznych między modułami | Gradle (cykle nie budują się) |
| ENG-M8 | Wszystkie powiązania tworzy tylko moduł **`app`** (composition root). Żaden inny moduł nie tworzy implementacji innego feature | Test Konsist |
| ENG-M9 | Kod specyficzny dla systemu (procesy, `say`, CoreAudio, pliki, HTTP) siedzi wyłącznie w **adapterach** w `impl`, za portem zdefiniowanym w `api` lub w `impl` | Przegląd + nazewnictwo `*Adapter` |
| ENG-M10 | Feature komunikują się **tylko przez interfejsy `api`** (wywołania, `Flow`, `StateFlow`). Brak współdzielonego mutowalnego stanu i globalnych singletonów | Test Konsist (zakaz `object` z `var`) |

## B. Interfejsy modułów

| ID | Wymaganie |
| --- | --- |
| ENG-I1 | Każdy publiczny typ w `api` ma **KDoc**: co robi, gwarancje, błędy, wątki (na jakim dispatcherze można wołać) |
| ENG-I2 | Interfejsy są **małe i skupione** (zasada segregacji interfejsów): osobno odczyt (`SessionMonitor`) i komendy (`SessionCommands`) |
| ENG-I3 | Typy w `api` są **niemutowalne** (`data class` z `val`, `List` zamiast `MutableList`). Identyfikatory to `value class` (`ProjectId`, `SessionId`) |
| ENG-I4 | Błędy przekraczające granicę modułu to **typowane wyniki** (`Outcome<T, E>` z `sealed interface` błędów), nie wyjątki. Wyjątki tylko wewnątrz adapterów |
| ENG-I5 | Operacje IO to funkcje `suspend` lub `Flow`. Żadna metoda `api` nie blokuje wątku |
| ENG-I6 | Zmiana sygnatury w `api` to **zmiana łamiąca**: wymaga aktualizacji testów kontraktowych i wpisu w CHANGELOG modułu |
| ENG-I7 | `api` udostępnia **testowe implementacje (fake)** w `testFixtures`, żeby inne moduły testowały się bez `impl` |
| ENG-I8 | Typy specyficzne dla bibliotek (JediTerm, Ktor, kotlinx.serialization `JsonElement`) **nie wyciekają** do `api` |

## C. Clean code

| ID | Wymaganie |
| --- | --- |
| ENG-C1 | Styl: oficjalne Kotlin Coding Conventions, formatowanie ktlint (przez Spotless), analiza statyczna detekt z konfiguracją repo. **Zero ostrzeżeń** na gałęzi głównej |
| ENG-C2 | Limity: funkcja ≤ 30 linii, złożoność cyklomatyczna ≤ 10, plik ≤ 400 linii, klasa ≤ 300 linii, ≤ 5 parametrów (więcej → obiekt parametrów) |
| ENG-C3 | Zakazane: `!!`, `GlobalScope`, `runBlocking` poza `main` i testami, `lateinit` w logice domenowej, `println`, puste `catch`, „magiczne” stringi flag CLI poza adapterem |
| ENG-C4 | Nazwy mówią **co**, nie **jak**: `SessionStateReducer`, nie `Helper`/`Manager`/`Utils`. Porty z sufiksem `Port` tylko gdy to nie oczywiste; adaptery z sufiksem `Adapter` |
| ENG-C5 | **Funkcyjny rdzeń, imperatywna powłoka:** logika (reducery, polityki, parsery, walidatory) to czyste funkcje bez IO; IO tylko w adapterach |
| ENG-C6 | Współbieżność: structured concurrency, `CoroutineScope` i dispatchery **wstrzykiwane** (testowalne), `Clock` wstrzykiwany zamiast `Instant.now()` |
| ENG-C7 | Komentarze tłumaczą **dlaczego**, nie **co**. Decyzje architektoniczne zapisujemy w ADR (`docs/adr/NNN-*.md`) |
| ENG-C8 | Logowanie przez jedną abstrakcję (`Logger` w `core:domain`), strukturalne pola (`sessionId`, `projectId`), bez danych wrażliwych (tokeny, treść promptów tylko na poziomie DEBUG) |
| ENG-C9 | Konfiguracja i stałe w jednym miejscu per moduł; brak duplikacji (zasada DRY w obrębie modułu, ale **nie** kosztem nowej zależności między modułami) |
| ENG-C10 | Commity w formacie Conventional Commits; jedna logiczna zmiana na PR; PR ≤ 400 zmienionych linii kodu (bez testów i fixtures) |

## D. Testy

| ID | Wymaganie | Próg |
| --- | --- | --- |
| ENG-T1 | **Testy jednostkowe** dla każdej klasy z logiką. Czyste rdzenie testowane tablicowo (wiele przypadków w jednym teście parametryzowanym) | pokrycie linii ≥ 80% per moduł `impl`, ≥ 90% dla rdzeni |
| ENG-T2 | **Testy kontraktowe** dla każdego interfejsu `api`: jeden abstrakcyjny zestaw testów uruchamiany na fake i na prawdziwej implementacji | 100% interfejsów `api` |
| ENG-T3 | **Testy integracyjne** adapterów na prawdziwych zasobach lokalnych (plik tymczasowy, losowy port HTTP, fałszywy plik wykonywalny `claude`) | każdy adapter |
| ENG-T4 | **Testy funkcjonalne** całej aplikacji przez UI (Compose UI test) na fake'owym runtime agentów, scenariusze Given/When/Then mapowane na wymagania FR | każde kryterium akceptacji MVP |
| ENG-T5 | **Testy architektury** (Konsist): reguły zależności, widoczność, nazewnictwo, zakazane importy | uruchamiane w każdym buildzie |
| ENG-T6 | Testy są **hermetyczne i deterministyczne**: brak sieci, brak prawdziwego Claude Code, brak zegara systemowego (wirtualny czas), brak zależności od kolejności | flaky = błąd krytyczny |
| ENG-T7 | Testy E2E z prawdziwym Claude Code tylko nocą lub ręcznie, oznaczone tagiem `e2e`, poza bramką PR | – |
| ENG-T8 | Każdy błąd naprawiany jest **najpierw testem**, który go odtwarza | – |
| ENG-T9 | Czas: testy jednostkowe całego repo < 60 s, pełna bramka PR < 10 min | – |

## E. Bramki jakości (CI)

Każdy PR musi przejść: kompilację wszystkich modułów, Spotless (ktlint), detekt, testy jednostkowe, kontraktowe, integracyjne, funkcjonalne, testy architektury Konsist, progi pokrycia Kover. Gałąź `main` jest zawsze zielona i wydawalna.

## F. Definition of Done

Zmiana jest skończona, gdy:

- [ ] kod spełnia ENG-C1–C10 i przechodzi bramki CI,
- [ ] nowa logika ma testy jednostkowe, nowy interfejs ma testy kontraktowe, nowa funkcja użytkownika ma test funkcjonalny,
- [ ] publiczne API ma KDoc, a decyzje architektoniczne ADR,
- [ ] zmiana dotknęła **tylko modułów, które musiała** (opis PR wymienia zmienione moduły i uzasadnia każdy spoza feature),
- [ ] dokumentacja w `docs/` jest aktualna (wymagania, koncepcja, skróty klawiszowe),
- [ ] PR przejrzany (przez człowieka lub agenta review) z checklistą z tego dokumentu.

## G. Zasady dla agentów Claude Code w tym repo

Skoro aplikacja będzie budowana także przy pomocy agentów, repozytorium ma `CLAUDE.md` z:

- mapą modułów i regułami zależności z sekcji A,
- poleceniami: `./gradlew check` (pełna bramka), `./gradlew :feature:<x>:impl:test` (testy jednego modułu),
- zasadą: agent pracuje **w jednym feature naraz** i nie modyfikuje `api` innego feature bez wyraźnej prośby,
- zasadą: nowy kod tylko z testami (ENG-T1–T4), a przed zakończeniem pracy `./gradlew check`.
