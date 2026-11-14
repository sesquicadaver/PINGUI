> **Мова:** Українська · [English](en/ADR_IPV6.md)

# ADR: Dual-stack host addresses (V6-002)

**Статус:** accepted (IPv6-S1…S2 + CR-C AF policy)  
**Дата:** 2026-06-26 · оновлено 2026-11-13

## Контекст

PINGUI приймав лише IPv4 literal і hostname. Потрібна поетапна підтримка IPv6 без ламання v4, з узгодженим address-family ownership між config / trace / expert ping.

## Рішення

1. **`HostAddressParser`** — єдиний нормалізатор для YAML і UI session.
2. **IPv6 literal** — RFC 5952 canonical (`2001:db8::1`); дужки `[...]` знімаються; zone ID (`%eth0`) — reject.
3. **Duplicate key** — canonical v6 lowercase; hostname case-insensitive; IPv4 as-is.
4. **Mixed profiles** — IPv4 + IPv6 hosts в одному `TracingProfile`.
5. **`AddressFamilyPolicy`** — єдиний AF API для AUTO probe, traceroute `-6`, expert ping / chain enrich.

### AF ownership matrix

| Шар | Хто вирішує AF | Поведінка |
|-----|----------------|-----------|
| Config | `HostAddressParser` | kind + normalize; без probe flags |
| Trace (literal v6) | `TraceTarget` ← `AddressFamilyPolicy.isIpv6Literal` | argv `-6` |
| AUTO probe | `DualStackRouteProbe` ← `requiresProcessTrace` | v6 literal → process; invalid → fail-closed |
| Expert ping (ціль) | UI AF + `ExpertPingArgs` | default `-4`; literal v6 → `-6`; hostname AAAA лише при `-6` |
| Expert chain enrich | `alignExpertToTarget(hop)` | AF від **hop literal**, не від UI `-4`/`-6` |
| Hostname AAAA (trace) | ОС | PINGUI не форсує `-6` для hostname у traceroute |

## Наслідки

- `HostsConfig.normalizeHostEntry` делегує в parser.
- `ProfilesConfig` duplicate detection через `HostAddressParser.duplicateKey`; `ping_expert.args` — через `PingExpertValidator`.
- Документація: config dual-stack; raw ICMPv6 / Windows expert — окремий backlog.

## Посилання

- [ROADMAP.md](ROADMAP.md) фаза 9  
- [SPIKE_IPV6.md](SPIKE_IPV6.md)  
- [JAVA.md](JAVA.md) — probe limitations
