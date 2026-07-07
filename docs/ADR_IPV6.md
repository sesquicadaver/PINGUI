> **Мова:** Українська · [English](en/ADR_IPV6.md)

# ADR: Dual-stack host addresses (V6-002)

**Статус:** accepted (IPv6-S1)  
**Дата:** 2026-06-26

## Контекст

PINGUI приймав лише IPv4 literal і hostname. Потрібна поетapна підтримка IPv6 без ламання v4.

## Рішення

1. **`HostAddressParser`** — єдиний нормалізатор для YAML і UI session.
2. **IPv6 literal** — RFC 5952 canonical (`2001:db8::1`); дужки `[...]` знімаються; zone ID (`%eth0`) — reject.
3. **Duplicate key** — canonical v6 lowercase; hostname case-insensitive; IPv4 as-is.
4. **Mixed profiles** — IPv4 + IPv6 hosts в одному `TracingProfile`.
5. **Probe/trace** — окремі задачі V6-020+; конфіг може містити v6 до готовності trace.

## Наслідки

- `HostsConfig.normalizeHostEntry` делегує в parser.
- `ProfilesConfig` duplicate detection через `HostAddressParser.duplicateKey`.
- Документація: config dual-stack; trace/raw ICMP — за ROADMAP фаза 9.2+.

## Посилання

- [ROADMAP.md](ROADMAP.md) фаза 9  
- [SPIKE_IPV6.md](SPIKE_IPV6.md)
