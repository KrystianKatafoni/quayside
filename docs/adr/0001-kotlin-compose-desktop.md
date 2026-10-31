# ADR 0001: Kotlin + Compose Desktop zamiast SwiftUI

- **Status:** przyjęta
- **Data:** 2026-10-18
- **Dotyczy:** cała aplikacja

## Kontekst

Aplikacja działa na macOS 15+. Research ([03](../03-architektura.md)) wskazał SwiftUI + SwiftTerm jako najbardziej natywną opcję. Autor rozwija projekt sam i pracuje na co dzień w Kotlinie i Javie.

## Decyzja

Kotlin (JVM 21) + Compose Multiplatform for Desktop. Terminal: JediTerm + pty4j w `SwingPanel`.

## Rozważone alternatywy

| Opcja | Plusy | Minusy |
| --- | --- | --- |
| SwiftUI + SwiftTerm | Najbardziej natywne, mały pakiet | Nowy język, mniejsza produktywność autora |
| Tauri / Electron | Duży ekosystem webowy | Dwa języki, WebView, większe zużycie RAM (Electron) |
| **Kotlin + Compose** | Znany stack, testowalność, współdzielenie wiedzy z innymi projektami | Pakiet z runtime JVM, start wolniejszy niż natywny |

## Konsekwencje

Do `.dmg` dołączamy okrojony runtime (jpackage). Wymagania NFR-7 pilnują czasu startu i pamięci. Integracje natywne (CoreAudio, Keychain, Dock) przez JNA w adapterach.
