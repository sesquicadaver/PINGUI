> **Мова:** Українська · [English](en/ADR_SESSION_SCHEMA.md)

# ADR: Еволюція SQLite session schema (P30)

**Статус:** accepted (P30-001+)  
**Дата:** 2026-09-03  
**Гілка:** `beta` (після merge — `main` і `beta`).  
**Джерело:** `pingui-evo-db.md`.

## Контекст

Schema v7 нормалізувала hops/history у дочірні таблиці, але **адреса хоста лишалась PRIMARY KEY**. Через це:

- `rename()` переписував пов’язані рядки;
- немає стабільного ключа для інцидентів / poll history / SLA;
- історичний аналіз незручний.

Повна заміна SQLite / ORM / окрема БД на підсистему — поза scope desktop-режиму.

## Рішення

Еволюція **без міграції старих `.db`**: `schema_version != supported` → fail-fast; оператор видаляє файл і створює наново (як P27).

### Цільова мінімальна схема (фаза 30)

| Крок | ID | Суть |
|------|-----|------|
| 1 | **P30-001** | `host_session.id` INTEGER PK; `address` UNIQUE; діти/`persistence_event` → `host_id` |
| 2 | **P30-002** | таблиця `incident` (FIRING/RESOLVED, duration, ack) — **done** (schema v9) |
| 3 | **P30-003** | `poll_result` — канонічний агрегат завершеного poll — **done** (schema v10) |
| 4 | **P30-004** | дедуплікована `route` (signature + hops_json) — **done** (schema v11) |
| 5 | **P30-005** | `metric_rollup` + bounded retention |
| 6 | **P30-006** | RO export connection / integrity_check CLI / backup-before-irreversible |

### P30-001 (зроблено)

```sql
CREATE TABLE host_session (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    address    TEXT NOT NULL UNIQUE,
    enabled    INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
```

- Публічний API (`load`/`save`/`rename`/`listHosts`) лишається **address-keyed**.
- `rename` = `UPDATE address` на тому ж `id` (події не переписуються).
- `PRAGMA busy_timeout = 5000` при відкритті.
- `telemetry_*` поки зберігають address TEXT (без FK); канон історії — наступні кроки.

### P30-002 (зроблено)

```sql
CREATE TABLE incident (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    host_id INTEGER NOT NULL,
    kind TEXT NOT NULL,
    severity TEXT NOT NULL,
    state TEXT NOT NULL,
    started_at TEXT NOT NULL,
    ended_at TEXT,
    acknowledged_at TEXT,
    occurrences INTEGER NOT NULL DEFAULT 1,
    peak_value REAL,
    details_json TEXT NOT NULL DEFAULT '{}',
    FOREIGN KEY (host_id) REFERENCES host_session(id) ON DELETE CASCADE
);
```

- Quality FIRING/RESOLVED (`endpoint_down` / `latency_high`) синхронізує `incident` через `PersistenceEventWriter`.
- Ack оновлює `acknowledged_at` на відкритих FIRING.
- MTTR / duration — з `started_at`/`ended_at` (без розбору JSON).

### P30-003 (зроблено)

```sql
CREATE TABLE poll_result (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    host_id INTEGER NOT NULL,
    observed_at TEXT NOT NULL,
    probe_mode TEXT NOT NULL,
    reachable INTEGER,
    terminal_rtt_ms REAL,
    jitter_ms REAL,
    loss_percent REAL,
    duration_ms REAL,
    route_id INTEGER,
    error_code TEXT,
    FOREIGN KEY (host_id) REFERENCES host_session(id) ON DELETE CASCADE
);
```

- Запис після кожного завершеного poll (`PollResultEffects` → `PersistenceEventWriter`).
- Канон історії RTT/loss/uptime; `telemetry_*` лишається каналом експорту.
- `route_id` nullable до P30-004.

### P30-004 (зроблено)

```sql
CREATE TABLE route (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    host_id INTEGER NOT NULL,
    signature TEXT NOT NULL,
    hops_json TEXT NOT NULL,
    first_seen TEXT NOT NULL,
    last_seen TEXT NOT NULL,
    seen_count INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY (host_id) REFERENCES host_session(id) ON DELETE CASCADE,
    UNIQUE(host_id, signature)
);
```

- Signature: `ip|ip|*|ip` (`*` = timeout).
- Успішний poll upsert-ить route і пише `poll_result.route_id`.

### Не робимо

- PostgreSQL для desktop session DB;
- ORM;
- таблиця на кожен telemetry event type;
- повна нормалізація hops / ASN / DNS IP;
- silent migrate старих файлів.

## Наслідки

- Rename і кореляція стають дешевшими.
- Наступні таблиці (`incident`, `poll_result`, `route`) чіпляються до `host_id`.
- Оператори з v7 `.db` повинні видалити файл перед оновленням.
