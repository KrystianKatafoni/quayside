# ADR 0024: Format `notifications.json`: schemat 2, zapis tylko odstępstw

- **Status:** proponowana
- **Data:** 2026-11-05
- **Dotyczy:** `feature:notifications:impl` (`NotificationSettingsSerializer`, `NotificationSettingsMigration`), `feature:notifications:api` (`defaultChannels`), plik `notifications.json`, `docs/07-powiadomienia.md`

## Kontekst

AM-812 zmienia domyślne kanały zdarzenia „Agent skończył turę” (`TURN_ENDED`) z samego popupu na popup + dźwięk (decyzja człowieka z 2026-11-05). Dotychczasowy serializer zapisywał kanały wszystkich rodzajów zdarzeń, więc każdy zapisany `notifications.json` zawiera `"turnEnded": ["popup"]`, czyli starą domyślną jako jawny wybór. Bez migracji nowa domyślna nie dotarłaby do nikogo, kto kiedykolwiek zapisał ustawienia, a to oni nie usłyszeliby końca pracy agenta, czyli wady, którą zadanie ma naprawić. Format pliku i tak jest decyzją trwałą (docs/11 §8).

## Decyzja

1. Schemat pliku rośnie do 2. Migracja 1 do 2 usuwa tylko `channels.turnEnded`, gdy jest zbiorem równym `{popup}` (powtórzenia bez znaczenia). Inne wartości (`[]`, `["popup","voice"]`, `["sound"]`…) i inne rodzaje zostają bez zmian; dane o nieoczekiwanym kształcie migracja zostawia, a serializer odrzuca je jak dotąd (plik trafia do `notifications.json.broken`).
2. Od schematu 2 `channels` zawiera tylko rodzaje, których kanały różnią się od `NotificationSettings.defaultChannels`. Brak klucza oznacza bieżącą domyślną.
3. Sekcja `sound` (AM-815: `volume`, `sounds` per rodzaj) jest opcjonalna, zapisywana tylko przy odstępstwach od domyślnych; nazwa dźwięku niebędąca poprawną nazwą pliku jest pomijana. Schemat zostaje 2.
4. Plik nowszy niż schemat 2 daje `NEWER_SCHEMA` (bez zapisu), jak dotąd.

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| A. Migracja + zapis tylko odstępstw (wybrana) | Domyślna dociera do wszystkich; kolejne zmiany domyślnych bez migracji | Wybór „tylko popup” sprzed migracji przepada |
| B. Sama migracja, zapis wszystkich rodzajów | Mniejsza zmiana serializera | Każda następna zmiana domyślnej wymaga kolejnej migracji |
| C. Sam zapis tylko odstępstw, bez migracji | Najprostsze | Istniejące pliki zostają z `["popup"]`, więc człowiek nie słyszy końca tury |
| D. Pozostawić domyślną bez zmian | Brak ryzyka | Wada z AM-812 zostaje |

## Konsekwencje

- Każda przyszła zmiana `defaultChannels` dociera do wszystkich, którzy nie nadpisali danego rodzaju, bez migracji.
- Świadomy wybór „tylko popup” sprzed migracji jest nie do odróżnienia od starej domyślnej i też dostaje dźwięk; użytkownik wyłącza go w macierzy, od tej chwili jest zapisany jako odstępstwo.
- Starsza wersja aplikacji (schemat 1) odczyta plik schematu 2 jako `NEWER_SCHEMA`: użyje domyślnych i nie zapisze ustawień powiadomień. Plik po migracji nie ma kopii schematu 1.
- Zmiana znaczenia istniejącego pola wymaga `schemaVersion = 3` i kolejnej migracji.
