# Changelog

All notable changes to ItzEditor are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.4] — 2026-10-21

### Fixed
- View snapping back to the top selection handle during the *select → scroll → drag the bottom handle* sequence. Root cause: caret-follow always tracked `selectionStart` (the top handle) and stayed active during touch-driven selection changes. Caret-follow now tracks the **active end** of the selection and is fully disabled while a touch gesture is in progress.

### Changed
- Status bar line/column now shows the position of the active selection end instead of always the selection start.
- Selection handle grab radius reduced from 30dp to 24dp, so scroll gestures starting near a handle are less likely to be hijacked as handle drags.

## [1.0.3] — 2026-10-21

### Fixed
- Selection being cancelled by manual scrolling. The previous fix handed *every* touch during a selection to the editor, so a scroll swipe was interpreted as a tap. Gestures are now split by handle hit-testing: touches on a handle go to the editor; touches anywhere else scroll the page and leave the selection untouched.

### Added
- Auto-scroll while dragging a selection handle: moving the handle into the top/bottom edge zone (56dp) scrolls the content with proportional speed (16 ms loop), and the selection extends to follow the finger while the content moves.

### Changed
- Wider editing area: editor side padding 12dp/16dp → 6dp/8dp, gutter right padding 6dp → 4dp.

### Notes
- The soft keyboard intentionally stays visible in selection mode.

## [1.0.2] — 2026-10-21

### Fixed
- Line numbers drifting out of alignment after the first word-wrapped line. The gutter is now built from the editor's actual layout: continuation lines of a wrapped logical line stay blank, and the gutter line height is matched explicitly to the editor's.
- Selection handle drags being stolen by the scroll container: touch interception is disabled for the scroll view while a selection is active.

### Added
- Per-tab close (×) button restored.

### Removed
- "Close tab" item from the overflow menu (redundant once the × button returned).

### Changed
- Slimmer line-number gutter, flush to the left edge (padding 14dp/8dp → 6dp/6dp).
- Release builds now go through **R8** (minify + obfuscate) with `proguard-rules.pro`; only framework entry points are kept by name and a mapping file is archived per version. APK size 80 KB → **68 KB**.

## [1.0.1] — 2026-10-21

*Not released separately — all changes rolled into 1.0.2.*

### Fixed
- Stray `"-"` items in the overflow menu (leftover separator hack).
- Untitled tab titles incrementing forever; new tabs now reuse the smallest free number among open tabs.

### Changed
- Scrolling rebuilt on a native `ScrollView` (editor and line-number gutter scroll as one content) with caret-follow while typing — noticeably smoother than the editor's internal scrolling.
- About dialog rewritten with a proper description and a clickable link to the GitHub repository.

### Removed
- Per-tab close (×) button (reverted in 1.0.2 — the menu item was removed instead).

## [1.0.0] — 2026-10-21

### Added
- Initial release. Native Java, no third-party dependencies, built without Gradle (aapt2 → javac → d8 → zipalign → apksigner).
- Multi-tab editing with dirty indicators and a save/discard prompt on close.
- Undo/redo with typing coalescing.
- Find & replace: highlight all, next/previous with wrap-around, replace one/all, case sensitivity, whole word, and regex (with invalid-pattern notice).
- Storage Access Framework open/save/save-as, recent files, and `text/*` VIEW/EDIT intents — no broad storage permission.
- Autosave to known files and full session restore.
- Line numbers, go to line, duplicate line, delete line, insert date/time, read-only mode.
- Status bar with line/column, selection length, and line/word/character statistics.
- Settings: theme (system/light/dark), language (system/English/Indonesian), font family and size, word wrap, line numbers, autosave, session restore.
- UTF-8 decoding with windows-1252 fallback for legacy files.
- Permanent crash reporter that records stack traces for diagnosis.
