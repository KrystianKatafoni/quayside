# ADR 0027: Kolory terminala z motywu w każdym trybie i paleta ANSI jako token

- **Status:** przyjęta (zatwierdził człowiek 2026-11-08)
- **Data:** 2026-11-08
- **Dotyczy:** `core:design-tokens`, `feature:appearance:impl`, `feature:terminal:impl`, `docs/08-wyglad.md`; uzupełnia [ADR 0016](0016-format-terminal-json.md) (decyzja 3) bez jej zmiany

## Kontekst

Do AM-811 jasne motywy (12 z 29) dawały terminalowi stałą ciemną parę `#1E242C`/`#E2E8F0` (AM-801, AM-805), a JediTerm rysował 16 kolorów ANSI z wbudowanej palety xterm (`ColorPaletteImpl.XTERM_PALETTE`). Decyzja człowieka 2026-11-05 („opcja b”): w jasnych motywach terminal też jest jasny. Paleta xterm na jasnym tle jest nieczytelna (np. żółty `#CDCD00` i biały `#E5E5E5` na `#FFFFFF` mają ok. 1,7:1 i 1,3:1), a część jej kolorów na ciemnym tle też nie spełnia NFR-6 (np. czerwony `#CD0000` na `#0B0A10` ok. 3,4:1). ADR 0016 dotyczy `terminal.json`; kolorów nie opisuje, ale jego decyzja 3 (ustawienia działają w nowych kartach) obowiązuje też kolory od AM-807. ADR 0016 jest przyjęta, więc jej nie edytujemy.

## Decyzja

1. `termBg` = `bg`, `termText` = `text` w każdym motywie, jasnym i ciemnym. Stałe `#1E242C`/`#E2E8F0` znikają; `--bg-terminal` z pliku źródłowego (`nordic-light`) przestaje obowiązywać.
2. Paleta ANSI (16 kolorów, kolejność xterm) jest tokenem motywu: `TerminalColors.ansi: AnsiColors` w `core:design-tokens`. Wyprowadza ją `appearance:impl` z jednej bazy (paleta xterm JediTerm) regułą `readableOn` względem `termBg`: każdy kolor ≥ 4,5:1, z wyjątkiem koloru tła trybu (indeks 0 w ciemnym, indeks 15 w jasnym), który zostaje bez zmian.
3. `terminal:impl` podaje JediTerm paletę z tokenu (`getTerminalColorPalette()`); zaznaczenie i kursor zostają odwrócone (para `termText`/`termBg`).
4. Nowe kolory dostają tylko karty otwarte później (ADR 0016, decyzja 3); przemalowanie otwartych kart nie wchodzi w zakres.

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| A. Paleta jako token, baza xterm + `readableOn` (wybrana) | Jedna reguła korekty dla całego katalogu; audyt na 29 motywach; AM-814 może walidować własne palety | Zmiana publicznego typu `TerminalColors` (konstruktor); kolory zwykłe i jasne bywają po korekcie bliskie |
| B. Token, osobna ręcznie dobrana baza jasna (np. GitHub Light, Solarized Light) + `readableOn` | Ładniejsza paleta jasna | 16 nowych stałych bez źródła w docs/08; dwie bazy do utrzymania |
| C. Paleta liczona w `terminal:impl` z `termBg` | Bez zmiany `TerminalColors` | `withContrast` musiałby przejść do `core:design-tokens` albo zostać skopiowany; audyt nie widzi katalogu |
| D. Domyślna paleta JediTerm | Bez kodu | Na jasnym tle nieczytelna (NFR-6) |
| E. Jasny terminal tylko jako opcja użytkownika | Programy TUI z ciemnymi kolorami 24-bit działają jak dziś | Nowe pole w `terminal.json` i zmiana `terminal:api`; sprzeczne z decyzją „opcja b” |

## Konsekwencje

- Ciemne motywy też dostają poprawione kolory ANSI tam, gdzie paleta xterm nie spełnia 4,5:1 (np. czerwony, magenta, jasna czerń); widoczna zmiana wyglądu.
- Programy rysujące kolorami 24-bit dobranymi do ciemnego tła (np. motyw `dark` Claude Code) są w jasnym terminalu słabo czytelne; docs/08 radzi motyw `light` albo `light-ansi` Claude Code. Opcja E zostaje na później, jeśli człowiek oceni to jako problem.
- Kontrast tekstu terminala (`termText` na kolorach ANSI jako tle, np. `ESC[41m`) nie jest audytowany.
- AM-814 (własne schematy) musi albo przyjmować paletę ANSI z pliku, albo wyprowadzać ją tą samą regułą.
- Zmiana tokenu `ansi` w przyszłości nie wymaga migracji plików: paleta nie jest zapisywana na dysku.
