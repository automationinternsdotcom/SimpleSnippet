# Per-Snippet Free-Form Shortcuts + Editable Quick-Save Pattern

**Date:** 2026-08-17
**Status:** Approved (design reviewed adversarially; blockers resolved below)

## Summary

Remove the global trigger-prefix concept entirely. Each snippet has one free-form full
shortcut string (e.g. `..email`, `;sig`, `omw`). The in-text quick-save pattern
(`(.save:%:%)`) becomes user-editable in Settings with real validation.

## Data model

- `Snippet.trigger` becomes the free-form full shortcut. JSON key and field name stay
  `trigger` for Gson compatibility; all UI copy says "shortcut".
- `AppConfig.snippetTriggerPrefix` is removed from all logic but kept as a deprecated
  field so old blobs still parse (and so a stored custom prefix remains readable for
  migration).
- New field `AppConfig.configVersion: Int` with constructor default `2` (the current
  schema version). Old blobs deserialize with `0` because Gson bypasses constructors.

## Migration

In `AppConfig.normalized()` — the app's only migration hook, called by both the service
and the UI:

- If `configVersion < 2`: prepend the stored `snippetTriggerPrefix` (or `".."` if
  null/blank) to every snippet's trigger, then stamp `configVersion = 2`.
- The existing null-patching of `snippetTriggerPrefix` must not run before the migration
  reads it, or a custom prefix could be lost; order the reads deliberately.
- **Persistence:** the migrated blob is written back once from the UI's `loadConfig()`
  in `SimpleSnippetApp.kt` when `configVersion` changed during normalization. The
  service's load path stays read-only (writing there would re-enter its own prefs
  listener, cancelling debounces and tearing down an open picker).
- `createDefaultConfig()` is constructor-built and therefore already at version 2 —
  its default snippets must carry the delimiter literally: `..email`, `..sign`.

## Matching (`SnippetMatcher`)

- `find` drops the `prefix` parameter and matches each snippet's `trigger` directly.
  `sortedByDescending { it.trigger.length }` already yields longest-shortcut-wins.
- **Boundary rule (new):** if a shortcut starts with an alphanumeric character, the
  character immediately before the matched occurrence must be non-alphanumeric or
  start-of-text. Shortcuts starting with a symbol need no boundary check — this exactly
  preserves today's behavior for migrated snippets (`hello..email` still expands).
- The service's `replaceTrigger` re-match passes the live snippet with no prefix; the
  existing stale-config invalidation semantics are preserved.
- `allowTriggerAnywhere` and `triggerDebounceMs` stay global and unchanged.

## Quick-save pattern (editable)

- New shared validator `SnippetMatcher.isValidSavePattern(pattern)`: exactly two `%`
  placeholders, **all three** literal segments non-blank, total length ≥ 5. Used by
  both the Settings screen and as `findSaveCommand`'s early-return guard (replacing the
  duplicated `length < 5` check).
- Settings gets a draft + Apply + validation editor (same UX the prefix field used),
  with live preview substituting `%` → `shortcut` / `content`. Invalid input shows an
  error and cannot be applied.
- Quick-save creates the snippet with the name exactly as typed — no prefix prepended.
- The service runs the shortcut validator on `cmd.trigger` (trimmed, no whitespace) and
  skips the save with a toast when it fails, so quick-save cannot bypass dialog rules.

## UI

- Settings: prefix field removed; save-pattern editor added (above).
- Snippet dialog: shortcut field is free-form, empty by default. Validation: non-blank,
  no whitespace, trimmed on save. Errors surface via `isError` + supporting text and a
  disabled save button (no more silently-dead save). Duplicate check unchanged.
- Legacy tolerance: a pre-existing shortcut that violates new validation (e.g. contains
  whitespace after migration) may be re-saved **unchanged**; validation applies only
  when the shortcut text is modified.
- Self-expansion warning: the dialog shows a non-blocking warning when any variation
  contains the shortcut as a substring (runaway risk, especially with
  `allowTriggerAnywhere` on).
- `TestScreen` receives the config and derives its presets from `config.snippets` and
  `config.saveSnippetPattern` instead of hardcoded strings.
- Copy updates: snippets usage card, onboarding typing animation, accessibility service
  description in `strings.xml`, README (the "prefix is configurable / quick-save is
  fixed" line inverts).

## Versioning

`versionCode 2`, `versionName "1.1.0"`.

## Testing

- `SnippetMatcherTest`: new `find` signature; boundary-rule cases (alphanumeric-start
  blocked mid-word, allowed after space/punct/start; symbol-start unaffected);
  `isValidSavePattern` accept/reject table (empty leading/middle/trailing literal,
  wrong `%` count, short pattern).
- Migration tests **via raw JSON through Gson** (constructor-built configs are born at
  version 2 and test nothing): old blob with custom prefix, old blob with default
  prefix, old blob missing `snippetTriggerPrefix` entirely, already-migrated
  idempotency, default-config triggers match UI copy.
- Quick-save validation: whitespace-containing name rejected.

## Out of scope

Per-snippet `allowTriggerAnywhere`, bulk-edit of migrated shortcuts, export/import.
