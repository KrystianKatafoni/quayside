# ADR 0015: Skróty klawiszowe jako dodatek, nie fundament obsługi

- **Status:** proponowana
- **Data:** 2026-10-31
- **Dotyczy:** mission-control:ui, command-palette:ui, projects:ui, app; wymagania FR-K1–K3, NFR-6; docs 01, 02, 04, 05

## Kontekst

Wybrany w [04](../04-koncepcje-ux.md) kierunek łączył Mission Control z koncepcją Keyboard-first: paleta ⌘K, skróty w stylu vima (j/k, ⏎, r, x, space, g p) i pasek trybu `NORMAL`. Wizja ([01](../01-wizja-i-zakres.md)) zakładała, że cała obsługa jest możliwa z klawiatury, a FR-K2 miało priorytet M.

Przy przenosinach projektu do repo Quayside (2026-10-31) właściciel uznał, że szybka klawiatura nie jest głównym wyróżnikiem produktu. Wartością jest to, że użytkownik widzi wszystkich agentów w jednym oknie i nie przegapi tego, który na niego czeka. Skróty z M4 i M5 (AM-406–411, AM-505, AM-506) już działają i zostają.

Dziś panel szczegółów ma przyciski Zatrzymaj, Respawn, Usuń i Podgląd, ale attach (`⏎`) i odpowiedź (`r`) są dostępne tylko skrótem albo z palety, wiersz sesji nie ma menu kontekstowego, a opisy w docs traktują klawiaturę jako podstawową drogę obsługi.

## Decyzja

Rdzeń produktu to przegląd sesji, powiadomienia i terminal. Każda akcja na sesji i projekcie ma widoczną kontrolkę dla myszy: przycisk w panelu szczegółów albo pozycję menu kontekstowego wiersza (prawy klik, przycisk ⋯). Podgląd i attach działają także kliknięciem (FR-K3, priorytet M). Paleta ⌘K zostaje jedynym miejscem z wszystkimi akcjami (FR-K1, M), a skróty w stylu vima są przyspieszeniem dla wprawionych (FR-K2, priorytet S).

Pasek trybu `NORMAL` staje się paskiem statusu. Ściągawka skrótów w sidebarze jest zwijana. Brakujące kontrolki dla myszy dostarcza nowe zadanie AM-511 (M5).

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| A. Zostać przy keyboard-first | Brak pracy; spójne z obecnymi docs | Próg wejścia dla nowych użytkowników; akcje ukryte za skrótami; nie odpowiada nowemu kierunkowi produktu |
| B. Skróty jako dodatek, każda akcja pod myszą i w palecie (wybrana) | Odkrywalne akcje; zostaje cały zbudowany kod; mała zmiana (jedno zadanie AM-511) | Trzeba utrzymywać dwie drogi do akcji i testować obie |
| C. Usunąć tryb vim i skróty jednoliterowe | Prostszy kod skrótów i fokusu | Wyrzuca działające funkcje; zmiana `command-palette` bez potrzeby |

## Konsekwencje

- Nowe zadanie AM-511: przyciski Attach i Odpowiedz w panelu szczegółów, menu kontekstowe wiersza, dwuklik = attach, podgląd kliknięciem, zwijana ściągawka. Testy funkcjonalne obejmują drogę myszą (strategia testów, scenariusz 3).
- Nowe akcje dostają najpierw kontrolkę (przycisk lub menu) i komendę palety; skrót jest opcjonalny.
- Lekcje w `CLAUDE.md` o fokusie i skrótach nadal obowiązują, bo opisują działający kod.
- ADR 0011 (komendy palety składane w `app`) i ADR 0013 (strategia odpowiedzi) bez zmian.
