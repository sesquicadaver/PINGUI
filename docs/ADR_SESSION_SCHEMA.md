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
| 5 | **P30-005** | `metric_rollup` + bounded retention — **done** (schema v12) |
| 6 | **P30-006** | RO export connection / integrity_check CLI / backup-before-irreversible — **done** |

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

### P30-005 (зроблено)

```sql
CREATE TABLE metric_rollup (
    host_id INTEGER NOT NULL,
    bucket_start TEXT NOT NULL,
    bucket_size INTEGER NOT NULL,
    samples INTEGER NOT NULL,
    uptime_ratio REAL,
    rtt_min REAL,
    rtt_avg REAL,
    rtt_max REAL,
    loss_avg REAL,
    PRIMARY KEY(host_id, bucket_start, bucket_size),
    FOREIGN KEY (host_id) REFERENCES host_session(id) ON DELETE CASCADE
);
```

- Bounded retention (`PollResultRetentionJob` / `--poll-retention`): raw `poll_result` 7 днів; 5-хв rollup (300s) до 90 днів; далі годинні (3600s); інциденти й дедуп `route` не чистяться.
- Legacy v11 `.db` → fail-fast (delete & recreate).

### P30-006 (зроблено)

- **`SessionDatabase.readOnly(path)`** — `mode=ro` для довгого export/dump без write-lock на daemon.
- CLI **`--integrity-check`** (+ `--session-db`) — `PRAGMA integrity_check`; exit non-zero при помилці.
- **Backup note:** перед retention/purge/delete-recreate — копія `.db` (див. DEPLOYMENT § SQLite session).

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
### P32-003 (зроблено, schema v13)

`poll_result` доповнено:

- `probe_outcome TEXT NOT NULL` — SUCCESS / TIMEOUT / REFUSED / DNS_ERROR / NETWORK_ERROR;
- `target_sampled INTEGER NOT NULL` — чи цього циклу перевірено target.

`loss_percent` / `jitter_ms` більше не синтезуються з reachability (NULL, якщо не виміряно; jitter — лише з серії RTT). Транзакційна міграція зі старих версій — **P32-004**.
### P32-004 (зроблено, schema v14)

`metric_rollup` зберігає адитивні лічильники (`sample_count`, `reachable_*`, `rtt_samples`/`rtt_sum`, `loss_samples`/`loss_sum`); середні обчислюються на читанні. `PollResultRetentionJob` виконує upsert+delete в **одній** транзакції. Відкриття БД мігрує **v13→v14** in-place.

### P32-008 (зроблено) — поділ persistence hotspot

Публічний API лишається `SessionDatabase` (connection + transactions). SQL рознесено package-private:

| Клас | Роль |
|------|------|
| `DbCommit` | `Connection`, `deferCommit`, `maybeCommit` / `rollbackQuietly` |
| `SchemaManager` | DDL, `schema_meta`, migrate v13→v14 |
| `SessionStateRepository` | `host_session` + дочірні hop/ping/stats |
| `HistoryRepository` | events, incident, poll_result, route, rollup, telemetry |

Без ORM і без interface-на-таблицю. Monitor-side effects уже винесені раніше (`PollResultEffects`, P26/P32).
