# ADR 0034: Historia rozmowy z pliku sesji czytana od końca

- **Status:** przyjęta (decyzja człowieka 2026-11-11: opcje a dla agent-runtime:api i sessions:api)
- **Data:** 2026-11-11
- **Dotyczy:** `feature:agent-runtime:api` (`SessionHistory.conversation`, `PastConversation.name`, `PastConversation.lastPrompt`), `feature:agent-runtime:impl` (`BackwardLines`, `ConversationEntries`, `FileSessionHistory`), `feature:sessions:api` (`SessionMonitor.conversation`), `feature:mission-control:ui`, `docs/compat.md`
- **Rozszerza:** ADR 0032 (nie zastępuje go)

## Kontekst

AM-315: sesja z dysku (FR-P9) jest „zatrzymana” i poza pierwszą wiadomością nic o niej nie wiadomo, a podgląd (spacja) jej nie pokazuje, bo `recentOutput` (`claude logs`) działa tylko dla zadań supervisora. ADR 0032 pozwala czytać najwyżej 64 KiB z początku i 64 KiB z końca pliku i tylko metadane. Pliki mają do 163 MB (pomiar AM-311), pojedyncza linia (wynik narzędzia) może mieć kilka MB, a treść rozmów to dane użytkownika (ENG-C8). Pomiar M7 (2.1.322): wpisów `summary` nie ma, wpis `custom-title` (pole `customTitle`) jest w 86 ze 132 plików.

## Decyzja

1. **Nazwa i podsumowanie z głowy i ogona (bez zmiany budżetu ADR 0032).** `PastConversation.name` = `customTitle` z ostatniego wpisu `custom-title` (ogon wygrywa z głową); `PastConversation.lastPrompt` = ostatnia prawdziwa wiadomość `user` w ogonie (te same reguły co tytuł: bez `isMeta`, komend `<command-`/`<local-command-` i samych `tool_result`; ≤ 80 code pointów). Wpisy `summary`, `agent-name` i nieznane typy są pomijane. Bez modelu i sieci.
2. **Historia rozmowy na żądanie, od końca, porcjami.** Nowa metoda portu `SessionHistory.conversation(id, startCwd, before, limit)`: plik `projects/<dirNameOf(startCwd)>/<uuid>.jsonl`, czytany od pozycji `before` (albo końca) wstecz porcjami po 64 KiB, cięty na linie po bajcie `\n` (w UTF-8 nie występuje wewnątrz znaku wielobajtowego), każda linia dekodowana osobno. Zwraca najwyżej `limit` wiadomości (domyślnie 20 w `sessions:impl`) od najstarszej oraz kursor `HistoryCursor(offset)`; `null`, gdy odczyt doszedł do początku pliku. Zwykle jest to początek linii najstarszej zwróconej wiadomości, ale kursor bywa też początkiem linii bez wiadomości (pominiętej) albo pozycją w środku pomijanej linii (pkt 3); kolejne wywołanie z takim kursorem pomija częściową resztę tej linii i idzie dalej bez luki i bez powtórzenia.
3. **Twarde limity pamięci:** linia dłuższa niż 1 MiB jest pomijana bez trzymania jej w pamięci (szukamy tylko jej początku); budżet 8 MiB jest sprawdzany przy każdej porcji (`refill()`), także w środku linii, którą i tak pomijamy (ponad 1 MiB albo częściowa pierwsza); wtedy czytnik staje na początku ostatniej porcji. Linia poniżej 1 MiB, która trwa na granicy budżetu, jest doczytywana do końca, aby jej wiadomość nie zginęła. Górna granica odczytu jednego wywołania: 8 MiB + do 1 MiB (taka doczytywana linia) + jedna porcja 64 KiB; potem wywołanie zwraca to, co ma, z kursorem. Tekst wiadomości jest przycinany do 4 000 code pointów (`truncated = true`).
4. **Bezpieczeństwo ścieżki:** `ConversationId` musi mieć postać UUID (małe litery szesnastkowe), inaczej `NOT_FOUND` bez dotykania dysku; tylko zwykły plik. Port nadal tylko czyta (ADR 0032, pkt 1).
5. **Pokazane są tylko teksty** wiadomości `user` i `assistant` (części `text`); `tool_use`, `tool_result`, `thinking`, wpisy `isMeta`, `isSidechain` i komendy są pomijane. Historia jest tylko do odczytu.
6. **Logi:** liczba wiadomości i kategoria błędu (bez liczby pominiętych linii: nie zmieniamy API ani wyniku strony tylko dla logu); nigdy tekst, ścieżka ani UUID obok treści. `toString` typów wiadomości ukrywa tekst.

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| A. Odczyt od końca porcjami z kursorem bajtowym (wybrana) | Koszt zależny od liczby wiadomości, nie od rozmiaru pliku; plik tylko dopisywany, więc kursor jest stabilny | Własny czytnik linii od końca; kursor nieważny, gdy plik zostanie przepisany (wtedy błąd `UNREADABLE`) |
| B. Indeks linii całego pliku przy pierwszym odczycie | Proste stronicowanie | Odczyt do 163 MB przy pierwszym otwarciu |
| C. `claude logs` dla wznowionej sesji | Bez znajomości formatu | Wymaga wznowienia (AM-313), zmienia stan sesji |
| D. Podsumowanie modelem (`claude -p`) | Lepsze streszczenie | Sieć, koszt, zgoda użytkownika; osobne zadanie |

## Konsekwencje

- Treść rozmów trafia do pamięci i UI (tylko porcja, tylko na żądanie przy zaznaczeniu sesji z dysku); nie trafia do logów ani plików Quayside.
- Zmiana formatu wpisów (`custom-title`, `message.content`) daje brak nazwy albo pustą historię, nie błąd okna; wykrywa to `/compat-check` (wiersz w `docs/compat.md`).
- Kursor zakłada, że Claude Code tylko dopisuje do pliku (pomiar R1–R2); przepisany plik daje błąd przy „Wczytaj starsze”, ponowne zaznaczenie czyta od końca.
- Historia sesji supervisora (także zatrzymanych) zostaje przy `recentOutput`; rozszerzenie na nie to osobna decyzja.
