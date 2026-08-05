> **Language:** Italian (Italiano) · [Українська](../USER_GUIDE.md) · [English](../en/USER_GUIDE.md)

# Guida utente PINGUI

## Avvio

```bash
./pingui.sh
```

Prima volta:

```bash
./pingui.sh --deploy
./pingui.sh
```

Si apre la finestra **"PINGUI — Linux Session Route Monitor"**.

## Interfaccia

```
┌──────────────────┬────────────────────────────────────┐
│ Elenco destinazioni│ Grafo percorso (alto → basso)    │
│ [ ] 8.8.8.8      │  [Il tuo PC] → hop1 → hop2 → dest. │
│ [✓] google.com   │  sinistra — precedente (grigio)    │
│                  │  destra — corrente                 │
├──────────────────┤                                    │
│ [IP o hostname]  │                                    │
│ Aggiungi Modifica│                                    │
│ Elimina  Salva   │                                    │
├──────────────────┤                                    │
│ Stato / Log      │                                    │
└──────────────────┴────────────────────────────────────┘
```

## Elenco destinazioni

### Casella

- **Abilitato** — il worker traccia il percorso verso la destinazione in background.
- **Disabilitato** — la destinazione resta solo nell’elenco, senza ICMP.
- Si possono tracciare fino a **10** destinazioni contemporaneamente (limite elenco = limite tracce attive).

### Aggiungi

1. Inserisci un indirizzo IPv4 o un hostname nel campo in basso.
2. Fai clic su **Aggiungi** o premi Invio.
3. La nuova destinazione compare nell’elenco (casella disabilitata).

### Modifica

1. Seleziona una destinazione nell’elenco.
2. Modifica il testo nel campo di input (oppure F2 / doppio clic sulla riga).
3. Fai clic su **Modifica**.

### Elimina

1. Seleziona una destinazione.
2. Fai clic su **Elimina**.

### Salva

Scrive l’elenco corrente nel file YAML di configurazione (percorso dall’avvio, di solito `config/hosts.example.yaml`).
La conferma appare come riga nel log.

## Grafo del percorso

- Mostrato per la destinazione **selezionata** nell’elenco.
- **Il tuo PC** — nodo locale all’inizio della catena.
- **Hop N** — router intermedio; l’etichetta mostra IP e RTT medio.
- **`*`** — timeout sull’hop (nessuna risposta).
- Colori RTT: verde (<50 ms), giallo (<150 ms), rosso (≥150 ms), grigio — nessun dato.

### Percorso precedente vs corrente

Quando la catena IP cambia:

- **Colonna sinistra (grigia)** — percorso precedente; per i timeout sono mostrati gli **ultimi IP noti**.
- **Colonna destra** — traccia corrente.

## Log

- **ROUTE CHANGE** — avviso con «era / ora».
- **Error [host]** — mancano permessi ICMP, errore DNS, timeout, ecc.
- Operazioni sull’elenco (aggiunto, modificato, eliminato, salvato).

## Barra di stato

«Last update [host]: HH:MM:SS» — ora dell’ultima traccia riuscita per la destinazione selezionata.

## Dati di sessione

Per impostazione predefinita percorsi e cronologia ping vivono **in RAM** (si perdono alla chiusura della finestra).
Opzionale: `--session-db` / **Impostazioni → Database…** — SQLite conserva metriche ed eventi tra i riavvii.
Lo YAML salvato contiene l’**elenco destinazioni** (e percorso/policy di persistenza se impostati), non la cronologia hop completa senza un DB.

## Problemi comuni

| Problema | Azione |
|----------|--------|
| Impossibile aggiungere destinazione | Controlla formato IP/hostname; limite di 10 destinazioni |
| Traccia non funziona | Abilita la casella; esegui `./scripts/check_caps.sh` |
| Grafo vuoto | Abilita la casella e attendi il primo ciclo (~1 s) |
| Campo di input grigio | Raggiunto il limite di 10 — elimina una destinazione |

## CLI (opzioni avanzate)

```bash
.venv/bin/python -m pingui --interval 2 --max-hops 30 --verbose
```

Dettagli: [CONFIGURATION.md](../en/CONFIGURATION.md).

## Workflow Pro / NOC (Java)

Scenario di riferimento per un turno NOC/SRE di guardia sull’**edizione Java** (`cd java && ./pingui-java.sh`). La GUI Python di base sopra resta per il monitoraggio di sessione rapido; sotto c’è il ciclo professionale.

### Avvio GUI Java

```bash
cd java
./pingui-java.sh -- --config config/hosts.example.yaml --session-db data/ping.db
# Il terminale torna subito libero (GUI distaccata). Debug: ./pingui-java.sh --foreground -- …
# Log GUI: ~/.cache/pingui/gui.log (o $PINGUI_GUI_LOG)
```

Permessi ICMP / raw: vedi [DEPLOYMENT.md](../en/DEPLOYMENT.md) e `./scripts/check_caps.sh`. Dettagli UI: [JAVA.md](../en/JAVA.md).

### Turno tipico (15–30 min)

1. **Abilita destinazioni** con le caselle (o `enabled: true` in YAML) — senza questo non c’è traccia né scrittura SQLite.
2. **Vista estesa** — grafo del percorso (percorso precedente sul canvas) e **Cronologia percorsi** (24h / 7d); clic su un evento per riprodurre il percorso sul grafo.
3. **Tag** — pulsante **Tags** sull’host; chip di filtro sopra l’elenco (es. `dc`, `vpn`, `customer-x`). Salva lo YAML (**Save**).
4. **Etichette hop** sul grafo (dopo l’IP): paese (suggerimenti GeoIP) → ASN (`asn_hints.yaml`) → rDNS (PTR asincrono, TTL 5 min). Suggerimenti offline: [CONFIGURATION.md](../en/CONFIGURATION.md#geoip-and-map).
5. **Expert ping** — casella **Expert** → **Exten.** → preset **MTU probe / DF / DSCP / Burst** da `ping_presets.yaml` (AF `-4`/`-6` conservato). Ogni preset riempie solo i flag di `ping(8)` e mostra summary/expect nella finestra. Sweep MTU — **MTU** nell’elenco o Expert **MTU wizard…** (Apply → `-M do -s`). **Self-check** — breve batch DF/DSCP/Burst → Alert (il modulo non cambia).
6. **Alert** — webhook / desktop al cambio percorso (`alerts:` in YAML o `--alert-webhook`). Rate limit per host: [CONFIGURATION.md](../en/CONFIGURATION.md).
7. **Persistenza** — `--session-db` o **Settings → Database…**; cronologia e `hop_stats` sopravvivono al riavvio. Export: `--export-report report.csv`.
8. **Telemetria** — **Settings → Telemetry…** (sink sqlite/jsonl/syslog/…); Apply + **Save** YAML. Dettagli: [CONFIGURATION.md](../en/CONFIGURATION.md).

### NOC headless (senza GUI)

Lo stesso monitor senza JavaFX — utile sul server di turno:

```bash
cd java
./pingui-java.sh -- --daemon --config config/hosts.example.yaml \
  --session-db data/ping.db --pid-file /tmp/pingui-java.pid \
  --alert-webhook https://hooks.example.com/pingui
```

Stato / stop: `--status` / `--stop`. systemd: `systemd/pingui-java.service.example`. Sezione completa: [DEPLOYMENT.md § Java NOC](../en/DEPLOYMENT.md#java-noc-headless-daemon-p12).

### Checklist di consegna

| Controllo | Aspettativa |
|-----------|-------------|
| Host abilitati | Il log mostra aggiornamenti / nessuna riga costante «Error» |
| Cambio percorso | Riga in **Route history** + webhook (se configurato) |
| SQLite | Il file `--session-db` cresce; il grafo si ripristina dopo il riavvio |
| Expert MTU / Self-check | Wizard Apply → `-M do -s`; Self-check → Alert senza cambiare il modulo |
| Telemetria (se attiva) | Percorso/remoto del sink nella finestra; eventi in sqlite/syslog secondo config |
| Daemon (se usato) | `--status` mostra running; gli alert arrivano |
