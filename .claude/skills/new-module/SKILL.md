---
name: new-module
description: Zakłada nowy feature jako moduły Gradle api / impl / ui zgodnie z konwencją z docs/11-koncepcja-techniczna.md — katalogi, build.gradle.kts z pluginami convention, pakiety, fabryka modułu, szkielet kontraktu i fake'a w testFixtures, wpis w settings.gradle.kts. Użyj, gdy plan zadania wymaga nowego feature.
argument-hint: "<feature> [api,impl,ui]"
arguments: feature parts
---

# /new-module — nowy feature

Feature: `$feature`, części: `$parts` (domyślnie `api,impl,ui`).

Wymaga, by istniały convention plugins z AM-101 (`am.feature.api`, `am.feature.impl`, `am.feature.ui`). Jeśli ich nie ma, zatrzymaj się.

## Kroki

1. Nazwa w kebab-case (`agent-runtime`), pakiet bez myślników (`dev.quayside.agentruntime`).
2. Utwórz pliki według [templates.md](templates.md), podmieniając `{{feature}}` (kebab-case), `{{pkg}}` (pakiet bez myślników), `{{Feature}}` (PascalCase) i `{{feature-camel}}` (camelCase, np. `agentRuntime`).
3. Dopisz do `settings.gradle.kts`: `include(":feature:$feature:api")` itd.
4. Zarejestruj fabrykę modułu w `app` dopiero, gdy implementacja ma treść (osobne zadanie).
5. `./gradlew :feature:$feature:api:build :tests:architecture:test` musi przejść.
6. Commit: `build($feature): scaffold $feature modules`.

Nie dodawaj zależności między nowym `impl` a innym `impl`. Zależności od innych feature tylko przez ich `api`.
