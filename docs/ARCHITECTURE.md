> **Мова:** Українська · [English](en/ARCHITECTURE.md)

# Архітектура PINGUI

## Огляд

PINGUI — desktop-додаток в одному процесі: **PyQt6 GUI** + **фоновий worker у QThread** + **in-memory SessionStore**.
Мережеві операції (ICMP) ізольовані в шарі `icmp/`; бізнес-логіка опитування — у `monitor/polling.py` без залежності від Qt.

```
┌─────────────────────────────────────────────────────────────┐
│  MainWindow (Qt GUI thread)                                 │
│  ├─ QListWidget (цілі + чекбокси)                           │
│  ├─ GraphCanvas (Matplotlib)                                │
│  └─ SessionStore (RAM)                                      │
└───────────────▲─────────────────────────────────────────────┘
                │ pyqtSignal: data_received, route_changed,
                │             probe_error
┌───────────────┴─────────────────────────────────────────────┐
│  LightweightMonitorWorker (QThread)                         │
│  └─ poll_host_route → trace_route → send_probe (scapy)      │
└─────────────────────────────────────────────────────────────┘
```

## Шари

| Шар | Каталог | Відповідальність |
|-----|---------|------------------|
| Entry | `__main__.py`, `ui/app.py` | CLI, bootstrap Qt |
| Config | `config.py` | YAML, валідація хостів, DNS |
| Domain | `models.py` | HopNode, RouteSnapshot, HostSessionData |
| ICMP | `icmp/` | Raw ICMP, traceroute TTL 1..N |
| Monitor | `monitor/` | Polling, store, worker, route change |
| UI | `ui/` | MainWindow, GraphCanvas |

## Потік даних (один цикл worker)

1. Worker збирає **увімкнені** цілі (`set_host_enabled`).
2. Для кожної цілі: `poll_host_route(host, previous_ips)`.
3. `trace_route` надсилає ICMP з TTL=1..max_hops через `ScapyProbeTransport`.
4. Timeout hop → `HopNode.timeout()` (`ip="*"`).
5. `detect_route_change` порівнює ланцюжок IP з попереднім.
6. Сигнали:
   - `data_received(host, RouteSnapshot)` — завжди при успіху;
   - `route_changed(host, old_ips, new_ips)` — лише при зміні;
   - `probe_error(host, message)` — при OSError / помилці trace.
7. GUI оновлює `SessionStore`, перемальовує граф для активного хоста.

## SessionStore

На хост:

- `current_route` — останній trace;
- `previous_route` — попередній ланцюжок при зміні послідовності IP;
- `last_known_by_hop` — останній відомий IP на кожному hop (для сірого ланцюжка);
- `ping_history[ip]` — до 50 зразків RTT на IP;
- `enabled` — прапор активного трасування.

## GraphCanvas

- Вертикальний layout: «Your PC» → hop 1 → … → ціль.
- Дві колонки: **ліворуч** — `inactive_route()` (сірий), **праворуч** — `current_route`.
- Колір вузла за середнім RTT (`ping_color`: green / yellow / red / gray).

## Thread safety

- `LightweightMonitorWorker` — mutex (`threading.Lock`) на списках хостів, enabled, last_routes.
- `SessionStore` — лише GUI thread (оновлення через Qt signals/slots).
- Coverage: `concurrency = ["thread"]` у pytest-cov.

## Залежності пакетів

```
config ← models
icmp ← config
monitor ← config, icmp, models
ui ← config, models, monitor
__main__ ← config, icmp, ui, logging_setup
```

Циклічні імпорти заборонені; перевірка: `scripts/check_imports.py`.

## ADR: Scapy для ICMP

`icmp/raw_socket.py` використовує **scapy** замість ручної збірки IP/ICMP заголовків:
надійніший парсинг TTL-exceeded / echo reply на Linux. Потрібен `CAP_NET_RAW` або root.

## Поза scope MVP

- За замовч. немає persistence між сесіями (RAM-only MVP).
- Опційно: `--session-db PATH` (SQLite routes/ping/enabled) — див. `persistence/session_db.py`.
- Окремий backend/API server відсутній.
- IPv6 не підтримується (лише IPv4).
