# ADR 0018: Wykrywanie aktywnego mikrofonu przez CoreAudio i JNA

- **Status:** proponowana
- **Data:** 2026-11-02
- **Dotyczy:** `feature:notifications:impl` (AM-703 i zadanie następcze), `gradle/libs.versions.toml`

## Kontekst

FR-N5 wymaga wyciszenia głosu, gdy mikrofon jest w użyciu (rozmowa, nagranie); docs/07 włącza to domyślnie. macOS nie ma polecenia CLI, które to podaje: informację zwraca CoreAudio, właściwość `kAudioDevicePropertyDeviceIsRunningSomewhere` domyślnego urządzenia wejściowego (`kAudioHardwarePropertyDefaultInputDevice`). Aplikacja jest w JVM, bez App Sandbox (NFR-1). JNA jest dziś w projekcie tylko przechodnio (przez pty4j w `terminal:impl`).

AM-703 wprowadza port `MicrophoneMonitor` (`active: StateFlow<Boolean>`) z implementacją „nigdy aktywny”. Ten ADR decyduje, czym go wypełnić.

## Decyzja

Adapter `CoreAudioMicrophoneAdapter` w `notifications:impl` woła `AudioObjectGetPropertyData` z frameworka CoreAudio przez **JNA** (bezpośrednia zależność `net.java.dev.jna:jna` w katalogu wersji, ta sama wersja co przechodnia z pty4j). Odczyt co 2 s na dispatcherze IO, tylko gdy głos i przełącznik „wycisz przy mikrofonie” są włączone; zmiana domyślnego urządzenia jest odczytywana przy każdym odpytaniu. Każdy błąd natywny (także `LinkageError`) daje „nieaktywny” i jeden `warn`, więc awaria wykrywania nigdy nie wycisza głosu na stałe ani nie zatrzymuje aplikacji.

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| A. JNA + CoreAudio (decyzja) | Bez dodatkowych plików wykonywalnych; JNA już jest w aplikacji | Nowa bezpośrednia zależność; kod natywny trudny do testów w CI (test integracyjny tylko na Macu) |
| B. Pomocnik w Swift dołączony do aplikacji | Czyste API Apple | Drugi język i build, podpis i notaryzacja dodatkowego pliku |
| C. Parsowanie `ioreg` / `log stream` | Bez natywnego kodu | Nieudokumentowane, zależne od sprzętu (Apple Silicon), wolne |
| D. Brak wykrywania (tylko przełącznik „mów tylko w tle”) | Zero kosztu | FR-N5 niespełnione w części |

## Konsekwencje

- Trzeba dopisać zadanie do roadmapy (adapter + test integracyjny z tagiem macOS) i wpis w `libs.versions.toml`.
- Do sprawdzenia przed przyjęciem: czy odczyt `DeviceIsRunningSomewhere` nie wywołuje pytania o zgodę na mikrofon (TCC). Jeśli wywołuje, wracamy do opcji D.
- Testy jednostkowe dalej używają fake'a portu; CI bez urządzenia audio dostaje „nieaktywny”.
