> **Мова:** Українська · [English](en/ADR_HOST_PROBLEM_INDICATOR.md)

# ADR: Індикатор проблеми на рядку хоста + автоіменування session DB (P22-001)

**Статус:** accepted (P22-001)  
**Дата:** 2026-07-22

## Контекст

Оператор бачить RTT-колір і metrics у списку хостів, але **немає явного значка** «є проблема доступності» з деталями інциденту. Канали webhook/desktop ([ADR_ALERTS](ADR_ALERTS.md) / [ADR_ALERT_RULES](ADR_ALERT_RULES.md)) не замінюють in-app індикацію.

Також підключення session SQLite через file picker незручне для швидкого старту NOC-сесії: потрібна кнопка **автостворення** файлу з передбачуваним імʼям.

Рішення користувача (2026-07-22):

| Тема | Рішення |
|------|---------|
| Scope значка v1 | Лише **`endpoint_down`**; `latency_high` / loss → **пізніше** разом із правилом ADR_ALERT_RULES v2 |
| Після RESOLVED | Значок **лишається** (історія сесії) |
| Після перегляду | Значок **деактивується (ack)** до наступного FIRING |
| Запис у БД | Інцидент **залишається** в SQLite після ack (якщо БД підключена) |
| Джерело діалогу | **RAM** сесії (`AlertRuleEngine`) **і** SQLite (якщо є) |
| Макс. тривалість | Найдовший **один** інцидент (FIRING→RESOLVED або до «зараз» якщо ще FIRING) |
| Повтори | Скільки разів входили в **FIRING** за сесію |
| Auto session DB | Кнопка в «База даних…» біля «Обрати…»; каталог `data/`; імʼя `YYYY-MM-DD_HH-mm-ss_<local-ip>.db` |
| Local IP | LAN-адреса операторської машини (типово RFC1918: `10/8`, `192.168/16`, `172.16/12`) |

## Рішення

### 1. Розділення шарів

| Шар | Відповідальність |
|-----|------------------|
| Rules / channels | [ADR_ALERT_RULES](ADR_ALERT_RULES.md), [ADR_ALERTS](ADR_ALERTS.md) — *коли* emit FIRING/RESOLVED |
| Session problem UX | цей ADR — badge, ack, діалог, агрегації сесії |
| Persistence | SQLite `persistence_event` — довготривалий запис інцидентів |
| Session DB naming | утиліта автоімені + GUI кнопка |

Без перетворення PINGUI на NMS ([ROADMAP X-003](ROADMAP.md)).

### 2. Модель сесійної проблеми (RAM)

На `(host, rule)` у сесії GUI/monitor:

| Поле | Опис |
|------|------|
| `unread` | `true` після першого FIRING (або нового FIRING після ack); `false` після ack |
| `fire_count` | кількість входів у FIRING за сесію |
| `max_duration` | max тривалість одного інциденту |
| `last_started_at` | початок поточного або останнього інциденту |
| `last_resolved_at` | час останнього RESOLVED (якщо був) |
| `last_state` | `firing` \| `resolved` \| `ok` (після ack без активного FIRING) |
| `description` | короткий текст (напр. «endpoint_down: ціль недоступна») |

**Ack:** UI після відкриття діалогу викликає `ack(host)` → `unread=false`; значок ховається. Лічильники й історія **не** скидаються. Наступний FIRING знову ставить `unread=true`.

Значок видимий **лише якщо `unread=true`**.

### 3. Діалог деталей

Показує мінімум:

1. **Час** — `last_started_at` (і `last_resolved_at`, якщо є)
2. **Опис** — `description` / rule id
3. **Макс. тривалість** — `max_duration` (людинозчитний інтервал)
4. **Кількість повторів** — `fire_count`

Якщо підключена session DB — діалог **може доповнити** список/агрегати з SQLite (ті самі метрики або історія FIRING/RESOLVED). RAM лишається первинним джерелом для «зараз у сесії».

### 4. SQLite

Новий тип події (або узгоджений id у `persistence_event`):

- `endpoint_down` (рекомендовано той самий дискримінатор, що в quality payload), payload ≈ `QualityAlertEvent.toJson()` (+ опційно `duration_ms` на RESOLVED)

Запис: на emit FIRING і RESOLVED з monitor-шару, **лише якщо** session DB відкрита і політика дозволяє (окремий прапор події або reuse існуючого policy — вирішує P22-003; default: писати quality incidents коли DB підключена).

**Ack не видаляє** рядки БД.

Міграція схеми — лише якщо потрібен новий `event_type` enum / CHECK; інакше новий id у існуючій таблиці.

### 5. UI рядка хоста

- У `HostListCell` — компактний значок (напр. ⚠ / «!» ) поруч із імʼям хоста.
- Visible/managed лише при `unread`.
- Click → діалог → ack.

Колір RTT рядка **не** замінює значок (різні сигнали).

### 6. Автостворення session DB

У `PersistenceSettingsDialog`, поруч із «Обрати…»:

- Кнопка на кшталт «Створити…» / «Авто».
- Шлях: `data/YYYY-MM-DD_HH-mm-ss_<local-ip>.db` (локальний час оператора).
- IP: перший придатний non-loopback IPv4 site-local / RFC1918 інтерфейсу; якщо немає — fallback (документувати в P22-005: напр. `unknown` або primary non-loopback).
- Крапка/двокрапка в IP → `_` або `-` у імені файлу (`192.168.1.10` лишається з крапками **або** `192-168-1-10` — імплементація обирає один варіант і фіксує в тестах; **рекомендація:** крапки → `-`, щоб уникнути плутанини з розширенням).
- Після генерації — підставити шлях у поле; Apply як звичайне підключення.

CLI `--session-db` / YAML lock — кнопка disabled (як «Обрати…»).

### 7. Поза цим ADR (наступні квитки)

| ID | Зміст |
|----|--------|
| **P22-002** | Engine: stats + ack API |
| **P22-003** | SQLite write path |
| **P22-004** | Icon + dialog UI |
| **P22-005** | Auto-create button + LocalIp naming |

## Наслідки

- Позитив: швидке виявлення проблеми без залежності від desktop/webhook.
- Позитив: передбачувані імена session DB для архіву.
- Негатив: ще один event type у persistence; потрібні тести агрегацій тривалості.
- Latency-значок **свідомо** відкладено — не евристика в UI без правила.

## Посилання

- [ADR_ALERT_RULES.md](ADR_ALERT_RULES.md) — lifecycle `endpoint_down`
- [ADR_ALERTS.md](ADR_ALERTS.md) — канали доставки
- [ROADMAP.md](ROADMAP.md) — фаза 22
