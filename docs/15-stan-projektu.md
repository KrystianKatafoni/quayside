# 15. Stan projektu i kontekst pracy

> Notatka przekazania dla nowych sesji (Mac, sesje w chmurze, telefon). Aktualizuj ją przy zmianie etapu albo ustaleń o środowisku. Postęp zadań jest w [ROADMAP.md](ROADMAP.md) — tu jest kontekst, którego roadmapa nie mówi.
>
> Stan na: 2026-10-31

## Gdzie jesteśmy

- **Nowe repo i nazwa (2026-10-31).** Projekt nazywał się Agent Manager i żył w `KrystianKatafoni/agent-manager`. Przeniesiony do `KrystianKatafoni/quayside` jednym commitem startowym, bez historii i starych PR-ów. Numery issues zaczynają się od nowa; stare numery z planów w `docs/plans/` i ADR-ów dotyczą starego repo.
- **M1–M4 zrobione, M5 prawie:** AM-507 zrobione (ADR 0014 przyjęta), został AM-508.
- **Dalej:** ścieżka krytyczna do 1.0: AM-601 → AM-603 → AM-701–705 → AM-903/904 → AM-905.

Zawsze aktualne źródło: `python3 scripts/roadmap_sync.py --dry-run` i otwarte PR-y.

## Jak pracujemy

- Jedno zadanie = jedna gałąź = jeden PR; **scala tylko człowiek** (squash merge). Po scaleniu `/roadmap-sync`.
- Kolejne zadanie: `/deliver AM-XXX` (lub `claude --agent am-lead`, potem `/deliver`).
- Po rebase własnej gałęzi wolno `git push --force-with-lease` (wyjątek w zasadzie 6 `CLAUDE.md`).
- CI publikuje liczbę testów w adnotacji **„Tests”** checka „Build and test (macOS)” — sesje bez dostępu do logów Actions czytają ją przez `gh api repos/KrystianKatafoni/quayside/check-runs/<id>/annotations`.

## Gdzie można pracować

| Miejsce | Kiedy | Uwagi |
| --- | --- | --- |
| Mac, terminal | główna praca | `claude --agent am-lead --remote-control "Quayside"` — ta sama sesja widoczna na telefonie (aplikacja Claude → Code). Mac musi być wybudzony (`caffeinate -i`). |
| Sesja w chmurze (claude.ai/code lub zakładka Code w aplikacji) | Mac wyłączony | Wynik zawsze jako gałąź + PR. Na Macu potem `git pull` albo `claude --teleport`. |
| GitHub, komentarz `@claude` w issue/PR | drobne poprawki | Wymaga sekretu `CLAUDE_CODE_OAUTH_TOKEN` (patrz niżej). |

## Ustalenia o środowisku

- **Sieć w sesjach w chmurze** (allowlista): `repo.maven.apache.org`, `plugins.gradle.org`, `plugins-artifacts.gradle.org`, `services.gradle.org`, `downloads.gradle.org`, `maven.google.com`, `dl.google.com`. Maven Central bywa przeciążony (HTTP 429) — powtórz build, ogranicz `--max-workers`.
- **GitHub w chmurze:** tylko REST (`gh api …`); polecenia `gh pr create`, `gh issue …` używają GraphQL i nie działają. Oznaczanie PR jako gotowego: `POST repos/…/pulls/<n>/ccr/ready_for_review`.
- **Review w PR („review”)** jest czerwony, dopóki w repo nie ma sekretu `CLAUDE_CODE_OAUTH_TOKEN` — uruchom raz na Macu `claude` → `/install-github-app`. Ten check nie jest wymagany do scalenia.
- **Wymagane checki na `main`** (ruleset): „Docs lint” i „Build and test (macOS)”.
- **Pamięć:** auto memory Claude Code jest lokalne dla maszyny (`~/.claude/projects/…/memory/`) i nie trafia do chmury. Wszystko, co ma być widoczne wszędzie, zapisujemy w repo: wnioski jako linie w sekcji „Lekcje” w `CLAUDE.md`, decyzje jako ADR, plany w `docs/plans/`.
- **Kredyt na sesje w chmurze** (Pro, 100 USD) wygasa 4 listopada 2026; zużywają go tylko sesje uruchomione jako Claude Code w chmurze, nie czat w projekcie claude.ai.

## Decyzje i pułapki warte pamiętania

- Wersje: Gradle 9.7.1, Kotlin 2.4.21, Compose Multiplatform 1.12.2, JDK 21. Zgodność Kotlin ↔ Gradle sprawdzaj w informacji o wydaniu Kotlin przed podbiciem Gradle.
- **detekt 2.0 alfa** (ADR 0008). Bramka używa `detektMain`/`detektTest` (z rozwiązywaniem typów) — zwykły `detekt` przepuszcza np. `println`.
- Pokrycie: 80% linii dla `feature:*:impl`, 90% dla `core:*` (`QualityThresholds` w `build-logic`).
- Reguły modułów działają w dwóch miejscach: Gradle przerywa build przy niedozwolonej zależności (`ModuleRules`), Konsist sprawdza importy, pakiety, widoczność i stan w `object`. Reguła dla modułu, którego jeszcze nie ma, jest „pominięta”, nie „zaliczona”.
- `core:*` i `feature:*:api` używają tego samego pluginu `am.feature.api` (explicit API, `testFixtures`, zależności tylko na `core:domain`, `core:design-tokens`, `core:testing`). Gdy moduł `core` będzie potrzebował innej zależności — osobny plugin `am.core`.
- Testy dispatcherów: `testDispatchers()` z `core:testing`; detekt (`InjectDispatcher`) odrzuca `Dispatchers.IO` itp. użyte wprost, także w testach.

## Otwarte sprawy

- Katalog danych aplikacji zmienił nazwę na `~/Library/Application Support/Quayside/`; dane ze starego `AgentManager/` trzeba raz przenieść ręcznie (`mv`).
- Powiadomienia głosowe hooka (`notify.sh`) działają tylko na macOS; w chmurze są cicho pomijane.
