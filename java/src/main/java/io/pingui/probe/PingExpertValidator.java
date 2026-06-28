package io.pingui.probe;

import io.pingui.config.ConfigError;
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
            PingOptionCatalog.PingOption option = PingOptionCatalog.find(token);
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
                validateValue(token, value);
                normalized.add(value);
            }
            index++;
        }
        validateCompatibility(flags);
        return List.copyOf(normalized);
    }

    private static void validateValue(String flag, String value) {
        if (value == null || value.isBlank()) {
            throw new ConfigError("Ping option " + flag + " requires a non-empty value");
        }
        switch (flag) {
            case "-s" -> {
                int size = parseInt(value, flag);
                if (size < 0 || size > 65507) {
                    throw new ConfigError("-s must be between 0 and 65507");
                }
            }
            case "-t" -> {
                int ttl = parseInt(value, flag);
                if (ttl < 1 || ttl > 255) {
                    throw new ConfigError("-t must be between 1 and 255");
                }
            }
            case "-e" -> {
                int id = parseInt(value, flag);
                if (id < 0 || id > 65535) {
                    throw new ConfigError("-e must be between 0 and 65535");
                }
            }
            case "-M" -> {
                String mode = value.toLowerCase(Locale.ROOT);
                if (!Set.of("do", "want", "probe", "dont").contains(mode)) {
                    throw new ConfigError("-M must be do, want, probe, or dont");
                }
            }
            case "-p" -> {
                if (!value.matches("(?i)[0-9a-f]{1,32}")) {
                    throw new ConfigError("-p must be hex pattern (up to 16 bytes)");
                }
            }
            case "-Q" -> {
                parseQos(value);
            }
            case "-F" -> {
                if (!value.matches("(?i)[0-9a-f]{1,5}")) {
                    throw new ConfigError("-F must be hex flow label up to 20 bits");
                }
            }
            case "-S", "-m" -> parseInt(value, flag);
            case "-T" -> {
                if (value.isBlank()) {
                    throw new ConfigError("-T requires timestamp option");
                }
            }
            default -> {
                // -I and other free-form values
            }
        }
    }

    private static void validateCompatibility(Set<String> flags) {
        if (flags.contains("-4") && flags.contains("-6")) {
            throw new ConfigError("Options -4 and -6 are mutually exclusive");
        }
        if (flags.contains("-F") && !flags.contains("-6")) {
            throw new ConfigError("Option -F requires IPv6 (-6)");
        }
        if (flags.contains("-n") && flags.contains("-H")) {
            throw new ConfigError("Options -n and -H conflict");
        }
        if (flags.contains("-r") && !flags.contains("-I")) {
            throw new ConfigError("Option -r usually requires -I (interface/source)");
        }
    }

    private static int parseInt(String value, String flag) {
        try {
            if (value.startsWith("0x") || value.startsWith("0X")) {
                return Integer.parseInt(value.substring(2), 16);
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new ConfigError("Invalid numeric value for " + flag + ": " + value);
        }
    }

    private static void parseQos(String value) {
        try {
            if (value.startsWith("0x") || value.startsWith("0X")) {
                int parsed = Integer.parseInt(value.substring(2), 16);
                if (parsed < 0 || parsed > 255) {
                    throw new ConfigError("-Q hex value must fit in one byte");
                }
                return;
            }
            int parsed = Integer.parseInt(value);
            if (parsed < 0 || parsed > 255) {
                throw new ConfigError("-Q must be between 0 and 255");
            }
        } catch (NumberFormatException ex) {
            throw new ConfigError("Invalid -Q value: " + value);
        }
    }
}
