# Backlog pomysłów

Luźna lista pomysłów na przyszłość. To **nie jest zakres**: żaden z wpisów nie ma konceptu, wymagań ani wpisu w [ROADMAP.md](ROADMAP.md). Gdy pomysł dojrzeje, trafia do wymagań (`02-wymagania.md`) i roadmapy w osobnym PR.

Format wpisu: krótki opis, bez rozwiązań.

## 1. Wiele kont Claude

Przeniesione do zakresu (2026-11-09): wymagania FR-L1–FR-L6 w [02-wymagania.md](02-wymagania.md), zadania M10 w [ROADMAP.md](ROADMAP.md), plan w [ADR 0029](adr/0029-wiele-kont-claude.md).

## 2. Import i eksport konfiguracji

Możliwość wyeksportowania całej konfiguracji aplikacji do pliku i zaimportowania jej na innym komputerze lub po reinstalacji.

## 3. Obsługa sesji z telefonu

Podgląd i obsługa sesji agentów zdalnie z telefonu: lista sesji, stan, odpowiedź agentowi, który czeka. Telefon łączy się bezpośrednio z aplikacją na macOS albo przez serwer pośredniczący (relay), gdy Mac nie jest osiągalny bezpośrednio. W 1.0 jest to poza zakresem (`01-wizja-i-zakres.md`, sekcja „Poza zakresem”).

## 4. Sandbox Docker dla agenta

Uruchamianie agenta Claude Code w kontenerze Docker ze wszystkimi uprawnieniami (bez pytań o zgodę), odizolowanego od hosta, z dostępem do git. Wstępny kierunek i ryzyka: [ADR 0028](adr/0028-sandbox-docker-dla-agenta.md) (proponowana).

## 5. Inni agenci obok Claude Code

Możliwość podpinania do aplikacji innych agentów kodujących (np. Codex) obok Claude Code, tak by dało się nimi zarządzać z jednego miejsca.

## 6. Analiza wykorzystania tokenów i AI

Przycisk uruchamiający analizę sesji dla jednego projektu albo dla wszystkich projektów: wykorzystanie tokenów i AI, przepływy pracy agentów oraz podpowiedzi, jak pracować lepiej. Możliwe źródło danych: logi sesji zbierane hookami.

## 7. Podpinanie sesji zdalnych

Możliwość podpinania sesji Claude uruchamianych zdalnie, np. na zewnętrznych serwerach, tak by były widoczne i obsługiwane w aplikacji obok sesji lokalnych.

## 8. Panel kontekstu sesji

Mały panel po prawej stronie, pod informacjami o sesji, który pokazuje, ile kontekstu sesja zużyła, jak działa pamięć podręczna promptu (cache) i podpowiada, co zrobić, żeby zmniejszyć zużycie tokenów i nie przepalać limitów. Źródłem są oficjalne strony dokumentacji Claude Code: [Manage costs](https://code.claude.com/docs/en/costs), [Prompt caching](https://code.claude.com/docs/en/prompt-caching) i [Status line](https://code.claude.com/docs/en/statusline) (sprawdzone 2026-11-10).

### Co pokazuje dokumentacja o kontekście

- Claude Code przy każdym zapytaniu wysyła całą rozmowę od nowa. Cache sprawia, że niezmieniony początek rozmowy jest czytany taniej, a przetwarzany od zera tylko nowy fragment.
- Cache wygasa po przerwie: 5 minut albo 1 godzina. Godzinę dostaje główna rozmowa na subskrypcji w ramach planu, subagenci i kompaktowanie dostają 5 minut. Pierwsza wiadomość po dłuższej przerwie przelicza cały kontekst od zera, więc jest wolniejsza i droższa.
- Cache unieważniają: zmiana modelu, zmiana poziomu wysiłku (na niektórych modelach), włączenie trybu fast, podłączenie lub usunięcie serwera MCP (gdy narzędzia nie są ładowane na żądanie), włączenie wtyczki z serwerem MCP, zablokowanie całego narzędzia, kompaktowanie, nagromadzenie wielu obrazów i aktualizacja Claude Code. Każda sesja ma osobny cache dla modelu.
- Nie unieważniają cache: edycja plików w repozytorium, zmiana trybu uprawnień, zmiana stylu odpowiedzi, wywołanie skilla, `/recap`, `/rewind`, uruchomienie subagenta. Edycja `CLAUDE.md` w trakcie sesji nic nie psuje, ale też nie działa do czasu `/clear`, `/compact` albo restartu.
- Zalecenia z dokumentacji: `/clear` przy przejściu do niezwiązanego zadania (wcześniej `/rename`, potem `/resume` wraca do sesji), `/compact` na naturalnych przerwach, z instrukcją, np. `/compact Focus on code samples and API usage`, albo sekcją „Compact instructions” w `CLAUDE.md`; `/rewind` zamiast kompaktowania, gdy trzeba porzucić ślepą uliczkę; model i poziom wysiłku wybrać na początku sesji; zbędne serwery MCP wyłączyć; gadatliwe operacje (testy, logi, dokumentacja) delegować subagentom; `CLAUDE.md` krótki (do ok. 200 linii), szczegółowe instrukcje w skillach; konkretne prompty zamiast „popraw projekt”; tryb planowania przy większych zadaniach.
- Kompaktowanie samo jest dużym zapytaniem: tanie, gdy cache jest ciepły, drogie po długiej przerwie (zwłaszcza przy wznowieniu starej sesji). Jest też „kompaktowanie w bezczynności” dla rozmów na godzinnym TTL (ustawienie `idleCompaction`). Auto-kompaktowanie ma konfigurowalny próg (`/autocompact`, `CLAUDE_CODE_AUTO_COMPACT_WINDOW`); okno kontekstu to 200 tys. albo 1 mln tokenów zależnie od modelu.
- Hooki: `PreCompact` (trigger `manual` albo `auto`) może zablokować kompaktowanie, `SessionStart` ze źródłem `compact` pozwala wstrzyknąć kontekst po kompaktowaniu.

### Podpowiedzi, które zmniejszają zużycie tokenów

Głównym celem panelu są podpowiedzi pojawiające się w odpowiednim momencie, a nie same liczby. Podpowiedź jest zawsze widoczna w panelu (kolor ostrzegawczy, gdy oszczędność jest duża), ma krótką treść z liczbą tokenów, której dotyczy, i **od razu przycisk akcji**. Każda wynika z sygnału, który Quayside może zmierzyć, i z zalecenia z dokumentacji. Oszczędności są jakościowe: dokumentacja nie podaje, ile dokładnie zaoszczędzi dana zmiana.

| Sygnał w sesji | Podpowiedź | Przycisk | Dlaczego oszczędza |
| :- | :- | :- | :- |
| Kontekst zajęty w ponad ok. 70–80% okna albo sesja trwa wiele godzin | Kontekst jest duży, a każda wiadomość wysyła go w całości | **Kompaktuj** (z polem na instrukcje, co zachować) | Krótszy kontekst obniża koszt każdej następnej wiadomości |
| Cache wygaśnie za chwilę (np. zostały 1–2 minuty z 5, a użytkownik wygląda na odchodzącego) | Teraz kompaktowanie jest tanie, bo czyta ciepły cache | **Kompaktuj teraz** | Po wygaśnięciu to samo kompaktowanie przetwarza całą historię od zera |
| Cache już wygasł, a kontekst jest duży | Następna wiadomość przeliczy około N tokenów od zera. Kompaktowanie teraz też przetworzy je raz, ale kolejne wiadomości będą na krótkim kontekście | **Kompaktuj** albo **Zacznij od nowa (`/clear`)** albo **Kontynuuj** | `/compact` po przerwie kosztuje całą historię jednorazowo, `/clear` nic nie kosztuje; opłaca się, jeśli sesja ma trwać dalej |
| Zmiana tematu (nowe zadanie bez związku z poprzednim) | Stary kontekst płaci się przy każdej wiadomości, a nic nie wnosi | **Zacznij od nowa** (`/rename`, potem `/clear`; wrócisz przez `/resume`) | Nic nie kosztuje, a usuwa zbędny kontekst |
| Sesja jest na pięciominutowym cache, a użytkownik robi przerwy | Cache nie przetrwa przerw | **Ustaw cache na 1 godzinę** (`promptCacheTtl` = `1h`; dotyczy API i kont na kredytach) | Cache przetrwa dłuższe przerwy; wyższa cena zapisu cache, więc tylko dla osób wracających po przerwach |
| Częste chybienia cache, a użytkownik właśnie zmienia model, wysiłek albo serwery MCP | Zmiana unieważni cache i następna wiadomość przeliczy wszystko | **Anuluj** albo **Zmień mimo to** (potwierdzenie przed akcją, np. przez hook `PreModelSwitch`) | Zmiana modelu czy wysiłku przelicza cały kontekst |
| Opus używany do prostych tur (krótkie odpowiedzi, mało narzędzi) | Sonnet wystarczy do większości zadań | **Przełącz na Sonnet** (najlepiej na początku zadania, bo zmiana modelu unieważnia cache) | Sonnet kosztuje mniej niż Opus |
| Wysoki poziom wysiłku przy prostych zadaniach | Rozumowanie jest liczone jak tokeny wyjścia | **Obniż wysiłek** (`/effort`) | Mniej tokenów rozumowania |
| Duże wyniki narzędzi (logi, wyniki testów, długie pliki) w kontekście | Gadatliwy wynik zostaje w kontekście do końca sesji | **Dodaj hook filtrujący** (gotowy wzorzec) albo wskazówka „deleguj subagentowi” | Do rozmowy wraca streszczenie albo tylko błędy |
| Wiele narzędzi z serwerów MCP w sesji | Każdy serwer dokłada wpisy do kontekstu | **Wyłącz nieużywane serwery** (otwiera `/mcp`) | Mniej definicji narzędzi i wpisów |
| Długi `CLAUDE.md` (np. ponad 200 linii) w projekcie | Jest wczytywany na starcie i płaci się go przy każdej wiadomości | **Otwórz `CLAUDE.md`** z podpowiedzią przeniesienia instrukcji do skilli | Skille ładują się tylko na żądanie |
| Wiele zrzutów ekranu albo plików PDF w rozmowie | Obrazy mocno zwiększają kontekst; Claude Code usuwa stare partiami, co też przelicza kontekst | Brak akcji, sama informacja | Mniej obrazów, mniej przeliczeń |
| Agent poszedł w złą stronę, kolejne wiadomości to poprawki | Cofnięcie jest tańsze niż nadbudowywanie | **Cofnij (`/rewind`)** | Rewind wraca do prefiksu, który jest już w cache, więc nic się nie przelicza |
| Duże zadanie bez planu | Plan unika drogiego przerabiania | **Włącz tryb planowania** (Shift+Tab) | Mniej pracy w złą stronę |
| Niejasny, ogólny prompt („popraw projekt”) | Szerokie przeszukiwanie czyta wiele plików | Brak akcji, sama wskazówka („wskaż plik i funkcję”) | Mniej odczytów |
| Włączone zadania cykliczne (`/loop`), zespoły agentów, wiele równoległych subagentów | Ta sesja zużywa tokeny także gdy stoi: co N minut wysyła cały kontekst | **Zatrzymaj** zadanie albo sesję | Zadania w tle wysyłają pełny kontekst przy każdym uruchomieniu; zespoły agentów zużywają około 7 razy więcej niż zwykła sesja (według dokumentacji, w trybie planowania) |
| Subagenci na modelu większym, niż potrzeba | Subagenci dziedziczą model sesji | **Ustaw `model: haiku`** dla prostych subagentów | Subagenci zużywają limity |

Przykład z życia: Anna odchodzi na godzinę od sesji z kontekstem 400 tys. tokenów. Dwie minuty przed końcem pięciominutowego cache panel pokazuje „Cache wygaśnie za 2 min: kompaktowanie teraz jest tanie” i przycisk **Kompaktuj teraz**. Jeśli nie zdąży, po powrocie panel pokazuje „Cache wygasł: następna wiadomość przeliczy około 400 tys. tokenów” i trzy przyciski: **Kompaktuj**, **Zacznij od nowa**, **Kontynuuj**.

Uwaga do pierwotnego założenia „kompaktuj, gdy cache minął”: według dokumentacji kompaktowanie jest samo dużym zapytaniem i po wygaśnięciu cache przetwarza całą historię jako zwykłe wejście (najdroższy przypadek, zwłaszcza przy wznowieniu starej sesji). Opłaca się wtedy tylko, gdy sesja ma trwać dalej, bo kolejne wiadomości idą już na krótkim kontekście. Najtaniej jest kompaktować, póki cache jest ciepły, albo zacząć od nowa, gdy zadanie się skończyło.

**Czego nie wiadomo, a decyduje o przyciskach.** Czy da się z zewnątrz wysłać do sesji w tle polecenia `/compact`, `/clear`, `/effort` czy `/model`. Wiadomo, że Quayside umie zatrzymać sesję, uruchomić ją ponownie i wejść do niej terminalem (`Attach`). Do sprawdzenia razem z wysyłaniem odpowiedzi do sesji (AM-1001): jeśli polecenia nie działają w tle, przyciski mogą wstawić polecenie w terminal po `Attach` albo prosić użytkownika o wykonanie go.

**Co panel pokazuje obok podpowiedzi:** wynik `/usage` (procent zużycia przypisany do skilli, subagentów, wtyczek i serwerów MCP, z rekomendacjami dokumentacji, np. „Trim CLAUDE.md”) oraz raport `/insights` (analiza ostatnich sesji). Oba polecenia liczą dane lokalnie. Panel pokazuje najważniejsze wiersze i otwiera pełny wynik.

Zasady, żeby podpowiedzi nie męczyły: każda ma próg, naraz widać najwyżej jedną główną i dwie dodatkowe, da się ją zamknąć i wyłączyć na stałe, nic nie robi sama bez zgody (przycisk wymaga kliknięcia), a tekst podpowiedzi nie zawiera treści rozmowy.

### Skąd wziąć liczby dla sesji w Quayside

- Skrypt `statusLine` dostaje na stdin m.in. `context_window` (zajęte tokeny, rozmiar okna, procent zajęcia, `current_usage` z podziałem na cache read i cache write) oraz obiekt `prompt_cache` (współczynnik trafień, chybienia, czy cache jest ciepły). Dla sesji w tle nie wiadomo, czy ten skrypt jest uruchamiany: to samo pytanie jest w AM-1001.
- Plik sesji (`~/.claude/projects/…/*.jsonl`) zawiera przy każdej odpowiedzi asystenta `usage` z liczbami `input_tokens`, `cache_creation_input_tokens`, `cache_read_input_tokens` i `output_tokens` (sprawdzone na komputerze autora 2026-11-10: 209 odpowiedzi z `usage` w jednym pliku). Zajęcie kontekstu to suma trzech pierwszych liczb z ostatniej odpowiedzi. Działa także dla starych sesji (pomysł z AM-311), ale nie mówi, czy cache jest jeszcze ciepły.

### Możliwe elementy panelu (bez ustalonego kształtu)

- Pasek zajęcia kontekstu z liczbą tokenów i procentem okna, kolor ostrzegawczy przy zbliżaniu się do progu auto-kompaktowania.
- Stan cache: ciepły albo zimny, ile minut zostało do wygaśnięcia, współczynnik trafień z sesji, ostrzeżenie „po przerwie następna wiadomość przeliczy cały kontekst”.
- Podpowiedzi zależne od sytuacji, np. „zmiana modelu teraz unieważni cache”, „kontekst ma 80%, rozważ kompaktowanie na przerwie”, „zmiana zadania: `/clear` zamiast kontynuacji”, „sesja stoi dłużej niż TTL: pierwsza wiadomość będzie droga”.
- Przycisk „Kompaktuj” z polem na instrukcje dla kompaktowania. Nie wiadomo jeszcze, czy da się wysłać `/compact` do sesji w tle z zewnątrz; do sprawdzenia razem z wysyłaniem odpowiedzi do sesji.
- Dla zespołów i kont: ten sam widok zużycia na koncie (limit 5-godzinny i tygodniowy) obok kontekstu, jeśli skrypt `statusLine` je podaje (pole `rate_limits`).
