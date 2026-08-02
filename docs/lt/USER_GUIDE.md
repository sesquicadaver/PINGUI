> **Language:** Lithuanian (Lietuvių) · [Українська](../USER_GUIDE.md) · [English](../en/USER_GUIDE.md)

# PINGUI naudotojo vadovas

## Paleidimas

```bash
./pingui.sh
```

Pirmą kartą:

```bash
./pingui.sh --deploy
./pingui.sh
```

Atsidaro langas **"PINGUI — Linux Session Route Monitor"**.

## Sąsaja

```
┌──────────────────┬────────────────────────────────────┐
│ Tikslų sąrašas   │  Maršruto grafas (viršus → apačia) │
│ [ ] 8.8.8.8      │  [Jūsų PC] → hop1 → hop2 → tikslas │
│ [✓] google.com   │  kairė — ankstesnis (pilka)        │
│                  │  dešinė — dabartinis               │
├──────────────────┤                                    │
│ [IP arba hostname]│                                   │
│ Pridėti Keisti   │                                    │
│ Šalinti Išsaugoti│                                    │
├──────────────────┤                                    │
│ Būsena / Žurnalas│                                    │
└──────────────────┴────────────────────────────────────┘
```

## Tikslų sąrašas

### Žymimasis langelis

- **Įjungta** — worker fone trasuoja maršrutą iki tikslo.
- **Išjungta** — tikslas lieka tik sąraše, be ICMP.
- Vienu metu galima trasuoti iki **10** tikslų (sąrašo limitas = aktyvių trasavimų limitas).

### Pridėti

1. Įveskite IPv4 adresą arba hostname apatiniame lauke.
2. Spustelėkite **Pridėti** arba Enter.
3. Naujas tikslas atsiranda sąraše (langelis išjungtas).

### Keisti

1. Pasirinkite tikslą sąraše.
2. Redaguokite tekstą įvesties lauke (arba F2 / dukart spustelėkite eilutę).
3. Spustelėkite **Keisti**.

### Šalinti

1. Pasirinkite tikslą.
2. Spustelėkite **Šalinti**.

### Išsaugoti

Įrašo dabartinį sąrašą į YAML konfigūracijos failą (kelias iš paleidimo, paprastai `config/hosts.example.yaml`).
Patvirtinimas pasirodo kaip eilutė žurnale.

## Maršruto grafas

- Rodomas **pasirinktam** tikslui sąraše.
- **Jūsų PC** — vietinis mazgas grandinės pradžioje.
- **Hop N** — tarpinis maršrutizatorius; etiketėje IP ir vidutinis RTT.
- **`*`** — laukimo limito viršijimas hope (nėra atsakymo).
- RTT spalvos: žalia (<50 ms), geltona (<150 ms), raudona (≥150 ms), pilka — nėra duomenų.

### Ankstesnis vs dabartinis maršrutas

Kai keičiasi IP grandinė:

- **Kairysis stulpelis (pilkas)** — ankstesnis maršrutas; laukimo limitams rodomi **paskutiniai žinomi IP**.
- **Dešinysis stulpelis** — dabartinė trasa.

## Žurnalas

- **ROUTE CHANGE** — įspėjimas su „buvo / dabar“.
- **Error [host]** — nėra ICMP teisių, DNS klaida, timeout ir pan.
- Sąrašo operacijos (pridėta, pakeista, pašalinta, išsaugota).

## Būsenos juosta

„Last update [host]: HH:MM:SS“ — paskutinės sėkmingos trasos laikas pasirinktam tikslui.

## Seanso duomenys

Pagal numatymą maršrutai ir ping istorija gyvena **RAM** (dingsta uždarius langą).
Pasirinktinai: `--session-db` / **Nustatymai → Duomenų bazė…** — SQLite saugo metrikas ir įvykius tarp paleidimų.
Išsaugotas YAML turi **tikslų sąrašą** (ir persistence kelią/politiką, jei nustatyta), ne pilną hop istoriją be DB.

## Dažnos problemos

| Problema | Veiksmas |
|----------|----------|
| Negalima pridėti tikslo | Patikrinkite IP/hostname formatą; 10 tikslų limitas |
| Trasavimas neveikia | Įjunkite langelį; paleiskite `./scripts/check_caps.sh` |
| Tuščias grafas | Įjunkite langelį ir palaukite pirmo ciklo (~1 s) |
| Įvesties laukas pilkas | Pasiekta 10 tikslų — pašalinkite vieną |

## CLI (išplėstinės parinktys)

```bash
.venv/bin/python -m pingui --interval 2 --max-hops 30 --verbose
```

Išsamiau: [CONFIGURATION.md](../en/CONFIGURATION.md).

## Pro / NOC darbo eiga (Java)

Tikslinis scenarijus NOC/SRE budėjimui **Java leidime** (`cd java && ./pingui-java.sh`). Pagrindinė Python GUI aukščiau lieka greitam sesijos stebėjimui; žemiau — profesionalus ciklas.

### Java GUI paleidimas

```bash
cd java
./pingui-java.sh -- --config config/hosts.example.yaml --session-db data/ping.db
# Terminalas iškart laisvas (GUI atskirtas). Derinimas: ./pingui-java.sh --foreground -- …
# GUI žurnalas: ~/.cache/pingui/gui.log (arba $PINGUI_GUI_LOG)
```

ICMP / raw teisės: žr. [DEPLOYMENT.md](../en/DEPLOYMENT.md) ir `./scripts/check_caps.sh`. UI detalės: [JAVA.md](../en/JAVA.md).

### Tipinė pamaina (15–30 min)

1. **Įjunkite tikslus** langeliais (arba `enabled: true` YAML) — be to nėra trasos ir SQLite įrašų.
2. **Išplėstas vaizdas** — maršruto grafas (ankstesnis kelias drobėje) ir **Maršrutų istorija** (24h / 7d); spustelėkite įvykį, kad atkurtumėte maršrutą grafe.
3. **Žymės** — mygtukas **Tags** ant hosto; filtro lustai virš sąrašo (pvz. `dc`, `vpn`, `customer-x`). Išsaugokite YAML (**Save**).
4. **Hop etiketės** grafe (po IP): šalis (GeoIP užuominos) → ASN (`asn_hints.yaml`) → rDNS (asinchroninis PTR, TTL 5 min). Neprisijungusios užuominos: [CONFIGURATION.md](../en/CONFIGURATION.md#geoip-and-map).
5. **Expert ping** — langelis **Expert** → **Exten.** → išankstiniai nustatymai **MTU probe / DF / DSCP / Burst** iš `ping_presets.yaml` (AF `-4`/`-6` išsaugomas). Kiekvienas nustatymas tik užpildo `ping(8)` vėliavėles ir rodo summary/expect dialoge. MTU peržiūra — **MTU** sąraše arba Expert **MTU wizard…** (Apply → `-M do -s`). **Self-check** — trumpas DF/DSCP/Burst rinkinys → Alert (forma nesikeičia).
6. **Perspėjimai** — webhook / darbalaukis keičiantis maršrutui (`alerts:` YAML arba `--alert-webhook`). Limitą hostui: [CONFIGURATION.md](../en/CONFIGURATION.md).
7. **Išliekamumas** — `--session-db` arba **Settings → Database…**; istorija ir `hop_stats` išlieka po paleidimo iš naujo. Eksportas: `--export-report report.csv`.
8. **Telemetrija** — **Settings → Telemetry…** (sinkai sqlite/jsonl/syslog/…); Apply + **Save** YAML. Išsamiau: [CONFIGURATION.md](../en/CONFIGURATION.md).

### Headless NOC (be GUI)

Tas pats monitorius be JavaFX — patogu pamainos serveryje:

```bash
cd java
./pingui-java.sh -- --daemon --config config/hosts.example.yaml \
  --session-db data/ping.db --pid-file /tmp/pingui-java.pid \
  --alert-webhook https://hooks.example.com/pingui
```

Būsena / stop: `--status` / `--stop`. systemd: `systemd/pingui-java.service.example`. Pilnas skyrius: [DEPLOYMENT.md § Java NOC](../en/DEPLOYMENT.md#java-noc-headless-daemon-p12).

### Perdavimo kontrolinis sąrašas

| Patikra | Lūkestis |
|---------|----------|
| Įjungti hostai | Žurnale yra atnaujinimai / nėra nuolatinių „Error“ eilučių |
| Maršruto keitimas | Eilutė **Route history** + webhook (jei sukonfigūruota) |
| SQLite | `--session-db` failas auga; grafas atkuriamas po paleidimo iš naujo |
| Expert MTU / Self-check | Wizard Apply → `-M do -s`; Self-check → Alert be formos keitimo |
| Telemetrija (jei įjungta) | Sink kelias / remote dialoge; įvykiai sqlite/syslog pagal konfigūraciją |
| Daemon (jei naudojamas) | `--status` rodo running; perspėjimai ateina |
