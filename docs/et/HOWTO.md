> **Language:** Estonian (Eesti) · [Українська](../HOWTO.md) · [English](../en/HOWTO.md)

# HOWTO — kiired stsenaariumid

Lühikesed sammud igapäevaseks tööks. Täielik UI kirjeldus: [USER_GUIDE.md](USER_GUIDE.md).

## GUI käivitamine

```bash
cd java
./pingui-java.sh
```

Keel: menüü **Keel** või `./pingui-java.sh -- --lang et`.

## Lisa sihtmärk ja jälgi

1. Sisesta IPv4 / hostname välja loendi all.
2. **Lisa** (või Enter).
3. Lülita märkeruut sisse — algab trace / ping.
4. Vali rida — paremal ilmub marsruudi graaf.

## Simple ja Extended vaade

- **Simple** — kompaktne aken (~580 px).
- **Extended** — laiem paneel, Expert ping, ajalugu.

## Ping only

Lülita **Ping only** probe-režiimis, et mõõta ainult RTT ilma täieliku traceroute’ta.

## Salvesta

**Salvesta** (Ctrl/Cmd+S) kirjutab loendi profiili YAML-i.

## Abi

**F1** / Abi menüü. Detailid: [USER_GUIDE.md](USER_GUIDE.md).
