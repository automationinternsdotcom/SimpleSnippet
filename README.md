# SimpleSnippet

**An offline text expander for Android that works in every app.**

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-brightgreen.svg" />
  <img src="https://img.shields.io/badge/License-GPLv3-blue.svg" />
  <img src="https://img.shields.io/badge/Kotlin-2.1.0-purple.svg" />
</p>

---

## Features

* **Snippet Expansion** — Type a short trigger and it expands into full text, in any app.
  * Example: `..email` → `user@example.com`
* **Multiple Variations** — Attach several possible expansions to one trigger; a floating picker lets you choose which one to insert.
* **In-Field Quick Save** — Create a new snippet without ever opening the app: type `(.save:name:content)` and it's saved instantly.
* **Fully Offline** — No network calls, no ads, no analytics. The app declares no `INTERNET` permission at all.
* **Material 3 UI** — Clean Jetpack Compose interface with full dark mode support.

---

## Usage

| Action | How |
| :--- | :--- |
| Expand a snippet | Type its trigger, e.g. `..email` |
| Pick a variation | Type a trigger that has multiple saved contents; a picker pops up |
| Quick save a new snippet | Type `(.save:name:content)` anywhere |

The `..` trigger prefix is configurable in **Settings**; the quick-save pattern is fixed at `(.save:name:content)`.

---

## Install & Setup

1. Install the APK.
2. Enable the **SimpleSnippet Accessibility Service** in Android Settings.
3. Flip the master switch on in the app.
4. Type a trigger in any app to expand it.

---

## Privacy

* All processing happens **entirely on-device** — nothing is ever sent anywhere.
* The app declares **no `INTERNET` permission**, so it has no way to transmit data even if it wanted to.
* The accessibility service scans the focused text field locally to look for your triggers; the text is never stored, logged, or transmitted.
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
