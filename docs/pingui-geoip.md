> **Мова:** Українська · [English](en/pingui-geoip.md)

# GeoIP / IP metadata — offline enrichment (P36)

**Джерело для фази 36.** ROADMAP: [ROADMAP.md](ROADMAP.md) § NEXT.

Аудит GeoIP @ `066469c` / `beta` (2026-09-08, після P35-010) — зовнішній звіт [`pingui-GeoIP-1.md`](../../GITFOLDER/p-only/pingui-GeoIP-1.md); **канон у репо — цей файл**.

> Історичні: [pingui-unattended.md](pingui-unattended.md) (P35), [pingui-route-persistence.md](pingui-route-persistence.md) (P34), [pingui-correctness.md](pingui-correctness.md) (P33) — **архівні**.

## Висновок

«GeoIP» у PINGUI зараз — лише статичні **country hints** (YAML CIDR → ISO), не повноцінна геолокація. Після P36-001 embedded/defaults містять лише тісні public-DNS префікси; грубі `/8` і помилковий documentation IPv6 `2001:db8::/32 → US` прибрані. Окремі `GeoCountry` / `AsnLookup` тимчасові до `IpMetadata*`. Python-карта (центроїди + jitter) — legacy, не розширюється в P36.

**Ціль фази:** локальна MMDB (Country/City + ASN) + YAML override + bounded enrichment поза probe-path; Java-only; без мережевих lookup під час моніторингу; без роздування `poll_result` / SQLite schema.

## Черга (лінійна)

| ID | Пріоритет | Задача | DoD (коротко) |
|----|-----------|--------|---------------|
| **P36-001** | P0 | Контракт і межі | [x] Java-only, offline-only, no probe blocking; «country hints» |
| **P36-002** | P0 | `IpMetadata` + provider contract | [x] Immutable, nullable fields, IPv4/IPv6 |
| **P36-003** | P0 | Класифікація special IP | [x] RFC1918, ULA, loopback, link-local, CGNAT, documentation → без фейкової країни |
| **P36-004** | P1 | YAML override provider | Longest-prefix; старий формат сумісний; розширені поля |
| **P36-005** | P1 | MMDB City/Country + ASN | Тип БД + build epoch; офіційний Java reader |
| **P36-006** | P1 | Bounded enrichment service | Cache, dedupe, negative cache, atomic reload |
| **P36-007** | P1 | Bootstrap і CLI | `--geoip-db` / `--geoip-asn-db` / hints / `--no-geoip`; передбачувані помилки |
| **P36-008** | P2 | GUI integration | Cache-only rendering, tooltip/details, geographic route strip |
| **P36-009** | P2 | Event/API/export enrichment | Backward-compatible; без зміни SQLite schema |
| **P36-010** | P2 | Observability | `/ops`, Prometheus, App Status |
| **P36-011** | P2 | Fault / concurrency / performance | Stalled provider не затримує polling |
| **P36-012** | P2 | Legacy/docs/package close | Python не розширено; docs parity; NEXT=`DONE` |

## Контракт (P36-001) — зафіксовано

| Інваріант | Зміст |
|-----------|--------|
| **Java-only** | Новий GeoIP/MMDB код лише в Java Pro; Python — legacy / bugfix-only. |
| **Offline-only** | Жодного HTTP/whois/DNS для enrichment під час моніторингу; літерали через `IpLiterals`. |
| **No probe blocking** | Enrichment не тримає `inFlight` і не ділить lock з probe/monitor (bounded service — P36-006+). |
| **Назва** | Поточне API/CLI — **country hints** (не «повний GeoIP»); `GeoCountry` / `AsnLookup` — тимчасові. |
| **Defaults** | Без грубих `/8` і без `2001:db8::/32 → country`; невідомий public IP → `null`/unknown. |
| **Persistence** | Не розширювати `poll_result` GeoIP-колонками (фаза 36). |

Код: `java/.../geoip/package-info.java`, `GeoCountry`, `config/geoip_hints.yaml`, `java/.../resources/geoip_hints.yaml`.

## Контракт типів (P36-002) — зафіксовано

| Тип | Роль |
|-----|------|
| `IpMetadata` | Immutable snapshot: ip + scope + source обовʼязкові; geo/ASN поля nullable |
| `IpAddressScope` | `PUBLIC` / `PRIVATE` / `SPECIAL` |
| `IpMetadataSource` | `OVERRIDE` / `MMDB` / `NONE` (NONE без payload) |
| `IpMetadataProvider` | Offline lookup; hostname → `null`; без DNS/HTTP |
| `EmptyIpMetadataProvider` | Baseline: literal → `NONE` + classified scope |
| `IpLiterals.canonicalLiteralOrNull` | Канонічний host-address без reverse DNS |
| `IpAddressClassifier` | RFC1918/ULA → PRIVATE; loopback/link-local/CGNAT/documentation/multicast → SPECIAL |

## Цільова архітектура

```text
Hop IP
  → private/special classification
  → YAML override (longest-prefix)
  → GeoLite/GeoIP City|Country MMDB
  → GeoLite/GeoIP ASN MMDB
  → bounded cache → GUI / API / route-change enrichment
```

Сутності: `IpMetadata`, `IpMetadataProvider`, `YamlIpMetadataOverrides`, `MmdbIpMetadataProvider`, `IpMetadataService`, `GeoIpOpsStats`. Після міграції прибрати паралельні `GeoCountry` / `AsnLookup` / `AsnInfo`.

## Поза scope

Мережевий GeoIP API; важка tile/web-карта в Java core; копіювання GeoIP у кожен `poll_result`; Python feature parity; credentials/`geoipupdate` всередині PINGUI; MMDB у Git.

## Кінцевий критерій

Реальні офлайн Country/City/ASN + корпоративні override; enrichment не блокує probe; bounded ресурси; точність даних видима (accuracy / approximate); SQLite schema без GeoIP-колонок у `poll_result`.
