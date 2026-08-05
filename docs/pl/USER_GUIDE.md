> **Language:** Polish (Polski) · [Українська](../USER_GUIDE.md) · [English](../en/USER_GUIDE.md)

# Podręcznik użytkownika PINGUI

## Uruchomienie

```bash
./pingui.sh
```

Pierwszy raz:

```bash
./pingui.sh --deploy
./pingui.sh
```

Otwiera się okno **"PINGUI — Linux Session Route Monitor"**.

## Interfejs

```
┌──────────────────┬────────────────────────────────────┐
│ Lista celów      │  Graf trasy (góra → dół)           │
│ [ ] 8.8.8.8      │  [Twój PC] → hop1 → hop2 → cel     │
│ [✓] google.com   │  lewa — poprzednia (szara)         │
│                  │  prawa — bieżąca                   │
├──────────────────┤                                    │
│ [IP lub hostname]│                                    │
│ Dodaj  Zmień     │                                    │
│ Usuń   Zapisz    │                                    │
├──────────────────┤                                    │
│ Status / Dziennik│                                    │
└──────────────────┴────────────────────────────────────┘
```

## Lista celów

### Pole wyboru

- **Włączone** — worker trasuje trasę do celu w tle.
- **Wyłączone** — cel pozostaje tylko na liście, bez ICMP.
- Jednocześnie można trasować do **10** celów (limit listy = limit aktywnych trasowań).

### Dodaj

1. Wpisz adres IPv4 lub hostname w polu na dole.
2. Kliknij **Dodaj** lub naciśnij Enter.
3. Nowy cel pojawia się na liście (pole wyboru wyłączone).

### Zmień

1. Wybierz cel na liście.
2. Edytuj tekst w polu wejściowym (lub F2 / podwójne kliknięcie wiersza).
3. Kliknij **Zmień**.

### Usuń

1. Wybierz cel.
2. Kliknij **Usuń**.

### Zapisz

Zapisuje bieżącą listę do pliku YAML konfiguracji (ścieżka ze startu, zwykle `config/hosts.example.yaml`).
Potwierdzenie pojawia się jako wiersz w dzienniku.

## Graf trasy

- Wyświetlany dla **zaznaczonego** celu na liście.
- **Twój PC** — węzeł lokalny na początku łańcucha.
- **Hop N** — router pośredni; etykieta pokazuje IP i średnie RTT.
- **`*`** — timeout na hopie (brak odpowiedzi).
- Kolory RTT: zielony (<50 ms), żółty (<150 ms), czerwony (≥150 ms), szary — brak danych.

### Poprzednia vs bieżąca trasa

Gdy łańcuch IP się zmienia:

- **Lewa kolumna (szara)** — poprzednia trasa; dla timeoutów pokazywane są **ostatnie znane IP**.
- **Prawa kolumna** — bieżące trasowanie.

## Dziennik

- **ROUTE CHANGE** — ostrzeżenie z «było / jest».
- **Error [host]** — brak uprawnień ICMP, błąd DNS, timeout itd.
- Operacje na liście (dodano, zmieniono, usunięto, zapisano).

## Pasek statusu

«Last update [host]: HH:MM:SS» — czas ostatniego udanego trasowania dla zaznaczonego celu.

## Dane sesji

Domyślnie trasy i historia ping żyją **w RAM** (znikają po zamknięciu okna).
Opcjonalnie: `--session-db` / **Ustawienia → Baza danych…** — SQLite zachowuje metryki i zdarzenia między restartami.
Zapisany YAML zawiera **listę celów** (oraz ścieżkę/politykę persistence, jeśli ustawiono), nie pełną historię hopów bez bazy.

## Typowe problemy

| Problem | Działanie |
|---------|-----------|
| Nie można dodać celu | Sprawdź format IP/hostname; limit 10 celów |
| Trasowanie nie działa | Włącz pole wyboru; uruchom `./scripts/check_caps.sh` |
| Pusty graf | Włącz pole wyboru i poczekaj na pierwszy cykl (~1 s) |
| Pole wejściowe szare | Osiągnięto 10 celów — usuń jeden |

## CLI (opcje zaawansowane)

```bash
.venv/bin/python -m pingui --interval 2 --max-hops 30 --verbose
```

Szczegóły: [CONFIGURATION.md](../en/CONFIGURATION.md).

## Workflow Pro / NOC (Java)

Scenariusz docelowy dla dyżuru NOC/SRE na **edycji Java** (`cd java && ./pingui-java.sh`). Podstawowe GUI Pythona powyżej pozostaje do szybkiego monitoringu sesji; poniżej pętla pro.

### Uruchomienie GUI Java

```bash
cd java
./pingui-java.sh -- --config config/hosts.example.yaml --session-db data/ping.db
# Terminal wraca od razu (GUI odłączone). Debug: ./pingui-java.sh --foreground -- …
# Log GUI: ~/.cache/pingui/gui.log (lub $PINGUI_GUI_LOG)
```

Uprawnienia ICMP / raw: zobacz [DEPLOYMENT.md](../en/DEPLOYMENT.md) i `./scripts/check_caps.sh`. Szczegóły UI: [JAVA.md](../en/JAVA.md).

### Typowa zmiana (15–30 min)

1. **Włącz cele** polami wyboru (lub `enabled: true` w YAML) — bez tego nie ma trasowania ani zapisów SQLite.
2. **Widok rozszerzony** — graf trasy (poprzednia ścieżka na canvasie) i **Historia tras** (24h / 7d); kliknij zdarzenie, aby odtworzyć trasę na grafie.
3. **Tagi** — przycisk **Tags** na hoście; chipy filtrów nad listą (np. `dc`, `vpn`, `customer-x`). Zapisz YAML (**Save**).
4. **Etykiety hop** na grafie (po IP): kraj (podpowiedzi GeoIP) → ASN (`asn_hints.yaml`) → rDNS (asynchroniczny PTR, TTL 5 min). Podpowiedzi offline: [CONFIGURATION.md](../en/CONFIGURATION.md#geoip-and-map).
5. **Expert ping** — pole **Expert** → **Exten.** → presety **MTU probe / DF / DSCP / Burst** z `ping_presets.yaml` (AF `-4`/`-6` jest zachowane). Każdy preset tylko wypełnia flagi `ping(8)` i pokazuje summary/expect w oknie. Przeszukanie MTU — **MTU** na liście lub Expert **MTU wizard…** (Apply → `-M do -s`). **Self-check** — krótka partia DF/DSCP/Burst → Alert (formularz bez zmian).
6. **Alerty** — webhook / pulpit przy zmianie trasy (`alerts:` w YAML lub `--alert-webhook`). Limit na host: [CONFIGURATION.md](../en/CONFIGURATION.md).
7. **Trwałość** — `--session-db` lub **Settings → Database…**; historia i `hop_stats` przetrwają restart. Eksport: `--export-report report.csv`.
8. **Telemetria** — **Settings → Telemetry…** (sinki sqlite/jsonl/syslog/…); Apply + **Save** YAML. Szczegóły: [CONFIGURATION.md](../en/CONFIGURATION.md).

### Headless NOC (bez GUI)

Ten sam monitor bez JavaFX — przydatny na serwerze dyżuru:

```bash
cd java
./pingui-java.sh -- --daemon --config config/hosts.example.yaml \
  --session-db data/ping.db --pid-file /tmp/pingui-java.pid \
  --alert-webhook https://hooks.example.com/pingui
```

Status / stop: `--status` / `--stop`. systemd: `systemd/pingui-java.service.example`. Pełna sekcja: [DEPLOYMENT.md § Java NOC](../en/DEPLOYMENT.md#java-noc-headless-daemon-p12).

### Lista kontrolna przekazania

| Sprawdzenie | Oczekiwanie |
|-------------|-------------|
| Włączone hosty | Dziennik pokazuje aktualizacje / brak stałych linii «Error» |
| Zmiana trasy | Wiersz w **Route history** + webhook (jeśli skonfigurowano) |
| SQLite | Plik `--session-db` rośnie; graf wraca po restarcie |
| Expert MTU / Self-check | Wizard Apply → `-M do -s`; Self-check → Alert bez zmiany formularza |
| Telemetria (jeśli włączona) | Ścieżka/remote sinka w oknie; zdarzenia w sqlite/syslog wg konfiguracji |
| Daemon (jeśli używany) | `--status` pokazuje running; alerty docierają |
