> **Language:** Lithuanian (Lietuvių) · [Українська](../HOWTO.md) · [English](../en/HOWTO.md)

# HOWTO — trumpi scenarijai

Trumpi žingsniai kasdieniam darbui. Pilnas UI aprašymas: [USER_GUIDE.md](USER_GUIDE.md).

## GUI paleidimas

```bash
cd java
./pingui-java.sh
```

Kalba: meniu **Kalba** arba `./pingui-java.sh -- --lang lt`.

## Pridėti taikinį ir stebėti

1. Įveskite IPv4 / hostname lauke po sąrašu.
2. **Pridėti** (arba Enter).
3. Įjunkite žymimąjį langelį — prasideda trace / ping.
4. Pažymėkite eilutę — dešinėje atsiranda maršruto grafas.

## Simple ir Extended rodinys

- **Simple** — kompaktiškas langas (~580 px).
- **Extended** — platesnė panelė, Expert ping, istorija.

## Ping only

Įjunkite **Ping only** zondo režime, kad matuotumėte tik RTT be pilno traceroute.

## Išsaugoti

**Išsaugoti** (Ctrl/Cmd+S) įrašo sąrašą į profilio YAML.

## Pagalba

**F1** / meniu Pagalba. Detalės: [USER_GUIDE.md](USER_GUIDE.md).
