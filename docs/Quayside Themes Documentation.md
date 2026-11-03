# System Motywów UI/UX: Quayside Dashboard

> Referencja źródłowa. Kanoniczny opis motywów (klucze, tokeny Kotlina, korekty kontrastu, zamiany fontów) jest w [08-wyglad.md](08-wyglad.md).

Niniejszy dokument zawiera kompletną definicję zmiennych CSS dla 20 profesjonalnych motywów (10 jasnych i 10 ciemnych) zaprojektowanych dla narzędzia deweloperskiego Quayside. System opiera się na zmiennych CSS (Custom Properties), co pozwala na dynamiczną zmianę wyglądu aplikacji w czasie rzeczywistym.

## Jak korzystać z systemu?

Aby zaimplementować motywy, umieść wybrane bloki CSS w głównym pliku stylów (np. `global.css`). Zmiana motywu odbywa się poprzez dodanie atrybutu `data-theme` do tagu `<body>` lub `<html>`, np:
`<body data-theme="dracula">`

---

## I. Motywy Jasne (Light Themes)

Zaprojektowane z myślą o czytelności w dobrze oświetlonych biurach. Skupiają się na delikatnych kontrastach i precyzyjnej typografii.

### 1. Nordic Slate (Domyślny Jasny)

Chłodny, stonowany i bardzo czysty. Inspirowany skandynawskim minimalizmem.

* **Czcionki:** Plus Jakarta Sans (UI), JetBrains Mono (Terminal)
* **Kształty:** Zaokrąglenia 8px, delikatne cienie.

![Screenshot Nordic Slate](./screenshots/light_01_nordic_slate.png)

```css
body[data-theme="nordic-light"] {
    --bg-main: #f4f6f8;
    --bg-panel: #ffffff;
    --bg-terminal: #1e242c;
    --text-main: #2d3748;
    --text-muted: #718096;
    --accent: #3182ce;
    --border-color: #e2e8f0;
    --border-width: 1px;
    --border-radius: 8px;
    --shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);
    --font-sans: 'Plus Jakarta Sans', sans-serif;
    --font-mono: 'JetBrains Mono', monospace;
    --panel-backdrop: none;
}
```

### 2. Cyber Ink Light

Kontrastowy, o niemal komiksowym, płaskim sznycie. Wykorzystuje grube krawędzie.

* **Czcionki:** Space Grotesk (UI), Fira Code (Terminal)
* **Kształty:** Ostre kąty (0px), grube ramki (2px), brak cieni.

![Screenshot Cyber Ink Light](./screenshots/light_02_cyber_ink.png)

```css
body[data-theme="cyber-light"] {
    --bg-main: #f3f4f6;
    --bg-panel: #ffffff;
    --text-main: #111827;
    --text-muted: #6b7280;
    --accent: #8b5cf6;
    --border-color: #111827;
    --border-width: 2px;
    --border-radius: 0px;
    --shadow: none;
    --font-sans: 'Space Grotesk', sans-serif;
    --font-mono: 'Fira Code', monospace;
    --panel-backdrop: none;
}
```

### 3. Minimal Sand

Organiczny, bardzo ciepły motyw chroniący wzrok. Przypomina papierowy notatnik.

* **Czcionki:** Outfit (UI), Courier Prime (Terminal)
* **Kształty:** Duże zaokrąglenia (12px), bardzo subtelne obramowania.

![Screenshot Minimal Sand](./screenshots/light_03_minimal_sand.png)

```css
body[data-theme="sand-light"] {
    --bg-main: #fdfbf7;
    --bg-panel: #f5f2eb;
    --text-main: #3a3832;
    --text-muted: #8a867a;
    --accent: #6b705c;
    --border-color: rgba(0,0,0,0.05);
    --border-width: 1px;
    --border-radius: 12px;
    --shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.02);
    --font-sans: 'Outfit', sans-serif;
    --font-mono: 'Courier Prime', monospace;
    --panel-backdrop: none;
}
```

### 4. High-Contrast Accessibility

Ekstremalnie czytelny motyw bez odcieni szarości w tle.

* **Czcionki:** IBM Plex Sans (UI), IBM Plex Mono (Terminal)
* **Kształty:** Małe zaokrąglenia (4px), grube i czarne linie, brak cieni.

![Screenshot High Contrast](./screenshots/light_04_high_contrast.png)

```css
body[data-theme="hc-light"] {
    --bg-main: #ffffff;
    --bg-panel: #ffffff;
    --text-main: #000000;
    --text-muted: #4a4a4a;
    --accent: #0052cc;
    --border-color: #000000;
    --border-width: 2px;
    --border-radius: 4px;
    --shadow: none;
    --font-sans: 'IBM Plex Sans', sans-serif;
    --font-mono: 'IBM Plex Mono', monospace;
    --panel-backdrop: none;
}
```

### 5. Steel & Cobalt

Klasyczny, korporacyjny styl narzędziowy nawiązujący do wczesnych interfejsów webowych, ale w nowoczesnym wydaniu.

* **Czcionki:** Inter (UI), Fira Code (Terminal)
* **Kształty:** Zaokrąglenia 4px, standardowe szare cienie.

![Screenshot Steel Cobalt](./screenshots/light_05_steel_cobalt.png)

```css
body[data-theme="steel-light"] {
    --bg-main: #ebedf0;
    --bg-panel: #ffffff;
    --text-main: #172b4d;
    --text-muted: #6b778c;
    --accent: #0052cc;
    --border-color: #c1c7d0;
    --border-width: 1px;
    --border-radius: 4px;
    --shadow: 0 1px 3px rgba(9, 30, 66, 0.13);
    --font-sans: 'Inter', sans-serif;
    --font-mono: 'Fira Code', monospace;
    --panel-backdrop: none;
}
```

### 6. Teal & Obsidian

Świeży, morski motyw korzystający z chłodnych pastelowych barw dla lepszego skupienia.

* **Czcionki:** DM Sans (UI), JetBrains Mono (Terminal)
* **Kształty:** Zaokrąglenia 12px, kolorowe (turkusowe) cienie i obramowania.

![Screenshot Teal Obsidian](./screenshots/light_06_teal_obsidian.png)

```css
body[data-theme="teal-light"] {
    --bg-main: #f0fdfa;
    --bg-panel: #ffffff;
    --text-main: #0f172a;
    --text-muted: #64748b;
    --accent: #0d9488;
    --border-color: #99f6e4;
    --border-width: 1px;
    --border-radius: 12px;
    --shadow: 0 4px 6px -1px rgba(13, 148, 136, 0.1);
    --font-sans: 'DM Sans', sans-serif;
    --font-mono: 'JetBrains Mono', monospace;
    --panel-backdrop: none;
}
```

### 7. Terminal Classic (Retro Light)

Stylizowany na stare ekrany wektorowe na jasnym papierze.

* **Czcionki:** PT Mono (globalnie)
* **Kształty:** Całkowicie ostre (0px), czarne ramki, surowy układ.

![Screenshot Terminal Classic](./screenshots/light_07_terminal_classic.png)

```css
body[data-theme="term-light"] {
    --bg-main: #f0f0f0;
    --bg-panel: #ffffff;
    --text-main: #333333;
    --text-muted: #808080;
    --accent: #008000;
    --border-color: #333333;
    --border-width: 1px;
    --border-radius: 0px;
    --shadow: none;
    --font-sans: 'PT Mono', monospace;
    --font-mono: 'PT Mono', monospace;
    --panel-backdrop: none;
}
```

### 8. Amethyst Glass

Bardzo nowoczesny, bazujący na efekcie matowego szkła (glassmorphism) nad fioletowym tłem.

* **Czcionki:** Outfit (UI), Ubuntu Mono (Terminal)
* **Kształty:** Zaokrąglenia 16px, półprzezroczyste panele z rozmyciem.

![Screenshot Amethyst Glass](./screenshots/light_08_amethyst_glass.png)

```css
body[data-theme="glass-light"] {
    --bg-main: #f3f0ff;
    --bg-panel: rgba(255, 255, 255, 0.6);
    --text-main: #3b2a5c;
    --text-muted: #796696;
    --accent: #8b5cf6;
    --border-color: rgba(139, 92, 246, 0.2);
    --border-width: 1px;
    --border-radius: 16px;
    --shadow: 0 8px 32px 0 rgba(139, 92, 246, 0.1);
    --font-sans: 'Outfit', sans-serif;
    --font-mono: 'Ubuntu Mono', monospace;
    --panel-backdrop: blur(12px);
}
```

### 9. Oatmeal & Terracotta

Alternatywne podejście odchodzące od informatycznego rygoru, dające "ludzki", rzemieślniczy wygląd.

* **Czcionki:** DM Sans (UI), Courier Prime (Terminal)
* **Kształty:** Zaokrąglenia 16px, grubsze, rudawe obramowania.

![Screenshot Oatmeal Terracotta](./screenshots/light_09_oatmeal_terracotta.png)

```css
body[data-theme="oatmeal-light"] {
    --bg-main: #f9f5f0;
    --bg-panel: #ffffff;
    --text-main: #4a3f35;
    --text-muted: #8c7e70;
    --accent: #d97757;
    --border-color: #e6dace;
    --border-width: 2px;
    --border-radius: 16px;
    --shadow: 0 4px 0px rgba(217, 119, 87, 0.1);
    --font-sans: 'DM Sans', sans-serif;
    --font-mono: 'Courier Prime', monospace;
    --panel-backdrop: none;
}
```

### 10. Corporate Emerald

Poważny, szmaragdowy motyw pasujący do systemów finansowych i enterprise.

* **Czcionki:** IBM Plex Sans (UI), IBM Plex Mono (Terminal)
* **Kształty:** Kwadratowe lub z mikroskopijnym zaokrągleniem (2px), bardzo czyste marginesy.

![Screenshot Corporate Emerald](./screenshots/light_10_corporate_emerald.png)

```css
body[data-theme="emerald-light"] {
    --bg-main: #f0fdf4;
    --bg-panel: #ffffff;
    --text-main: #064e3b;
    --text-muted: #059669;
    --accent: #059669;
    --border-color: #a7f3d0;
    --border-width: 1px;
    --border-radius: 2px;
    --shadow: 0 1px 2px rgba(6, 78, 59, 0.1);
    --font-sans: 'IBM Plex Sans', sans-serif;
    --font-mono: 'IBM Plex Mono', monospace;
    --panel-backdrop: none;
}
```

---

## II. Motywy Ciemne (Dark Themes)

Zaprojektowane w celu ochrony wzroku podczas długich sesji programistycznych, ze specjalnym naciskiem na balans kontrastów.

### 1. Midnight Pro (Domyślny Ciemny)

Profesjonalny, bezpieczny ciemnogranatowy motyw popularny w nowoczesnych narzędziach SaaS.

* **Czcionki:** Inter (UI), JetBrains Mono (Terminal)
* **Kształty:** Zaokrąglenia 6px, głębokie cienie.

![Screenshot Midnight Pro](./screenshots/dark_01_midnight_pro.png)

```css
body[data-theme="midnight-pro"] {
    --bg-main: #0B0F19;
    --bg-panel: #111827;
    --text-main: #F3F4F6;
    --text-muted: #9CA3AF;
    --accent: #3B82F6;
    --border-color: #1F2937;
    --border-width: 1px;
    --border-radius: 6px;
    --shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.5);
    --font-sans: 'Inter', sans-serif;
    --font-mono: 'JetBrains Mono', monospace;
    --panel-backdrop: none;
}
```

### 2. Dracula

Klasyka znana ze środowisk IDE. Dużo fioletu i różowych akcentów dla doskonałego oddzielenia elementów interfejsu.

* **Czcionki:** Inter (UI), Fira Code (Terminal)
* **Kształty:** Zaokrąglenia 8px, wyraźne fioletowe obramowania.

![Screenshot Dracula](./screenshots/dark_02_dracula.png)

```css
body[data-theme="dracula"] {
    --bg-main: #282A36;
    --bg-panel: #44475A;
    --text-main: #F8F8F2;
    --text-muted: #6272A4;
    --accent: #FF79C6;
    --border-color: #6272A4;
    --border-width: 1px;
    --border-radius: 8px;
    --shadow: 0 4px 6px rgba(0,0,0,0.3);
    --font-sans: 'Inter', sans-serif;
    --font-mono: 'Fira Code', monospace;
    --panel-backdrop: none;
}
```

### 3. Monokai

Bardzo wysoki, agresywny kontrast z czarno-szarymi panelami i neonowymi wskaźnikami, pomagający szybko zlokalizować powiadomienia.

* **Czcionki:** Space Grotesk (UI), Source Code Pro (Terminal)
* **Kształty:** Techniczne, małe zaokrąglenia (4px), brak cieni zrzutowanych.

![Screenshot Monokai](./screenshots/dark_03_monokai.png)

```css
body[data-theme="monokai"] {
    --bg-main: #272822;
    --bg-panel: #1E1F1C;
    --text-main: #F8F8F2;
    --text-muted: #75715E;
    --accent: #F92672;
    --border-color: #3E3D32;
    --border-width: 1px;
    --border-radius: 4px;
    --shadow: none;
    --font-sans: 'Space Grotesk', sans-serif;
    --font-mono: 'Source Code Pro', monospace;
    --panel-backdrop: none;
}
```

### 4. Gruvbox Dark

Ciepły, retro motyw bazujący na sepii i brązach, uważany przez programistów za najmniej męczący wzrok.

* **Czcionki:** IBM Plex Mono (globalnie)
* **Kształty:** Płaskie, proste kąty (0px).

![Screenshot Gruvbox](./screenshots/dark_04_gruvbox.png)

```css
body[data-theme="gruvbox"] {
    --bg-main: #282828;
    --bg-panel: #3C3836;
    --text-main: #EBDBB2;
    --text-muted: #928374;
    --accent: #FE8019;
    --border-color: #504945;
    --border-width: 1px;
    --border-radius: 0px;
    --shadow: none;
    --font-sans: 'IBM Plex Mono', monospace;
    --font-mono: 'IBM Plex Mono', monospace;
    --panel-backdrop: none;
}
```

### 5. Cyberpunk

Czysta czerń i agresywne, nasycone neony (cyjan/magenta). Czysto hakerska estetyka.

* **Czcionki:** Space Grotesk (UI), Fira Code (Terminal)
* **Kształty:** Kanciaste (0px), cienkie krawędzie, lekka fluorescencyjna poświata akcentów.

![Screenshot Cyberpunk](./screenshots/dark_05_cyberpunk.png)

```css
body[data-theme="cyberpunk"] {
    --bg-main: #000000;
    --bg-panel: #090909;
    --text-main: #E0E0E0;
    --text-muted: #FF00FF;
    --accent: #00FFCC;
    --border-color: #333333;
    --border-width: 1px;
    --border-radius: 0px;
    --shadow: 0 0 10px rgba(0,255,204,0.1);
    --font-sans: 'Space Grotesk', sans-serif;
    --font-mono: 'Fira Code', monospace;
    --panel-backdrop: none;
}
```

### 6. Nord Dark

Bardzo niska saturacja barw, wykorzystująca lodowate, skandynawskie szaro-błękity. Bardzo elegancki.

* **Czcionki:** Inter (UI), Roboto Mono (Terminal)
* **Kształty:** Gładkie panele (6px), cienie o niskim nasyceniu.

![Screenshot Nord Dark](./screenshots/dark_06_nord.png)

```css
body[data-theme="nord-dark"] {
    --bg-main: #2E3440;
    --bg-panel: #3B4252;
    --text-main: #ECEFF4;
    --text-muted: #D8DEE9;
    --accent: #88C0D0;
    --border-color: #434C5E;
    --border-width: 1px;
    --border-radius: 6px;
    --shadow: 0 4px 6px rgba(0,0,0,0.2);
    --font-sans: 'Inter', sans-serif;
    --font-mono: 'Roboto Mono', monospace;
    --panel-backdrop: none;
}
```

### 7. Minimal Black

Ewaluacja do absolutnych podstaw - czarne tło, biały tekst, brak zbędnych kolorów upraszczający widok na kod.

* **Czcionki:** Inter (UI), JetBrains Mono (Terminal)
* **Kształty:** Brak cieniowania przestrzennego (100% Flat), cienkie separatory.

![Screenshot Minimal Black](./screenshots/dark_07_minimal_black.png)

```css
body[data-theme="minimal-black"] {
    --bg-main: #000000;
    --bg-panel: #0A0A0A;
    --text-main: #FFFFFF;
    --text-muted: #888888;
    --accent: #FFFFFF;
    --border-color: #333333;
    --border-width: 1px;
    --border-radius: 4px;
    --shadow: none;
    --font-sans: 'Inter', sans-serif;
    --font-mono: 'JetBrains Mono', monospace;
    --panel-backdrop: none;
}
```

### 8. Solarized Dark

Naukowo zbadana paleta morskich barw minimalizująca zmęczenie wzroku na ekranach Retina/4K.

* **Czcionki:** Fira Code (globalnie)
* **Kształty:** Surowe i kwadratowe, ostre krawędzie.

![Screenshot Solarized Dark](./screenshots/dark_08_solarized_dark.png)

```css
body[data-theme="solarized-dark"] {
    --bg-main: #002B36;
    --bg-panel: #073642;
    --text-main: #839496;
    --text-muted: #586E75;
    --accent: #2AA198;
    --border-color: #1A4B57;
    --border-width: 1px;
    --border-radius: 0px;
    --shadow: none;
    --font-sans: 'Fira Code', monospace;
    --font-mono: 'Fira Code', monospace;
    --panel-backdrop: none;
}
```

### 9. Developer Gray

Znajomy motyw dla każdego dewelopera, oparty na systemie kolorystycznym serwisu GitHub.

* **Czcionki:** Segoe UI / San Francisco (UI), Consolas (Terminal)
* **Kształty:** Subtelne zaokrąglenia (6px), funkcjonalny, stonowany border.

![Screenshot Developer Gray](./screenshots/dark_09_developer_gray.png)

```css
body[data-theme="developer-gray"] {
    --bg-main: #22272E;
    --bg-panel: #2D333B;
    --text-main: #ADBAC7;
    --text-muted: #768390;
    --accent: #539BF5;
    --border-color: #444C56;
    --border-width: 1px;
    --border-radius: 6px;
    --shadow: none;
    --font-sans: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
    --font-mono: Consolas, monospace;
    --panel-backdrop: none;
}
```

### 10. Deep Abyss

Bardzo głębokie i luksusowe granaty połączone ze szlachetnymi, bursztynowymi i złotymi detalami.

* **Czcionki:** Plus Jakarta Sans (UI), Ubuntu Mono (Terminal)
* **Kształty:** Zaawansowane, zaokrąglone interfejsy (12px), mocne, twarde i czarne cienie podkreślające elewację okien.

![Screenshot Deep Abyss](./screenshots/dark_10_deep_abyss.png)

```css
body[data-theme="deep-abyss"] {
    --bg-main: #050B14;
    --bg-panel: #0A1423;
    --text-main: #CBD5E1;
    --text-muted: #64748B;
    --accent: #F59E0B;
    --border-color: #1E293B;
    --border-width: 1px;
    --border-radius: 12px;
    --shadow: 0 4px 12px rgba(0,0,0,0.5);
    --font-sans: 'Plus Jakarta Sans', sans-serif;
    --font-mono: 'Ubuntu Mono', monospace;
    --panel-backdrop: none;
}
```
