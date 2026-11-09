# ADR 0030: Logo jako pliki SVG w zasobach rysowane przez Skia SVGDOM

- **Status:** proponowana
- **Data:** 2026-11-09
- **Dotyczy:** `core:design-system` (zasoby `logo/`, `AmLogo`), `app` (ikona okna i Docka), AM-901 (`.icns`)

## Kontekst

Człowiek wybrał 2026-11-09 logo „latarnia” (wariant D, `docs/screenshots/logo-propozycje-2026-11-09/`): dwa rysunki SVG, pełny `quayside-logo.svg` (od 32 px) i uproszczony `quayside-logo-16.svg` (do 20 px). Znak ma się pojawić w górnym pasku, jako ikona okna i Docka, a w AM-901 w `.icns` — zawsze z tego samego rysunku. Compose Multiplatform 1.12 ma `loadSvgPainter` i `painterResource(String)` oznaczone `@Deprecated` („Migrate to the Compose resources library”), a projekt kompiluje z `allWarningsAsErrors`. Ikony liniowe (`IconKind`) są dziś przepisane do `ImageVector`, ale mają jeden kolor i jedną ścieżkę.

## Decyzja

1. Oba pliki SVG trafiają bez zmian treści do `core/design-system/src/main/resources/logo/` i są jedynym źródłem rysunku: górny pasek, ikona okna, ikona Docka i (w AM-901) `.icns`.
2. `core:design-system` rysuje je własnym `internal` `Painter` opartym na `org.jetbrains.skia.svg.SVGDOM` (Skia jest już na ścieżce klas Compose Desktop; nie dochodzi nowa zależność w `libs.versions.toml`). Typy Skia nie wychodzą poza moduł; publiczne są tylko `AmLogo`, `rememberAmLogoPainter` i `amLogoImage` (JDK `BufferedImage`).
3. Wybór rysunku zależy od rozmiaru w dp: poniżej 32 dp rysunek uproszczony, od 32 dp pełny. Kolory znaku są stałe (nie zależą od motywu).

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| A. SVG w zasobach + własny `Painter` na Skia SVGDOM (wybrana) | Jeden rysunek dla UI, okna, Docka i `.icns`; pełna obsługa SVG (`rx`, `opacity`); bez nowej biblioteki | Kod zależny od API Skia (`SVGDOM`, `SVGLength`); ok. 40 linii własnego `Painter` |
| B. Biblioteka `org.jetbrains.compose.components:components-resources` (`decodeToSvgPainter`) | Oficjalna ścieżka JetBrains | Nowa zależność i wtyczka zasobów (generowany `Res`) tylko dla dwóch plików |
| C. Przepisanie rysunków do `ImageVector` (jak `IconKind`) | Bez Skia i zasobów; czysty Kotlin | Dwa źródła prawdy (SVG dla `.icns`, Kotlin dla UI), ręczna konwersja `rect rx` na ścieżki, rozjazd niewidoczny w testach |
| D. `loadSvgPainter` z `@Suppress("DEPRECATION")` | Najmniej kodu | Przestarzałe API przy `allWarningsAsErrors`; zniknie w kolejnej wersji Compose |

## Konsekwencje

- AM-901 generuje `.icns` z `logo/quayside-logo.svg` (np. `rsvg-convert`/`sips` w build-logic) — bez trzeciej kopii rysunku.
- Zmiana logo to podmiana plików w `logo/`; testy pikselowe `AmLogo` przypinają kolory i miejsca znaku, więc zmiana rysunku wymaga ich aktualizacji.
- Aktualizacja Compose może zmienić API Skia (`SVGDOM`); wtedy padnie kompilacja `core:design-system`, nie aplikacja w runtime.
- Pliki w `docs/screenshots/logo-propozycje-2026-11-09/` zostają jako materiał decyzji; źródłem są zasoby.
