> **Мова:** Українська · [English](en/ARCHITECTURE.md)

# Архітектура PINGUI

## Огляд

PINGUI — однопроцесний desktop-додаток: **PyQt6 GUI** + **фоновий QThread worker** + **in-memory SessionStore**.
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

1. Worker збирає список **enabled** цілей (`set_host_enabled`).
2. Для кожної цілі: `poll_host_route(host, previous_ips)`.
3. `trace_route` надсилає ICMP з TTL=1..max_hops через `ScapyProbeTransport`.
4. Timeout-hop → `HopNode.timeout()` (`ip="*"`).
5. `detect_route_change` порівнює IP-ланцюг з попереднім.
6. Сигнали:
   - `data_received(host, RouteSnapshot)` — завжди при успіху;
   - `route_changed(host, old_ips, new_ips)` — лише при зміні;
   - `probe_error(host, message)` — при OSError / помилці trace.
7. GUI оновлює `SessionStore`, перемальовує граф для активного хоста.

## SessionStore

На хост зберігається:

- `current_route` — останній trace;
- `previous_route` — попередній ланцюг при зміні IP-послідовності;
- `last_known_by_hop` — останній відомий IP на кожному hop (для сірого ланцюга);
- `ping_history[ip]` — до 50 RTT-зразків на IP;
- `enabled` — прапорець активного трасування.

## GraphCanvas

- Вертикальний layout: «Ваш ПК» → hop 1 → … → ціль.
- Дві колонки: **ліворуч** — `inactive_route()` (сірий), **праворуч** — `current_route`.
- Колір вузла за середнім RTT (`ping_color`: зелений / жовтий / червоний / сірий).

## Потокобезпека

- `LightweightMonitorWorker` — mutex (`threading.Lock`) на списки хостів, enabled, last_routes.
- `SessionStore` — лише в GUI-потоці (оновлення через Qt signals/slots).
- Coverage: `concurrency = ["thread"]` у pytest-cov.

## Залежності між пакетами

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

## Що поза scope MVP

- Немає персистентності між сесіями за замовчуванням (RAM-only MVP).
- Опційно: `--session-db PATH` — Python `persistence/session_db.py`; Java `io.pingui.persistence` (P11-011…012 на `beta`).
- Немає окремого backend/API-сервера.
- IPv6 literal у YAML (RFC 5952); subprocess `traceroute -6` для v6 trace; raw ICMP лишається v4-only.
