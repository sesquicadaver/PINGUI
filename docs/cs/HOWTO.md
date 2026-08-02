> **Language:** Czech (Čeština) · [Українська](../HOWTO.md) · [English](../en/HOWTO.md)

# HOWTO — rychlé scénáře

Krátké kroky pro denní práci. Plný popis UI: [USER_GUIDE.md](USER_GUIDE.md).

## Spuštění GUI

```bash
cd java
./pingui-java.sh
```

Jazyk: menu **Jazyk** nebo `./pingui-java.sh -- --lang cs`.

## Přidat cíl a monitorovat

1. Zadejte IPv4 / hostname do pole pod seznamem.
2. **Přidat** (nebo Enter).
3. Zapněte checkbox — spustí se trace / ping.
4. Vyberte řádek — vpravo se zobrazí graf trasy.

## Režim Simple a Extended

- **Simple** — kompaktní okno (~580 px).
- **Extended** — širší panel, Expert ping, historie.

## Ping only

Zapněte **Ping only** v režimu probe pro měření pouze RTT bez plného traceroute.

## Uložit

**Uložit** (Ctrl/Cmd+S) zapíše seznam do YAML profilu.

## Nápověda

**F1** / menu Nápověda. Podrobnosti: [USER_GUIDE.md](USER_GUIDE.md).
