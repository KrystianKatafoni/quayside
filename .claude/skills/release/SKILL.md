---
name: release
description: Przygotowuje wydanie — wersja SemVer z Conventional Commits, CHANGELOG, gałąź release/vX.Y.Z i PR; po scaleniu i potwierdzeniu człowieka tag vX.Y.Z uruchamiający workflow release. Użyj, gdy człowiek chce wydać wersję lub sprawdzić, co weszłoby do wydania.
argument-hint: "[prepare|tag] [X.Y.Z]"
disable-model-invocation: true
---

# /release

Tryb: `$ARGUMENTS` (domyślnie `prepare`).

- Ostatni tag: !`git describe --tags --abbrev=0 2>/dev/null || echo "brak"`
- Commity od tagu: !`git log $(git describe --tags --abbrev=0 2>/dev/null || git rev-list --max-parents=0 HEAD | tail -1)..HEAD --oneline 2>/dev/null | head -40 || true`

## prepare

Deleguj do subagenta `release-manager` (kroki 1–5 z jego instrukcji). Przed utworzeniem PR pokaż człowiekowi wyliczoną wersję i changelog; zmiana wersji wymaga jego zgody.

## tag

Tylko gdy PR wydania jest scalony i człowiek wprost potwierdził w tej rozmowie:

```bash
git switch main && git pull --ff-only
git tag -a vX.Y.Z -m "vX.Y.Z"
git push origin vX.Y.Z
```

Potem `gh run list --workflow release.yml --limit 1` i podaj link do przebiegu oraz (po zakończeniu) do GitHub Release.
