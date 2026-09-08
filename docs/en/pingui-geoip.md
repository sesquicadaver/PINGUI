> **Language:** English · [Українська](../pingui-geoip.md)

# GeoIP / IP metadata — offline enrichment (P36)

**Source for phase 36.** ROADMAP: [ROADMAP.md](ROADMAP.md) § NEXT.

GeoIP audit @ `066469c` / `beta` (2026-09-08, after P35-010) — external report `pingui-GeoIP-1.md`; **canonical in-repo copy is this file**.

> Historical: [pingui-unattended.md](pingui-unattended.md) (P35), [pingui-route-persistence.md](pingui-route-persistence.md) (P34), [pingui-correctness.md](pingui-correctness.md) (P33) — **archival**.

## Summary

What PINGUI calls “GeoIP” today is only static **country hints** (YAML CIDR → ISO), not full geolocation. Embedded defaults include coarse `/8` blocks, a wrong documentation IPv6 prefix `2001:db8::/32 → US`, separate `GeoCountry` / `AsnLookup` paths, and a Python map built from country centroids plus artificial hop jitter.

**Phase goal:** local MMDB (Country/City + ASN) + YAML overrides + bounded enrichment off the probe path; Java-only; no network lookups during monitoring; no `poll_result` / SQLite schema bloat.

## Linear queue

| ID | Priority | Task | DoD (short) |
|----|----------|------|-------------|
| **P36-001** | P0 | Contract and boundaries | Java-only, offline-only, no probe blocking; rename “country hints” |
| **P36-002** | P0 | `IpMetadata` + provider contract | Immutable, nullable fields, IPv4/IPv6 |
| **P36-003** | P0 | Special-IP classification | RFC1918, ULA, loopback, link-local, CGNAT, documentation → no fake country |
| **P36-004** | P1 | YAML override provider | Longest-prefix; old format compatible; extended fields |
| **P36-005** | P1 | MMDB City/Country + ASN | DB type + build epoch; official Java reader |
| **P36-006** | P1 | Bounded enrichment service | Cache, dedupe, negative cache, atomic reload |
| **P36-007** | P1 | Bootstrap and CLI | `--geoip-db` / `--geoip-asn-db` / hints / `--no-geoip`; predictable errors |
| **P36-008** | P2 | GUI integration | Cache-only rendering, tooltip/details, geographic route strip |
| **P36-009** | P2 | Event/API/export enrichment | Backward-compatible; no SQLite schema change |
| **P36-010** | P2 | Observability | `/ops`, Prometheus, App Status |
| **P36-011** | P2 | Fault / concurrency / performance | Stalled provider must not delay polling |
| **P36-012** | P2 | Legacy/docs/package close | Python not expanded; docs parity; NEXT=`DONE` |

## Target architecture

```text
Hop IP
  → private/special classification
  → YAML override (longest-prefix)
  → GeoLite/GeoIP City|Country MMDB
  → GeoLite/GeoIP ASN MMDB
  → bounded cache → GUI / API / route-change enrichment
```

Entities: `IpMetadata`, `IpMetadataProvider`, `YamlIpMetadataOverrides`, `MmdbIpMetadataProvider`, `IpMetadataService`, `GeoIpOpsStats`. After migration remove parallel `GeoCountry` / `AsnLookup` / `AsnInfo`.

## Out of scope

Network GeoIP APIs; heavy tile/web map in Java core; copying GeoIP into every `poll_result`; Python feature parity; credentials/`geoipupdate` inside PINGUI; MMDB files in Git.

## Done when

Real offline Country/City/ASN + corporate overrides; enrichment never blocks probes; bounded resources; data accuracy visible (accuracy / approximate); no GeoIP columns on `poll_result`.
