> **Language:** English · [Українська](../ADR_IPV6.md)

# ADR: Dual-stack host addresses (V6-002)

**Status:** accepted (IPv6-S1)  
**Date:** 2026-06-26

## Context

PINGUI accepted only IPv4 literals and hostnames. Incremental IPv6 support is needed without breaking v4.

## Decision

1. **`HostAddressParser`** — single normalizer for YAML and UI session.
2. **IPv6 literal** — RFC 5952 canonical (`2001:db8::1`); brackets `[...]` are stripped; zone ID (`%eth0`) — reject.
3. **Duplicate key** — canonical v6 lowercase; hostname case-insensitive; IPv4 as-is.
4. **Mixed profiles** — IPv4 + IPv6 hosts in one `TracingProfile`.
5. **Probe/trace** — separate tasks V6-020+; config may contain v6 before trace is ready.

## Consequences

- `HostsConfig.normalizeHostEntry` delegates to the parser.
- `ProfilesConfig` duplicate detection via `HostAddressParser.duplicateKey`.
- Documentation: config dual-stack; trace/raw ICMP — per [ROADMAP](ROADMAP.md) phase 9.2+.

## References

- [ROADMAP.md](ROADMAP.md) phase 9  
- [SPIKE_IPV6.md](SPIKE_IPV6.md)
