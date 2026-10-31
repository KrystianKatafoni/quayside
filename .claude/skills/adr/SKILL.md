---
name: adr
description: Tworzy nowy Architecture Decision Record w docs/adr z kolejnym numerem i szablonu, ze statusem „proponowana”. Użyj, gdy zapada decyzja o trwałych skutkach (biblioteka, format danych, granice modułów) lub gdy trzeba zastąpić przyjęty ADR.
argument-hint: "<tytuł decyzji>"
allowed-tools: Read, Write, Glob, Bash(ls docs/adr*)
---

# /adr — nowy ADR

Tytuł: `$ARGUMENTS`

Istniejące: !`ls docs/adr 2>/dev/null || true`

1. Numer = najwyższy istniejący + 1, czterocyfrowo. Nazwa pliku: `NNNN-<tytuł-kebab-bez-polskich-znaków>.md`.
2. Treść z `docs/adr/0000-template.md`. Status `proponowana`, data dzisiejsza, sekcja „Dotyczy” z modułami.
3. Kontekst i alternatywy wypełnij konkretnie (tabela plusów i minusów). Decyzja w 1–2 akapitach.
4. Jeśli zastępuje inny ADR, napisz to w statusie nowego. Przyjętego ADR nie edytuj (blokuje to hook); zmianę statusu starego na „zastąpiona przez NNNN” robi człowiek przy scalaniu.
5. ADR trafia do PR razem z planem lub kodem, którego dotyczy. Status „przyjęta” ustawia człowiek przed scaleniem.
