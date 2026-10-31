# ADR 0012: JediTerm i pty4j w SwingPanel jako terminal aplikacji

- **Status:** przyjęta (wynik spike'a AM-501: TAK)
- **Data:** 2026-10-19
- **Dotyczy:** `feature:terminal:*`, `app`, `settings.gradle.kts`, `gradle/libs.versions.toml`

## Kontekst

ADR 0001 wybrał JediTerm + pty4j w `SwingPanel`. docs/09 oznacza osadzenie w Compose jako ryzyko średnie (fokus, skróty, HiDPI); plan B to osobne okno Swing. Spike AM-501 sprawdza to na macOS (plan: `docs/plans/AM-501.md`).

Fakty o bibliotekach (stan na 2026-10-19):

| Biblioteka | Wersja | Źródło | Licencja |
| --- | --- | --- | --- |
| `org.jetbrains.jediterm:jediterm-core`, `jediterm-ui` | 3.76 | tylko `packages.jetbrains.team/maven/p/ij/intellij-dependencies` (brak w Maven Central) | POM: LGPL-3.0; README: LGPLv3 lub Apache 2.0 do wyboru |
| `org.jetbrains.pty4j:pty4j` | 0.14.0 | Maven Central | EPL-1.0; zależy od JNA 5.19.1 (Apache 2.0/LGPL) |

## Decyzja

Przyjęte przez Krystiana 2026-10-19 (przed spike'iem):

1. **Repozytorium.** `https://packages.jetbrains.team/maven/p/ij/intellij-dependencies` dodajemy w `settings.gradle.kts` (`dependencyResolutionManagement`) wyłącznie przez `exclusiveContent` z filtrem `includeGroup("org.jetbrains.jediterm")`. Pozostałe zależności dalej pochodzą tylko z Maven Central i Google.
2. **Licencje.** JediTerm używamy na licencji Apache 2.0, pty4j na EPL-1.0, JNA na Apache 2.0. Teksty licencji trafiają do pakietu `.dmg` w AM-901.

Przyjęte po spike'u AM-501 (wynik TAK według kryterium z planu; obserwacje Krystiana 2026-10-19):

1. **Osadzenie.** Terminal to `JediTermWidget` z `pty4j` w `feature:terminal:impl`, osadzany w głównym oknie Compose przez `SwingPanel` w `terminal:ui`. Typy JediTerm i pty4j nie wychodzą poza `terminal:impl` (ENG-I8). `terminal:api` udostępnia tylko `javax.swing.JComponent` (docs/11 §3).
2. **Kolejność rysowania.** Aplikacja włącza `compose.interop.blending=true`. Bez tego `SwingPanel` rysuje się nad Compose i zasłania paletę ⌘K oraz popupy (obserwacja F6).
3. **Skróty aplikacji.** Skróty aplikacji (⌘K i kolejne) są przechwytywane przed komponentem z fokusem przez `KeyEventDispatcher` w `app`/`terminal:ui`. JediTerm na macOS ma domyślnie ⌘K jako „Clear Buffer” (`DefaultSettingsProvider.getClearBufferActionPresentation()`, `KeyStroke(VK_K, META_DOWN_MASK)`), więc `TerminalWidgetSettings` odpina tę akcję od ⌘K. Inne domyślne skróty JediTerm na macOS: ⌘C, ⌘V, ⌘F, ⌘↑/⌘↓, ⇧PgUp/⇧PgDn — przy kolejnych skrótach aplikacji sprawdzać kolizje. Realizacja: AM-502/AM-503.

## Wyniki spike'a

Środowisko: macOS 15.7.3, Mac16,8, wbudowany ekran Liquid Retina XDR (3024 × 1964) i monitor 5K. Nowe jary: jediterm-core i jediterm-ui (ok. 0,4 MB), pty4j 0.14.0 (2,8 MB), JNA i jna-platform 5.19.1 (3,4 MB) — razem ok. 6,3 MB.

Warianty: F1, F2, F5 sprawdzone w zwykłym uruchomieniu; pozostałe z `-PspikeBlending=true` i „Capture app shortcuts: true”.

| Obserwacja | Wynik | Uwaga |
| --- | --- | --- |
| F1 fokus w terminalu | TAK | |
| F2 pole Compose obok terminala | TAK | |
| F3 ⌘K przy fokusie w terminalu | TAK z przechwytywaniem | Bez przechwytywania NIE: ⌘K czyści bufor (JediTerm „Clear Buffer”), w logu `TerminalTextBuffer - Attempt to get line out of bounds: -1 < 0` |
| F4 Esc oddaje fokus terminalowi | TAK | z blending |
| F5 Tab w zsh | TAK | |
| F6 paleta nad terminalem | TAK z blending | Bez blending NIE: terminal zasłania paletę |
| K1 ⌘C/⌘V, wklejanie wielu linii | TAK | |
| K2 Ctrl-C | TAK | |
| K3 strzałki (historia, `less`, `vim`) | TAK | |
| K4 Esc w `vim` | TAK | |
| K5 ⌥B/⌥F, ⌥A | ⌥B/⌥F skaczą po słowach; ⌥A nie daje „ą” | Option jako Meta koliduje z polską klawiaturą — decyzja w AM-504 (np. Meta tylko na lewym Option) |
| H1 ostrość na Retinie | TAK | |
| H2 ekran o innej skali | TAK | |
| H3 ligatury | TAK | informacyjnie |
| R1 resize i `stty size` | TAK | |
| R2 szybkie przeciąganie krawędzi | artefakty | Przy szybkim przeciąganiu pojawiają się śmieci; nie blokuje — do obserwacji w AM-503 |
| R3 ukryj/pokaż terminal | TAK | sesja i ekran zostają |
| P1 `seq 1 200000` | TAK | < 3 s, Compose bez zacięć |
| Z1 sieroty po zamknięciu okna i ⌘Q | TAK | nic nie zostało |
| B1 plan B (osobne okno) | niezweryfikowane | „na pierwszy rzut oka działa”; niepotrzebne, bo kryterium TAK spełnione |

Kryterium TAK (F1–F5, K1–K4, H1, R1, Z1) jest spełnione pod warunkami 2 i 3 z decyzji przyjętych po spike'u.

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| **JediTerm w `SwingPanel`** | Terminal w układzie okna (panele, tryb terminala z docs/06) | Ryzyko fokusu, skrótów i kolejności rysowania nad Compose |
| JediTerm w osobnym oknie Swing (plan B) | Brak problemów interop Compose–Swing | Brak paneli w Mission Control; osobne okno do zarządzania |
| Jary JediTerm w repo zamiast repozytorium JetBrains | Brak nowego repozytorium | Ręczne aktualizacje, binaria w git, brak metadanych POM |
| JediTerm budowany ze źródeł (included build) | Tylko Maven Central | Dłuższy build, utrzymanie builda obcego projektu |

## Konsekwencje

- Nowe repozytorium Maven w buildzie (ograniczone do jednej grupy); w sesjach w chmurze może być poza allowlistą sieci, wtedy build weryfikuje CI.
- pty4j wypakowuje natywne biblioteki do katalogu tymczasowego; podpis i notaryzacja (AM-902) muszą to uwzględnić (`pty4j.preferred.native.folder`).
- Okno testowe `app/src/terminalSpike` żyje do AM-503, potem jest usuwane.
- ADR 0001 w części „Terminal” pozostaje w mocy; plan B (osobne okno) nie jest potrzebny.
- `compose.interop.blending` jest flagą Compose Desktop — przy aktualizacji Compose sprawdzać, czy nadal działa (F6).
- Option jako Meta i polskie znaki: do rozstrzygnięcia w AM-504.
- Artefakty przy szybkim resize (R2): do obserwacji w AM-503.
