> **Language:** English · [Українська](../pingui-stabilization.md)

# Stabilization — MTR / history / side-effects (P32)

**Source for phase 32.** ROADMAP: [ROADMAP.md](ROADMAP.md) § NEXT.

Audit of `beta` @ `2c08a61` (after P31-007). Stabilization phase — **not** feature expansion.

## Linear queue scope (phase 32)

| ID | Task |
|----|------|
| **P32-001** | MTR freshness and topology completeness |
| **P32-002** | MTR concurrency and lifecycle |
| **P32-003** | Structured `PollResult` and TCP outcomes |
| **P32-004** | Schema v13, accurate rollups, atomic retention |
| **P32-005** | Bounded side-effect consumers and persistence batching |
| **P32-006** | Alert lifecycle separate from silence/cooldown |
| **P32-007** | Runtime i18n and remaining accessibility |
| **P32-008** | Local split of DB/monitor hotspots and documentation |

Full specification (UK): [../pingui-stabilization.md](../pingui-stabilization.md).

**Out of scope:** ORM; Kafka/reactive bus; new dashboard framework; silent delete of production DBs for v13 (transactional migrate required).
