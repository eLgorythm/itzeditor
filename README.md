# ItzEditor

A lightweight native Android text editor — multi-tab editing, find & replace, dark mode, and session restore in a **68 KB** APK with **zero dependencies**.

![Version](https://img.shields.io/badge/version-1.0.4-blue)
![APK size](https://img.shields.io/badge/APK-68%20KB-brightgreen)
![Min SDK](https://img.shields.io/badge/minSdk-26%20(Android%208.0)-orange)
![Language](https://img.shields.io/badge/language-Java-red)
![Dependencies](https://img.shields.io/badge/dependencies-none-brightgreen)

## Features

- **Multi-tab editing** with dirty indicators and a save/discard prompt on close
- **Undo / redo** with typing coalescing
- **Find & replace** — highlight all matches, next/previous navigation, replace one or all; supports case sensitivity, whole word, and regular expressions
- **Line numbers** that stay aligned with word-wrapped text (computed from the real layout, so wrapped continuation lines are handled correctly)
- **Selection that behaves** — handles can be dragged past the edge of the screen with auto-scroll, and scrolling the page never cancels an active selection
- **Dark mode** — follow system, light, or dark; interface in **English or Indonesian**
- **Autosave** and **full session restore** — reopen the app and your tabs are where you left them
- **Storage Access Framework** file handling — open/save any file the system picker offers, no broad storage permission needed; also opens `text/*` files shared from other apps
- **Editor essentials** — go to line, duplicate line, delete line, insert date/time, read-only mode, word wrap toggle, monospace/sans/serif fonts at 10–22sp
- **Status bar** with line/column (following the active end of a selection), selection length, and line/word/character counts
- Built-in **crash reporter** — if anything goes wrong, the stack trace is kept so it can actually be fixed

## Download

Grab the latest APK from the [Releases](https://github.com/eLgorythm/ItzEditor/releases) page and install it directly. Every release is signed with the same key, so updates install cleanly over the previous version.

**Requirements:** Android 8.0 (API 26) or newer.

## Building from source

There is deliberately **no Gradle** here. The release build is a transparent shell pipeline:

```
aapt2  ->  javac  ->  R8 (minify + obfuscate)  ->  zipalign  ->  apksigner
```

Prerequisites: JDK 17 and the Android SDK (platform android-34, build-tools 35.0.0).

```bash
./build-manual.sh
```

The script produces a signed release APK in `build-manual/`. On the first run it generates a local release keystore under `release/` — keep `release/.storepass` and the keystore itself private, and never commit them. R8 rules live in `proguard-rules.pro` (only the framework entry points — `Application`, activities, and the custom editor view — are kept by name), and the obfuscation mapping for each version is archived as `release/mapping-<version>.txt` so crash stack traces stay readable.

## Architecture

Plain Java against the Android SDK only — no third-party libraries at all. One class, one responsibility:

```
app/src/main/java/me/fndlabs/itzeditor/
├── ItzEditorApp.java        # Application entry point, crash reporter install
├── ui/                      # Activities, tabs, dialogs, menus, status bar
│   ├── MainActivity.java
│   ├── SettingsActivity.java
│   └── BaseActivity.java    # applies theme + language preferences
├── editor/                  # editing engine
│   ├── EditorView.java      # EditText subclass (font, selection events)
│   ├── EditorController.java# undo/redo glue, gutter sync, gestures, auto-scroll
│   ├── FindController.java  # find & replace UI flow
│   ├── Document.java / DocumentManager.java
│   ├── GutterNumbers.java   # wrap-aware line numbers (pure logic)
│   ├── TextStats.java       # line/column & statistics (pure logic)
│   ├── FindEngine.java      # search incl. regex (pure logic)
│   └── UndoHistory.java     # snapshot history with coalescing (pure logic)
├── data/                    # Prefs, FileRepository (SAF), SessionStore
└── util/                    # LocaleHelper, CrashReporter
```

The pure-logic classes are covered by a dependency-free JVM test suite in `test/TestMain.java` — currently **44 tests, all passing**.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for the full history. Latest: **v1.0.4** fixes the view snapping back to the top selection handle during *select → scroll → drag the bottom handle* — caret-follow now tracks the active end of the selection and stays out of the way during touch gestures.

## License

MIT — see `LICENSE` if present, or treat this code as MIT-licensed open source.

---

Developed by [eLgorythm](https://github.com/eLgorythm)
