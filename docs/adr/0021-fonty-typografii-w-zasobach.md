# ADR 0021: Fonty typografii w zasobach `core:design-system`

- **Status:** przyjęta (zaakceptował człowiek 2026-11-03, PR scalony)
- **Data:** 2026-11-03
- **Dotyczy:** `core:design-system` (zasoby `fonts/`), AM-802, AM-805, AM-901 (licencje w `.dmg`)

## Kontekst

8 podstawowych typografii ([08](../08-wyglad.md)) używa 14 rodzin fontów: IBM Plex Sans, IBM Plex Mono, Geist, Geist Mono, Instrument Sans, DM Mono, JetBrains Mono, Chakra Petch, Fira Code, Space Grotesk, Space Mono, Atkinson Hyperlegible Next, Atkinson Hyperlegible Mono, Martian Mono. Wszystkie są na licencji SIL Open Font License 1.1 (OFL). Pliki fontów trafiają do repozytorium i do pakietu aplikacji, więc źródło, format, zakres krojów i sposób dołączenia licencji to decyzje trwałe. AM-805 dołoży 8 kolejnych rodzin tą samą drogą.

## Decyzja

Fonty pobieramy z repozytorium [google/fonts](https://github.com/google/fonts) (katalog `ofl/<rodzina>/`) z **przypiętego commita**. Bierzemy plik zmienny (`[wght]`, `[wdth,wght]`), gdy katalog go ma; w przeciwnym razie pliki statyczne Regular i SemiBold (gdy go brak: Medium, a dla Space Mono: Bold). Bez kursyw i bez przycinania znaków (subsetting to modyfikacja w rozumieniu OFL, a część rodzin ma Reserved Font Name). Pliki leżą w `core/design-system/src/main/resources/fonts/<katalog>/` razem z `OFL.txt` tej rodziny. `fonts/SOURCES.md` podaje commit, URL i SHA-256 każdego pliku. Fonty ładuje Compose przez `Font(resource = …, weight = …)`; dla pliku zmiennego ta sama ścieżka występuje z wagą 400 i 600. Pliki trzymamy w zwykłym Gicie (bez LFS).

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| google/fonts, przypięty commit, pliki zmienne (wybrana) | jedno źródło dla 22 rodzin (też AM-805), `OFL.txt` obok każdej rodziny, mniej plików | duże pliki zmienne (osie, których nie używamy, np. `wdth`); trzeba sprawdzić, że Compose Desktop stosuje oś `wght` (test) |
| Wydania autorów (IBM, Vercel, JetBrains …), pliki statyczne | statyczne kroje, czasem nowsze wersje | 14 różnych źródeł i formatów archiwów, trudniejsza powtarzalność |
| Pobieranie fontów przy pierwszym uruchomieniu | mały pakiet | sieć w aplikacji (wbrew NFR prywatności i pracy offline), więcej kodu i błędów |
| Git LFS | lżejsza historia | dodatkowa konfiguracja CI i klonowania; przy ok. 10 MB nie jest potrzebny |

## Konsekwencje

- Repozytorium i pakiet `.dmg` rosną o kilka MB (szacunek 5–10 MB dla 14 rodzin; dokładny rozmiar podaje `SOURCES.md`).
- Aktualizacja fontu = nowy commit źródła, nowe sumy SHA-256 i przegląd zmian licencji.
- AM-901 dołącza teksty `OFL.txt` do listy licencji pakietu (jak w ADR 0012).
- Font terminala (JediTerm, AWT) nie korzysta jeszcze z tych plików; rejestracja w AWT to osobne zadanie.
- Test w `core:design-system` sprawdza, że każdy plik ma polskie znaki i rozróżnialne glify `0O1lI`, więc nowa rodzina z AM-805 przechodzi ten sam test.
