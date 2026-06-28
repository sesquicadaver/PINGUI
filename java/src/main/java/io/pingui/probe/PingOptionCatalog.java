package io.pingui.probe;

import java.util.List;
import java.util.Set;

/** Expert ping options from pingMan.txt; excludes count/time-limit flags. */
public final class PingOptionCatalog {
    public enum Kind {
        FLAG,
        VALUE
    }

    public record PingOption(String flag, Kind kind, String description, String valueHint) {}

    private static final Set<String> EXCLUDED =
            Set.of("-c", "-w", "-W", "-i", "-f", "-l", "-A", "-h", "-V", "-N");

    private static final List<PingOption> OPTIONS =
            List.of(
                    new PingOption("-4", Kind.FLAG, "Лише IPv4", null),
                    new PingOption("-6", Kind.FLAG, "Лише IPv6", null),
                    new PingOption("-a", Kind.FLAG, "Звуковий ping", null),
                    new PingOption("-b", Kind.FLAG, "Broadcast ping", null),
                    new PingOption("-B", Kind.FLAG, "Не змінювати source address", null),
                    new PingOption("-C", Kind.FLAG, "connect() на сокеті", null),
                    new PingOption("-d", Kind.FLAG, "SO_DEBUG на сокеті", null),
                    new PingOption("-D", Kind.FLAG, "Timestamp перед кожним рядком", null),
                    new PingOption("-e", Kind.VALUE, "ICMP identification field", "0-65535"),
                    new PingOption("-F", Kind.VALUE, "IPv6 flow label (hex)", "00000-fffff"),
                    new PingOption("-H", Kind.FLAG, "DNS reverse для виводу", null),
                    new PingOption("-I", Kind.VALUE, "Інтерфейс / source / VRF", "eth0 або адреса"),
                    new PingOption("-L", Kind.FLAG, "Приглушити loopback multicast", null),
                    new PingOption("-m", Kind.VALUE, "SO_MARK для пакетів", "mark"),
                    new PingOption("-M", Kind.VALUE, "Path MTU Discovery", "do|want|probe|dont"),
                    new PingOption("-n", Kind.FLAG, "Лише числові адреси", null),
                    new PingOption("-O", Kind.FLAG, "Звіт про неотримані відповіді", null),
                    new PingOption("-p", Kind.VALUE, "Pad pattern (hex)", "ff або ff00"),
                    new PingOption("-q", Kind.FLAG, "Quiet output", null),
                    new PingOption("-Q", Kind.VALUE, "QoS / TOS bits", "0-255 або hex"),
                    new PingOption("-r", Kind.FLAG, "Bypass routing table", null),
                    new PingOption("-R", Kind.FLAG, "Record route (RECORD_ROUTE)", null),
                    new PingOption("-s", Kind.VALUE, "Розмір data bytes", "56"),
                    new PingOption("-S", Kind.VALUE, "Socket sndbuf", "bytes"),
                    new PingOption("-t", Kind.VALUE, "IP TTL", "1-255"),
                    new PingOption("-T", Kind.VALUE, "IP timestamp option", "tsonly|tsandaddr|tsprespec …"),
                    new PingOption("-U", Kind.FLAG, "User-to-user latency", null),
                    new PingOption("-v", Kind.FLAG, "Verbose output", null));

    private PingOptionCatalog() {}

    public static List<PingOption> options() {
        return OPTIONS;
    }

    public static boolean isExcluded(String flag) {
        return EXCLUDED.contains(flag);
    }

    public static PingOption find(String flag) {
        for (PingOption option : OPTIONS) {
            if (option.flag().equals(flag)) {
                return option;
            }
        }
        return null;
    }
}
