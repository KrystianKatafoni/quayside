# Plany zadań

Każde zadanie z [ROADMAP.md](../ROADMAP.md) dostaje tu plik `AM-XXX.md`, który pisze subagent `architect` przed rozpoczęciem pracy. Plan trafia do tego samego PR co kod, więc recenzent widzi zamiar i wykonanie obok siebie.

Szablon:

```markdown
# AM-XXX · Tytuł

## Zakres
Co dokładnie robimy i czego nie robimy.

## Moduły
Lista modułów, które wolno zmienić. Zmiana poza listą wymaga aktualizacji planu.

## Zmiany API
Nowe lub zmienione typy w `api` (sygnatury w Kotlinie). „Brak”, jeśli nie ma.

## Testy
- [ ] jednostkowe: …
- [ ] kontraktowe: …
- [ ] integracyjne: …
- [ ] funkcjonalne: …

## Ryzyka i pytania
Co może pójść źle; pytania do człowieka (blokują start).
```
