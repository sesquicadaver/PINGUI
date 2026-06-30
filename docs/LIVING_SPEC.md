# Living Specification — PINGUI Java (`main`)

Матриця «модуль → unit-тести». Оновлюй при додаванні фіч.

| Модуль / ТЗ | Клас | Тести |
|-------------|------|-------|
| Парсинг Unix trace | `UnixTraceOutputParser`, `ProcessRouteProbe` | `ProcessRouteProbeTest` (unix fixtures) |
| Парсинг Windows tracert | `WindowsTraceOutputParser`, `ProcessRouteProbe` | `ProcessRouteProbeTest` (win fixtures, `<1 ms`, wait ms) |
| Trace argv (OS) | `TraceCommandBuilder`, `LinuxTracerouteCommand`, `MacTracerouteCommand`, `WindowsTracertCommand` | `ProcessRouteProbeTest` (timing); parity via `./gradlew check` |
| Валідація хостів IPv4 | `HostsConfig` | `HostsConfigTest` (legacy list, save/load, IPv6, duplicates) |
| CLI interval vs YAML (M-014) | `CliProfileOverrides`, `ProfilesConfig` | `PinguiApplicationTest.m014_*` |
| Build metadata | `AppInfo`, `generateBuildProperties` | `AppInfoTest` |
| Layer deps (no ui in config) | `scripts/check-layer-deps.sh` | `./gradlew layerCheck` |
| YAML profiles v2 + legacy | `ProfilesConfig`, `ProfileDocument` | `ProfilesConfigTest`, `ProfileDocumentTest` |
| GeoIP hints | `GeoCountry` | `GeoCountryTest` |
| CLI override профілю | `CliProfileOverrides`, `PinguiApplication` | `PinguiApplicationTest` |
| Monitor polling | `MonitorService`, `RoutePoller` | `MonitorServiceTest` |
| Session metrics | `SessionStore`, `HostTargetStats` | `SessionStoreTest`, `HopStatsTest` |
| Raw ICMP packet | `IcmpPacket` | `IcmpPacketTest` |
| Expert ping flags | `PingExpertValidator` | `PingExpertValidatorTest` |
| GUI / MonitorService | `MainController`, `MonitorService` | *(manual / TestFX — backlog)* |
| UI coordinators | `ProfileUiCoordinator`, `HostListPresenter`, `MonitorLifecycle`, `ViewModeController`, `RouteGraphPresenter` | `./gradlew check`; B-035 manual smoke |
| CI gate | `.github/workflows/java.yml` | `./gradlew check` (Spotless + Checkstyle + layerCheck + test) |
| JaCoCo coverage | `build.gradle.kts` `jacocoTestCoverageVerification` | `./gradlew check` (≥80%; tightened exclusions B-064) |
| Static imports | `config/checkstyle/checkstyle.xml` | `./gradlew checkstyleMain` / `checkstyleTest` |

**Прогін локально:** `cd java && ./gradlew check`

**CI:** push/PR на `main` / `beta` → workflow [Java CI](../.github/workflows/java.yml)

Фікстури trace: `java/src/test/resources/trace/`
