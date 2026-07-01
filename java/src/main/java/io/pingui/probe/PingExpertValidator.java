package io.pingui.probe;

import io.pingui.config.ConfigError;
import io.pingui.probe.PingOptionCatalog.PingOption;
import io.pingui.probe.PingOptionCatalog.ValueSpec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Validates expert ping flag combinations (iputils ping). */
public final class PingExpertValidator {
    private PingExpertValidator() {}

    public static List<String> validateAndNormalize(List<String> rawArgs) {
        if (rawArgs == null || rawArgs.isEmpty()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : rawArgs) {
            if (token == null || token.isBlank()) {
                continue;
            }
            tokens.add(token.strip());
        }
        Set<String> flags = new HashSet<>();
        List<String> normalized = new ArrayList<>();
        int index = 0;
        while (index < tokens.size()) {
            String token = tokens.get(index);
            if (!token.startsWith("-")) {
                throw new ConfigError("Invalid ping argument (expected flag): " + token);
            }
            if (PingOptionCatalog.isExcluded(token)) {
                throw new ConfigError("Ping option not allowed in monitor mode: " + token);
            }
            PingOption option = PingOptionCatalog.find(token);
            if (option == null) {
                throw new ConfigError("Unknown ping option: " + token);
            }
            if (!flags.add(token)) {
                throw new ConfigError("Duplicate ping option: " + token);
            }
            normalized.add(token);
            if (option.kind() == PingOptionCatalog.Kind.VALUE) {
                if (index + 1 >= tokens.size()) {
                    throw new ConfigError("Ping option " + token + " requires a value");
                }
                String value = tokens.get(++index);
                normalized.add(validateAndNormalizeValue(option, value));
            }
            index++;
        }
        validateCompatibility(flags);
        return List.copyOf(normalized);
    }

    /** Validates one VALUE argument; returns normalized token for ping CLI. */
    public static String validateAndNormalizeValue(PingOption option, String rawValue) {
        if (option.kind() != PingOptionCatalog.Kind.VALUE || option.valueSpec() == null) {
            throw new ConfigError("Option " + option.flag() + " does not take a value");
        }
        if (rawValue == null || rawValue.isBlank()) {
            throw new ConfigError("Ping option " + option.flag() + " requires a non-empty value");
        }
        String value = rawValue.strip();
        ValueSpec spec = option.valueSpec();
        return switch (spec.kind()) {
            case INT_RANGE -> normalizeIntRange(option.flag(), value, spec.intRange());
            case CHOICES -> normalizeChoice(option.flag(), value, spec.choices());
            case HEX_PATTERN -> normalizeHexPattern(value);
            case HEX_FLOW_LABEL -> normalizeHexFlowLabel(value);
            case TIMESTAMP -> normalizeTimestamp(value);
            case TEXT -> value;
        };
    }

    /** Returns human-readable allowed range/choices for UI hints. */
    public static String describeValueSpec(PingOption option) {
        if (option.kind() != PingOptionCatalog.Kind.VALUE || option.valueSpec() == null) {
            return "";
        }
        ValueSpec spec = option.valueSpec();
        return switch (spec.kind()) {
            case INT_RANGE -> spec.intRange().min() + "–" + spec.intRange().max();
            case CHOICES -> String.join(", ", spec.choices());
            case HEX_PATTERN -> "hex, до 16 байт";
            case HEX_FLOW_LABEL -> "00000–fffff";
            case TIMESTAMP -> "tsonly, tsandaddr, tsprespec h1 [h2…]";
            case TEXT -> spec.hint() != null ? spec.hint() : "";
        };
    }

    private static void validateCompatibility(Set<String> flags) {
        PingExpertCompatibility.validate(flags);
    }

    private static String normalizeIntRange(String flag, String value, PingOptionCatalog.IntRange range) {
        long parsed = parseLongValue(flag, value);
        if (parsed < range.min() || parsed > range.max()) {
            throw new ConfigError(flag + " must be between " + range.min() + " and " + range.max() + ", got " + parsed);
        }
        return formatIntValue(value, parsed);
    }

    private static String formatIntValue(String original, long parsed) {
        if (original.startsWith("0x") || original.startsWith("0X")) {
            return "0x" + Long.toHexString(parsed);
        }
        return Long.toString(parsed);
    }

    private static String normalizeChoice(String flag, String value, List<String> choices) {
        for (String choice : choices) {
            if (choice.equalsIgnoreCase(value)) {
                return choice;
            }
        }
        throw new ConfigError(flag + " must be one of: " + String.join(", ", choices) + ", got " + value);
    }

    private static String normalizeTimestamp(String value) {
        String trimmed = value.strip();
        if ("tsonly".equalsIgnoreCase(trimmed) || "tsandaddr".equalsIgnoreCase(trimmed)) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("tsprespec")) {
            String[] parts = trimmed.split("\\s+");
            if (parts.length < 2 || parts.length > 5) {
                throw new ConfigError("-T tsprespec requires 1 to 4 hop addresses");
            }
            return trimmed;
        }
        throw new ConfigError("-T must be tsonly, tsandaddr, or tsprespec host1 [host2 … host4]");
    }

    private static String normalizeHexPattern(String value) {
        if (!value.matches("(?i)[0-9a-f]{1,32}")) {
            throw new ConfigError("-p must be hex pattern (up to 16 bytes)");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static String normalizeHexFlowLabel(String value) {
        if (!value.matches("(?i)[0-9a-f]{1,5}")) {
            throw new ConfigError("-F must be hex flow label up to 20 bits");
        }
        long parsed = Long.parseLong(value, 16);
        if (parsed > 0xFFFFF) {
            throw new ConfigError("-F must fit in 20 bits (00000–fffff)");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static long parseLongValue(String flag, String value) {
        try {
            if (value.startsWith("0x") || value.startsWith("0X")) {
                return Long.parseLong(value.substring(2), 16);
            }
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new ConfigError("Invalid numeric value for " + flag + ": " + value);
        }
    }
}
