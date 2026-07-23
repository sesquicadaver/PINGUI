> **Мова:** Українська · [English](en/ADR_PROBE_MODES.md)

# ADR: режими probe — `trace | mtr | ping_only` (P13-001)

**Дата:** 2026-07-09  
**Статус:** accepted  
**Гілка:** `beta`

## Контекст

PINGUI зараз має два **різні** поняття «режиму»:

| Поняття | Де | Що означає |
|---------|-----|------------|
| `probe: auto\|process\|raw` | Профіль YAML (`ProbeMode`) | **Транспорт** trace: subprocess `traceroute`/`tracert` vs raw ICMP (Linux) |
| `ping_only: true` | Хост YAML / GUI checkbox | **Стратегія poll**: лише RTT до цілі, без hop-ів |

Проблеми поточної моделі:

1. **Повний trace кожен цикл** — дорого на Windows (`tracert` 3 probe/hop, хвилини на ціль).
2. **`ping_only`** закриває лише «ціль без hop-ів», але не дає **per-hop RTT/loss** без повного trace.
3. **Один `interval` на профіль** — ping-only і trace мають різні оптимальні інтервали (1–2 с vs 30–300 с).

Фаза 13 вводить явний **`probe_mode`** на рівні профілю та override на хост.

## Рішення

### Три режими моніторингу (`probe_mode`)

| Режим | Poll-цикл | Hop-и | Route change |
|-------|-----------|-------|--------------|
| **`trace`** | Повний traceroute/tracert/raw trace до `max_hops` | Так, кожен цикл | Так (порівняння `route_ips`) |
| **`mtr`** | **Continuous per-hop**: один або кілька hop-ів за цикл (state machine) | Так, інкрементально | Так (коли змінився шлях) |
| **`ping_only`** | Один ping до цілі (існуючий `pollHostPingOnly`) | Ні (порожній route) | Ні (лише RTT/loss цілі) |

**MTR ≠ trace:** не запускати повний trace на кожен tick. `MtrProbe` тримає курсор TTL/hop, probe-ить наступний hop або ротацію hop-ів за цикл (деталі — P13-010).

### YAML (цільова схема, P13-011)

```yaml
profiles:
  default:
    interval: 30.0          # default для trace
    probe_mode: trace       # trace | mtr | ping_only
    hosts:
      - address: 8.8.8.8
        enabled: true
        probe_mode: ping_only   # optional per-host override
```

**Backward compatibility:**

- Відсутній `probe_mode` → **`trace`**.
- Існуючий `ping_only: true` на хості → еквівалент **`probe_mode: ping_only`** (мапінг у `ProfilesConfig`, deprecated flag залишається до P13-050).
- `probe: auto|process|raw` **не змінюється** — це транспорт trace, ортогонально `probe_mode`.

### Інтервали (P13-020, P13-021)

| `probe_mode` | Рекомендований default interval | Примітка |
|--------------|----------------------------------|----------|
| `ping_only` | 1–2 с | Легкий ICMP до цілі |
| `mtr` | 5–15 с | Per-hop кроки; N hop за цикл |
| `trace` | 30–300 с | Повний trace; Windows ≥ 60 с |

**Burst on change (P13-021):** після `route_change` — `effective_interval = interval × 0.25` на 5 хв, потім повернення.

### Паралелізм (P13-030)

`max_concurrent_traces` (default **10** = `HostsConfig.MAX_HOSTS`) обмежує одночасні subprocess/raw trace — не стосується `ping_only` (легкі ping).

**Операторський контракт:** якщо в сесії увімкнено TRACE на N хостах (N ≤ 10), усі N мають право йти паралельно. Default = ліміт сесії; зменшувати в YAML лише свідомо (навантаження).

### Платформи

| OS | `trace` | `mtr` | `ping_only` |
|----|---------|-------|-------------|
| Linux | ✅ recommended | ✅ target (mtr-like state machine) | ✅ |
| Windows | ⚠ повільний trace | ⚠ MTR через subprocess обмежений | ✅ **recommended** |
| macOS | ✅ | best-effort / not shipped as full MTR | ✅ |

Windows preset (P13-040): `probe_mode: ping_only`, `interval: 60` у `hosts.windows.example.yaml`.

## Альтернативи

1. **Лише збільшити `interval` без MTR** — відхилено: не дає per-hop без повного trace.
2. **Зовнішній `mtr` subprocess** — відкладено: P13-010 починає з in-process state machine + існуючі probe; subprocess MTR — optional spike.
3. **Об’єднати `probe` і `probe_mode` в одне поле** — відхилено: плутає транспорт (raw vs tracert) і стратегію (trace vs ping).

## Наслідки

- Нові типи: `HostProbeMode` enum, `MtrProbe`, `HostPollSchedule`, `BurstSchedulePolicy`.
- `MonitorService.pollHostOnce` гілкується за `resolveProbeMode(host)` замість лише `pingOnly` boolean.
- GUI: чекбокс «Ping only» → відображає `probe_mode=ping_only` (P13-011).
- LIVING_SPEC / JAVA.md — обмеження MTR vs traceroute (P13-050).

## Follow-ups

| ID | Задача |
|----|--------|
| P13-010 | `MtrProbe` state machine |
| P13-011 | YAML + `HostEntry.probeMode()` |
| P13-020 | `HostPollSchedule` smart intervals |
| P13-021 | `BurstSchedulePolicy` |
| P13-030 | `max_concurrent_traces` |
| P13-040 | Windows example YAML |
| P13-050 | LIVING_SPEC + JAVA.md |

## Зв’язок з існуючим кодом (evidence)

- `MonitorService.pollHostOnce` — гілка `hostPingOnly ? pollHostPingOnly : pollHostRoute` (`beta`).
- `ProbeMode` enum — транспорт trace, не стратегія poll.
- `HostEntry.pingOnly()` + GUI checkbox — тимчасовий шлях до `probe_mode: ping_only`.
