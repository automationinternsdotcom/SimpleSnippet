# SimpleSnippet

**An offline text expander for Android that works in every app.**

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-brightgreen.svg" />
  <img src="https://img.shields.io/badge/License-GPLv3-blue.svg" />
  <img src="https://img.shields.io/badge/Kotlin-2.1.0-purple.svg" />
</p>

---

## Features

* **Snippet Expansion** — Type a short shortcut and it expands into full text, in any app.
  * Example: `..email` → `user@example.com`
* **Free-Form Shortcuts** — Every snippet owns its whole shortcut, so `..email`, `;sig`, and plain `omw` can all coexist. A shortcut that begins with a letter or digit won't fire mid-word, so `omw` never expands inside `shomw` — but it still fires as soon as you finish typing it, so avoid shortcuts that begin longer words you type.
* **Multiple Variations** — Attach several possible expansions to one shortcut; a floating picker lets you choose which one to insert.
* **In-Field Quick Save** — Create a new snippet without ever opening the app: type `(.save:shortcut:content)` and it's saved instantly.
* **Fully Offline** — No network calls, no ads, no analytics. The app declares no `INTERNET` permission at all.
* **Material 3 UI** — Clean Jetpack Compose interface with full dark mode support.

---

## Usage

| Action | How |
| :--- | :--- |
| Expand a snippet | Type its shortcut, e.g. `..email` |
| Pick a variation | Type a shortcut that has multiple saved contents; a picker pops up |
| Quick save a new snippet | Type `(.save:shortcut:content)` anywhere |

Each snippet's shortcut is free-form — set it to whatever you like when you create the snippet. The quick-save pattern is configurable in **Settings**; it just needs two `%` placeholders with text before, between, and after them.

**Upgrading from 1.0.x:** your snippets migrate automatically the first time you open the app. Whatever trigger prefix you had configured is folded into each snippet's shortcut, so everything you already type keeps working.

---

## Install & Setup

1. Install the APK.
2. Enable the **SimpleSnippet Accessibility Service** in Android Settings.
3. Flip the master switch on in the app.
4. Type a shortcut in any app to expand it.

---

## Privacy

* All processing happens **entirely on-device** — nothing is ever sent anywhere.
* The app declares **no `INTERNET` permission**, so it has no way to transmit data even if it wanted to.
* The accessibility service scans the focused text field locally to look for your shortcuts; the text is never stored, logged, or transmitted.
* Nothing is persisted beyond the snippets and settings you save yourself.

---

## Tech Stack

* **Language:** Kotlin 2.1
* **UI:** Jetpack Compose (Material 3)
* **Service:** Android `AccessibilityService`
* **Serialization:** Gson

---

## License & Attribution

Distributed under the **GPLv3 License**. See `LICENSE` for details.

SimpleSnippet is a derivative work extracted from [TypeAssist](https://github.com/estiaksoyeb/TypeAssist) by Istiak Ahmmed Soyeb — the snippet-expansion engine at the core of this app originates there. All credit for the original implementation goes to the upstream project; this fork isolates and continues just the text-expander feature.
