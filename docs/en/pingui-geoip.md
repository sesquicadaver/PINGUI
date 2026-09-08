> **Language:** English · [Українська](../pingui-geoip.md)

# GeoIP / IP metadata — offline enrichment (P36)

**Source for phase 36.** ROADMAP: [ROADMAP.md](ROADMAP.md) § NEXT.

GeoIP audit @ `066469c` / `beta` (2026-09-08, after P35-010) — external report [`pingui-GeoIP-1.md`](../../GITFOLDER/p-only/pingui-GeoIP-1.md); **repo canon is this file**.

> Historical: [pingui-unattended.md](pingui-unattended.md) (P35), [pingui-route-persistence.md](pingui-route-persistence.md) (P34), [pingui-correctness.md](pingui-correctness.md) (P33) — **archival**.

## Summary

What PINGUI calls “GeoIP” is offline enrichment via `IpMetadata*` (YAML overrides + optional MMDB). After P36-001, embedded/defaults keep only tight public-DNS prefixes; coarse `/8` blocks and the wrong documentation IPv6 `2001:db8::/32 → US` mapping are gone. Parallel `GeoCountry` / `AsnLookup` / `AsnInfo` were **removed** in P36-012 (`MergingIpMetadataProvider` merges geo+ASN hints). The Python map (centroids + jitter) is legacy and was not extended in P36. Phase 36 is **closed**; ROADMAP **NEXT=DONE**.

**Phase goal:** local MMDB (Country/City + ASN) + YAML override + bounded enrichment off the probe path; Java-only; no network lookups during monitoring; no bloating `poll_result` / SQLite schema.

## Queue (linear)

| ID | Priority | Task | DoD (short) |
|----|----------|------|-------------|
| **P36-001** | P0 | Contract and boundaries | [x] Java-only, offline-only, no probe blocking; “country hints” |
| **P36-002** | P0 | `IpMetadata` + provider contract | [x] Immutable, nullable fields, IPv4/IPv6 |
| **P36-003** | P0 | Special-IP classification | [x] RFC1918, ULA, loopback, link-local, CGNAT, documentation → no fake country |
| **P36-004** | P1 | YAML override provider | [x] Longest-prefix; legacy format OK; extended fields |
| **P36-005** | P1 | MMDB City/Country + ASN | [x] DB type + build epoch; official Java reader |
| **P36-006** | P1 | Bounded enrichment service | [x] Cache, dedupe, negative cache, atomic reload |
| **P36-007** | P1 | Bootstrap and CLI | [x] `--geoip-db` / `--geoip-asn-db` / hints / `--no-geoip`; predictable errors |
| **P36-008** | P2 | GUI integration | [x] Cache-only rendering, tooltip/details, geographic route strip |
| **P36-009** | P2 | Event/API/export enrichment | [x] `/routes?include=geo`; route_change `geo_diff`/`asn_diff` + `detail_json`; `--export-geo` |
| **P36-010** | P2 | Observability | [x] `/ops` + Prometheus + App Status (`GeoIpOpsStats`) |
| **P36-011** | P2 | Fault / concurrency / performance | [x] lookup timeout; cached/offer stall proofs |
| **P36-012** | P2 | Legacy/docs/package close | [x] removed GeoCountry/AsnLookup/AsnInfo; Python not extended; docs parity; NEXT=`DONE` |

## Contract (P36-001) — locked

| Invariant | Meaning |
|-----------|---------|
| **Java-only** | New GeoIP/MMDB code only in Java Pro; Python is legacy / bugfix-only. |
| **Offline-only** | No HTTP/whois/DNS for enrichment during monitoring; literals via `IpLiterals`. |
| **No probe blocking** | Enrichment must not hold `inFlight` or share locks with probe/monitor (bounded service — P36-006+). |
| **Naming** | API/CLI is **country/ASN hints** + optional MMDB (not “full online GeoIP”); legacy parallel APIs removed. |
| **Defaults** | No coarse `/8` and no `2001:db8::/32 → country`; unknown public IP → `null`/unknown. |
| **Persistence** | Do not add GeoIP columns to `poll_result` (phase 36). |

Code: `java/.../geoip/package-info.java`, `YamlIpMetadataOverrides`, `MergingIpMetadataProvider`, `config/geoip_hints.yaml` + `asn_hints.yaml`, bundled resources.

## Type contract (P36-002) — locked

| Type | Role |
|------|------|
| `IpMetadata` | Immutable snapshot: ip + scope + source required; geo/ASN fields nullable |
| `IpAddressScope` | `PUBLIC` / `PRIVATE` / `SPECIAL` |
| `IpMetadataSource` | `OVERRIDE` / `MMDB` / `NONE` (NONE carries no payload) |
| `IpMetadataProvider` | Offline lookup; hostname → `null`; no DNS/HTTP |
| `EmptyIpMetadataProvider` | Baseline: literal → `NONE` + classified scope |
| `IpLiterals.canonicalLiteralOrNull` | Canonical host-address without reverse DNS |
| `IpAddressClassifier` | RFC1918/ULA → PRIVATE; loopback/link-local/CGNAT/documentation/multicast → SPECIAL |
| `YamlIpMetadataOverrides` | Legacy `CIDR: US` + extended mapping; longest-prefix; source=`OVERRIDE` |
| `MmdbIpMetadataProvider` | Official `DatabaseReader`; City/Country + optional ASN; `MmdbDatabaseInfo` (type + build epoch) |
| `IpMetadataService` | Precedence YAML→MMDB→NONE; LRU+negative cache; offer/dedupe; atomic reload; lookup timeout (P36-011) |
| `GeoIpOpsStats` | Cache/queue/hit counters on `/ops`, Prometheus, App Status |
| `IpMetadataBootstrap` | CLI → service; fail-fast on explicit broken MMDB |
| `IpMetadataRuntime` | Process-wide install/get/close for GUI/daemon |
| `HopGeoLabels` | Cache-only compact/details/strip (IpMetadataRuntime only) |
| `RouteGeoEnrichment` | API hop fields; webhook/persist `geo_diff`/`asn_diff`; enriched CSV/HTML |
| `MergingIpMetadataProvider` | Compose geo hints + ASN hints (P36-012) |

## Target architecture

```text
Hop IP
  → private/special classification
  → YAML override (longest-prefix)
  → GeoLite/GeoIP City|Country MMDB
  → GeoLite/GeoIP ASN MMDB
  → bounded cache → GUI / API / route-change enrichment
```

Entities: `IpMetadata`, `IpMetadataProvider`, `YamlIpMetadataOverrides`, `MergingIpMetadataProvider`, `MmdbIpMetadataProvider`, `IpMetadataService`, `GeoIpOpsStats`. Legacy `GeoCountry` / `AsnLookup` / `AsnInfo` removed (P36-012).

## Out of scope

Network GeoIP APIs; heavy tile/web map in Java core; copying GeoIP into every `poll_result`; Python feature parity; credentials/`geoipupdate` inside PINGUI; MMDB files in Git.

## Exit criterion

Real offline Country/City/ASN plus corporate overrides; enrichment does not block probes; bounded resources; data accuracy visible (accuracy / approximate); SQLite schema without GeoIP columns on `poll_result`.

## Phase close (P36-012)

- Removed Java parallel APIs: `GeoCountry`, `AsnLookup`, `AsnInfo`.
- ASN hints wire through `MergingIpMetadataProvider` in `IpMetadataBootstrap` (`--no-asn` skips merge).
- Python `src/pingui/geoip/` was not extended.
- ROADMAP **NEXT=DONE**.
