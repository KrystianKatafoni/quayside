# 08 · Wygląd: motywy, schematy kolorów i typografia

Terminy w tym dokumencie:

- **schemat kolorów** — zestaw tokenów kolorów (`bg`, `text`, `warn` …);
- **motyw** — schemat kolorów + tokeny kształtu (promień, grubość ramki, cień, tło panelu) + opcjonalnie domyślna typografia, którą ustawienia tylko podpowiadają;
- **typografia** — para fontów: font UI i font terminala.

Katalog ma **29 motywów**: 9 podstawowych (sekcja „Schematy kolorów”) i 20 motywów z kształtem (sekcja „Motywy z kształtem”). Jasnych jest 12, ciemnych 17, w tym 2 motywy wysokiego kontrastu (`contrast` ciemny, `hc-light` jasny). Motyw i typografia są **niezależne**: każdą z 22 typografii można połączyć z każdym z 29 motywów (638 kombinacji). Wybór odbywa się w *Ustawienia → Wygląd*, z podglądem prawdziwego ekranu Mission Control na żywo.

Ekrany czytają tokeny z `AmTheme` od AM-806 (domyślnie `plex` z typografią `plex`); terminal JediTerm też od AM-807: domyślny tekst i tło widżetu to `termText` i `termBg`, a font (gdy użytkownik nie wybrał własnego) to font terminala typografii, rejestrowany w AWT przy starcie. Zmiana działa na karty otwierane później (ADR 0016). Pasek przewijania terminala (AM-810) rysuje własny UI w kolorach wyprowadzonych z tych dwóch tokenów: tło `termBg` (dziś zakryte przez ścieżkę, zostaje na wypadek mniejszej ścieżki), ścieżka `termBg` z 6 % `termText`, uchwyt `termBg` z najmniejszym udziałem `termText` (45–100 %), przy którym ma ≥ 3:1 względem ścieżki i `termBg` (NFR-6); przy braku historii uchwyt wypełnia ścieżkę.

## Schematy kolorów (9 podstawowych motywów)

| Klucz | Nazwa | Tryb | bg | panel | text | accent | warn | ok | err | work | promień |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `plex` | Plex Violet (domyślny) | ciemny | `#0B0A10` | `#100E17` | `#E8E5F2` | `#A78BFA` | `#FBBF24` | `#86EFAC` | `#FCA5A5` | `#C4B5FD` | 8 px |
| `graphite` | Graphite | ciemny | `#111111` | `#161616` | `#EDEDED` | `#60A5FA` | `#F59E0B` | `#4ADE80` | `#F87171` | `#22D3EE` | 6 px |
| `paper` | Paper Indigo | jasny | `#F4F6F8` | `#FFFFFF` | `#111827` | `#4338CA` | `#A16207` | `#15803D` | `#B91C1C` | `#4338CA` | 10 px |
| `phosphor` | Phosphor | ciemny | `#050805` | `#070B07` | `#C8F7DB` | `#39FF88` | `#FFB000` | `#A3E635` | `#FF6B66` | `#5CE1FF` | 0 px |
| `synth` | Synth | ciemny | `#0A0F1F` | `#0D1326` | `#E6EAF7` | `#22D3EE` | `#FBBF24` | `#4ADE80` | `#FB7185` | `#C084FC` | 4 px |
| `amber` | Amber CRT | ciemny | `#0E0B07` | `#13100A` | `#F5E6C8` | `#FFB000` | `#5EEAD4` | `#A3E635` | `#FF6B57` | `#FDBA74` | 4 px |
| `sand` | Sand | jasny | `#FBF7EE` | `#FFFDF8` | `#1F2328` | `#0F766E` | `#A16207` | `#15803D` | `#B91C1C` | `#0F766E` | 8 px |
| `rose` | Rose Night | ciemny | `#191724` | `#1F1D2E` | `#E0DEF4` | `#C4A7E7` | `#F6C177` | `#9CCFD8` | `#EB6F92` | `#C4A7E7` | 10 px |
| `contrast` | Wysoki kontrast | ciemny | `#000000` | `#000000` | `#FFFFFF` | `#FFD400` | `#00E5FF` | `#00FF6A` | `#FF5C5C` | `#FFD400` | 2 px |

Pełny zestaw tokenów kolorów w każdym motywie (także w 20 motywach z kształtem, po wyprowadzeniu brakujących): `bg`, `panel`, `raised`, `sel`, `border`, `border2`, `text`, `muted`, `dim`, `accent`, `accentInk`, `warn`, `warnInk`, `warnBg`, `ok`, `err`, `work`, `termBg`, `termText`. Tokeny kształtu: `radius`, `borderWidth`, `shadow`, `backdrop`. Terminal ma kolory motywu w obu trybach (`termBg` = `bg`, `termText` = `text`); paleta ANSI jest w tokenie `ansi` (AM-811); dotyczy to także własnych schematów (AM-814), które dostają jasny terminal i skorygowaną paletę tą samą regułą.

**Semantyka kolorów:** `warn` = agent czeka na Ciebie (najważniejszy sygnał w UI), `work` = pracuje, `ok` = gotowe, `err` = błąd, `accent` = akcje główne i zaznaczenie. W schemacie *Amber CRT* akcent jest bursztynowy, więc `warn` jest turkusowy, żeby oba się nie myliły.

Kształt 9 podstawowych motywów: `radius` z tabeli, `borderWidth` 1 px, brak cienia, panel nieprzezroczysty. Domyślną typografię (`plex`) ma tylko motyw `plex`, pozostałych ośmiu podstawowych motywów jej nie podpowiada (AM-816; wcześniej żaden nie miał, AM-801).

Korekty względem pierwotnej tabeli (test kontrastu AM-801, odcienie `warn`/`ok`/`err`/`accent` ≥ 30°): `paper` `warn` z `#B45309` na `#A16207`, `phosphor` `ok` z `#39FF88` na `#A3E635`, `synth` `ok` z `#34D399` na `#4ADE80`, `rose` `accent` z `#EBBCBA` na `#C4A7E7`.

### Wyprowadzenie tokenów 9 podstawowych schematów (AM-801)

Tabela wyżej podaje 8 kolorów; pozostałe 11 liczy czysta funkcja `deriveTokens` (mieszanie w kanałach sRGB, zaokrąglone). Reguły oznaczone ★ są nowe: `muted` = `text` zmieszany z `panel` 30 %; `border` = `panel` zmieszany z `text` 12 %. Pozostałe jak w wyprowadzeniu AM-805: `border2` = `border` + `text` 15 %, `dim` = `muted` + `bg` 30 %, `raised` i `sel` z `panel` (`text` 4 %/6 %, `accent` 12 %/18 % w jasnym/ciemnym), `accentInk` i `warnInk` czarne albo białe (większy kontrast), `warnBg` = `panel` + `warn` 15 %, `termBg` = `bg` i `termText` = `text` w każdym trybie (AM-811), `ansi` jak w „Paleta ANSI terminala (AM-811)”. Reguły dla motywów z kształtem są w „Wyprowadzenie i korekty (AM-805)”.

## Typografie

Typografie podstawowe (8):

| Klucz | Nazwa | Font UI | Font terminala | Charakter |
| --- | --- | --- | --- | --- |
| `plex` | IBM Plex (domyślna) | IBM Plex Sans | IBM Plex Mono | techniczna, neutralna |
| `geist` | Geist | Geist | Geist Mono | nowoczesna, zwarta |
| `instrument` | Instrument | Instrument Sans | DM Mono | miękka, „produktowa” |
| `jetbrains` | JetBrains Mono | JetBrains Mono | JetBrains Mono | wszystko monospace, ligatury |
| `chakra` | Chakra | Chakra Petch | Fira Code | kanciasta, „sci-fi” |
| `space` | Space | Space Grotesk | Space Mono | ekspresyjna, retro |
| `hyper` | Hyperlegible | Atkinson Hyperlegible Next | Atkinson Hyperlegible Mono | maksymalna czytelność |
| `martian` | Martian Mono | Martian Mono | Martian Mono | szeroki monospace |

Typografie dodatkowe (14) — pary domyślne 20 motywów z kształtem, których nie ma wśród podstawowych (AM-805):

| Klucz | Font UI | Font terminala | Domyślna w motywach |
| --- | --- | --- | --- |
| `jakarta-jb` | Plus Jakarta Sans | JetBrains Mono | `nordic-light`, `deep-abyss` |
| `grotesk-fira` | Space Grotesk | Fira Code | `cyber-light`, `cyberpunk` |
| `outfit-courier` | Outfit | Courier Prime | `sand-light` |
| `outfit-dm` | Outfit | DM Mono | `glass-light` |
| `inter-fira` | Inter | Fira Code | `steel-light`, `dracula` |
| `inter-jb` | Inter | JetBrains Mono | `midnight-pro`, `minimal-black` |
| `inter-roboto` | Inter | Roboto Mono | `nord-dark` |
| `dmsans-jb` | DM Sans | JetBrains Mono | `teal-light` |
| `dmsans-courier` | DM Sans | Courier Prime | `oatmeal-light` |
| `grotesk-source` | Space Grotesk | Source Code Pro | `monokai` |
| `ptmono` | PT Mono | PT Mono | `term-light` |
| `plexmono` | IBM Plex Mono | IBM Plex Mono | `gruvbox` |
| `firacode` | Fira Code | Fira Code | `solarized-dark` |
| `system` | SF Pro (systemowy) | Menlo (systemowy) | `developer-gray` |

Motywy `hc-light` i `emerald-light` mają parę IBM Plex Sans + IBM Plex Mono, czyli podstawową typografię `plex`.

**Dlaczego dodatkowe typografie, a nie „tylko para domyślna motywu”**: domyślna typografia motywu jest wtedy zwykłym odwołaniem `TypographyId` do katalogu. Nie ma specjalnej wartości „typografia motywu” w `theme.json`, rozwiązywanie motywu się nie zmienia, a istniejący test polskich znaków obejmuje nowe fonty automatycznie. Koszt: dłuższa lista na ekranie Wygląd (grupy „Podstawowe” i „Z motywów”).

Zamiany względem pliku źródłowego:

- **Ubuntu Mono** ma licencję Ubuntu Font Licence, nie OFL. W `glass-light` zastępuje go DM Mono (już w katalogu, miękki krój pasujący do Outfit), w `deep-abyss` JetBrains Mono.
- **Fonty systemowe** w `developer-gray`: `-apple-system`/Segoe UI → SF Pro, Consolas → Menlo. Oba są fontami macOS, więc aplikacja ich nie dołącza (nie ma czego licencjonować) i czyta je z systemu po nazwie rodziny.

Fonty wszystkich 22 typografii (22 rodziny; SF Pro i Menlo to fonty systemowe i nie są dołączane) są w zasobach `core/design-system/src/main/resources/fonts` (źródła i sumy SHA-256 w `fonts/SOURCES.md`): 14 rodzin podstawowych z AM-802 i nowe z AM-805. Nowe rodziny w `resources/fonts`: Plus Jakarta Sans, Outfit, Courier Prime, Inter, DM Sans, Roboto Mono, Source Code Pro, PT Mono — wszystkie na OFL (Google Fonts, katalog `ofl/`). Pozostałe fonty z pliku źródłowego należą do 8 podstawowych typografii (AM-802).

Wszystkie dołączane fonty są na licencji SIL Open Font License i można je dołączyć do aplikacji (folder `resources/fonts`, ładowane przez `Font(resource = …)` w Compose). Każda typografia jest sprawdzana na polskich znakach (`ąęłśżź`) i znakach łatwych do pomylenia w kodzie (`0O1lI`, `=>`, `!=`).

## Motywy z kształtem (20)

Źródło: [Quayside Themes Documentation.md](Quayside%20Themes%20Documentation.md) (zmienne CSS, klucz motywu = wartość `data-theme`). Ten dokument jest kanoniczny; plik źródłowy zostaje jako referencja.

### Mapowanie zmiennych CSS na tokeny

| Zmienna CSS | Token Kotlina | Uwagi |
| --- | --- | --- |
| `--bg-main` | `bg` | |
| `--bg-panel` | `panel` | `rgba(…)` → kolor z kanałem alfa, rysowany na `bg` |
| `--bg-terminal` | `termBg` | podany tylko w `nordic-light` (`#1e242c`); od AM-811 ignorowany: `termBg` = `bg` także w `nordic-light` (odstępstwo od pliku źródłowego, decyzja człowieka 2026-11-08); w pozostałych wyprowadzony (AM-805) |
| `--text-main` | `text` | |
| `--text-muted` | `muted` | |
| `--accent` | `accent` | |
| `--border-color` | `border` | `rgba(…)` → kolor z kanałem alfa |
| `--border-width` | `borderWidth` | nowy token kształtu, px (dp) |
| `--border-radius` | `radius` | px (dp) |
| `--shadow` | `shadow` | nowy token kształtu; `none` → brak cienia |
| `--font-sans` | font UI domyślnej typografii | `defaultTypography` motywu |
| `--font-mono` | font terminala domyślnej typografii | jw. |
| `--panel-backdrop` | `backdrop` | nowy token kształtu; `blur(…)` → przybliżenie (niżej) |

### Tokeny kształtu

Szkic modelu w `core:design-tokens` (bez typów Compose; ostateczne sygnatury ustala plan AM-801):

```kotlin
data class ShapeTokens(val radius: Int, val borderWidth: Int, val shadow: ShadowSpec?, val backdrop: Backdrop)
data class ShadowSpec(val offsetY: Int, val blur: Int, val spread: Int, val color: Rgba)
enum class Backdrop { OPAQUE, TRANSLUCENT }
```

- `borderWidth` — grubość ramki paneli i kart: 1 albo 2 px.
- `shadow` — cień paneli; przesunięcie w poziomie zawsze 0, więc model go nie ma. `cyberpunk` ma poświatę (przesunięcie 0, kolor akcentu).
- `backdrop` — `TRANSLUCENT` tylko w `glass-light`. Compose Desktop nie rozmywa tła pod panelem, więc **bez rozmycia**: panel jest półprzezroczysty (`#FFFFFF` 60 %) nad jednolitym `bg`. To przybliżenie efektu „matowego szkła” z pliku źródłowego; ekran Wygląd i ten dokument tak je opisują. Kontrast liczymy na kolorze złożonym (`#FAF9FF`).

### Katalog

Cień: `y/rozmycie/rozrzut` w px, potem kolor i krycie. Kolumna „typografia” to domyślna typografia, którą ekran Wygląd podpowiada po wyborze motywu (nie zmienia jej sam).

| Klucz | Nazwa | Tryb | bg | panel | text | muted | accent | border | ramka | promień | cień | typografia |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `nordic-light` | Nordic Slate | jasny | `#F4F6F8` | `#FFFFFF` | `#2D3748` | `#657286` | `#3182CE` | `#E2E8F0` | 1 px | 8 px | 4/6/-1 `#000` 5 % | `jakarta-jb` |
| `cyber-light` | Cyber Ink Light | jasny | `#F3F4F6` | `#FFFFFF` | `#111827` | `#69707D` | `#8B5CF6` | `#111827` | 2 px | 0 px | brak | `grotesk-fira` |
| `sand-light` | Minimal Sand | jasny | `#FDFBF7` | `#F5F2EB` | `#3A3832` | `#716E64` | `#6B705C` | `#000` 5 % | 1 px | 12 px | 10/15/-3 `#000` 2 % | `outfit-courier` |
| `hc-light` | High-Contrast Accessibility | jasny, wysoki kontrast | `#FFFFFF` | `#FFFFFF` | `#000000` | `#4A4A4A` | `#0052CC` | `#000000` | 2 px | 4 px | brak | `plex` |
| `steel-light` | Steel & Cobalt | jasny | `#EBEDF0` | `#FFFFFF` | `#172B4D` | `#616C7F` | `#0052CC` | `#C1C7D0` | 1 px | 4 px | 1/3/0 `#091E42` 13 % | `inter-fira` |
| `teal-light` | Teal & Obsidian | jasny | `#F0FDFA` | `#FFFFFF` | `#0F172A` | `#64748B` | `#0D9488` | `#99F6E4` | 1 px | 12 px | 4/6/-1 `#0D9488` 10 % | `dmsans-jb` |
| `term-light` | Terminal Classic | jasny | `#F0F0F0` | `#FFFFFF` | `#333333` | `#6D6D6D` | `#008000` | `#333333` | 1 px | 0 px | brak | `ptmono` |
| `glass-light` | Amethyst Glass | jasny | `#F3F0FF` | `#FFFFFF` 60 % | `#3B2A5C` | `#796696` | `#8B5CF6` | `#8B5CF6` 20 % | 1 px | 16 px | 8/32/0 `#8B5CF6` 10 % | `outfit-dm` |
| `oatmeal-light` | Oatmeal & Terracotta | jasny | `#F9F5F0` | `#FFFFFF` | `#4A3F35` | `#7B6F63` | `#D97757` | `#E6DACE` | 2 px | 16 px | 4/0/0 `#D97757` 10 % | `dmsans-courier` |
| `emerald-light` | Corporate Emerald | jasny | `#F0FDF4` | `#FFFFFF` | `#064E3B` | `#04835B` | `#059669` | `#A7F3D0` | 1 px | 2 px | 1/2/0 `#064E3B` 10 % | `plex` |
| `midnight-pro` | Midnight Pro | ciemny | `#0B0F19` | `#111827` | `#F3F4F6` | `#9CA3AF` | `#3B82F6` | `#1F2937` | 1 px | 6 px | 4/6/-1 `#000` 50 % | `inter-jb` |
| `dracula` | Dracula | ciemny | `#282A36` | `#44475A` | `#F8F8F2` | `#ADB6D0` | `#FF79C6` | `#6272A4` | 1 px | 8 px | 4/6/0 `#000` 30 % | `inter-fira` |
| `monokai` | Monokai | ciemny | `#272822` | `#1E1F1C` | `#F8F8F2` | `#928F80` | `#F92672` | `#3E3D32` | 1 px | 4 px | brak | `grotesk-source` |
| `gruvbox` | Gruvbox Dark | ciemny | `#282828` | `#3C3836` | `#EBDBB2` | `#ABA094` | `#FE8019` | `#504945` | 1 px | 0 px | brak | `plexmono` |
| `cyberpunk` | Cyberpunk | ciemny | `#000000` | `#090909` | `#E0E0E0` | `#FF00FF` | `#00FFCC` | `#333333` | 1 px | 0 px | 0/10/0 `#00FFCC` 10 % | `grotesk-fira` |
| `nord-dark` | Nord Dark | ciemny | `#2E3440` | `#3B4252` | `#ECEFF4` | `#D8DEE9` | `#88C0D0` | `#434C5E` | 1 px | 6 px | 4/6/0 `#000` 20 % | `inter-roboto` |
| `minimal-black` | Minimal Black | ciemny | `#000000` | `#0A0A0A` | `#FFFFFF` | `#888888` | `#FFFFFF` | `#333333` | 1 px | 4 px | brak | `inter-jb` |
| `solarized-dark` | Solarized Dark | ciemny | `#002B36` | `#073642` | `#EEE8D5` | `#93A1A1` | `#2AA198` | `#1A4B57` | 1 px | 0 px | brak | `firacode` |
| `developer-gray` | Developer Gray | ciemny | `#22272E` | `#2D333B` | `#ADBAC7` | `#909BA5` | `#539BF5` | `#444C56` | 1 px | 6 px | brak | `system` |
| `deep-abyss` | Deep Abyss | ciemny | `#050B14` | `#0A1423` | `#CBD5E1` | `#707F94` | `#F59E0B` | `#1E293B` | 1 px | 12 px | 4/12/0 `#000` 50 % | `jakarta-jb` |

Klucze nie kolidują z 9 podstawowymi (`sand` ≠ `sand-light`, `contrast` ≠ `hc-light`). Kolumna `muted` ma wartości końcowe po korekcie funkcją `withContrast` (AM-805); wartości źródłowe i reguła są w „Wyprowadzenie i korekty (AM-805)”. Pozostałe kolumny są przepisane z pliku źródłowego, z wyjątkiem `text` w `solarized-dark` (decyzja człowieka: base2 `#EEE8D5` zamiast `#839496`).

## Wyprowadzenie i korekty (AM-805)

### Wyprowadzenie brakujących tokenów

Motywy z kształtem podają 6 kolorów (`bg`, `panel`, `text`, `muted`, `accent`, `border`); pozostałe tokeny liczą czyste funkcje w `appearance:impl` (`deriveShapedTokens`, `deriveStatus`, `withContrast`). `panel` z alfą (tylko `glass-light`) daje `Backdrop.TRANSLUCENT`; jasność powierzchni liczymy na `panel` złożonym na `bg`.

| Token | Reguła |
| --- | --- |
| `muted` | `withContrast`: ten sam odcień, najmniejsze rozjaśnienie (ciemny motyw) albo przyciemnienie (jasny) do ≥ 4,5:1 względem `bg` i złożonego `panel`; kolor, który już przechodzi, zostaje |
| `work` | `accent`, a gdy ma mniej niż 3:1, korekta `withContrast` do 3:1 (zmienia się tylko w `oatmeal-light`) |
| `warn`, `ok`, `err` | pierwsza trójka z list kandydatów (niżej), w której odcienie `warn`, `ok`, `err` różnią się od `accent` o ≥ 30°, a `warn` od `ok` i `err` też |
| `accentInk`, `warnInk` | czarny albo biały — ten, który daje większy kontrast |
| `warnBg` | `warn` z kryciem 15 % na złożonym `panel` |
| `raised`, `sel` | złożony `panel` zmieszany z `text` (4 % jasny, 6 % ciemny) i z `accent` (12 % jasny, 18 % ciemny) |
| `border2`, `dim` | `border` zmieszany z `text` (15 %, zachowuje alfę `border`); `muted` zmieszany z `bg` (30 %) |
| `termBg`, `termText` | `bg` i `text` w każdym trybie (AM-811); `ansi` wg „Paleta ANSI terminala (AM-811)” |

Kandydaci statusów (kolejność = preferencja; pierwsza trójka bez konfliktu odcieni wygrywa). Jasny motyw: `warn` `#A16207`, `#0F766E`; `ok` `#15803D`, `#4D7C0F`; `err` `#B91C1C`, `#BE185D`. Ciemny motyw: `warn` `#F59E0B`, `#FACC15`, `#5EEAD4`; `ok` `#4ADE80`, `#A3E635`; `err` `#F87171`, `#FF6E40`, `#FB7185`. Brak trójki przerywa budowę katalogu błędem. Odstępstwa od pierwszych kandydatów: `term-light` i `emerald-light` (`ok` `#4D7C0F`), `oatmeal-light` (`warn` `#0F766E`, `err` `#BE185D`), `monokai` (`warn` `#FACC15`, `err` `#FF6E40`), `gruvbox` (`warn` `#5EEAD4`, `ok` `#A3E635`, `err` `#FB7185`), `cyberpunk` (`ok` `#A3E635`), `deep-abyss` (`warn` `#5EEAD4`, `ok` `#A3E635`). Granicznie przechodzą: `dracula` (`err`~`accent` 34,5°), `teal-light` (`ok`~`accent` 32,3°), `oatmeal-light` (`warn`~`ok` 32,9°).

### Korekta kontrastu NFR-6

Test kontrastu (doc 12) obejmuje pary `muted/bg` i `muted/panel` (panel półprzezroczysty złożony na `bg`) oraz kontrast ≥ 3:1 dla `warn`, `ok`, `err`, `work`. Korekty `muted` (wartość źródłowa → końcowa):

- jasne: `nordic-light` `#718096` → `#657286`; `cyber-light` `#6B7280` → `#69707D`; `sand-light` `#8A867A` → `#716E64`; `steel-light` `#6B778C` → `#616C7F`; `term-light` `#808080` → `#6D6D6D`; `oatmeal-light` `#8C7E70` → `#7B6F63`; `emerald-light` `#059669` → `#04835B`;
- ciemne: `dracula` `#6272A4` → `#ADB6D0` (decyzja człowieka: `panel` `#44475A` bez zmian, rozjaśniamy `muted`); `monokai` `#75715E` → `#928F80`; `gruvbox` `#928374` → `#ABA094`; `developer-gray` `#768390` → `#909BA5`; `deep-abyss` `#64748B` → `#707F94`;
- `solarized-dark` ma w wierszu wprost `text` `#EEE8D5` (base2) i `muted` `#93A1A1` (base1), bez korekty funkcją.

Bez zmian (już przechodzą): `hc-light`, `teal-light`, `glass-light` (4,51:1 na złożonym `panel`, granicznie), `midnight-pro`, `cyberpunk`, `nord-dark`, `minimal-black`.

Uwagi do oceny wzrokowej w AM-803: w `cyberpunk` `muted` (magenta) przechodzi kontrast, ale jest bardziej jaskrawy niż `text` — przeczy roli „przygaszony”; w `minimal-black` `accent` = `text` (`#FFFFFF`), więc zaznaczenie i akcje główne nie różnią się od tekstu; w `emerald-light` `muted` po korekcie jest ciemniejszy od `accent`.

### Paleta ANSI terminala (AM-811)

Baza to 16 kolorów xterm z JediTerm 3.76 (`ColorPaletteImpl`): `000000 CD0000 00CD00 CDCD00 1E90FF CD00CD 00CDCD E5E5E5 4C4C4C FF0000 00FF00 FFFF00 4682B4 FF00FF 00FFFF FFFFFF`. Funkcja `deriveTerminal(bg, text, dark)` poprawia każdy kolor regułą `readableOn` względem `termBg` (= `bg`): najmniejsze zmieszanie ku bieli (ciemny motyw) albo ku czerni (jasny) dające kontrast ≥ 4,5:1; kolor, który już przechodzi, zostaje. Wyjątek: kolor tła trybu (indeks 0 w ciemnym, 15 w jasnym) zostaje bez zmian. Zmiana dotyczy także ciemnych motywów (np. czerwony `#CD0000` na `#0B0A10` ma 3,4:1 i jaśnieje). Zaznaczenie i kursor to odwrócona para `termText`/`termBg`. Zmiana palety działa na karty otwierane później (ADR 0016). Szczegóły: ADR 0027.

Rada: programy z kolorami 24-bit dobranymi pod ciemne tło (np. motyw `dark` w Claude Code) bywają nieczytelne na jasnym terminalu. W Claude Code ustaw `/theme light` albo `light-ansi` (kolory z palety terminala).

### Korekta kontrastu ekranów (AM-803)

Audyt par rysowanych przez ekrany (`ScreenContrastTest`, wszystkie 29 motywów) wykazał tokeny poniżej progu. Od AM-803 wyprowadzenie poprawia je regułą `readableOn` (`withContrast` do 4,5:1 w stronę bieli w ciemnym motywie i czerni w jasnym; kolor, który przechodzi, zostaje): `warn`, `ok`, `err`, `work` jako tekst na `bg`, `panel` i `sel`; `muted` na `bg`, `panel`, `sel` i `warnBg`; `accent` motywów z kształtem jako znak 3:1 na `bg`, `panel`, `raised` i `sel`. `work` motywów z kształtem ma teraz próg 4,5:1 (nie 3:1). Listy wartości w tej sekcji i w „Korekta kontrastu NFR-6” są stanem z AM-805; wartości końcowe zmienionych motywów (23 z 29) są w tabeli planu `docs/plans/AM-803.md`, sekcja „Korekta kolorów”.

### Brak zrzutów ekranu

Plik źródłowy odwołuje się do `docs/screenshots/light_01_nordic_slate.png` … `dark_10_deep_abyss.png` (20 plików). Tych plików nie ma w repo. Nie tworzymy ich teraz; zrzuty z działającej aplikacji można dodać po AM-805.

## Ikony, tony i przyciski ikonowe (AM-808)

Źródło: mockupy z 2026-11-04 (A · Mission Control, B · terminal po Attach, C · ikony, przyciski, statusy). Geometrię ikon przejmujemy z arkusza C; kolory i promienie zawsze z tokenów motywu, nie z hexów mockupu.

**Ikony.** 22 ikony liniowe w kodzie (`IconKind`), siatka 24 × 24, kreska 1,8, zaokrąglone końce i złącza, bez wypełnień. Cztery ikony narysowano w repo w tym samym stylu, bo mockup ich nie ma: `STOPPED`, `REMOVE`, `MORE`, `BACK`. Status nigdy nie jest tylko kolorem (NFR-6): ikony statusów różnią się kształtem, pigułki i kafelki mają tekst albo opis.

**Tony.** Tło pigułki lub kafelka to `tinted(c) = panel.over(bg).mix(c, 15 %)`; ta sama reguła daje `warnBg` w `plex`.

| Ton | Znak | Tło | Tekst pigułki |
| --- | --- | --- | --- |
| `NEUTRAL` | `muted` | `raised` | `text` |
| `ACCENT` | `accent` | `tinted(accent)` | znak, gdy ≥ 4,5:1 na tle, inaczej `text` |
| `WARN` | `warn` | `warnBg` | jw. |
| `WORK` / `OK` / `ERR` | `work` / `ok` / `err` | `tinted(ton)` | jw. |

Znak (ikona) ma ten sam wybór z progiem 3:1 (WCAG 1.4.11). Plakietka licznika na ikonie to `warn` z tuszem `warnInk`.

**Przyciski ikonowe.** Style: `GHOST` (przezroczysty, ikona `muted`), `OUTLINED` (`raised` i `border2`), `SELECTED` (`sel` i `border2`), `DANGER` (obrys `tinted(err)` 35 %, ikona `err`). Rozmiary celu: `COMPACT` 28 dp (ikona 16), `REGULAR` 34 dp (ikona 18), `LARGE` 44 dp (ikona 20). Wyłączony przycisk ma ikonę `dim`. Przycisk niefokusowalny nie bierze fokusu i nie rezerwuje miejsca na ramkę. Przycisk ikonowy nie rysuje nakładki najechania ani naciśnięcia: styl (`SELECTED`) mówi, czy jest zaznaczony, a nakładka przyciemniałaby kolory z tokenów.

**Hierarchia przycisków z tekstem (AM-809, arkusz C).** `AmButton` ma trzy rodzaje: `PRIMARY` (`accentInk` na `accent`, jeden na widok), `SECONDARY` (`text` na `raised`, obrys `border2`) i `DANGER` (obrys `tinted(err)` 35 %, etykieta `err`, bez wypełnienia; gdy `err` ma na `panel` mniej niż 4,5:1, etykieta jest w kolorze `text`). Opcjonalnie ma ikonę 16 dp przed tekstem i skrót za tekstem (kolor etykiety, bez ramki; odczytywany razem z etykietą). W panelu szczegółów kolejność jest zawsze taka: główna, zwykłe, groźne osobno. Przyciski na ekranach (poza oknami dialogowymi) są niefokusowalne, żeby Spacja i Enter trafiały do skrótów okna. Kolor znaku stanu (ikona, kafelek) to `StatusTone.markColor()`.

## Promienie pochodne i role tekstu (AM-816)

Źródło: mockupy z 2026-11-04, iteracja 2. Nowe tokeny nie powstają: `core:design-system` wylicza promienie z tokenu `radius` (r). Wiersz = r + 2, karta = r + 4, kafelek ikony = r + 2, licznik = pełne koło (kwadrat przy r = 0). Przy r = 0 wszystkie promienie są 0, więc motywy kwadratowe zostają kwadratowe.

| r | wiersz | karta | kafelek |
| --- | --- | --- | --- |
| 0 | 0 | 0 | 0 |
| 2 | 4 | 6 | 4 |
| 8 (`plex`) | 10 | 12 | 10 |
| 16 | 18 | 20 | 18 |

Wiersz: zaznaczony projekt lub widok (tło `sel`, ramka `accent`), wiersz `AmListRow`, segmenty filtra. Karta: `AmPanel`, `AmStatCard`, lista sesji, ramka pól szczegółów, ramka panelu terminala (widżetu Swing nie da się przyciąć do zaokrąglenia, więc ma odstęp co najmniej r·(1 − 1/√2) i 2 dp od ramki). Kafelek: `AmIconTile`. Licznik: `AmCounter`, najmniej 22 dp, czyta się jego opis („2 czekają”), a nie sama liczba.

Dwie nowe role tekstu obok TITLE, HEADING, BODY, CAPTION i CODE: `NAME` (mono, 15 sp, SemiBold: nazwy sesji w wierszach, w panelu szczegółów, na pasku sesji i w okruszkach trybu terminala) oraz `LABEL` (UI, 11 sp, SemiBold, odstęp liter 0,12 em: „PROJEKTY”, „WIDOKI”, „PODSUMOWANIE”; wielkie litery są w tekstach). Kolory zawsze z tokenów: `LABEL` jest w `muted` na `panel` (≥ 4,5:1 w audycie).

## Logo (AM-822)

Znak Quayside to latarnia na ciemnym kaflu ([ADR 0030](adr/0030-logo-svg-w-zasobach-skia-svgdom.md)). Dwa rysunki SVG w zasobach `core/design-system/src/main/resources/logo/`: pełny `quayside-logo.svg` (viewBox 32, od 32 dp) i uproszczony `quayside-logo-16.svg` (viewBox 16, poniżej 32 dp, np. 20 dp w górnym pasku). Kolory są stałe (kafel `#1E1930`, wiązka `#FBBF24`, latarnia `#E8E5F2`, głowica i podstawa `#A78BFA`, czubek latarni w pełnym rysunku `#C4B5FD`), więc znak wygląda tak samo w jasnych i ciemnych motywach i nie zmienia się z `LocalTextColor`. Znak nie jest klikalny ani fokusowalny; ma etykietę dostępności albo jest dekoracją.

Słowo „Quayside” (`AmWordmark`) to jeden węzeł tekstu w IBM Plex Sans SemiBold 15 sp niezależnie od typografii motywu: „Quay” w `text`, „side” w kolorze `wordmarkAccent()`. Akcent zostaje, gdy ma 4,5:1 na `panel` (rysowanym na `bg`); w przeciwnym razie jest mieszany w stronę `text` krokami po 5%, aż kontrast wystarczy (NFR-6). Ikona okna to ten sam rysunek (32 dp), ikona Docka to `amLogoImage(256)` przez `java.awt.Taskbar`; plik `.icns` robi AM-901.

## Pozostałe ustawienia wyglądu

| Ustawienie | Opcje |
| --- | --- |
| Rozmiar interfejsu | S / **M** / L (skala `Density` w Compose) |
| Gęstość listy sesji | Kompaktowa (kafelek 28 dp, odstęp pionowy 4 dp, jedna linia; podsumowanie tylko w panelu szczegółów) / **Komfortowa** (kafelek 38 dp, odstęp 8 dp, nazwa i podsumowanie); dotyczy listy sesji, nie paska projektów |
| Font terminala | Z typografii albo własny |
| Rozmiar terminala | 8–32 px (zakres `TerminalSettingsRules`), domyślnie 13; krok ±1 bez zawijania |
| Dzień / noc | Przełączanie razem z trybem macOS; osobny schemat na dzień (np. Paper Indigo) i na noc (np. Plex Violet) |
| Własne motywy | Własny schemat, import i eksport `.json` |

## `theme.json`

```json
{
  "schemaVersion": 1,
  "data": {
    "scheme": "plex",
    "typography": "plex",
    "uiScale": "M",
    "density": "COMFORTABLE",
    "followSystem": false,
    "dayScheme": "paper",
    "nightScheme": "plex"
  }
}
```

Format ustala [ADR 0023](adr/0023-format-theme-json-i-tryb-macos.md). Koperta `schemaVersion` + `data` jest taka sama jak w pozostałych plikach konfiguracji (`JsonStore`). `uiScale` przyjmuje `S`, `M`, `L`, a `density` `COMPACT` lub `COMFORTABLE`. Sekcji `terminal` nie ma: font i rozmiar terminala zostają w `terminal.json` (ADR 0016). Pole `customSchemes` (opcjonalna tablica własnych schematów: `key`, `name`, `mode`, `radius` i osiem kolorów `#RRGGBB` z tabeli) pojawia się tylko wtedy, gdy są własne schematy; schemat wchodzi do katalogu po audycie kontrastu NFR-6, a „Importuj schemat…” na ekranie Wygląd dodaje schematy z pliku od razu (zapis bez „Zastosuj”). Okno Wygląd ma też edytor schematu („Nowy schemat…”: klucz, nazwa, promień, tryb i osiem kolorów z raportem kontrastu i próbką), „Usuń schemat” i „Eksportuj schemat…” dla wybranego własnego schematu (AM-820). Szczegóły w uzupełnieniach ADR 0023 (AM-814, AM-820).

Plik leży w `~/Library/Application Support/Quayside/theme.json`. Pola `scheme`, `dayScheme` i `nightScheme` przyjmują klucz każdego z 29 motywów. Wybór motywu nie zmienia `typography` — ekran Wygląd tylko podpowiada domyślną typografię motywu.

Odczyt przy starcie jest łagodny: brak klucza, zły typ, nieznana nazwa enuma albo id spoza katalogu daje wartość domyślną tego pola. Plik, który nie jest obiektem JSON, trafia do `theme.json.broken`. Plik z nowszym `schemaVersion` nie jest czytany ani nadpisywany (aplikacja używa wartości domyślnych, a „Zastosuj” kończy się błędem zapisu).

**Dzień i noc (FR-A5).** Przy `followSystem: true` aplikacja co 5 s czyta tryb macOS poleceniem `defaults read -g AppleInterfaceStyle` i rysuje motyw `nightScheme` (tryb ciemny) albo `dayScheme` (jasny); `scheme` zostaje wyborem zapisanym. Pierwszy odczyt jest przed otwarciem okna. Gdy odczyt się nie uda, obowiązuje ostatni znany tryb.

**Import i eksport (FR-A6).** Na ekranie Wygląd „Eksportuj…” zapisuje szkic (to, co widać w podglądzie) w tym samym formacie, więc wyeksportowany plik można położyć jako `theme.json`. „Importuj…” czyta plik ściśle (obecne, ale złe pole to błąd; brak pola daje wartość domyślną; plik do 64 KiB), wkłada wynik do szkicu i niczego nie zapisuje — zapis dopiero przez „Zastosuj”.
