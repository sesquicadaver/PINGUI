> **Language:** Spanish (Español) · [Українська](../USER_GUIDE.md) · [English](../en/USER_GUIDE.md)

# Guía de usuario de PINGUI

## Inicio

```bash
./pingui.sh
```

Primera vez:

```bash
./pingui.sh --deploy
./pingui.sh
```

Se abre la ventana **"PINGUI — Linux Session Route Monitor"**.

## Interfaz

```
┌──────────────────┬────────────────────────────────────┐
│ Lista de destinos│  Grafo de ruta (arriba → abajo)    │
│ [ ] 8.8.8.8      │  [Su PC] → hop1 → hop2 → destino   │
│ [✓] google.com   │  izquierda — anterior (gris)       │
│                  │  derecha — actual                  │
├──────────────────┤                                    │
│ [IP o hostname]  │                                    │
│ Añadir  Cambiar  │                                    │
│ Eliminar Guardar │                                    │
├──────────────────┤                                    │
│ Estado / Registro│                                    │
└──────────────────┴────────────────────────────────────┘
```

## Lista de destinos

### Casilla

- **Activado** — el worker traza la ruta al destino en segundo plano.
- **Desactivado** — el destino permanece en la lista, sin ICMP.
- Se pueden trazar hasta **10** destinos a la vez (límite de lista = límite de trazas activas).

### Añadir

1. Introduzca una dirección IPv4 o un hostname en el campo inferior.
2. Pulse **Añadir** o Enter.
3. El nuevo destino aparece en la lista (casilla desactivada).

### Cambiar

1. Seleccione un destino en la lista.
2. Edite el texto en el campo de entrada (o F2 / doble clic en la fila).
3. Pulse **Cambiar**.

### Eliminar

1. Seleccione un destino.
2. Pulse **Eliminar**.

### Guardar

Escribe la lista actual en el archivo YAML de configuración (ruta del arranque, normalmente `config/hosts.example.yaml`).
La confirmación aparece como una línea en el registro.

## Grafo de ruta

- Se muestra para el destino **seleccionado** en la lista.
- **Su PC** — nodo local al inicio de la cadena.
- **Hop N** — router intermedio; la etiqueta muestra IP y RTT medio.
- **`*`** — tiempo de espera en el hop (sin respuesta).
- Colores RTT: verde (<50 ms), amarillo (<150 ms), rojo (≥150 ms), gris — sin datos.

### Ruta anterior vs actual

Cuando cambia la cadena de IP:

- **Columna izquierda (gris)** — ruta anterior; para tiempos de espera se muestran las **últimas IP conocidas**.
- **Columna derecha** — traza actual.

## Registro

- **ROUTE CHANGE** — aviso con «era / ahora».
- **Error [host]** — sin permisos ICMP, fallo DNS, tiempo de espera, etc.
- Operaciones de lista (añadido, cambiado, eliminado, guardado).

## Barra de estado

«Last update [host]: HH:MM:SS» — hora de la última traza correcta del destino seleccionado.

## Datos de sesión

Por defecto las rutas y el historial de ping viven **en RAM** (se pierden al cerrar la ventana).
Opcionalmente: `--session-db` / **Ajustes → Base de datos…** — SQLite conserva métricas y eventos entre reinicios.
El YAML guardado contiene la **lista de destinos** (y la ruta/política de persistencia si está definida), no el historial completo de hops sin una BD.

## Problemas frecuentes

| Problema | Acción |
|----------|--------|
| No se puede añadir un destino | Compruebe el formato IP/hostname; límite de 10 destinos |
| La traza no funciona | Active la casilla; ejecute `./scripts/check_caps.sh` |
| Grafo vacío | Active la casilla y espere el primer ciclo (~1 s) |
| Campo de entrada en gris | Se alcanzó el límite de 10 — elimine uno |

## CLI (opciones avanzadas)

```bash
.venv/bin/python -m pingui --interval 2 --max-hops 30 --verbose
```

Detalles: [CONFIGURATION.md](../en/CONFIGURATION.md).

## Flujo Pro / NOC (Java)

Escenario objetivo para un turno NOC/SRE de guardia en la **edición Java** (`cd java && ./pingui-java.sh`). La GUI Python básica anterior sigue siendo útil para monitorización de sesión rápida; abajo está el ciclo profesional.

### Inicio de la GUI Java

```bash
cd java
./pingui-java.sh -- --config config/hosts.example.yaml --session-db data/ping.db
# El terminal vuelve de inmediato (GUI desacoplada). Depuración: ./pingui-java.sh --foreground -- …
# Registro GUI: ~/.cache/pingui/gui.log (o $PINGUI_GUI_LOG)
```

Permisos ICMP / raw: ver [DEPLOYMENT.md](../en/DEPLOYMENT.md) y `./scripts/check_caps.sh`. Detalles de UI: [JAVA.md](../en/JAVA.md).

### Turno típico (15–30 min)

1. **Active destinos** con casillas (o `enabled: true` en YAML) — sin esto no hay traza ni escritura en SQLite.
2. **Vista ampliada** — grafo de ruta (ruta anterior en el lienzo) e **Historial de rutas** (24h / 7d); pulse un evento para reproducir la ruta en el grafo.
3. **Etiquetas** — botón **Tags** en el host; chips de filtro sobre la lista (p. ej. `dc`, `vpn`, `customer-x`). Guarde el YAML (**Save**).
4. **Etiquetas de hop** en el grafo (después de la IP): país (sugerencias GeoIP) → ASN (`asn_hints.yaml`) → rDNS (PTR asíncrono, TTL 5 min). Sugerencias sin conexión: [CONFIGURATION.md](../en/CONFIGURATION.md#geoip-and-map).
5. **Expert ping** — casilla **Expert** → **Exten.** → presets **MTU probe / DF / DSCP / Burst** de `ping_presets.yaml` (se conserva AF `-4`/`-6`). Cada preset solo rellena flags de `ping(8)` y muestra summary/expect en el diálogo. Barrido MTU — **MTU** en la lista o Expert **MTU wizard…** (Apply → `-M do -s`). **Self-check** — lote corto DF/DSCP/Burst → Alert (el formulario no cambia).
6. **Alertas** — webhook / escritorio ante cambio de ruta (`alerts:` en YAML o `--alert-webhook`). Límite por host: [CONFIGURATION.md](../en/CONFIGURATION.md).
7. **Persistencia** — `--session-db` o **Settings → Database…**; el historial y `hop_stats` sobreviven al reinicio. Exportación: `--export-report report.csv`.
8. **Telemetría** — **Settings → Telemetry…** (sinks sqlite/jsonl/syslog/…); Apply + **Save** YAML. Detalles: [CONFIGURATION.md](../en/CONFIGURATION.md).

### NOC sin interfaz (sin GUI)

El mismo monitor sin JavaFX — útil en el servidor de turno:

```bash
cd java
./pingui-java.sh -- --daemon --config config/hosts.example.yaml \
  --session-db data/ping.db --pid-file /tmp/pingui-java.pid \
  --alert-webhook https://hooks.example.com/pingui
```

Estado / parada: `--status` / `--stop`. systemd: `systemd/pingui-java.service.example`. Sección completa: [DEPLOYMENT.md § Java NOC](../en/DEPLOYMENT.md#java-noc-headless-daemon-p12).

### Lista de traspaso

| Comprobación | Expectativa |
|--------------|-------------|
| Hosts activados | El registro muestra actualizaciones / no hay líneas constantes de «Error» |
| Cambio de ruta | Fila en **Route history** + webhook (si está configurado) |
| SQLite | El archivo `--session-db` crece; el grafo se restaura tras reiniciar |
| Expert MTU / Self-check | Wizard Apply → `-M do -s`; Self-check → Alert sin cambiar el formulario |
| Telemetría (si está activa) | Ruta/remoto del sink en el diálogo; eventos en sqlite/syslog según config |
| Daemon (si se usa) | `--status` muestra running; las alertas llegan |
