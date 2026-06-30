# Living Specification — PINGUI Java (`main`)

Матриця «модуль → unit-тести». Оновлюй при додаванні фіч.

| Модуль / ТЗ | Клас | Тести |
|-------------|------|-------|
| Парсинг Unix trace | `ProcessRouteProbe` | `ProcessRouteProbeTest` (unix fixtures) |
| Парсинг Windows tracert | `ProcessRouteProbe` | `ProcessRouteProbeTest` (win fixtures, `<1 ms`, wait ms) |
| Валідація хостів IPv4 | `HostsConfig` | `HostsConfigTest` |
| YAML profiles v2 + legacy | `ProfilesConfig` | `ProfilesConfigTest` |
| CLI override профілю | `CliProfileOverrides`, `PinguiApplication` | `PinguiApplicationTest` |
| Expert ping flags | `PingExpertValidator` | `PingExpertValidatorTest` |
| GUI / MonitorService | `MainController`, `MonitorService` | *(manual / TestFX — backlog)* |
| CI gate | `.github/workflows/java.yml` | `./gradlew check` (Linux required; Windows optional) |

**Прогін локально:** `cd java && ./gradlew check`

**CI:** push/PR на `main` / `beta` → workflow [Java CI](../.github/workflows/java.yml)

Фікстури trace: `java/src/test/resources/trace/`
