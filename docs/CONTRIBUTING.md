> **Мова:** Українська · [English](en/CONTRIBUTING.md)

# Участь у розробці PINGUI

Дякуємо за інтерес до проєкту. Цей документ описує процес змін у репозиторії.

## Перед PR

1. `./pingui.sh --deploy` у venv — усі gates зелені.
2. Оновлено [LIVING_SPEC.md](LIVING_SPEC.md) при зміні поведінки або модулів.
3. Додано/оновлено тести для нової логіки.
4. Документація: **UK-канон** у `docs/` + обов’язковий EN у `docs/en/` (див. [ADR_I18N.md](ADR_I18N.md)). Інші мови — **лише user-facing** (`USER_GUIDE`, `HOWTO`, stub `README.<lang>.md`); ADR/CHECKLIST/CONTRIBUTING тощо **не** перекладаємо. Gate: `scripts/check_doc_parity.py`. UI-рядки — ключі в `messages_uk.properties` (+ EN і відповідний bundle).

## Гілки

- `beta` — активна розробка; `/autopilot` бере ID з ROADMAP NEXT (якщо **DONE** — черга порожня, потрібен явний новий ID); CI на push/PR.
- `main` — стабільний зріз після merge з `beta`; CI на push/PR.
- Feature-гілки: `feature/короткий-опис` → PR у `beta`.

## Pull Request

Шаблон: `.github/pull_request_template.md`

### Обов'язковий чекліст

- [ ] `./pingui.sh --deploy` або `./scripts/ci_venv.sh` проходить
- [ ] [LIVING_SPEC.md](LIVING_SPEC.md) оновлено
- [ ] Anti-stub: немає необґрунтованих заглушок у `src/pingui/`
- [ ] Manual QA (якщо змінено UI або мережу) — чекліст у README

### Anti-stub review

Окремий пункт рев'ю:

- Жодних `pass` / `return None` / `Mock` у production, якщо це не тимчасово з `TODO(#issue)`.
- Worker, tracer, store — реальна логіка, не no-op.

## Стиль комітів

Короткий imperative subject, англійською (як у історії репо):

```
Add host rename validation in session store

Fix probe timeout handling when TTL exceeds max hops.
```

## Код-рев'ю

Очікування:

- Мінімальний diff, без unrelated змін.
- Відповідність [DEVELOPMENT.md](DEVELOPMENT.md).
- Thread-safety для worker API.
- Injectable transport для тестованості ICMP.

## Звіти про проблеми

Включайте:

- Версію OS і Python
- Вивід `./pingui.sh --deploy` або `./scripts/check_caps.sh`
- Кроки відтворення для GUI/мережі

## Ліцензія

MIT — контрибуції під тією ж ліцензією.
