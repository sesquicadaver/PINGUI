> **Language:** Latvian (Latviešu) · [Українська](../HOWTO.md) · [English](../en/HOWTO.md)

# HOWTO — īsi scenāriji

Īsi soļi ikdienas darbam. Pilns UI apraksts: [USER_GUIDE.md](USER_GUIDE.md).

## GUI palaišana

```bash
cd java
./pingui-java.sh
```

Valoda: izvēlne **Valoda** vai `./pingui-java.sh -- --lang lv`.

## Pievienot mērķi un monitorēt

1. Ievadiet IPv4 / hostname laukā zem saraksta.
2. **Pievienot** (vai Enter).
3. Ieslēdziet izvēles rūtiņu — sākas trace / ping.
4. Atlasiet rindu — labajā pusē parādās maršruta grafs.

## Simple un Extended skats

- **Simple** — kompakts logs (~580 px).
- **Extended** — platāka panelis, Expert ping, vēsture.

## Ping only

Ieslēdziet **Ping only** zondes režīmā, lai mērītu tikai RTT bez pilna traceroute.

## Saglabāt

**Saglabāt** (Ctrl/Cmd+S) ieraksta sarakstu profila YAML.

## Palīdzība

**F1** / izvēlne Palīdzība. Detaļas: [USER_GUIDE.md](USER_GUIDE.md).
