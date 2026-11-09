# Backlog pomysłów

Luźna lista pomysłów na przyszłość. To **nie jest zakres**: żaden z wpisów nie ma konceptu, wymagań ani wpisu w [ROADMAP.md](ROADMAP.md). Gdy pomysł dojrzeje, trafia do wymagań (`02-wymagania.md`) i roadmapy w osobnym PR.

Format wpisu: krótki opis, bez rozwiązań.

## 1. Wiele kont Claude

Obsługa wielu loginów/kont Claude naraz. Gdy limit jednej subskrypcji się wyczerpie, aplikacja automatycznie przełącza się na inne konto. Użytkownik może też zarządzać kontami ręcznie (wybór konta, wyłączenie automatyki). Plan i pytania: [ADR 0029](adr/0029-wiele-kont-claude.md) (proponowana).

## 2. Import i eksport konfiguracji

Możliwość wyeksportowania całej konfiguracji aplikacji do pliku i zaimportowania jej na innym komputerze lub po reinstalacji.

## 3. Obsługa sesji z telefonu

Podgląd i obsługa sesji agentów zdalnie z telefonu: lista sesji, stan, odpowiedź agentowi, który czeka. Telefon łączy się bezpośrednio z aplikacją na macOS albo przez serwer pośredniczący (relay), gdy Mac nie jest osiągalny bezpośrednio. W 1.0 jest to poza zakresem (`01-wizja-i-zakres.md`, sekcja „Poza zakresem”).

## 4. Sandbox Docker dla agenta

Uruchamianie agenta Claude Code w kontenerze Docker ze wszystkimi uprawnieniami (bez pytań o zgodę), odizolowanego od hosta, z dostępem do git. Wstępny kierunek i ryzyka: [ADR 0028](adr/0028-sandbox-docker-dla-agenta.md) (proponowana).
