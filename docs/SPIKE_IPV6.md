# SPIKE: IPv6 trace + ping (B-050)

**Дата:** 2025-06-26  
**Статус:** **wontfix** — IPv4-only by design (B-053)  
**Гілки:** `main` (Java GUI), `beta` (+ Python)

---

## Питання

Чи варто додати підтримку IPv6 для:

1. Цілей моніторингу (literal `2001:db8::1`, hostname AAAA)
2. Subprocess trace (`traceroute -6`, `tracert` IPv6)
3. Raw ICMP (`AF_INET6`, Linux cap)

---

## Поточний стан (evidence)

| Шар | IPv6 сьогодні |
|-----|----------------|
| `HostsConfig` | Лише IPv4 literal + hostname `[a-zA-Z0-9.-]`; `:` → `ConfigError` (IPv4-only) |
| `ProcessRouteProbe` / parsers | Regex під IPv4; IPv6 trace output не парситься ([JAVA.md](JAVA.md#обмеження-парсера-known-limitations)) |
| `RawIcmpRouteProbe` | `AF_INET` only |
| Документація | M-002: явна примітка IPv4-only у README, DEPLOYMENT, Help |

---

## Обсяг implement (оцінка)

| Компонент | Робота | Ризик |
|-----------|--------|-------|
| B-051 Validator | RFC 5952 normalize, IDNA, duplicate rules, YAML docs | Середній |
| B-052 Raw ICMP v6 | JNA `sockaddr_in6`, cap, TTL/hop limit parity | Високий |
| Trace parsers | Окремі regex/парсери для `traceroute -6`, Windows IPv6 tracert | Високий |
| GUI / GeoIP | Підписи hop, CIDR hints для v6 | Середній |
| Тести + CI | Фікстури v6 trace, matrix OS | Середній |

**Орієнтовно:** 2–4 тижні full-stack; не атомарний MR.

---

## Рішення: **wontfix**

**Обґрунтування:**

1. **MVP scope** — desktop utility для IPv4 маршрутів у корпоративних/LAN сценаріях; продукт уже задокументований як IPv4-only.
2. **Probe складність** — формати виводу IPv6 trace різняться за ОС/локаллю сильніше за IPv4; ROI низький без попиту.
3. **Raw ICMP v6** — окремий transport, лише Linux; не вирівнює Windows/macOS.
4. ** Альтернатива** — hostname з AAAA резолвиться ОС у subprocess trace, але literal v6 і повний parity не потрібні для поточного MVP.

---

## Наслідки (B-053)

- Закрити B-051, B-052 як **cancelled / out of scope**
- Залишити явну помилку валідатора для IPv6 literal
- README / Help — без змін (вже IPv4-only)
- Перегляд рішення — лише за явним ticket / product request

---

## Посилання

- [ROADMAP.md](ROADMAP.md) фаза 7  
- [JAVA.md](JAVA.md) — probe limitations  
- [LIVING_SPEC.md](LIVING_SPEC.md) — HostsConfig tests
