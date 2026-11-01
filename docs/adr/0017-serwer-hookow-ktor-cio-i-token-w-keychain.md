# ADR 0017: Serwer hooków na Ktor CIO, token w Keychain przez `security`

- **Status:** przyjęta
- **Data:** 2026-11-01
- **Dotyczy:** `feature:agent-runtime:impl` (AM-601), `gradle/libs.versions.toml`

## Kontekst

ADR 0004 przyjmuje zdarzenia z hooków HTTP, a NFR-4 wymaga nasłuchu wyłącznie na `127.0.0.1` z tokenem w nagłówku. docs/03 i docs/11 wskazują Ktor CIO i token w Keychain, ale żadna z tych decyzji nie ma jeszcze ADR-a, a Ktor to nowa zależność. Kod Ktor zna `agent-runtime:impl`; reszta aplikacji widzi tylko `AgentEventSource` z `api`.

## Decyzja

1. **Ktor server CIO** (`ktor-server-core`, `ktor-server-cio`, jedna wersja w katalogu) jako serwer; bez dodatkowych pluginów (bez content negotiation i bez klienta HTTP — treść parsuje `kotlinx.serialization` w czystym `HookPayloadParser`).
2. **Powierzchnia HTTP:** tylko `POST /hooks`, host `127.0.0.1`, port z konfiguracji (domyślnie 47321). Odpowiedzi to same kody bez treści, więc nic z wejścia nie wraca do nadawcy: 200 (przyjęty albo celowo pominięty), 400 (nieczytelny — w logu tylko stała kategoria), 401 (zły albo brak tokenu), 411 (brak `Content-Length`), 413 (> 1 MiB). Token porównywany w stałym czasie, sprawdzany przed odczytem treści.
3. **Token:** 256 bitów z `SecureRandom`, URL-safe Base64. Leży w Keychain (usługa `Quayside`, konto `hook-token`), czytany i zapisywany przez `/usr/bin/security` przez `ProcessRunner`, bez natywnej biblioteki. `add-generic-password` przyjmuje sekret wyłącznie jako argument `-w`, więc przez ułamek sekundy widać go w liście procesów tego samego użytkownika; przyjmujemy to, bo token chroni przed innymi lokalnymi procesami innych użytkowników i przypadkowymi nadawcami, a nie przed procesem tego samego użytkownika, który i tak czyta `~/.claude`. Token nie trafia do logów ani do `toString`.
4. **Cykl życia:** fabryka `hookEventSourceModule(…, scope, …)` zwraca `AgentEventSource` od razu i startuje serwer w podanym zakresie; anulowanie zakresu zatrzymuje serwer. Brak dostępu do Keychain albo zajęty port nie rzuca: błąd idzie do logu, strumień milczy, a polling pozostaje źródłem prawdy (ADR 0004).
5. **Strumień:** `MutableSharedFlow(extraBufferCapacity = 64)`, `tryEmit`; przy pełnym buforze zdarzenie ginie z ostrzeżeniem w logu, bo polling to naprawia.

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| JDK `com.sun.net.httpserver` | Bez zależności | Brak coroutines; docs/03 i docs/11 już wskazują Ktor |
| Token w pliku w katalogu danych | Prosto | NFR-4 i docs/11 §8 chcą Keychain |
| Natywne API Keychain (JNA) | Sekret nie trafia do argv | Nowa zależność natywna, trudna do testowania w CI |
| Zwracać treść błędu w odpowiedzi | Łatwiejsza diagnoza | Echo wejścia; hook i tak jej nie pokazuje |

## Konsekwencje

AM-602 (`HookInstaller`) musi znać token i port: czyta token z tego samego `HookTokenStore` (rozszerzenie fabryki lub nowy wewnętrzny typ w `impl`, bez zmiany `api`) i zapisuje `X-AM-Token` zgodnie z ADR 0010. Zmiana nazwy usługi albo konta w Keychain unieważnia zainstalowane hooki. Sposób przekazania tokenu do procesu `claude` (`allowedEnvVars` w docs/03) rozstrzyga AM-602.
