> **Language:** Latvian (Latviešu) · [Українська](../USER_GUIDE.md) · [English](../en/USER_GUIDE.md)

# PINGUI lietotāja ceļvedis

## Palaišana

```bash
./pingui.sh
```

Pirmo reizi:

```bash
./pingui.sh --deploy
./pingui.sh
```

Atveras logs **"PINGUI — Linux Session Route Monitor"**.

## Saskarne

```
┌──────────────────┬────────────────────────────────────┐
│ Mērķu saraksts   │  Maršruta grafs (augšā → apakšā)   │
│ [ ] 8.8.8.8      │  [Jūsu PC] → hop1 → hop2 → mērķis  │
│ [✓] google.com   │  pa kreisi — iepriekšējais (pelēks)│
│                  │  pa labi — pašreizējais            │
├──────────────────┤                                    │
│ [IP vai hostname]│                                    │
│ Pievienot Mainīt │                                    │
│ Dzēst  Saglabāt  │                                    │
├──────────────────┤                                    │
│ Statuss / Žurnāls│                                    │
└──────────────────┴────────────────────────────────────┘
```

## Mērķu saraksts

### Izvēles rūtiņa

- **Iespējots** — worker fona režīmā trasē maršrutu līdz mērķim.
- **Atspējots** — mērķis paliek tikai sarakstā, bez ICMP.
- Vienlaikus var trasēt līdz **10** mērķiem (saraksta limits = aktīvo trašu limits).

### Pievienot

1. Ievadiet IPv4 adresi vai hostname apakšējā laukā.
2. Noklikšķiniet **Pievienot** vai nospiediet Enter.
3. Jaunais mērķis parādās sarakstā (rūtiņa atspējota).

### Mainīt

1. Atlasiet mērķi sarakstā.
2. Rediģējiet tekstu ievades laukā (vai F2 / dubultklikšķis uz rindas).
3. Noklikšķiniet **Mainīt**.

### Dzēst

1. Atlasiet mērķi.
2. Noklikšķiniet **Dzēst**.

### Saglabāt

Ieraksta pašreizējo sarakstu YAML konfigurācijas failā (ceļš no starta, parasti `config/hosts.example.yaml`).
Apstiprinājums parādās kā rinda žurnālā.

## Maršruta grafs

- Rādīts **atlasītajam** mērķim sarakstā.
- **Jūsu PC** — lokālais mezgls ķēdes sākumā.
- **Hop N** — starpnieka maršrutētājs; etiķete rāda IP un vidējo RTT.
- **`*`** — taimauts hopā (nav atbildes).
- RTT krāsas: zaļa (<50 ms), dzeltena (<150 ms), sarkana (≥150 ms), pelēka — nav datu.

### Iepriekšējais vs pašreizējais maršruts

Kad IP ķēde mainās:

- **Kreisā kolonna (pelēka)** — iepriekšējais maršruts; taimautiem rādītas **pēdējās zināmās IP**.
- **Labā kolonna** — pašreizējā trase.

## Žurnāls

- **ROUTE CHANGE** — brīdinājums ar «bija / ir».
- **Error [host]** — nav ICMP tiesību, DNS kļūda, taimauts u. c.
- Saraksta darbības (pievienots, mainīts, dzēsts, saglabāts).

## Statusa josla

«Last update [host]: HH:MM:SS» — pēdējās veiksmīgās trases laiks atlasītajam mērķim.

## Sesijas dati

Pēc noklusējuma maršruti un ping vēsture dzīvo **RAM** (pazūd, aizverot logu).
Pēc izvēles: `--session-db` / **Iestatījumi → Datubāze…** — SQLite saglabā metriku un notikumus starp restartiem.
Saglabātais YAML satur **mērķu sarakstu** (un persistence ceļu/politiku, ja iestatīts), nevis pilnu hop vēsturi bez DB.

## Biežākās problēmas

| Problēma | Darbība |
|----------|---------|
| Nevar pievienot mērķi | Pārbaudiet IP/hostname formātu; 10 mērķu limits |
| Trace nedarbojas | Iespējojiet rūtiņu; palaidiet `./scripts/check_caps.sh` |
| Tukšs grafs | Iespējojiet rūtiņu un pagaidiet pirmo ciklu (~1 s) |
| Ievades lauks pelēks | Sasniegti 10 mērķi — dzēsiet vienu |

## CLI (papildu opcijas)

```bash
.venv/bin/python -m pingui --interval 2 --max-hops 30 --verbose
```

Sīkāk: [CONFIGURATION.md](../en/CONFIGURATION.md).

## Pro / NOC darbplūsma (Java)

Mērķa scenārijs NOC/SRE dežūrai **Java izdevumā** (`cd java && ./pingui-java.sh`). Pamata Python GUI augstāk paliek ātrai sesijas uzraudzībai; zemāk — profesionālais cikls.

### Java GUI palaišana

```bash
cd java
./pingui-java.sh -- --config config/hosts.example.yaml --session-db data/ping.db
# Terminālis uzreiz brīvs (GUI atdalīts). Atkļūdošana: ./pingui-java.sh --foreground -- …
# GUI žurnāls: ~/.cache/pingui/gui.log (vai $PINGUI_GUI_LOG)
```

ICMP / raw tiesības: skatiet [DEPLOYMENT.md](../en/DEPLOYMENT.md) un `./scripts/check_caps.sh`. UI detaļas: [JAVA.md](../en/JAVA.md).

### Tipiska maiņa (15–30 min)

1. **Iespējojiet mērķus** ar rūtiņām (vai `enabled: true` YAML) — bez tā nav trases un SQLite ierakstu.
2. **Paplašinātais skats** — maršruta grafs (iepriekšējais ceļš uz audekla) un **Maršrutu vēsture** (24h / 7d); noklikšķiniet notikumu, lai atskaņotu maršrutu grafā.
3. **Birkas** — poga **Tags** uz resursdatora; filtra čipi virs saraksta (piem. `dc`, `vpn`, `customer-x`). Saglabājiet YAML (**Save**).
4. **Hop etiķetes** grafā (pēc IP): valsts (GeoIP mājieni) → ASN (`asn_hints.yaml`) → rDNS (asinhronais PTR, TTL 5 min). Bezsaistes mājieni: [CONFIGURATION.md](../en/CONFIGURATION.md#geoip-and-map).
5. **Expert ping** — rūtiņa **Expert** → **Exten.** → iepriekšiestatījumi **MTU probe / DF / DSCP / Burst** no `ping_presets.yaml` (AF `-4`/`-6` saglabājas). Katrs iepriekšiestatījums tikai aizpilda `ping(8)` karodziņus un rāda summary/expect dialogā. MTU pārbaude — **MTU** sarakstā vai Expert **MTU wizard…** (Apply → `-M do -s`). **Self-check** — īss DF/DSCP/Burst komplekts → Alert (forma nemainās).
6. **Brīdinājumi** — webhook / darbvirsma pie maršruta maiņas (`alerts:` YAML vai `--alert-webhook`). Limits uz resursdatoru: [CONFIGURATION.md](../en/CONFIGURATION.md).
7. **Noturība** — `--session-db` vai **Settings → Database…**; vēsture un `hop_stats` pārdzīvo restartu. Eksports: `--export-report report.csv`.
8. **Telemetrija** — **Settings → Telemetry…** (sinki sqlite/jsonl/syslog/…); Apply + **Save** YAML. Sīkāk: [CONFIGURATION.md](../en/CONFIGURATION.md).

### Headless NOC (bez GUI)

Tas pats monitors bez JavaFX — ērti maiņas serverī:

```bash
cd java
./pingui-java.sh -- --daemon --config config/hosts.example.yaml \
  --session-db data/ping.db --pid-file /tmp/pingui-java.pid \
  --alert-webhook https://hooks.example.com/pingui
```

Statuss / stop: `--status` / `--stop`. systemd: `systemd/pingui-java.service.example`. Pilna sadaļa: [DEPLOYMENT.md § Java NOC](../en/DEPLOYMENT.md#java-noc-headless-daemon-p12).

### Nodošanas kontrolsaraksts

| Pārbaude | Gaidāmais |
|----------|-----------|
| Iespējotie resursdatori | Žurnālā ir atjauninājumi / nav pastāvīgu «Error» rindu |
| Maršruta maiņa | Rinda **Route history** + webhook (ja konfigurēts) |
| SQLite | `--session-db` fails aug; grafs atjaunojas pēc restarta |
| Expert MTU / Self-check | Wizard Apply → `-M do -s`; Self-check → Alert bez formas maiņas |
| Telemetrija (ja ieslēgta) | Sink ceļš / remote dialogā; notikumi sqlite/syslog pēc konfigurācijas |
| Daemon (ja izmantots) | `--status` rāda running; brīdinājumi nonāk |
