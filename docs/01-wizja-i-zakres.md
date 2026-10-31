# 01 · Wizja i zakres

## Problem

Praca z kilkoma agentami Claude Code naraz rozjeżdża się po wielu oknach terminala. Trudno zauważyć, który agent czeka na odpowiedź lub zgodę, a który skończył albo się wysypał. Sesje są rozproszone po projektach i worktree, a historia leży w plikach JSONL w `~/.claude/projects/`.

## Rozwiązanie

Główna idea: **widzisz wszystkich agentów w jednym oknie i nie przegapisz tego, który na Ciebie czeka.** Rdzeń produktu to przegląd sesji, powiadomienia i terminal. Skróty klawiszowe przyspieszają pracę, ale nie są warunkiem obsługi ([ADR 0015](adr/0015-skroty-jako-dodatek.md)).

Jedno natywne okno na macOS, które:

1. trzyma listę **projektów** (lokalnych katalogów), które użytkownik sam dodaje i usuwa,
2. pokazuje **sesje agentów** w każdym projekcie z ich stanem i krótkim podsumowaniem,
3. pozwala **startować, zatrzymywać, wznawiać** agentów i **odpowiadać** im,
4. ma **pełnoprawny terminal** (attach do agenta, zwykły shell, logi),
5. **powiadamia** popupem i głosem, gdy agent czegoś potrzebuje, z konfiguracją per zdarzenie i per projekt,
6. ma każdą akcję pod ręką **myszą** (przyciski, menu kontekstowe) i w **palecie ⌘K**, a skróty w stylu vima są dodatkiem dla wprawionych,
7. jest **konfigurowalny wizualnie** (schematy kolorów i typografia).

## Użytkownik docelowy

Programista pracujący równolegle z wieloma agentami Claude Code na jednym Macu. Pierwszy użytkownik to autor projektu: projekty Kotlin/Java (Gradle), komunikacja po polsku, czasem niemiecki i angielski.

## Cele (wersja 1.0)

- W **≤ 2 sekundy** od zdarzenia widać, że agent czeka (popup lub głos).
- Odpowiedź na pytanie agenta lub zgoda na narzędzie **bez przełączania okna**: z popupu albo panelu szczegółów.
- Start nowego agenta w projekcie w **≤ 3 akcjach** (⌘N → prompt → Enter).
- Sesje **przeżywają zamknięcie aplikacji**, bo trzyma je supervisor Claude Code, a nie aplikacja.
- Każda akcja na sesji i projekcie dostępna **myszą** (widoczna kontrolka) i z **palety ⌘K**; skróty są opcjonalne.

## Poza zakresem (nie robimy w 1.0)

- Własnej pętli agenta ani własnego klienta API Claude. Korzystamy z Claude Code.
- Sesji w chmurze (Claude Code on the web, Routines) i zdalnego dostępu z telefonu.
- Edytora kodu i przeglądu diffów. Do tego służy IntelliJ IDEA, a ścieżki z terminala otwierają się w IDE.
- Wsparcia dla Windows i Linuksa. Kotlin/Compose to ułatwi, ale nie jest celem.
- Dystrybucji przez Mac App Store. Aplikacja wymaga wyłączonego App Sandbox.

## Słownik

| Pojęcie | Znaczenie |
| --- | --- |
| Projekt | Lokalny katalog (zwykle repo git) dodany do narzędzia przez użytkownika |
| Sesja / agent | Sesja Claude Code działająca w tle pod supervisorem, z krótkim ID (np. `7c5dcf5d`) |
| Supervisor | Proces Claude Code hostujący sesje w tle (`claude daemon`), niezależny od terminala |
| Attach | Podłączenie interaktywnego terminala do sesji (`claude attach <id>`) |
| Hook | Zdarzenie Claude Code (np. `Notification`, `Stop`) wysyłane do aplikacji jako HTTP POST |
| Worktree | Izolowana kopia robocza git, w której sesja w tle edytuje pliki (`.claude/worktrees/…`) |
