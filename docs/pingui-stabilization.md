> **Мова:** Українська · [English](en/pingui-stabilization.md)

# Stabilization — MTR / history / side-effects (P32)

> **Архів:** фаза 32 закрита. Фаза 33 (correctness) також **closed** — [pingui-correctness.md](pingui-correctness.md); ROADMAP **NEXT=DONE**.

**Джерело для фази 32 (історичне).** ROADMAP: [ROADMAP.md](ROADMAP.md) § NEXT.

Аудит `beta` @ `2c08a61` (після P31-007). Фаза стабілізації — **не** розширення функціональності.

## Висновок

Поточна `beta` на [`2c08a61`](https://github.com/sesquicadaver/PINGUI/commit/2c08a6162ca1de9775a382f1fc40c6de435a38ef) технічно стабільна: CI зелений, P31-007 завершений, статичні перевірки проходять. Але історичну аналітику й MTR-режим ще не варто вважати production-ready — в них є кілька системних помилок семантики даних.

Для TRACE/PING_ONLY проєкт уже достатньо зрілий. Наступну фазу доцільно присвятити стабілізації, не розширенню функціональності.

## Основні знахідки

| Пріоритет | Проблема                                                           | Наслідок                                                      |
| --------: | ------------------------------------------------------------------ | ------------------------------------------------------------- |
| Критичний | MTR повторно обробляє кешовані hops як нові вимірювання            | неправильний loss, дубльована telemetry, недостовірна історія |
| Критичний | MTR discovery плутає останній знайдений router із target           | суперечливі UP/DOWN стани та хибні alerts                     |
|   Високий | Retention не атомарний                                             | повторний запуск після збою може подвоїти rollup              |
|   Високий | Rollup використовує неправильні знаменники                         | спотворені RTT/loss averages                                  |
|   Високий | DNS, webhook і time-series I/O виконуються у критичних потоках     | зависання host `inFlight`, probe workers або JavaFX UI        |
|   Високий | Alert silence/cooldown може назавжди поглинути firing notification | активний інцидент залишається без повідомлення                |
|  Середній | `poll_result` містить синтетичний loss та `null` jitter            | база виглядає точнішою, ніж фактичні probes                   |
|  Середній | TCP `REFUSED`, `TIMEOUT`, `ERROR` зливаються                       | адміністратор не бачить причини недоступності                 |
|  Середній | Runtime locale switch неідемпотентний                              | дублювання listeners і частково стара мова в UI               |
|  Середній | `SessionDatabase` має 1979 LOC                                     | складні транзакції, міграції та fault-path тести              |

## 1. Спочатку виправити модель MTR

У [`MtrProbe`](https://github.com/sesquicadaver/PINGUI/blob/2c08a6162ca1de9775a382f1fc40c6de435a38ef/java/src/main/java/io/pingui/probe/MtrProbe.java) за один poll реально перевіряється один hop, але назовні повертається весь накопичений route snapshot. Потім `SessionStore` і telemetry обробляють усі його вузли як свіжі samples.

Через це:

* старі RTT повторно записуються при кожному MTR-кроці;
* один timeout розмивається загальною кількістю hops;
* partial discovery може породжувати `endpoint_down`;
* кожне розширення route prefix виглядає як route change;
* timeout із заміною hop на `*` також може створити хибну зміну маршруту;
* UI може показувати останній знайдений router як доступний target.

Потрібен явний контракт результату MTR:

```java
MtrPollResult {
    phase: DISCOVERING | MONITORING | INCOMPLETE
    probedHop
    freshHopSample
    targetSampled
    targetOutcome
    completeRoute
}
```

Правила обробки:

* статистику hop і telemetry оновлює лише `freshHopSample`;
* endpoint alert і `poll_result` змінюються лише коли цього циклу перевірено target;
* prefix growth під час discovery не є зміною маршруту;
* timeout не є доказом зміни topology;
* route change порівнюється з останнім завершеним маршрутом.

MTR state потокобезпечний (P32-002): `ConcurrentHashMap`, generation token проти race poll↔reset, clear на remove/rename host.

## 2. Переробити rollup без надмірного ускладнення БД

P32-004 зроблено (schema v14): retention в одній транзакції; nullable-метрики зважуються через `*_samples`/`*_sum`.

Канонічні поля:

| Поле                 | Призначення                   |
| -------------------- | ----------------------------- |
| `sample_count`       | усі polls                     |
| `reachable_samples`  | polls із відомим reachability |
| `reachable_count`    | успішні polls                 |
| `rtt_samples`        | кількість ненульових RTT      |
| `rtt_sum`            | сума RTT                      |
| `rtt_min`, `rtt_max` | межі RTT                      |
| `loss_samples`       | кількість ненульових loss     |
| `loss_sum`           | сума loss                     |

Average та availability слід обчислювати на читанні. Такі агрегати точно складаються між 5m, 1h та наступними рівнями.

Retention має бути однією транзакцією:

```text
BEGIN
  select source buckets
  batch upsert destination buckets
  delete processed source buckets
COMMIT
```

Обов’язкові тести:

* збій після upsert, але до delete → повний rollback;
* повторний запуск → той самий результат;
* різна кількість RTT/loss samples;
* `NULL` метрики;
* часткова міграція v12→v13.

Оскільки база вже зберігає SLA та incidents, політика «видалити стару БД» більше неприйнятна. Для v13 потрібна хоча б транзакційна міграція з v12.

## 3. Зробити `poll_result` правдивим

P32-003 зроблено: `loss_percent = NULL`, якщо probe не вимірював loss; структурований `probe_outcome` (SUCCESS/TIMEOUT/REFUSED/DNS_ERROR/NETWORK_ERROR); `target_sampled`; jitter лише з серії RTT. `TCP REFUSED` відрізняється від мережевого timeout.

## 4. Винести повільні side effects із probe та UI потоків

Зараз потенційно блокують роботу:

* `InetAddress.getAllByName()` без реального timeout;
* синхронний webhook HTTP;
* Timescale/Influx writes;
* багаторазові SQLite saves одного poll;
* `SessionDatabase.load()` у `ensureHostRow`.

Мінімальна архітектура без «комбайна»:

```text
Probe → immutable PollResult → bounded queues
                              ├─ SQLite writer
                              ├─ telemetry writer
                              ├─ alert dispatcher
                              └─ UI projection
```

Достатньо:

* одного bounded single-thread database writer;
* одного bounded telemetry dispatcher;
* маленького bounded DNS executor із cache/timeout;
* webhook через наявну telemetry queue;
* одного persistence update на poll;
* `INSERT ... ON CONFLICT DO NOTHING` замість повного `load()`.

Це не потребує Kafka, reactive framework чи plugin bus.

## 5. Відокремити alert lifecycle від доставки

Нині silence/cooldown застосовується після переходу alert у `FIRING`. Якщо notification було приглушено, наступні polls бачать уже `FIRING` і більше його не відправляють — навіть після завершення silence/cooldown.

Правильна модель:

* alert engine завжди фіксує `FIRING`/`RESOLVED`;
* incident persistence не залежить від notification policy;
* dispatcher окремо вирішує, чи надсилати повідомлення;
* приглушений firing отримує `pending notification`;
* після expiry він відправляється рівно один раз, якщо умова ще активна.

Так не виникатиме `RESOLVED` без відповідного відкритого incident.

## 6. UI та локалізація

P31-007 реально покращив accessibility, але залишилися невеликі регресії:

* `problemsFirstCheck` має `focusTraversable=false`, тому недоступний із клавіатури;
* повторний `HostListPresenter.configure()` під час зміни мови додає нові listeners;
* не всі prompt/label/tooltip та відкритий inspector перебудовуються;
* шість locale-файлів не мають десяти ключів;
* у bundles дублюється `history.initial_route`.

Рекомендація: викликати `configureOnce()` лише при створенні presenter-а, а для мови зробити окремий ідемпотентний `retranslate()`.

Корисне легке UI-поліпшення після виправлення даних — показувати в існуючому inspector:

* вік останнього вимірювання;
* `fresh/stale`;
* останній структурований probe outcome;
* availability/loss/RTT за 1h, 24h і 7d.

Не варто додавати окремий dashboard framework.

## 7. Архітектурний борг

Найбільший hotspot тепер не `MainController`, а `SessionDatabase`:

| Клас                |  LOC |
| ------------------- | ---: |
| `SessionDatabase`   | 1979 |
| `ProfilesConfig`    |  785 |
| `HostListPresenter` |  767 |
| `PinguiApplication` |  752 |
| `MonitorService`    |  588 |
| `MainController`    |  550 |

Доцільний обмежений поділ `SessionDatabase`:

* `SessionDatabase` — connection і transaction owner;
* `SchemaManager`;
* `SessionStateRepository`;
* `HistoryRepository`.

Не рекомендую вводити ORM або repository interface для кожної таблиці.

Також варто офіційно визначити Java як канонічну реалізацію, а Python — як maintenance/legacy edition із bugfix-only політикою. Інакше розвиток двох уже суттєво різних schema та feature sets постійно подвоюватиме складність.

## Рекомендована черга P32

1. **P32-001 — MTR freshness і topology completeness**
2. **P32-002 — MTR concurrency та lifecycle**
3. **P32-003 — структурований `PollResult` і TCP outcomes**
4. **P32-004 — schema v14, точні rollups, атомарний retention**
5. **P32-005 — bounded side-effect consumers і persistence batching**
6. **P32-006 — alert lifecycle окремо від silence/cooldown**
7. **P32-007 — runtime i18n та залишки accessibility**
8. **P32-008 — локальний поділ DB/monitor hotspot-ів і документація**

## Перевірка

Для exact commit успішні всі GitHub checks:

* [Java — Ubuntu](https://github.com/sesquicadaver/PINGUI/actions/runs/33966179378/job/101306611435)
* [Java — Windows](https://github.com/sesquicadaver/PINGUI/actions/runs/33966179378/job/101306611572)
* [Python tests](https://github.com/sesquicadaver/PINGUI/actions/runs/33966179388/job/101306611293)

Локально також пройшли import-cycle check, documentation parity, Python compilation, shell syntax і `git diff --check`. Повний локальний test run не повторювався: середовище має Java 17 замість потрібної Java 21 і не містить `pytest`.

Чергу roadmap не змінено, вихідні файли не модифіковано. Мій підсумковий вердикт: **beta якісна за інженерною дисципліною, але перед наступним функціональним розширенням потрібно виправити семантику MTR і історичних агрегатів — зараз саме вони є головним ризиком достовірності програми.**
