package io.pingui.probe;

import io.pingui.model.Models;
import io.pingui.model.Models.HopNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses Windows {@code tracert} stdout into hop nodes. */
final class WindowsTraceOutputParser {

    private static final Pattern WINDOWS_HOP = Pattern.compile("^\\s*(\\d+)\\s+(.+)$");
    private static final Pattern WINDOWS_IP_IN_BRACKETS = Pattern.compile("\\[(\\d{1,3}(?:\\.\\d{1,3}){3})\\]");
    private static final Pattern WINDOWS_IP6_IN_BRACKETS = Pattern.compile("\\[([0-9a-fA-F:]+)\\]");
    private static final Pattern WINDOWS_BARE_IP = Pattern.compile("(?<!\\d)(\\d{1,3}(?:\\.\\d{1,3}){3})(?!\\.\\d)");
    private static final Pattern WINDOWS_BARE_V6 =
            Pattern.compile("(?:^|\\s)((?:[0-9a-fA-F]{0,4}:){2,}[0-9a-fA-F:]{0,4})\\s*$");
    private static final Pattern WINDOWS_RTT_MS = Pattern.compile(
            "(?:<\\s*1|(\\d+(?:\\.\\d+)?))\\s*(?:ms|мс)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private WindowsTraceOutputParser() {}

    static List<HopNode> parse(List<String> lines) {
        List<HopNode> nodes = new ArrayList<>();
        for (String line : lines) {
            Matcher hopMatcher = WINDOWS_HOP.matcher(line);
            if (!hopMatcher.matches()) {
                continue;
            }
            int hop = Integer.parseInt(hopMatcher.group(1));
            String rest = hopMatcher.group(2).trim();
            if (isTimeoutLine(rest)) {
                nodes.add(Models.timeout(hop));
                continue;
            }
            String ip = extractIp(rest);
            if (ip == null) {
                continue;
            }
            Double pingMs = parseRtt(line);
            nodes.add(new HopNode(hop, ip, pingMs, false));
        }
        return nodes;
    }

    static boolean isTimeoutLine(String rest) {
        String lower = rest.toLowerCase(Locale.ROOT);
        if (lower.contains("timed out") || lower.contains("timeout") || lower.contains("перевищ")) {
            return true;
        }
        String compact = rest.replaceAll("\\s+", "");
        return compact.startsWith("*") && !compact.contains(".");
    }

    static String extractIp(String rest) {
        Matcher v6Bracket = WINDOWS_IP6_IN_BRACKETS.matcher(rest);
        String ip = null;
        while (v6Bracket.find()) {
            ip = v6Bracket.group(1).toLowerCase(Locale.ROOT);
        }
        if (ip != null) {
            return ip;
        }
        Matcher bracket = WINDOWS_IP_IN_BRACKETS.matcher(rest);
        while (bracket.find()) {
            ip = bracket.group(1);
        }
        if (ip != null) {
            return ip;
        }
        Matcher bareV6 = WINDOWS_BARE_V6.matcher(rest.trim());
        if (bareV6.find()) {
            return bareV6.group(1).toLowerCase(Locale.ROOT);
        }
        Matcher bare = WINDOWS_BARE_IP.matcher(rest);
        while (bare.find()) {
            ip = bare.group(1);
        }
        return ip;
    }

    static Double parseRtt(String line) {
        Matcher matcher = WINDOWS_RTT_MS.matcher(line);
        if (!matcher.find()) {
            return null;
        }
        String numeric = matcher.group(1);
        if (numeric != null) {
            return Double.parseDouble(numeric);
        }
        return 0.5;
    }
}
