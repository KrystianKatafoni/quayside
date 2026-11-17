# ADR 0036: Klucz sesji przy wielu kontach (etap AM-1003)

- **Status:** proponowana (wariant A wybrany przez człowieka 2026-11-17; status „przyjęta” nadaje człowiek przy scaleniu PR AM-1003)
- **Data:** 2026-11-17
- **Dotyczy:** `feature:agent-runtime:api` (`RuntimeSession`), `feature:sessions:api` (`Session`, `NewSessionSpec`), `feature:sessions:impl` (scalanie list), `app`; plan [AM-1003](../plans/AM-1003.md)
- **Rozszerza:** [ADR 0029](0029-wiele-kont-claude.md), punkt 3 (nie zastępuje go)

## Kontekst

ADR 0029 (punkt 3) mówi, że kluczem sesji w Quayside jest para (konto, krótkie ID), bo każde konto ma własnego supervisora, a krótkie ID jest unikalne tylko w jego obrębie. Pomiar AM-1001 (`docs/compat.md`, punkt 5) pokazał, że `--resume` na innym koncie daje **to samo** krótkie ID. Kolizja jest więc pewna dopiero przy przenoszeniu sesji (AM-1007). Bez przenoszenia dwa konta zderzają się z prawdopodobieństwem rzędu 10⁻⁶ (8 znaków szesnastkowych UUID).

`SessionId` jest adresem sesji w `sessions:api` (`SessionCommands`, `SessionMonitor`, `SessionEvent`, `SessionError`), a także w `notifications:api` i w całym `mission-control:ui` (57 plików `src/main`). Pełny klucz w API wymaga zmian w `api` innych feature'ów. Tych modułów nie ma na liście zadania AM-1003.

## Decyzja

1. **AM-1003:** `RuntimeSession` i `Session` dostają pole `account: AccountId` (domyślnie `AccountId.DEFAULT`). `SessionId` zostaje adresem w `sessions:api`. `sessions:impl` scala listy supervisorów po kluczu (konto, ID). Jeśli to samo krótkie ID przyjdzie z dwóch kont, na liście zostaje sesja konta wcześniejszego w kolejności (konto domyślne pierwsze, potem kolejność z `accounts.json`). Pozostałe trafiają do logu jako liczba kolizji. Polecenia trafiają do supervisora konta sesji.
2. **Przed AM-1007:** osobne zadanie (propozycja AM-1007a w roadmapie, M10) wprowadza pełny klucz sesji w API (`SessionKey(account, id)` albo `SessionId` z kontem) w `sessions:api`, `notifications:api/impl`, `mission-control:ui` i `app`, z migracją `session-history.json`, jeśli będzie potrzebna.
3. Dane zapisane przed zmianą (ukryte sesje, przypisane miejsca) mają klucz bez konta i należą do konta domyślnego. Format plików się nie zmienia.

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| A. Pole `account` + deduplikacja, pełny klucz przed AM-1007 (wybrana) | Mieści się w modułach AM-1003; bez zmian formatu plików; AM-1009 dostaje `Session.account` | Do AM-1007 jedna z dwóch sesji o tym samym ID jest niewidoczna; zmiana API rozłożona na dwa zadania |
| B. `SessionKey(account, id)` w `sessions:api` od razu | Zgodne dosłownie z ADR 0029 | Zmienia `notifications:api`, `mission-control:ui`, `core:domain` (poza listą); szacunkowo ponad 1500 linii; migracja `session-history.json` |
| C. Konto zakodowane w wartości `SessionId` (`work:7c5dcf5d`) | Brak zmian typów | Obejście: `SessionId` przestaje być ID supervisora; `attach`, logi i ID komend palety dostają dane spoza CLI |

## Konsekwencje

- Decyzje człowieka 2026-11-17: wariant A; zgoda na pola `RuntimeSession.account`, `Session.account` i `NewSessionSpec.account` (ostatnie w konstruktorze, domyślnie `AccountId.DEFAULT`, bez nowych wariantów `SessionError` i `RuntimeProblem`).
- AM-1009 (plakietka konta, wybór konta) korzysta z `Session.account` i `NewSessionSpec.account` bez zmian w `sessions:api`.
- AM-1007 jest zablokowane do czasu zadania z punktu 2, dopisanego do roadmapy (M10) jako propozycja AM-1007a. Zmianę zależności AM-1007 na AM-1007a zatwierdza człowiek.
- Logi kolizji zawierają tylko liczbę i ID kont, nigdy ścieżki (ENG-C8).
