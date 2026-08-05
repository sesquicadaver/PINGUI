> **Language:** Polish (Polski) · [Українська](../HOWTO.md) · [English](../en/HOWTO.md)

# HOWTO — szybkie scenariusze

Krótkie kroki na co dzień. Pełny opis UI: [USER_GUIDE.md](USER_GUIDE.md).

## Uruchomienie GUI

```bash
cd java
./pingui-java.sh
```

Język: menu **Język** lub `./pingui-java.sh -- --lang pl`.

## Dodaj cel i monitoruj

1. Wpisz IPv4 / hostname w polu pod listą.
2. **Dodaj** (lub Enter).
3. Włącz checkbox — start trace / ping.
4. Zaznacz wiersz — po prawej pojawi się graf trasy.

## Widok Simple i Extended

- **Simple** — kompaktowe okno (~580 px).
- **Extended** — szerszy panel, Expert ping, historia.

## Ping only

Włącz **Ping only** w trybie probe, aby mierzyć tylko RTT bez pełnego traceroute.

## Zapisz

**Zapisz** (Ctrl/Cmd+S) zapisuje listę do YAML profilu.

## Pomoc

**F1** / menu Pomoc. Szczegóły: [USER_GUIDE.md](USER_GUIDE.md).
