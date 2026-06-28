package io.pingui.probe;

import java.util.List;
import java.util.Set;

/** Expert ping options from pingMan.txt; excludes count/time-limit flags. */
public final class PingOptionCatalog {
    public enum Kind {
        /** On/off flag (-4, -n, …). */
        FLAG,
        /** Option with argument (-s, -M, …). */
        VALUE
    }

    /** How a VALUE option is edited and validated. */
    public enum ValueKind {
        INT_RANGE,
        CHOICES,
        HEX_PATTERN,
        HEX_FLOW_LABEL,
        TIMESTAMP,
        TEXT
    }

    public record IntRange(long min, long max) {}

    public record ValueSpec(ValueKind kind, IntRange intRange, List<String> choices, String hint) {
        public ValueSpec {
            choices = choices != null ? List.copyOf(choices) : List.of();
        }

        public static ValueSpec intRange(long min, long max, String hint) {
            return new ValueSpec(ValueKind.INT_RANGE, new IntRange(min, max), List.of(), hint);
        }

        public static ValueSpec choices(List<String> values) {
            return new ValueSpec(ValueKind.CHOICES, null, values, null);
        }

        public static ValueSpec hexPattern(String hint) {
            return new ValueSpec(ValueKind.HEX_PATTERN, null, List.of(), hint);
        }

        public static ValueSpec hexFlowLabel() {
            return new ValueSpec(ValueKind.HEX_FLOW_LABEL, null, List.of(), "00000–fffff");
        }

        public static ValueSpec text(String hint) {
            return new ValueSpec(ValueKind.TEXT, null, List.of(), hint);
        }

        public static ValueSpec timestamp() {
            return new ValueSpec(ValueKind.TIMESTAMP, null, List.of("tsonly", "tsandaddr"), "tsonly / tsandaddr / tsprespec h1 …");
        }
    }

    public record PingOption(String flag, Kind kind, String description, ValueSpec valueSpec) {
        public PingOption {
            if (kind == Kind.FLAG && valueSpec != null) {
                throw new IllegalArgumentException("FLAG option must not have valueSpec: " + flag);
            }
            if (kind == Kind.VALUE && valueSpec == null) {
                throw new IllegalArgumentException("VALUE option requires valueSpec: " + flag);
            }
        }

        public static PingOption flag(String flag, String description) {
            return new PingOption(flag, Kind.FLAG, description, null);
        }

        public static PingOption value(String flag, String description, ValueSpec spec) {
            return new PingOption(flag, Kind.VALUE, description, spec);
        }
    }

    private static final Set<String> EXCLUDED =
            Set.of("-c", "-w", "-W", "-i", "-f", "-l", "-A", "-h", "-V", "-N");

    private static final List<String> PMTUDISC = List.of("do", "want", "probe", "dont");

    private static final List<PingOption> OPTIONS =
            List.of(
                    PingOption.flag("-4", "Лише IPv4"),
                    PingOption.flag("-6", "Лише IPv6"),
                    PingOption.flag("-a", "Звуковий ping"),
                    PingOption.flag("-b", "Broadcast ping"),
                    PingOption.flag("-B", "Не змінювати source address"),
                    PingOption.flag("-C", "connect() на сокеті"),
                    PingOption.flag("-d", "SO_DEBUG на сокеті"),
                    PingOption.flag("-D", "Timestamp перед кожним рядком"),
                    PingOption.value("-e", "ICMP identification field", ValueSpec.intRange(0, 65535, "0–65535")),
                    PingOption.value("-F", "IPv6 flow label (hex)", ValueSpec.hexFlowLabel()),
                    PingOption.flag("-H", "DNS reverse для виводу"),
                    PingOption.value("-I", "Інтерфейс / source / VRF", ValueSpec.text("eth0 або адреса")),
                    PingOption.flag("-L", "Приглушити loopback multicast"),
                    PingOption.value("-m", "SO_MARK для пакетів", ValueSpec.intRange(0, 4294967295L, "0–4294967295")),
                    PingOption.value("-M", "Path MTU Discovery", ValueSpec.choices(PMTUDISC)),
                    PingOption.flag("-n", "Лише числові адреси"),
                    PingOption.flag("-O", "Звіт про неотримані відповіді"),
                    PingOption.value("-p", "Pad pattern (hex)", ValueSpec.hexPattern("до 16 байт (hex)")),
                    PingOption.flag("-q", "Quiet output"),
                    PingOption.value("-Q", "QoS / TOS bits", ValueSpec.intRange(0, 255, "0–255 або 0x..")),
                    PingOption.flag("-r", "Bypass routing table"),
                    PingOption.flag("-R", "Record route (RECORD_ROUTE)"),
                    PingOption.value("-s", "Розмір data bytes", ValueSpec.intRange(0, 65507, "0–65507")),
                    PingOption.value("-S", "Socket sndbuf", ValueSpec.intRange(0, 2147483647L, "≥ 0")),
                    PingOption.value("-t", "IP TTL", ValueSpec.intRange(1, 255, "1–255")),
                    PingOption.value("-T", "IP timestamp option", ValueSpec.timestamp()),
                    PingOption.flag("-U", "User-to-user latency"),
                    PingOption.flag("-v", "Verbose output"));

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
