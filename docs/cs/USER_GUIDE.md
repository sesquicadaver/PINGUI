> **Language:** Czech (Čeština) · [Українська](../USER_GUIDE.md) · [English](../en/USER_GUIDE.md)

# Uživatelská příručka PINGUI

## Spuštění

```bash
./pingui.sh
```

Poprvé:

```bash
./pingui.sh --deploy
./pingui.sh
```

Otevře se okno **"PINGUI — Linux Session Route Monitor"**.

## Rozhraní

```
┌──────────────────┬────────────────────────────────────┐
│ Seznam cílů      │  Graf trasy (shora → dolů)         │
│ [ ] 8.8.8.8      │  [Váš PC] → hop1 → hop2 → cíl      │
│ [✓] google.com   │  vlevo — předchozí (šedá)          │
│                  │  vpravo — aktuální                 │
├──────────────────┤                                    │
│ [IP nebo hostname]│                                   │
│ Přidat  Změnit   │                                    │
│ Smazat  Uložit   │                                    │
├──────────────────┤                                    │
│ Stav / Protokol  │                                    │
└──────────────────┴────────────────────────────────────┘
```

## Seznam cílů

### Zaškrtávací políčko

- **Zapnuto** — worker trasuje trasu k cíli na pozadí.
- **Vypnuto** — cíl zůstává jen v seznamu, bez ICMP.
- Současně lze trasovat až **10** cílů (limit seznamu = limit aktivních trasování).

### Přidat

1. Zadejte IPv4 adresu nebo hostname do pole dole.
2. Klikněte **Přidat** nebo stiskněte Enter.
3. Nový cíl se objeví v seznamu (políčko vypnuté).

### Změnit

1. Vyberte cíl v seznamu.
2. Upravte text ve vstupním poli (nebo F2 / dvojklik na řádek).
3. Klikněte **Změnit**.

### Smazat

1. Vyberte cíl.
2. Klikněte **Smazat**.

### Uložit

Zapíše aktuální seznam do YAML konfiguračního souboru (cesta ze startu, obvykle `config/hosts.example.yaml`).
Potvrzení se objeví jako řádek v protokolu.

## Graf trasy

- Zobrazuje se pro **vybraný** cíl v seznamu.
- **Váš PC** — lokální uzel na začátku řetězce.
- **Hop N** — mezilehlý směrovač; štítek ukazuje IP a průměrné RTT.
- **`*`** — timeout na hopu (žádná odpověď).
- Barvy RTT: zelená (<50 ms), žlutá (<150 ms), červená (≥150 ms), šedá — žádná data.

### Předchozí vs aktuální trasa

Když se změní IP řetězec:

- **Levý sloupec (šedý)** — předchozí trasa; u timeoutů se zobrazují **poslední známé IP**.
- **Pravý sloupec** — aktuální trasování.

## Protokol

- **ROUTE CHANGE** — varování s „bylo / je“.
- **Error [host]** — chybí oprávnění ICMP, selhání DNS, timeout atd.
- Operace se seznamem (přidáno, změněno, smazáno, uloženo).

## Stavový řádek

„Last update [host]: HH:MM:SS“ — čas posledního úspěšného trasování vybraného cíle.

## Data relace

Ve výchozím stavu trasy a historie pingu žijí **v RAM** (ztratí se po zavření okna).
Volitelně: `--session-db` / **Nastavení → Databáze…** — SQLite uchovává metriky a události mezi restarty.
Uložený YAML obsahuje **seznam cílů** (a cestu/politiku persistence, pokud je nastavena), ne úplnou historii hopů bez DB.

## Časté problémy

| Problém | Akce |
|---------|------|
| Nelze přidat cíl | Zkontrolujte formát IP/hostname; limit 10 cílů |
| Trasování nefunguje | Zapněte políčko; spusťte `./scripts/check_caps.sh` |
| Prázdný graf | Zapněte políčko a počkejte na první cyklus (~1 s) |
| Vstupní pole šedé | Dosaženo 10 cílů — jeden smažte |

## CLI (pokročilé volby)

```bash
.venv/bin/python -m pingui --interval 2 --max-hops 30 --verbose
```

Podrobnosti: [CONFIGURATION.md](../en/CONFIGURATION.md).

## Pro / NOC workflow (Java)

Cílový scénář pro směnu NOC/SRE na **Java edici** (`cd java && ./pingui-java.sh`). Základní Python GUI výše zůstává pro rychlé session monitorování; níže je profesionální smyčka.

### Spuštění Java GUI

```bash
cd java
./pingui-java.sh -- --config config/hosts.example.yaml --session-db data/ping.db
# Terminál se ihned uvolní (GUI oddělené). Debug: ./pingui-java.sh --foreground -- …
# Log GUI: ~/.cache/pingui/gui.log (nebo $PINGUI_GUI_LOG)
```

Oprávnění ICMP / raw: viz [DEPLOYMENT.md](../en/DEPLOYMENT.md) a `./scripts/check_caps.sh`. Detaily UI: [JAVA.md](../en/JAVA.md).

### Typická směna (15–30 min)

1. **Zapněte cíle** políčky (nebo `enabled: true` v YAML) — bez toho není trasování ani zápis do SQLite.
2. **Rozšířené zobrazení** — graf trasy (předchozí cesta na canvasu) a **Historie tras** (24h / 7d); kliknutím na událost přehrajete trasu na grafu.
3. **Štítky** — tlačítko **Tags** na hostiteli; filtrační chipy nad seznamem (např. `dc`, `vpn`, `customer-x`). Uložte YAML (**Save**).
4. **Popisky hop** na grafu (za IP): země (GeoIP nápovědy) → ASN (`asn_hints.yaml`) → rDNS (asynchronní PTR, TTL 5 min). Offline nápovědy: [CONFIGURATION.md](../en/CONFIGURATION.md#geoip-and-map).
5. **Expert ping** — políčko **Expert** → **Exten.** → předvolby **MTU probe / DF / DSCP / Burst** z `ping_presets.yaml` (AF `-4`/`-6` zůstává). Každá předvolba pouze vyplní příznaky `ping(8)` a zobrazí summary/expect v dialogu. Průchod MTU — **MTU** v seznamu nebo Expert **MTU wizard…** (Apply → `-M do -s`). **Self-check** — krátká dávka DF/DSCP/Burst → Alert (formulář se nemění).
6. **Alerty** — webhook / desktop při změně trasy (`alerts:` v YAML nebo `--alert-webhook`). Limit na hostitele: [CONFIGURATION.md](../en/CONFIGURATION.md).
7. **Perzistence** — `--session-db` nebo **Settings → Database…**; historie a `hop_stats` přežijí restart. Export: `--export-report report.csv`.
8. **Telemetrie** — **Settings → Telemetry…** (sinky sqlite/jsonl/syslog/…); Apply + **Save** YAML. Podrobnosti: [CONFIGURATION.md](../en/CONFIGURATION.md).

### Headless NOC (bez GUI)

Stejný monitor bez JavaFX — užitečné na serveru směny:

```bash
cd java
./pingui-java.sh -- --daemon --config config/hosts.example.yaml \
  --session-db data/ping.db --pid-file /tmp/pingui-java.pid \
  --alert-webhook https://hooks.example.com/pingui
```

Stav / stop: `--status` / `--stop`. systemd: `systemd/pingui-java.service.example`. Celá sekce: [DEPLOYMENT.md § Java NOC](../en/DEPLOYMENT.md#java-noc-headless-daemon-p12).

### Kontrolní seznam předání

| Kontrola | Očekávání |
|----------|-----------|
| Zapnutí hostitelé | Protokol ukazuje aktualizace / žádné trvalé řádky „Error“ |
| Změna trasy | Řádek v **Route history** + webhook (pokud je nastaven) |
| SQLite | Soubor `--session-db` roste; graf se po restartu obnoví |
| Expert MTU / Self-check | Wizard Apply → `-M do -s`; Self-check → Alert bez změny formuláře |
| Telemetrie (pokud je zapnutá) | Cesta/remote sinku v dialogu; události v sqlite/syslog dle konfigurace |
| Daemon (pokud se používá) | `--status` ukazuje running; alerty dorazí |
