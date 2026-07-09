package io.pingui.probe;

import io.pingui.config.ConfigError;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared expert ping flag compatibility rules (validator + UI). */
public final class PingExpertCompatibility {
    /** Mutually exclusive flag groups (only one flag per group may be active). */
    public static final List<List<String>> MUTUALLY_EXCLUSIVE = List.of(List.of("-4", "-6"), List.of("-n", "-H"));

    /** VALUE/flag options that require another flag to be set. */
    public static final Map<String, String> REQUIRES_FLAG = Map.of("-F", "-6");

    /** FLAG options that require a non-empty VALUE for another option. */
    public static final Map<String, String> REQUIRES_VALUE_FOR = Map.of("-r", "-I");

    private PingExpertCompatibility() {}

    public static void validate(Set<String> flags) {
        for (List<String> group : MUTUALLY_EXCLUSIVE) {
            long active = group.stream().filter(flags::contains).count();
            if (active > 1) {
                throw new ConfigError("Options " + group.get(0) + " and " + group.get(1) + " are mutually exclusive");
            }
        }
        for (Map.Entry<String, String> entry : REQUIRES_FLAG.entrySet()) {
            if (flags.contains(entry.getKey()) && !flags.contains(entry.getValue())) {
                throw new ConfigError("Option " + entry.getKey() + " requires IPv6 (" + entry.getValue() + ")");
            }
        }
        if (flags.contains("-r") && !flags.contains("-I")) {
            throw new ConfigError("Option -r usually requires -I (interface/source)");
        }
    }

    /** Returns the other flag in the same exclusion group, or null. */
    public static String exclusivePartner(String flag) {
        for (List<String> group : MUTUALLY_EXCLUSIVE) {
            if (group.contains(flag)) {
                return group.get(0).equals(flag) ? group.get(1) : group.get(0);
            }
        }
        return null;
    }
}
