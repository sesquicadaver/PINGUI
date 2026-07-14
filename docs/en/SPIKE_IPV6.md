> **Language:** English · [Українська](../SPIKE_IPV6.md)

# SPIKE: IPv6 trace + ping (B-050)

**Date:** 2025-06-26  
**Updated:** 2026-06-26  
**Status:** **implemented** (phase 9 code-complete, semver **0.2.0**; on `main` and `beta` after merge)  
**Previous status:** wontfix (MVP IPv4-only, B-053) — lifted per product request  
**Branches:** `main` (stable snapshot), `beta` (development) — Java + Python dual-stack

---

## Question

Should IPv6 support be added for:

1. Monitoring targets (literal `2001:db8::1`, hostname AAAA)
2. Subprocess trace (`traceroute -6`, `tracert -6`)
3. Raw ICMP (`AF_INET6`, Linux cap)

**Answer (2026-06):** yes — incrementally, see phase 9.

---

## Phase 9 result (evidence)

| Layer | IPv4 | IPv6 (phase 9) |
|-------|------|----------------|
| `HostsConfig` | Literal v4 + hostname | v6 literal RFC 5952 ✅ |
| `ProcessRouteProbe` / parsers | Regex IPv4 | v6 tokens + fixtures ✅ |
| Trace commands | `-4` / no `-6` | `-6` on Linux/Windows ✅ |
| `GeoCountry` | `Inet4Address` | CIDR v6 hints ✅ |
| `RawIcmpRouteProbe` | `AF_INET` | `AF_INET6` (`probe: raw`) ✅ |
| Expert ping | iputils `-4` | `-6` + resolve ✅ |
| Documentation | Dual-stack | V6-074 ✅ |

---

## Historical MVP decision (B-050 wontfix)

1. Desktop utility for IPv4 LAN/corp scenarios.
2. Complexity of trace output v6 across OS/locale.
3. Raw ICMP v6 — Linux only.

Retained as **constraints until phase 9**: raw v6 remains P2; process trace — P0.

---

## SPIKE → ROADMAP mapping

| SPIKE component | Task IDs |
|-----------------|----------|
| Validator RFC 5952 | V6-003, V6-010…V6-014 |
| Trace parsers | V6-024…V6-029 |
| Raw ICMP v6 | V6-040…V6-045 |
| GUI / GeoIP | V6-035…V6-037, V6-060…V6-062 |
| Tests + CI | V6-027…V6-029, V6-070…V6-073 |

**Estimate (historical, before phase start):** 3–5 sprints; atomic tasks ≤ 1 day each. Actually closed (see ROADMAP phase 9).

---

## Phase 9 DoD (release)

- [x] Literal IPv6 in YAML (session validator)
- [x] Process trace v6 on Linux (macOS best-effort)
- [x] Windows `tracert -6` parsed (fixtures)
- [x] v4 regression green (`ProcessRouteProbeTest.v4FixturesRemainGreen`, `./gradlew check`)
- [ ] CHECKLIST IPv6 smoke passed (manual Linux/Windows; CI tests — see CHECKLIST § raw/process)
- [x] Docs: dual-stack, not «IPv4-only»

---

## References

- [ROADMAP.md](ROADMAP.md) — Phase 9  
- [JAVA.md](JAVA.md) — probe limitations  
- [LIVING_SPEC.md](LIVING_SPEC.md) — update on V6-015
