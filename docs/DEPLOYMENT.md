# Розгортання PINGUI (Java)

## Платформи

| ОС | Підтримка |
|----|-----------|
| Linux (Ubuntu 22.04+) | ✅ |
| Windows 11+ | ✅ |
| macOS 12+ | ✅ |

Покроковий checklist: **[CHECKLIST.md](CHECKLIST.md)**.

Expert ping (режим «Експерт») — **лише Linux** (iputils `ping`).

## Вимоги

| Компонент | Версія |
|-----------|--------|
| JDK | **21** (не Java 25 як launcher Gradle) |
| traceroute | Linux/macOS |
| tracert | Windows (вбудований) |
| Дисплей | X11 / Wayland / Windows Desktop |

## Запуск

**Linux / macOS**

```bash
cd java
chmod +x pingui-java.sh gradlew
export PINGUI_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64   # за потреби
./pingui-java.sh --build
./pingui-java.sh
```

**Windows**

```bat
cd java
pingui-java.bat --build
pingui-java.bat
```

## Збірка та пакування

```bash
cd java
./gradlew build          # compile + jar
./pingui-java.sh --package   # jpackage: .deb / .dmg / .msi
```

## Raw ICMP (Linux, опційно)

За замовч. використовується `traceroute`/`tracert`. Raw ICMP (`probe: auto|raw`):

```bash
sudo setcap cap_net_raw+ep "$(readlink -f "$(which java)")"
```

## Конфігурація

Приклад: `java/config/hosts.example.yaml`. Формат v2:

```yaml
active_profile: default
profiles:
  default:
    interval: 1.0
    max_hops: 20
    timeout: 0.5
    probe: auto
    hosts:
      - address: "8.8.8.8"
        enabled: true
```

## Troubleshooting

| Симптом | Рішення |
|---------|---------|
| Gradle «What went wrong: 25.0.3» | JDK 21: `export PINGUI_JAVA_HOME=.../java-21-openjdk-*` |
| «No hops parsed» | Встановити `traceroute`; на macOS — `/usr/sbin/traceroute` |
| JavaFX runtime missing | `./gradlew run` або jpackage installer |
| Expert ping без RTT | Linux + `iputils-ping` |

## Розробка

Тести, CI, Python-редакція — гілка **`beta`**.
