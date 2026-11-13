> **Language:** English · [Українська](../ADR_IPV6.md)

# ADR: Dual-stack host addresses (V6-002)

**Status:** accepted (IPv6-S1…S2 + CR-C AF policy)  
**Date:** 2026-06-26 · updated 2026-11-13

## Context

PINGUI accepted only IPv4 literals and hostnames. Incremental IPv6 support is needed without breaking v4, with a coherent address-family ownership model across config / trace / expert ping.

## Decision

1. **`HostAddressParser`** — single normalizer for YAML and UI session.
2. **IPv6 literal** — RFC 5952 canonical (`2001:db8::1`); brackets `[...]` are stripped; zone ID (`%eth0`) — reject.
3. **Duplicate key** — canonical v6 lowercase; hostname case-insensitive; IPv4 as-is.
4. **Mixed profiles** — IPv4 + IPv6 hosts in one `TracingProfile`.
5. **`AddressFamilyPolicy`** — single AF API for AUTO probe, traceroute `-6`, and expert ping / chain enrich.

### AF ownership matrix

| Layer | AF owner | Behavior |
|-------|----------|----------|
| Config | `HostAddressParser` | kind + normalize; no probe flags |
| Trace (v6 literal) | `TraceTarget` ← `AddressFamilyPolicy.isIpv6Literal` | argv `-6` |
| AUTO probe | `DualStackRouteProbe` ← `requiresProcessTrace` | v6 literal → process; invalid → fail-closed |
| Expert ping (target) | UI AF + `ExpertPingArgs` | default `-4`; v6 literal → `-6`; hostname AAAA only with `-6` |
| Expert chain enrich | `alignExpertToTarget(hop)` | AF from **hop literal**, not UI `-4`/`-6` |
| Hostname AAAA (trace) | OS | PINGUI does not force traceroute `-6` for hostnames |

## Consequences

- `HostsConfig.normalizeHostEntry` delegates to the parser.
- `ProfilesConfig` duplicate detection via `HostAddressParser.duplicateKey`; `ping_expert.args` via `PingExpertValidator`.
- Docs: config dual-stack; raw ICMPv6 / Windows expert remain backlog.

## References

- [ROADMAP.md](ROADMAP.md) phase 9  
- [SPIKE_IPV6.md](SPIKE_IPV6.md)  
- [JAVA.md](JAVA.md) — probe limitations
