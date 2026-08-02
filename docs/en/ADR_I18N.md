> **Language:** English · [Українська](../ADR_I18N.md)

# ADR: UI and documentation internationalization (P25)

**Date:** 2026-08-03  
**Status:** accepted  
**Branch:** `beta`

## Context

The GUI and most dialogs used hardcoded Ukrainian strings (~270). Documentation was UK↔EN only. Additional languages are needed without breaking Simple layout (~580 px) or geometry/maximize behavior.

## Decision

### 1. Canon

- **Ukrainian** is the source of truth for UI (`messages_uk.properties`) and docs (`docs/*.md`).
- Behavior changes must update UK + EN.
- Other locales: **end-user materials only** (see §5); developer/DevOps docs are not required to be translated.

### 2. Languages v1

| Code | Status |
|------|--------|
| `uk` | canon |
| `en` | full UI + full docs (including developer docs) |
| `es`, `it`, `pl`, `cs`, `lv`, `lt`, `et` | UI bundles + **user docs** (README stub / USER_GUIDE / HOWTO) |
| `de`, `fr` | **deferred** |

### 3. UI runtime

- `io.pingui.i18n.UiI18n` + `ResourceBundle` (`messages_<lang>.properties`).
- Fallback: selected locale → `uk` → key string (UI never crashes).
- Persist: `~/.config/pingui/ui-locale.properties` (`locale=en`).
- CLI: `--lang en` (session override); without the flag — prefs, else `uk` (not system locale by default).
- **Language** menu: locale change refreshes chrome without JVM restart; dialogs pick up locale on open.
- No logic branches on visible text (`startsWith("Довідка")`, `getText().equals("Розширений")`) — keys / `userData` / enums only.

### 4. Layout

Before mass translations: soften fixed `minWidth` on host row; CRUD wrap/`USE_PREF_SIZE`; smoke Simple ~580 with long EN/PL strings.

### 5. Docs — end users only

Multilingual coverage does **not** include ADR, CHECKLIST, LIVING_SPEC, CONTRIBUTING, TESTING, etc. Contributors who extend the project work from UK/EN.

```
docs/                 # UK canon (all files, including developer)
docs/en/              # full EN twin of docs/
docs/{es,it,…}/       # USER_GUIDE.md + HOWTO.md only
README.<lang>.md      # stub product README → links to USER_GUIDE/HOWTO
```

Required set for stub locales:

| File | Purpose |
|------|---------|
| `README.<lang>.md` | short root README stub + links to the guide |
| `docs/<lang>/USER_GUIDE.md` | user guide |
| `docs/<lang>/HOWTO.md` | quick scenarios |

- `check_doc_parity.py`: UK↔EN — full matrix; stub locales — **user-facing set only**; extra files (CHECKLIST/ADR) under `docs/<lang>/` are an error.
- A new user-facing doc (like HOWTO) is added to `USER_FACING_DOCS` in the script.

## Consequences

- Positive: smaller translation volume; focus on end users; CI does not inflate the stub matrix.
- Negative: no CONTRIBUTING/ADR in other languages — intentional.

## Follow-ups

- DE/FR when capacity allows.
- Full translation of root `README.<lang>.md` (currently a stub with links).
- Python GUI i18n — separate track.

## Implementation (JavaFX UI)

- Chrome / dialogs / feedback call `UiI18n.get("key")` / `UiI18n.get("key", args)`.
- `MonitorModeToolbar` sets `userData` = `UiViewMode`; `ViewModeController` does not compare `getText()`.
- `AppMenuDialogs` — About/Help width via enum, not `title.startsWith(...)`.
- Leading spaces in `.properties` values use `\u0020` (`Properties.load` trims after `=`).
