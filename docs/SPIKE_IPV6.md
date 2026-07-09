> **Мова:** Українська · [English](en/SPIKE_IPV6.md)

# SPIKE: IPv6 trace + ping (B-050)

**Дата:** 2025-06-26  
**Оновлено:** 2026-06-26  
**Статус:** **implemented** (фаза 9 code-complete на `beta`, semver **0.2.0**)  
**Попередній статус:** wontfix (MVP IPv4-only, B-053) — знято за product request  
**Гілки:** `main` (Java GUI), `beta` (+ Python, тести)

---

## Питання

Чи варто додати підтримку IPv6 для:

1. Цілей моніторингу (literal `2001:db8::1`, hostname AAAA)
2. Subprocess trace (`traceroute -6`, `tracert -6`)
3. Raw ICMP (`AF_INET6`, Linux cap)

**Відповідь (2026-06):** так — поетapно, див. фазу 9.

---

## Поточний стан (evidence)

| Шар | IPv4 сьогодні | Ціль фази 9 |
|-----|---------------|-------------|
| `HostsConfig` | Literal v4 + hostname; v6 literal RFC 5952 (V6-S1) | V6-020+ trace для v6 |
| `ProcessRouteProbe` / parsers | Regex IPv4 | V6-024…V6-029: v6 tokens + fixtures |
| Trace commands | без `-6` | V6-021…V6-023 |
| `GeoCountry` | `Inet4Address` only | V6-035…V6-037 |
| `RawIcmpRouteProbe` | `AF_INET` + `AF_INET6` (`probe: raw`) | V6-040…V6-045 ✅ |
| Expert ping | iputils `-4`/`-6` catalog, v4 targets | V6-050…V6-053 |
| Документація | Dual-stack (V6-S4) | V6-074 |

---

## Історичне рішення MVP (B-050 wontfix)

1. Desktop utility для IPv4 LAN/corp сценаріїв.
2. Складність trace output v6 за OS/локаллю.
3. Raw ICMP v6 — лише Linux.

Збережено як **обмеження до фази 9**: raw v6 лишається P2; process trace — P0.

---

## Мапінг SPIKE → ROADMAP

| SPIKE компонент | ID задач |
|-----------------|----------|
| Validator RFC 5952 | V6-003, V6-010…V6-014 |
| Trace parsers | V6-024…V6-029 |
| Raw ICMP v6 | V6-040…V6-045 |
| GUI / GeoIP | V6-035…V6-037, V6-060…V6-062 |
| Тести + CI | V6-027…V6-029, V6-070…V6-073 |

**Орієнтовно:** 3–5 sprint; атомарні задачі ≤ 1 день кожна.

---

## DoD фази 9 (release)

- [x] Literal IPv6 у YAML (session validator)
- [x] Process trace v6 на Linux (macOS best-effort)
- [x] Windows `tracert -6` парситься (фікстури)
- [x] v4 regression green (`./gradlew check`)
- [x] v4 regression green (`ProcessRouteProbeTest.v4FixturesRemainGreen`, `./gradlew check`)
- [ ] CHECKLIST IPv6 smoke пройдено (ручний прогін Linux/Windows; CI-тести — див. CHECKLIST § raw/process)
- [x] Docs: dual-stack, не «IPv4-only»

---

## Посилання

- [ROADMAP.md](ROADMAP.md) — Фаза 9  
- [JAVA.md](JAVA.md) — probe limitations  
- [LIVING_SPEC.md](LIVING_SPEC.md) — оновлювати при V6-015
