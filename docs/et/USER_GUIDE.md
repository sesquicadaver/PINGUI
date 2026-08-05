> **Language:** Estonian (Eesti) · [Українська](../USER_GUIDE.md) · [English](../en/USER_GUIDE.md)

# PINGUI kasutaja juhend

## Käivitamine

```bash
./pingui.sh
```

Esimest korda:

```bash
./pingui.sh --deploy
./pingui.sh
```

Avaneb aken **"PINGUI — Linux Session Route Monitor"**.

## Liides

```
┌──────────────────┬────────────────────────────────────┐
│ Sihtide loend    │  Marsruudi graaf (ülalt → alla)    │
│ [ ] 8.8.8.8      │  [Teie PC] → hop1 → hop2 → siht    │
│ [✓] google.com   │  vasakul — eelmine (hall)          │
│                  │  paremal — praegune                │
├──────────────────┤                                    │
│ [IP või hostname]│                                    │
│ Lisa  Muuda      │                                    │
│ Kustuta Salvesta │                                    │
├──────────────────┤                                    │
│ Olek / Logi      │                                    │
└──────────────────┴────────────────────────────────────┘
```

## Sihtide loend

### Märkeruut

- **Lubatud** — worker jälgib taustal marsruuti sihtini.
- **Keelatud** — siht jääb ainult loendisse, ilma ICMP-ta.
- Samal ajal saab jälgida kuni **10** sihti (loendi limiit = aktiivsete jälgimiste limiit).

### Lisa

1. Sisestage IPv4-aadress või hostname all olevasse välja.
2. Klõpsake **Lisa** või vajutage Enter.
3. Uus siht ilmub loendisse (märkeruut keelatud).

### Muuda

1. Valige siht loendist.
2. Muutke teksti sisestusväljas (või F2 / topeltklõps real).
3. Klõpsake **Muuda**.

### Kustuta

1. Valige siht.
2. Klõpsake **Kustuta**.

### Salvesta

Kirjutab praeguse loendi YAML-konfiguratsioonifaili (tee käivitusest, tavaliselt `config/hosts.example.yaml`).
Kinnitus ilmub logisse reana.

## Marsruudi graaf

- Kuvatakse **valitud** sihi jaoks loendis.
- **Teie PC** — kohalik sõlm ahela alguses.
- **Hop N** — vahepealne marsruuter; silt näitab IP-d ja keskmist RTT-d.
- **`*`** — ajalimiit hopil (vastust pole).
- RTT värvid: roheline (<50 ms), kollane (<150 ms), punane (≥150 ms), hall — andmeid pole.

### Eelmine vs praegune marsruut

Kui IP-ahel muutub:

- **Vasak veerg (hall)** — eelmine marsruut; ajalimiitidel näidatakse **viimaseid teadaolevaid IP-sid**.
- **Parem veerg** — praegune jälgimine.

## Logi

- **ROUTE CHANGE** — hoiatus „oli / on“.
- **Error [host]** — puuduvad ICMP õigused, DNS tõrge, ajalimiit jne.
- Loendi toimingud (lisatud, muudetud, kustutatud, salvestatud).

## Olekuriba

„Last update [host]: HH:MM:SS“ — viimase eduka jälgimise aeg valitud sihi jaoks.

## Seansi andmed

Vaikimisi elavad marsruudid ja pingi ajalugu **RAM-is** (kaovad akna sulgemisel).
Valikuliselt: `--session-db` / **Seaded → Andmebaas…** — SQLite hoiab mõõdikuid ja sündmusi taaskäivituste vahel.
Salvestatud YAML sisaldab **sihtide loendit** (ja püsivuse teed/poliitikat, kui määratud), mitte täielikku hopi ajalugu ilma andmebaasita.

## Levinud probleemid

| Probleem | Tegevus |
|----------|---------|
| Sihti ei saa lisada | Kontrollige IP/hostname vormingut; 10 sihi limiit |
| Jälgimine ei tööta | Lubage märkeruut; käivitage `./scripts/check_caps.sh` |
| Tühi graaf | Lubage märkeruut ja oodake esimest tsüklit (~1 s) |
| Sisestusväli hall | 10 sihti täis — kustutage üks |

## CLI (täpsemad valikud)

```bash
.venv/bin/python -m pingui --interval 2 --max-hops 30 --verbose
```

Üksikasjad: [CONFIGURATION.md](../en/CONFIGURATION.md).

## Pro / NOC töövoog (Java)

Sihtstsenaarium NOC/SRE valveseks **Java väljaandes** (`cd java && ./pingui-java.sh`). Ülaltoodud Pythoni põhi-GUI jääb kiireks seansimonitooringuks; allpool on professionaalne tsükkel.

### Java GUI käivitamine

```bash
cd java
./pingui-java.sh -- --config config/hosts.example.yaml --session-db data/ping.db
# Terminal vabaneb kohe (GUI eraldatud). Silumine: ./pingui-java.sh --foreground -- …
# GUI logi: ~/.cache/pingui/gui.log (või $PINGUI_GUI_LOG)
```

ICMP / raw õigused: vt [DEPLOYMENT.md](../en/DEPLOYMENT.md) ja `./scripts/check_caps.sh`. UI üksikasjad: [JAVA.md](../en/JAVA.md).

### Tüüpiline vahetus (15–30 min)

1. **Lubage sihid** märkeruutudega (või `enabled: true` YAML-is) — ilma selleta pole jälgimist ega SQLite kirjutusi.
2. **Laiendatud vaade** — marsruudi graaf (eelmine tee lõuendil) ja **Marsruudi ajalugu** (24h / 7d); klõpsake sündmust, et taasesitada marsruut graafil.
3. **Sildid** — nupp **Tags** hostil; filtri kiibid loendi kohal (nt `dc`, `vpn`, `customer-x`). Salvestage YAML (**Save**).
4. **Hopi sildid** graafil (pärast IP-d): riik (GeoIP vihjed) → ASN (`asn_hints.yaml`) → rDNS (asünkroonne PTR, TTL 5 min). Võrguühenduseta vihjed: [CONFIGURATION.md](../en/CONFIGURATION.md#geoip-and-map).
5. **Expert ping** — märkeruut **Expert** → **Exten.** → eelseaded **MTU probe / DF / DSCP / Burst** failist `ping_presets.yaml` (AF `-4`/`-6` säilib). Iga eelseade täidab ainult `ping(8)` lipud ja näitab summary/expect dialoogis. MTU skaneering — **MTU** loendis või Expert **MTU wizard…** (Apply → `-M do -s`). **Self-check** — lühike DF/DSCP/Burst pakett → Alert (vorm ei muutu).
6. **Hoiatused** — webhook / töölaud marsruudi muutumisel (`alerts:` YAML-is või `--alert-webhook`). Limiteerimine hosti kohta: [CONFIGURATION.md](../en/CONFIGURATION.md).
7. **Püsivus** — `--session-db` või **Settings → Database…**; ajalugu ja `hop_stats` jäävad alles pärast taaskäivitust. Eksport: `--export-report report.csv`.
8. **Telemeetria** — **Settings → Telemetry…** (sinkid sqlite/jsonl/syslog/…); Apply + **Save** YAML. Üksikasjad: [CONFIGURATION.md](../en/CONFIGURATION.md).

### Headless NOC (ilma GUI-ta)

Sama monitor ilma JavaFX-ita — kasulik vahetuse serveris:

```bash
cd java
./pingui-java.sh -- --daemon --config config/hosts.example.yaml \
  --session-db data/ping.db --pid-file /tmp/pingui-java.pid \
  --alert-webhook https://hooks.example.com/pingui
```

Olek / stop: `--status` / `--stop`. systemd: `systemd/pingui-java.service.example`. Täielik peatükk: [DEPLOYMENT.md § Java NOC](../en/DEPLOYMENT.md#java-noc-headless-daemon-p12).

### Üleandmise kontrollnimekiri

| Kontroll | Ootus |
|----------|-------|
| Lubatud hostid | Logis on uuendused / pole pidevaid „Error“ ridu |
| Marsruudi muutus | Rida **Route history**-s + webhook (kui seadistatud) |
| SQLite | `--session-db` fail kasvab; graaf taastub pärast taaskäivitust |
| Expert MTU / Self-check | Wizard Apply → `-M do -s`; Self-check → Alert ilma vormi muutmata |
| Telemeetria (kui lubatud) | Sinki tee / remote dialoogis; sündmused sqlite/syslog konfiguratsiooni järgi |
| Daemon (kui kasutatakse) | `--status` näitab running; hoiatused jõuavad kohale |
