# 13 · Plan wdrożenia

Plan opisuje, jak przejść od dokumentacji do wydanej wersji 1.0 i jak potem utrzymywać aplikację. Kod pisze głównie agent Claude Code skonfigurowany w tym repo ([14-agent-claude-code.md](14-agent-claude-code.md)). Człowiek wyznacza kierunek, przegląda i scala PR-y oraz wydaje wersje. Szczegółowa lista zadań to [ROADMAP.md](ROADMAP.md). Ten dokument doprecyzowuje etapy z [09](09-plan-i-otwarte-kwestie.md).

## 1. Zasady

1. **Małe kroki, zawsze działający `main`.** Jedno zadanie z roadmapy = jedna gałąź = jeden PR. Każdy PR przechodzi pełną bramkę `./gradlew check`.
2. **Agent dowozi, człowiek decyduje.** Agent tworzy gałęzie, commity, pushe i PR-y. **Nie scala** do `main` i nie wydaje wersji bez polecenia.
3. **Najpierw kontrakt i testy.** Każde zadanie zaczyna się od planu (moduły, API, lista testów), potem testy, potem kod ([10](10-wymagania-inzynierskie.md), [12](12-strategia-testow.md)).
4. **Dogfooding od M4.** Gdy lista sesji działa, autor używa aplikacji do zarządzania agentem, który ją buduje.

## 2. Warunki wstępne (jednorazowo)

| # | Krok | Kto | Jak |
| --- | --- | --- | --- |
| 1 | Repo GitHub `quayside` (publiczne) | człowiek | github.com → New repository, bez README |
| 2 | Ochrona gałęzi `main`: wymagany PR i zielony status `CI / build` oraz `CI / docs`, zakaz force-push | człowiek | Settings → Branches (lub ruleset) |
| 3 | Claude GitHub App + sekret `CLAUDE_CODE_OAUTH_TOKEN` | człowiek | `claude` w repo → `/install-github-app` albo `claude setup-token` i Settings → Secrets |
| 4 | Labele, milestone'y i issues z roadmapy | agent | `scripts/bootstrap-github.sh` (wywołuje `/roadmap-sync`) |
| 5 | JDK 21, Claude Code ≥ 2.1.139, `gh auth login` na Macu | człowiek | `brew install --cask temurin@21`, `brew install gh` |
| 6 | Certyfikat Developer ID i dane notaryzacji jako sekrety (przed M9) | człowiek | `MACOS_CERT_P12_BASE64`, `MACOS_CERT_PASSWORD`, `APPLE_ID`, `APPLE_TEAM_ID`, `APPLE_APP_PASSWORD` |

## 3. Etapy

Szacunki zakładają pracę agenta z codziennym przeglądem PR-ów przez autora. Są orientacyjne i będą korygowane po M1.

| Etap | Cel | Kryterium wyjścia | Punkt kontrolny człowieka | Szac. |
| --- | --- | --- | --- | --- |
| **M0** Fundament | Repo, dokumentacja, agent, automatyzacja GitHub | Ten PR scalony; `/deliver` działa na pustej roadmapie M1 | Przegląd konfiguracji agenta i uprawnień | 1 dzień |
| **M1** Szkielet | Moduły `core`, `build-logic`, testy architektury, CI na macOS | `./gradlew check` zielony w CI; okno aplikacji się otwiera | Akceptacja struktury modułów (ADR 0003) | 1 tydz. |
| **M2** Claude Code | `agent-runtime`: parser, adapter CLI, health check | Testy kontraktowe na fałszywym `claude` przechodzą; ręczny test na prawdziwym Claude Code | Weryfikacja na własnym Macu | 1 tydz. |
| **M3** Projekty | Dodawanie i usuwanie projektów, sidebar | Scenariusze funkcjonalne 1–2 z [12](12-strategia-testow.md) | Przegląd UX dialogu usuwania | 1 tydz. |
| **M4** Sesje | Mission Control: lista, szczegóły, start/stop, ⌘K | Scenariusz 3; **v0.1.0** (pierwsza używalna wersja) | Start dogfoodingu | 1,5 tyg. |
| **M5** Terminal | JediTerm, karty, panele, szukanie | Attach do sesji z aplikacji działa | Ocena wygody terminala | 1,5 tyg. |
| **M6** Hooki | Serwer hooków, instalator, podsumowania | Stan w ≤ 1 s; scenariusz 7; **v0.2.0** | Zgoda na zmianę `~/.claude/settings.json` | 1 tydz. |
| **M7** Powiadomienia | Popup, głos, reguły, cisza nocna | Scenariusze 4–6; **v0.3.0** | Strojenie głosu i popupów w praktyce | 1,5 tyg. |
| **M8** Wygląd | 9 schematów × 8 typografii, dzień/noc | Scenariusz 8; test kontrastu zielony | Wybór domyślnego zestawu | 1 tydz. |
| **M9** Wydanie | `.dmg`, podpis, notaryzacja, Dock, menu bar | Komplet 9 scenariuszy; **v1.0.0** | Decyzja o wydaniu | 1 tydz. |

## 4. Cykl dostarczenia zadania

Każde zadanie z roadmapy przechodzi ten sam cykl, uruchamiany przez `/deliver` (szczegóły w [14](14-agent-claude-code.md)):

1. **Wybór**: pierwsze zadanie `[ ]` z roadmapy, którego zależności są `[x]`. Utworzenie lub podpięcie issue.
2. **Gałąź**: `feat/AM-XXX-krotki-opis` od aktualnego `main`.
3. **Plan**: subagent `architect` zapisuje `docs/plans/AM-XXX.md`: moduły, zmiany API, lista testów, ryzyka. Zmiana `api` innego feature lub nowy ADR → **stop i pytanie do człowieka**.
4. **Testy**: subagent `test-engineer` pisze testy, które na początku nie przechodzą.
5. **Implementacja**: subagent `implementer` doprowadza testy do zielonego, tylko w modułach z planu.
6. **Bramka**: `./gradlew check` (maks. 3 rundy poprawek, potem eskalacja).
7. **Review**: subagent `reviewer` sprawdza zmianę z checklistą z [10](10-wymagania-inzynierskie.md). Uwagi blokujące muszą zostać naprawione.
8. **Wysyłka**: `/ship` tworzy commity (Conventional Commits, `Refs: #N`), pushuje gałąź i otwiera PR z wypełnionym szablonem. Wpis w roadmapie zmienia się na `[~]`.
9. **Scalenie**: człowiek przegląda, ewentualnie pisze `@claude` w komentarzu z poprawkami i scala (squash). Po scaleniu `/roadmap-sync` oznacza zadanie jako `[x]`.

## 5. Gałęzie, commity, PR-y

| Element | Reguła |
| --- | --- |
| Model | Trunk-based: krótkie gałęzie od `main`, squash merge |
| Nazwy gałęzi | `feat/AM-XXX-opis`, `fix/AM-XXX-opis` lub `fix/issue-N-opis`, `chore/…`, `docs/…`, `release/vX.Y.Z` |
| Commity | Conventional Commits po angielsku: `feat(sessions): add state reducer`, stopka `Refs: #N` |
| PR | Tytuł jak commit, opis po polsku wg szablonu: zakres, zmienione moduły, testy, checklista DoD, `Closes #N` |
| Rozmiar | ≤ 400 linii kodu (bez testów i fixtures); większe zadanie dzielimy w roadmapie |
| Scalanie | Tylko człowiek, po zielonym CI. Agent nigdy nie pushuje do `main` i nie używa `--force` (blokuje to hook) |

## 6. Wersje i wydania

- **SemVer**, `0.x` do wydania 1.0. Wersje pośrednie: v0.1.0 (M4), v0.2.0 (M6), v0.3.0 (M7), v1.0.0 (M9).
- `/release` wylicza wersję z Conventional Commits od ostatniego tagu, aktualizuje `CHANGELOG.md`, tworzy gałąź `release/vX.Y.Z` i PR. Po scaleniu człowiek (lub agent na polecenie) taguje `vX.Y.Z`.
- Tag `v*` uruchamia workflow `release.yml`: build `.dmg` na macOS, podpis i notaryzacja (gdy są sekrety), GitHub Release z plikiem i changelogiem.

## 7. Wdrożenie u użytkownika

1. Pobranie `.dmg` z GitHub Releases, przeciągnięcie do Aplikacji.
2. Pierwsze uruchomienie: kreator sprawdza Claude Code (wersja, Agent View), proponuje instalację hooków z podglądem zmian w `~/.claude/settings.json` i kopią zapasową, prosi o zgodę na powiadomienia.
3. Dodanie pierwszego projektu z dysku.
4. Aktualizacje: na start ręcznie z GitHub Releases; automatyczne aktualizacje (np. Sparkle) jako zadanie po 1.0.

## 8. Utrzymanie po wydaniu

| Mechanizm | Częstotliwość | Co robi |
| --- | --- | --- |
| `@claude` w issue/PR | na żądanie | Agent w GitHub Actions odpowiada, poprawia, pushuje na gałąź PR |
| Review PR (`claude-review.yml`) | każdy PR | Skill `/review-pr` komentuje zgodność z wymaganiami inżynierskimi |
| Kompatybilność Claude Code (`maintenance.yml`) | co tydzień | `/compat-check`: nowa wersja Claude Code, zmiany w Agent View, hookach, `agents --json` → issue z etykietą `compat` |
| Dependabot | co tydzień | PR-y z aktualizacjami Gradle i GitHub Actions; CI je weryfikuje |
| Zgłoszenia | na żądanie | `/triage` zamienia opis problemu w issue z modułem, krokami odtworzenia i priorytetem |
| Hotfix | na żądanie | `fix/…` od `main` → PR → patch release `vX.Y.Z+1` |

## 9. Ryzyka wdrożenia

| Ryzyko | Działanie |
| --- | --- |
| Agent przekracza zakres zadania | Plan z listą modułów, hook blokujący zmiany konfiguracji agenta, review z sekcją „zmienione moduły” |
| Pętla poprawek bez końca | Maks. 3 rundy w `/deliver` i limit w hooku `Stop`, potem eskalacja do człowieka |
| Zmiany w Claude Code (research preview) | Cotygodniowy `/compat-check`, fixtures w testach kontraktowych |
| Brak certyfikatu Apple przed M9 | Wersje 0.x jako niepodpisane buildy do użytku własnego |
| Koszt tokenów | Jedno zadanie na sesję, `--max-turns` w GitHub Actions, review tylko na PR-ach gotowych do przeglądu |

## 10. Mierniki postępu

- Zadania `[x]` / wszystkie w [ROADMAP.md](ROADMAP.md) (aktualizowane przez `/roadmap-sync`).
- Czas od otwarcia PR do scalenia i liczba rund poprawek po review.
- Odsetek PR-ów zielonych za pierwszym razem w CI.
- Pokrycie testami (Kover) i liczba naruszeń architektury (cel: 0).
