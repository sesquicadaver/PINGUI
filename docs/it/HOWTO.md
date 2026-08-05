> **Language:** Italian (Italiano) · [Українська](../HOWTO.md) · [English](../en/HOWTO.md)

# HOWTO — scenari rapidi

Passi brevi per l’uso quotidiano. Descrizione completa UI: [USER_GUIDE.md](USER_GUIDE.md).

## Avviare la GUI

```bash
cd java
./pingui-java.sh
```

Lingua: menu **Lingua** o `./pingui-java.sh -- --lang it`.

## Aggiungere un target e monitorare

1. Inserire IPv4 / hostname nel campo sotto l’elenco.
2. **Aggiungi** (o Invio).
3. Attivare la casella — partono trace / ping.
4. Selezionare la riga — a destra compare il grafo del percorso.

## Vista Simple ed Extended

- **Simple** — finestra compatta (~580 px).
- **Extended** — pannello largo, Expert ping, cronologia.

## Ping only

Attivare **Ping only** nella modalità probe per misurare solo RTT senza traceroute completo.

## Salvare

**Salva** (Ctrl/Cmd+S) scrive l’elenco nel YAML del profilo.

## Aiuto

**F1** / menu Aiuto. Dettagli: [USER_GUIDE.md](USER_GUIDE.md).
