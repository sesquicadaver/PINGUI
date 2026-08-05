> **Мова:** Українська · [English](en/ADR_I18N.md)

# ADR: UI і документація — інтернаціоналізація (P25)

**Дата:** 2026-08-03  
**Статус:** accepted  
**Гілка:** `beta`

## Контекст

GUI і більшість діалогів мали захардкоджені українські рядки (~270). Документація була лише UK↔EN. Потрібні додаткові мови без поломки Simple-layout (~580 px) і без «пастки» maximize/geometry.

## Рішення

### 1. Канон

- **Українська** — source of truth для UI (`messages_uk.properties`) і docs (`docs/*.md`).
- Зміна поведінки → оновити UK + EN обов’язково.
- Інші локалі: лише **користувацькі** матеріали (див. §5); developer/DevOps docs не вимагають перекладу.

### 2. Мови v1

| Код | Статус |
|-----|--------|
| `uk` | канон |
| `en` | повний UI + повні docs (у т.ч. для розробників) |
| `es`, `it`, `pl`, `cs`, `lv`, `lt`, `et` | UI bundles + **user docs** (README stub / USER_GUIDE / HOWTO) |
| `de`, `fr` | **відкладено** |

### 3. UI runtime

- `io.pingui.i18n.UiI18n` + `ResourceBundle` (`messages_<lang>.properties`).
- Fallback: вибрана локаль → `uk` → сам ключ (UI не падає).
- Persist: `~/.config/pingui/ui-locale.properties` (`locale=en`).
- CLI: `--lang en` (override сесії); без прапорця — prefs, інакше `uk` (не system locale за замовчуванням).
- Меню **Мова**: зміна локалі → refresh chrome без рестарту JVM; діалоги підхоплюють мову при відкритті.
- Заборонено гілки логіки на видимому тексті (`startsWith("Довідка")`, `getText().equals("Розширений")`) — лише ключі / `userData` / enum.

### 4. Layout

Перед масовими перекладами: пом’якшити фіксовані `minWidth` у host row; CRUD wrap/`USE_PREF_SIZE`; перевірка Simple ~580 з довгими рядками EN/PL.

### 5. Docs — лише для користувачів програми

Багатомовність **не** поширюється на ADR, CHECKLIST, LIVING_SPEC, CONTRIBUTING, TESTING тощо. Розробник, який хоче розвивати проєкт, працює з UK/EN.

```
docs/                 # UK canon (усі файли, вкл. developer)
docs/en/              # повний EN twin усього docs/
docs/{es,it,…}/       # лише USER_GUIDE.md + HOWTO.md
README.<lang>.md      # stub product README → посилання на USER_GUIDE/HOWTO
```

Обов’язковий набір для stub-локалей:

| Файл | Призначення |
|------|-------------|
| `README.<lang>.md` | короткий stub кореневого README + лінки на гід |
| `docs/<lang>/USER_GUIDE.md` | посібник користувача |
| `docs/<lang>/HOWTO.md` | швидкі сценарії |

- `check_doc_parity.py`: UK↔EN — повна матриця; stub-локалі — **лише** user-facing set; зайві файли (CHECKLIST/ADR) у `docs/<lang>/` — помилка.
- Новий user-facing doc (на кшталт HOWTO) додається до `USER_FACING_DOCS` у скрипті.

## Наслідки

- Позитив: менший обсяг перекладів; фокус на кінцевому користувачі; CI не роздуває stub-матрицю.
- Негатив: CONTRIBUTING/ADR іншими мовами немає — свідомо.

## Follow-ups

- DE/FR коли буде готовий обсяг.
- Повний переклад root `README.<lang>.md` (зараз stub з лінками).
- Python GUI i18n — окремо.

## Реалізація (JavaFX UI)

- Chrome / діалоги / feedback викликають `UiI18n.get("key")` / `UiI18n.get("key", args)`.
- `MonitorModeToolbar` ставить `userData` = `UiViewMode`; `ViewModeController` не порівнює `getText()`.
- `AppMenuDialogs` — ширина About/Help через enum, не `title.startsWith(...)`.
- Пробіл на початку value у `.properties` — через `\u0020` (`Properties.load` trim після `=`).
